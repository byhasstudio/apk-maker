using System.Text.Json;

namespace RemoteCtlAgent;

/// <summary>
/// Jendela untuk mengontrol perangkat LAIN (HP atau komputer Windows
/// lain) dari komputer ini. Masukkan kode sesi, lalu layar perangkat
/// itu tampil di sini dan bisa diklik/digeser dengan mouse -- padanan
/// ControlActivity di aplikasi Android.
/// </summary>
public class ControlForm : Form
{
    private readonly TextBox _codeBox;
    private readonly Button _connectBtn;
    private readonly Label _statusLabel;
    private readonly PictureBox _screen;

    private readonly FirebaseRest _firebase = new();
    private CancellationTokenSource? _cts;
    private string? _sessionCode;
    private int _remoteWidth;
    private int _remoteHeight;
    private Point _downPoint;
    private DateTime _downTime;

    public ControlForm()
    {
        Text = "Remote Ctl — Mengontrol";
        Width = 900;
        Height = 640;
        StartPosition = FormStartPosition.CenterScreen;
        UiTheme.ApplyFormChrome(this);

        var topBar = new Panel { Dock = DockStyle.Top, Height = 56, BackColor = UiTheme.Surface };

        _codeBox = new TextBox
        {
            Top = 12, Left = 16, Width = 140, Font = new Font("Segoe UI", 13),
            MaxLength = 6, TextAlign = HorizontalAlignment.Center
        };
        _codeBox.KeyPress += (_, e) => { if (!char.IsDigit(e.KeyChar) && e.KeyChar != (char)8) e.Handled = true; };

        _connectBtn = UiTheme.MakeButton("Sambung", UiTheme.Primary);
        _connectBtn.SetBounds(166, 8, 110, 36);
        _connectBtn.Click += async (_, _) => await ConnectAsync();

        _statusLabel = new Label
        {
            Top = 18, Left = 290, Width = 560,
            ForeColor = UiTheme.TextSecondary, Font = UiTheme.BodyFont
        };

        topBar.Controls.Add(_codeBox);
        topBar.Controls.Add(_connectBtn);
        topBar.Controls.Add(_statusLabel);

        _screen = new PictureBox
        {
            Dock = DockStyle.Fill,
            BackColor = Color.Black,
            SizeMode = PictureBoxSizeMode.Zoom
        };
        _screen.MouseDown += Screen_MouseDown;
        _screen.MouseUp += Screen_MouseUp;

        Controls.Add(_screen);
        Controls.Add(topBar);

        FormClosing += (_, _) => _cts?.Cancel();
    }

    private async Task ConnectAsync()
    {
        var code = _codeBox.Text.Trim();
        if (code.Length != 6)
        {
            _statusLabel.Text = "Masukkan kode 6 digit yang valid";
            _statusLabel.ForeColor = UiTheme.Danger;
            return;
        }

        _cts?.Cancel();
        _cts = new CancellationTokenSource();
        _sessionCode = code;
        _remoteWidth = 0;
        _remoteHeight = 0;

        _statusLabel.ForeColor = UiTheme.TextSecondary;
        _statusLabel.Text = "Masuk ke server...";

        if (!await _firebase.SignInAnonymouslyAsync())
        {
            _statusLabel.Text = "Gagal masuk ke server. Cek internet.";
            _statusLabel.ForeColor = UiTheme.Danger;
            return;
        }

        _statusLabel.Text = $"Menyambung ke {code}...";
        _ = MetaLoopAsync(_cts.Token);
        _ = FrameLoopAsync(_cts.Token);
    }

    private async Task MetaLoopAsync(CancellationToken token)
    {
        while (!token.IsCancellationRequested && _remoteWidth == 0)
        {
            try
            {
                var meta = await _firebase.GetAsync($"sessions/{_sessionCode}/meta");
                if (meta is JsonElement el && el.ValueKind == JsonValueKind.Object &&
                    el.TryGetProperty("width", out var w) && el.TryGetProperty("height", out var h))
                {
                    _remoteWidth = w.GetInt32();
                    _remoteHeight = h.GetInt32();
                    SetStatus($"Tersambung ({_remoteWidth} x {_remoteHeight})", UiTheme.Success);
                }
            }
            catch
            {
                // Coba lagi.
            }

            try { await Task.Delay(500, token); } catch (TaskCanceledException) { break; }
        }
    }

    private async Task FrameLoopAsync(CancellationToken token)
    {
        while (!token.IsCancellationRequested)
        {
            try
            {
                var frame = await _firebase.GetAsync($"sessions/{_sessionCode}/frame");
                if (frame is JsonElement el && el.ValueKind == JsonValueKind.Object &&
                    el.TryGetProperty("jpegBase64", out var b64El))
                {
                    var bytes = Convert.FromBase64String(b64El.GetString() ?? "");
                    using var ms = new MemoryStream(bytes);
                    var img = Image.FromStream(ms);
                    if (InvokeRequired)
                        BeginInvoke(() => { _screen.Image?.Dispose(); _screen.Image = img; });
                    else
                    {
                        _screen.Image?.Dispose();
                        _screen.Image = img;
                    }
                }
            }
            catch
            {
                // Lewati frame yang gagal, coba lagi di putaran berikutnya.
            }

            try { await Task.Delay(120, token); } catch (TaskCanceledException) { break; }
        }
    }

    private void SetStatus(string text, Color color)
    {
        if (InvokeRequired) { BeginInvoke(() => SetStatus(text, color)); return; }
        _statusLabel.Text = text;
        _statusLabel.ForeColor = color;
    }

    private void Screen_MouseDown(object? sender, MouseEventArgs e)
    {
        _downPoint = e.Location;
        _downTime = DateTime.UtcNow;
    }

    private async void Screen_MouseUp(object? sender, MouseEventArgs e)
    {
        if (_sessionCode == null || _remoteWidth == 0 || _screen.Image == null) return;

        var (dx1, dy1) = ToRemoteCoords(_downPoint);
        var (dx2, dy2) = ToRemoteCoords(e.Location);
        var durationMs = (int)(DateTime.UtcNow - _downTime).TotalMilliseconds;
        var moved = Math.Sqrt(Math.Pow(dx2 - dx1, 2) + Math.Pow(dy2 - dy1, 2));

        object cmd = moved < 10
            ? new { type = "tap", x = dx1, y = dy1 }
            : new
            {
                type = "swipe",
                x = dx1, y = dy1, x2 = dx2, y2 = dy2,
                durationMs = Math.Max(50, durationMs)
            };

        await _firebase.PutAsync($"sessions/{_sessionCode}/cmd", cmd);
    }

    /// <summary>
    /// PictureBox memakai mode Zoom (gambar diperkecil dan diberi letterbox
    /// supaya proporsinya tetap benar) -- jadi koordinat klik di layar
    /// jendela harus dihitung ulang ke koordinat asli perangkat jarak jauh.
    /// </summary>
    private (double x, double y) ToRemoteCoords(Point p)
    {
        var img = _screen.Image!;
        var boxW = (double)_screen.Width;
        var boxH = (double)_screen.Height;
        var imgW = (double)img.Width;
        var imgH = (double)img.Height;

        var scale = Math.Min(boxW / imgW, boxH / imgH);
        var offsetX = (boxW - imgW * scale) / 2.0;
        var offsetY = (boxH - imgH * scale) / 2.0;

        var relX = Math.Clamp((p.X - offsetX) / scale, 0, imgW);
        var relY = Math.Clamp((p.Y - offsetY) / scale, 0, imgH);

        return (relX / imgW * _remoteWidth, relY / imgH * _remoteHeight);
    }
}
