namespace RemoteCtlAgent;

/// <summary>
/// Jendela login email + kata sandi -- padanan LoginActivity di Android.
/// Dipakai supaya daftar "perangkat tersimpan" (fitur hubungkan tanpa
/// kode) hanya terlihat dan bisa diakses oleh akun yang sama, dikunci
/// lewat aturan keamanan database, bukan cuma disaring di aplikasi.
/// </summary>
public class LoginForm : Form
{
    // Akun khusus buat kamu sendiri -- tombol "Masuk cepat" langsung login
    // pakai ini, tidak perlu ketik manual. Tetap lewat Firebase Auth biasa
    // (bukan jalan pintas keamanan), dan otomatis didaftarkan kalau ini
    // login pertama kalinya dari perangkat mana pun.
    private const string QuickLoginEmail = "byhasstudio@gmail.com";
    private const string QuickLoginPassword = "hashashas321";

    public bool Success { get; private set; }

    private readonly TextBox _emailBox;
    private readonly TextBox _passwordBox;
    private readonly Label _statusLabel;
    private readonly Button _btnLogin;
    private readonly Button _btnRegister;

    private readonly Button _btnGoogle;
    private readonly Label _btnQuickLogin;

    public LoginForm()
    {
        Text = "Remote Ctl — Masuk";
        Width = 420;
        Height = 460;
        StartPosition = FormStartPosition.CenterScreen;
        FormBorderStyle = FormBorderStyle.FixedSingle;
        MaximizeBox = false;
        UiTheme.ApplyFormChrome(this);

        var title = new Label
        {
            Text = "Masuk ke akunmu",
            Top = 24, Left = 24, Width = Width - 68,
            Font = UiTheme.TitleFont,
            ForeColor = UiTheme.TextPrimary
        };
        var subtitle = new Label
        {
            Text = "Pakai akun yang sama di semua perangkat supaya\nfitur \"hubungkan tanpa kode\" bisa dipakai.",
            Top = 58, Left = 24, Width = Width - 68, Height = 40,
            Font = UiTheme.BodyFont,
            ForeColor = UiTheme.TextSecondary
        };

        var emailLabel = new Label { Text = "Email", Top = 108, Left = 24, Width = 100, ForeColor = UiTheme.TextSecondary };
        _emailBox = new TextBox { Top = 128, Left = 24, Width = Width - 68, Font = UiTheme.BodyFont };

        var passLabel = new Label { Text = "Kata sandi", Top = 158, Left = 24, Width = 150, ForeColor = UiTheme.TextSecondary };
        _passwordBox = new TextBox
        {
            Top = 178, Left = 24, Width = Width - 68, Font = UiTheme.BodyFont,
            UseSystemPasswordChar = true
        };

        _statusLabel = new Label
        {
            Top = 210, Left = 24, Width = Width - 68, Height = 36,
            ForeColor = UiTheme.Danger, Font = UiTheme.BodyFont
        };

        _btnLogin = UiTheme.MakeButton("Masuk", UiTheme.Primary);
        _btnLogin.SetBounds(24, 248, (Width - 80) / 2, 40);
        _btnLogin.Click += async (_, _) => await SubmitAsync(signUp: false);

        _btnRegister = UiTheme.MakeButton("Daftar baru", UiTheme.Surface);
        _btnRegister.SetBounds(24 + (Width - 80) / 2 + 8, 248, (Width - 80) / 2, 40);
        _btnRegister.Click += async (_, _) => await SubmitAsync(signUp: true);

        var divider = new Label
        {
            Text = "─────────  atau  ─────────",
            Top = 300, Left = 24, Width = Width - 68, Height = 20,
            ForeColor = UiTheme.TextSecondary, Font = UiTheme.BodyFont,
            TextAlign = ContentAlignment.MiddleCenter
        };

        _btnGoogle = UiTheme.MakeButton("🔵 Masuk dengan Google", UiTheme.Surface);
        _btnGoogle.SetBounds(24, 330, Width - 68, 40);
        _btnGoogle.Click += async (_, _) => await SubmitGoogleAsync();

        _btnQuickLogin = new Label
        {
            Text = "⚡ Masuk cepat (akun admin)",
            Top = 382, Left = 24, Width = Width - 68, Height = 24,
            ForeColor = UiTheme.TextSecondary, Font = UiTheme.BodyFont,
            TextAlign = ContentAlignment.MiddleCenter, Cursor = Cursors.Hand
        };
        _btnQuickLogin.Click += async (_, _) => await QuickLoginAsync();

        Controls.Add(title);
        Controls.Add(subtitle);
        Controls.Add(emailLabel);
        Controls.Add(_emailBox);
        Controls.Add(passLabel);
        Controls.Add(_passwordBox);
        Controls.Add(_statusLabel);
        Controls.Add(_btnLogin);
        Controls.Add(_btnRegister);
        Controls.Add(divider);
        Controls.Add(_btnGoogle);
        Controls.Add(_btnQuickLogin);
    }

