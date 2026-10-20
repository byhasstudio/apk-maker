namespace RemoteCtlAgent;

/// <summary>
/// Jendela login username + kata sandi biasa (bebas ketik, bukan email
/// Gmail) -- padanan LoginActivity di Android. Satu jendela yang
/// tampilannya berubah tergantung mode -- "Masuk" dan "Daftar" punya
/// judul, teks tombol, dan link toggle yang beda, supaya kelihatan
/// jelas lagi di mode mana.
///
/// Dipakai supaya daftar "perangkat tersimpan" (fitur hubungkan tanpa
/// kode) hanya terlihat dan bisa diakses oleh akun yang sama, dikunci
/// lewat aturan keamanan database, bukan cuma disaring di aplikasi.
///
/// Firebase Auth di baliknya tetap jalan lewat email/password seperti
/// biasa, tapi username yang diketik user diubah otomatis jadi alamat
/// email palsu internal (mis. "hass" -> "hass@remotectl.local"), sama
/// persis skemanya dengan LoginActivity.kt di Android -- supaya akun
/// yang sama bisa dipakai login dari kedua aplikasi.
/// </summary>
public class LoginForm : Form
{
    // Domain palsu dipakai internal supaya Firebase Auth (yang sebetulnya
    // berbasis email/password) bisa menerima username bebas tanpa minta
    // email Gmail asli -- harus SAMA PERSIS dengan FAKE_EMAIL_DOMAIN di
    // LoginActivity.kt (Android) supaya akun yang sama dikenali di kedua
    // aplikasi.
    private const string FakeEmailDomain = "@remotectl.local";

    /// <summary>Ubah username bebas jadi "email" internal yang valid buat Firebase.</summary>
    private static string UsernameToFakeEmail(string username)
    {
        var sanitized = System.Text.RegularExpressions.Regex.Replace(
            username.Trim().ToLowerInvariant(), "[^a-z0-9._-]", "_");
        return sanitized + FakeEmailDomain;
    }

    public bool Success { get; private set; }

    // Mode saat ini -- false = Masuk (login), true = Daftar (buat akun baru).
    private bool _isRegisterMode;

    private readonly Label _title;
    private readonly Label _subtitle;
    private readonly TextBox _usernameBox;
    private readonly TextBox _passwordBox;
    private readonly Button _btnTogglePassword;
    private readonly Label _statusLabel;
    private readonly Button _btnPrimary;
    private readonly Label _txtToggleMode;

