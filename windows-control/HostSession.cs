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

    // Isi kolom ketik terakhir yang diketahui dari perangkat pengontrol --
    // dipakai buat menghitung selisih (huruf apa yang ditambah/dihapus)
    // tiap kali perintah "settext" baru datang, supaya bisa diterjemahkan
    // jadi tombol backspace + ketik asli lewat SendInput (Windows tidak
    // punya cara "timpa kolom yang sedang fokus" langsung seperti
    // AccessibilityService di Android).
    private string _lastTypedText = "";

    /// <summary>
    /// Dipanggil kalau menjalankan satu perintah (tap/swipe/key/teks) gagal
    /// -- dulu kegagalan ini DITELAN diam-diam di CommandLoopAsync, jadi
    /// dari sisi pengguna rasanya "diklik tapi tidak ngefek apa-apa" tanpa
    /// penjelasan sama sekali. Sekarang dilaporkan lewat event ini supaya
    /// HostForm bisa menampilkannya di status (lihat InputSimulator.Send
    /// untuk penyebab paling umum: UIPI karena ada jendela Administrator aktif).
    /// </summary>
    public event Action<string>? OnCommandError;

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
                        try
                        {
                            await HandleCommandAsync(el);
                        }
                        catch (Exception ex)
                        {
                            // Perintah ini sendiri gagal dijalankan (lihat
                            // InputSimulator.Send) -- laporkan, tapi JANGAN
                            // hentikan loop supaya perintah berikutnya tetap dicoba.
                            OnCommandError?.Invoke(ex.Message);
                        }
                    }
                }
            }
            catch
            {
                // Gagal mengambil/membaca perintahnya sendiri (mis. jaringan
                // putus sesaat) -- bukan kegagalan menjalankan perintah,
                // cukup dicoba lagi di putaran berikutnya.
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
            case "settext":
                ApplyTypedTextDiff(cmd.TryGetProperty("value", out var v) ? v.GetString() ?? "" : "");
                break;
            case "key":
                var key = cmd.TryGetProperty("key", out var k) ? k.GetString() : null;
                switch (key)
                {
                    case "ENTER":
                        InputSimulator.SendSpecialKey(InputSimulator.VK_RETURN);
                        break;
                    case "BACKSPACE":
                        InputSimulator.SendSpecialKey(InputSimulator.VK_BACK);
                        if (_lastTypedText.Length > 0)
                            _lastTypedText = _lastTypedText[..^1];
                        break;
                }
                break;
        }
    }

    /// <summary>
    /// Ubah isi kolom ketik terakhir ("" di awal) jadi `value` lewat
    /// keystroke asli: hapus bagian yang sudah tidak sama dulu (backspace),
    /// lalu ketik bagian yang baru -- hasilnya terasa seperti mengetik
    /// langsung di keyboard komputer ini.
    /// </summary>
    private void ApplyTypedTextDiff(string value)
    {
        var old = _lastTypedText;
        var commonLen = 0;
        var maxCommon = Math.Min(old.Length, value.Length);
        while (commonLen < maxCommon && old[commonLen] == value[commonLen]) commonLen++;

        var backspaces = old.Length - commonLen;
        for (var i = 0; i < backspaces; i++) InputSimulator.SendSpecialKey(InputSimulator.VK_BACK);

        var toType = value[commonLen..];
        if (toType.Length > 0) InputSimulator.TypeText(toType);

        _lastTypedText = value;
    }
}
