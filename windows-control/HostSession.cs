using System.Text.Json;

namespace RemoteCtlAgent;

/// <summary>
/// Logika inti "jadi yang dikontrol" (kirim cuplikan layar, terima
/// perintah tap/swipe) dipisah dari HostForm supaya bisa dipakai juga
/// tanpa jendela terbuka -- yaitu saat permintaan "hubungkan tanpa kode"
/// datang lewat PresenceService.
/// </summary>
public class HostSession
{
    private readonly FirebaseRest _firebase;
    private readonly string _sessionCode;
    private long _lastCommandTs;
    private CancellationTokenSource? _cts;

    public HostSession(FirebaseRest firebase, string sessionCode)
    {
        _firebase = firebase;
        _sessionCode = sessionCode;
    }

    public void Start()
    {
        _cts = new CancellationTokenSource();
        _ = CaptureLoopAsync(_cts.Token);
        _ = CommandLoopAsync(_cts.Token);
    }

    public void Stop() => _cts?.Cancel();

    private async Task CaptureLoopAsync(CancellationToken token)
    {
        var metaSent = false;
        while (!token.IsCancellationRequested)
        {
            try
            {
                var (b64, w, h) = ScreenCapture.CaptureJpegBase64();
                if (!metaSent)
                {
                    await _firebase.PutAsync($"sessions/{_sessionCode}/meta", new
                    {
                        width = w,
                        height = h,
                        createdAt = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),
                        active = true
                    });
                    metaSent = true;
                }
                await _firebase.PutAsync($"sessions/{_sessionCode}/frame", new
                {
                    jpegBase64 = b64,
                    ts = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
                });
            }
            catch
            {
                // Satu cuplikan gagal tidak boleh menghentikan loop -- coba lagi di putaran berikutnya.
            }

            try { await Task.Delay(120, token); } catch (TaskCanceledException) { break; }
        }
    }

    private async Task CommandLoopAsync(CancellationToken token)
    {
        while (!token.IsCancellationRequested)
        {
            try
            {
                var cmd = await _firebase.GetAsync($"sessions/{_sessionCode}/cmd");
                if (cmd is JsonElement el && el.ValueKind == JsonValueKind.Object)
                {
                    var ts = el.TryGetProperty("ts", out var tsEl) ? tsEl.GetInt64() : 0;
                    if (ts > _lastCommandTs)
                    {
                        _lastCommandTs = ts;
                        await HandleCommandAsync(el);
                    }
                }
            }
            catch
            {
                // Lewati, coba lagi di putaran berikutnya.
            }

            try { await Task.Delay(80, token); } catch (TaskCanceledException) { break; }
        }
    }

    private async Task HandleCommandAsync(JsonElement cmd)
    {
        var type = cmd.TryGetProperty("type", out var t) ? t.GetString() : null;
        switch (type)
        {
            case "tap":
                InputSimulator.Tap(
                    (int)cmd.GetProperty("x").GetDouble(),
                    (int)cmd.GetProperty("y").GetDouble());
                break;
            case "swipe":
                await InputSimulator.SwipeAsync(
                    (int)cmd.GetProperty("x").GetDouble(),
                    (int)cmd.GetProperty("y").GetDouble(),
                    (int)cmd.GetProperty("x2").GetDouble(),
                    (int)cmd.GetProperty("y2").GetDouble(),
                    cmd.TryGetProperty("durationMs", out var d) ? (int)d.GetDouble() : 200);
                break;
        }
    }
}