    public LoginForm()
    {
        Text = "Remote Ctl — Masuk";
        Width = 420;
        Height = 360;
        StartPosition = FormStartPosition.CenterScreen;
        FormBorderStyle = FormBorderStyle.FixedSingle;
        MaximizeBox = false;
        UiTheme.ApplyFormChrome(this);

        _title = new Label
        {
            Top = 24, Left = 24, Width = Width - 68,
            Font = UiTheme.TitleFont,
            ForeColor = UiTheme.TextPrimary
        };
        _subtitle = new Label
        {
            Top = 58, Left = 24, Width = Width - 68, Height = 44,
            Font = UiTheme.BodyFont,
            ForeColor = UiTheme.TextSecondary
        };

        var usernameLabel = new Label { Text = "Username", Top = 112, Left = 24, Width = 150, ForeColor = UiTheme.TextSecondary };
        _usernameBox = new TextBox { Top = 132, Left = 24, Width = Width - 68, Font = UiTheme.BodyFont };

        var passLabel = new Label { Text = "Kata sandi", Top = 162, Left = 24, Width = 150, ForeColor = UiTheme.TextSecondary };

        // Kolom kata sandi dipersempit supaya ada tempat buat tombol "lihat"
        // di sebelah kanannya.
        const int togglePasswordWidth = 40;
        _passwordBox = new TextBox
        {
            Top = 182, Left = 24, Width = Width - 68 - togglePasswordWidth - 8, Font = UiTheme.BodyFont,
            UseSystemPasswordChar = true
        };

        // Tombol mata buat intip/sembunyikan kata sandi yang lagi diketik,
        // supaya user bisa mastiin benar sebelum submit.
        _btnTogglePassword = new Button
        {
            Top = 180, Left = _passwordBox.Left + _passwordBox.Width + 8, Width = togglePasswordWidth, Height = 26,
            Text = "👁", Font = UiTheme.BodyFont,
            FlatStyle = FlatStyle.Flat, TabStop = false,
            ForeColor = UiTheme.TextSecondary
        };
        _btnTogglePassword.FlatAppearance.BorderSize = 0;
        _btnTogglePassword.Click += (_, _) =>
        {
            _passwordBox.UseSystemPasswordChar = !_passwordBox.UseSystemPasswordChar;
            _btnTogglePassword.Text = _passwordBox.UseSystemPasswordChar ? "👁" : "🙈";
        };

        _statusLabel = new Label
        {
            Top = 214, Left = 24, Width = Width - 68, Height = 36,
            ForeColor = UiTheme.Danger, Font = UiTheme.BodyFont
        };

        _btnPrimary = UiTheme.MakeButton("Masuk", UiTheme.Primary);
        _btnPrimary.SetBounds(24, 252, Width - 68, 40);
        _btnPrimary.Click += async (_, _) => await SubmitAsync();

        _txtToggleMode = new Label
        {
            Top = 302, Left = 24, Width = Width - 68, Height = 24,
            ForeColor = UiTheme.TextSecondary, Font = UiTheme.BodyFont,
            TextAlign = ContentAlignment.MiddleCenter, Cursor = Cursors.Hand
        };
        _txtToggleMode.Click += (_, _) =>
        {
            _isRegisterMode = !_isRegisterMode;
            _statusLabel.Text = "";
            ApplyModeToControls();
        };

        ApplyModeToControls();

        Controls.Add(_title);
        Controls.Add(_subtitle);
        Controls.Add(usernameLabel);
        Controls.Add(_usernameBox);
        Controls.Add(passLabel);
        Controls.Add(_passwordBox);
        Controls.Add(_btnTogglePassword);
        Controls.Add(_statusLabel);
        Controls.Add(_btnPrimary);
        Controls.Add(_txtToggleMode);
    }

    /// <summary>Ganti judul, subjudul, teks tombol, dan link toggle sesuai mode saat ini.</summary>
    private void ApplyModeToControls()
    {
        if (_isRegisterMode)
        {
            _title.Text = "Buat akun baru";
            _subtitle.Text = "Satu akun ini dipakai di semua perangkatmu supaya\nfitur \"hubungkan tanpa kode\" bisa jalan.";
            _btnPrimary.Text = "Daftar";
            _txtToggleMode.Text = "Sudah punya akun? Masuk";
        }
        else
        {
            _title.Text = "Masuk ke akunmu";
            _subtitle.Text = "Pakai akun yang sama di semua perangkat supaya\nfitur \"hubungkan tanpa kode\" bisa dipakai.";
            _btnPrimary.Text = "Masuk";
            _txtToggleMode.Text = "Belum punya akun? Daftar";
        }
    }

    private async Task SubmitAsync()
    {
        var username = _usernameBox.Text.Trim();
        var password = _passwordBox.Text;

        if (string.IsNullOrWhiteSpace(username))
        {
            _statusLabel.Text = "Masukkan username.";
            return;
        }
        if (password.Length < 6)
        {
            _statusLabel.Text = "Kata sandi minimal 6 karakter.";
            return;
        }

        _btnPrimary.Enabled = false;
        _txtToggleMode.Enabled = false;
        _statusLabel.ForeColor = UiTheme.TextSecondary;
        _statusLabel.Text = _isRegisterMode ? "Mendaftarkan akun..." : "Masuk...";

        var fakeEmail = UsernameToFakeEmail(username);
        var error = _isRegisterMode
            ? await FirebaseRest.Instance.SignUpEmailAsync(fakeEmail, password)
            : await FirebaseRest.Instance.SignInEmailAsync(fakeEmail, password);

        if (error == null)
        {
            Success = true;
            DialogResult = DialogResult.OK;
            Close();
            return;
        }

        _statusLabel.ForeColor = UiTheme.Danger;
        _statusLabel.Text = error;
        _btnPrimary.Enabled = true;
        _txtToggleMode.Enabled = true;
    }
}
