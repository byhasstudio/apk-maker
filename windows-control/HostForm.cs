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
    private HostSession? _session;

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
        FormClosing += (_, _) => _session?.Stop();
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

        _session = new HostSession(_firebase, _sessionCode);
        _session.Start();
    }
}
