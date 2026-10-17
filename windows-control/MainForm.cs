using System.Text.Json;

namespace RemoteCtlAgent;

/// <summary>
/// Jendela utama: menampilkan kode sesi 6 digit dan status koneksi.
/// Sengaja dibuat sangat sederhana -- satu tombol, dua label -- karena
/// tujuannya hanya dijalankan sekali lalu dibiarkan jalan di latar
/// belakang selagi menerima kontrol dari HP.
/// </summary>
public class MainForm : Form
{
    private readonly Label _codeLabel;
    private readonly Label _statusLabel;
    private readonly FirebaseRest _firebase = new();
    private string _sessionCode = "------";
    private long _lastCommandTs;
    private CancellationTokenSource? _cts;

    public MainForm()
    {
        Text = "Remote Ctl - Perangkat ini";
        Width = 420;
        Height = 220;
        StartPosition = FormStartPosition.CenterScreen;

        var title = new Label
        {
            Text = "Kode sesi:",
            Top = 20, Left = 20, Width = 380, Font = new Font("Segoe UI", 11)
        };
        _codeLabel = new Label
        {
            Text = _sessionCode,
            Top = 45, Left = 20, Width = 380, Height = 60,
            Font = new Font("Segoe UI", 32, FontStyle.Bold),
            TextAlign = ContentAlignment.MiddleLeft
        };
        _statusLabel = new Label
        {
            Text = "Menyiapkan...",
            Top = 115, Left = 20, Width = 380, Font = new Font("Segoe UI", 10)
        };

        Controls.Add(title);
        Controls.Add(_codeLabel);
        Controls.Add(_statusLabel);

        Load += async (_, _) => await StartAsync();
        FormClosing += (_, _) => _cts?.Cancel();
    }

    private async Task StartAsync()
    {
        _statusLabel.Text = "Masuk ke server...";
        var signedIn = await _firebase.SignInAnonymouslyAsync();
        if (!signedIn)
        {
            _statusLabel.Text = "Gagal masuk ke server. Cek koneksi internet.";
            return;
        }

        _sessionCode = new Random().Next(100000, 999999).ToString();
        _codeLabel.Text = _sessionCode;
        _statusLabel.Text = "Aktif. Bagikan kode ini ke perangkat pengontrol.";

        _cts = new CancellationTokenSource();
        _ = CaptureLoopAsync(_cts.Token);
        _ = CommandLoopAsync(_cts.Token);
    }

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

            await Task.Delay(400, token).ContinueWith(_ => { });
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

            await Task.Delay(150, token).ContinueWith(_ => { });
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