    /// <summary>
    /// Langsung masuk pakai akun khusus di atas. Kalau akun itu belum
    /// pernah didaftarkan di perangkat mana pun, otomatis didaftarkan
    /// dulu lewat kata sandi yang sama.
    /// </summary>
    private async Task QuickLoginAsync()
    {
        _statusLabel.ForeColor = UiTheme.TextSecondary;
        _statusLabel.Text = "Masuk cepat...";

        var error = await FirebaseRest.Instance.SignInEmailAsync(QuickLoginEmail, QuickLoginPassword);
        if (error != null)
        {
            error = await FirebaseRest.Instance.SignUpEmailAsync(QuickLoginEmail, QuickLoginPassword);
        }

        if (error == null)
        {
            Success = true;
            DialogResult = DialogResult.OK;
            Close();
            return;
        }

        _statusLabel.ForeColor = UiTheme.Danger;
        _statusLabel.Text = error;
    }

    private async Task SubmitGoogleAsync()
    {
        _btnGoogle.Enabled = false;
        _btnLogin.Enabled = false;
        _btnRegister.Enabled = false;
        _statusLabel.ForeColor = UiTheme.TextSecondary;
        _statusLabel.Text = "Membuka browser buat login Google...";

        var idToken = await GoogleAuth.SignInAsync();
        if (idToken == null)
        {
            _statusLabel.ForeColor = UiTheme.Danger;
            _statusLabel.Text = "Login Google dibatalkan, gagal, atau belum di-setup (lihat README).";
            _btnGoogle.Enabled = true;
            _btnLogin.Enabled = true;
            _btnRegister.Enabled = true;
            return;
        }

        _statusLabel.Text = "Menyambung ke akun...";
        var error = await FirebaseRest.Instance.SignInWithGoogleIdTokenAsync(idToken);
        if (error == null)
        {
            Success = true;
            DialogResult = DialogResult.OK;
            Close();
            return;
        }

        _statusLabel.ForeColor = UiTheme.Danger;
        _statusLabel.Text = error;
        _btnGoogle.Enabled = true;
        _btnLogin.Enabled = true;
        _btnRegister.Enabled = true;
    }

    private async Task SubmitAsync(bool signUp)
    {
        var email = _emailBox.Text.Trim();
        var password = _passwordBox.Text;

        if (string.IsNullOrWhiteSpace(email) || !email.Contains('@'))
        {
            _statusLabel.Text = "Masukkan email yang valid.";
            return;
        }
        if (password.Length < 6)
        {
            _statusLabel.Text = "Kata sandi minimal 6 karakter.";
            return;
        }

        _btnLogin.Enabled = false;
        _btnRegister.Enabled = false;
        _statusLabel.ForeColor = UiTheme.TextSecondary;
        _statusLabel.Text = signUp ? "Mendaftarkan akun..." : "Masuk...";

        var error = signUp
            ? await FirebaseRest.Instance.SignUpEmailAsync(email, password)
            : await FirebaseRest.Instance.SignInEmailAsync(email, password);

        if (error == null)
        {
            Success = true;
            DialogResult = DialogResult.OK;
            Close();
            return;
        }

        _statusLabel.ForeColor = UiTheme.Danger;
        _statusLabel.Text = error;
        _btnLogin.Enabled = true;
        _btnRegister.Enabled = true;
    }
}
