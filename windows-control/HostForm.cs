using System.Text.Json;

namespace RemoteCtlAgent;

/// <summary>
/// Jendela untuk komputer yang AKAN DIKONTROL: menampilkan kode sesi
/// 6 digit, lalu mengirim cuplikan layar berkala dan menjalankan
/// perintah klik/geser yang diterima dari perangkat pengontrol.
/// </summary>
public class HostForm : Form
{
    private readonly Label _codeLabel;
    private readonly Label _statusLabel;
    private readonly Label _dot;
    private readonly FirebaseRest _firebase = new();
    private string _sessionCode = "------";
    private long _lastCommandTs;
    private CancellationTokenSource? _cts;

    public HostForm()
    {
        Text = "Remote Ctl — Dikontrol";
        Width = 460;
        Height = 300;
        StartPosition = FormStartPosition.CenterScreen;
        FormBorderStyle = FormBorderStyle.FixedSingle;
        MaximizeBox = false;
        UiTheme.ApplyFormChrome(this);

        var card = new Panel
        {
            BackColor = UiTheme.Surface
        };
        card.SetBounds(24, 24, Width - 72, Height - 100);

        var title = new Label
        {
            Text = "KODE SESI",
            Top = 24, Left = 0, Width = card.Width,
            Font = new Font("Segoe UI", 9, FontStyle.Bold),
            ForeColor = UiTheme.TextSecondary,
            TextAlign = ContentAlignment.MiddleCenter
        };
        _codeLabel = new Label
        {
            Text = _sessionCode,
            Top = 48, Left = 0, Width = card.Width, Height = 70,
            Font = UiTheme.CodeFont,
            ForeColor = UiTheme.TextPrimary,
            TextAlign = ContentAlignment.MiddleCenter
        };

        _dot = new Label
        {
            Text = "●",
            Top = 128, Left = card.Width / 2 - 100, Width = 20,
            ForeColor = UiTheme.TextSecondary,
            Font = new Font("Segoe UI", 9)
        };
        _statusLabel = new Label
        {
            Text = "Menyiapkan...",
            Top = 128, Left = card.Width / 2 - 78, Width = 240,
            Font = UiTheme.BodyFont,
            ForeColor = UiTheme.TextSecondary
        };

        card.Controls.Add(title);
        card.Controls.Add(_codeLabel);
        card.Controls.Add(_dot);
        card.Controls.Add(_statusLabel);
        Controls.Add(card);

        Load += async (_, _) => await StartAsync();
        FormClosing += (_, _) => _cts?.Cancel();
    }

    private void SetStatus(string text, Color color)
    {
        if (InvokeRequired) { BeginInvoke(() => SetStatus(text, color)); return; }
        _statusLabel.Text = text;
        _dot.ForeColor = color;
    }

    private async Task StartAsync()
    {
        SetStatus("Masuk ke server...", UiTheme.TextSecondary);
        var signedIn = await _firebase.SignInAnonymouslyAsync();
        if (!signedIn)
        {
            SetStatus("Gagal masuk ke server. Cek internet.", UiTheme.Danger);
            return;
        }

        _sessionCode = new Random().Next(100000, 999999).ToString();
        if (InvokeRequired) BeginInvoke(() => _codeLabel.Text = _sessionCode);
        else _codeLabel.Text = _sessionCode;

        SetStatus("Aktif — bagikan kode ke pengontrol", UiTheme.Success);

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
