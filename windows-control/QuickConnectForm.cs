namespace RemoteCtlAgent;

/// <summary>
/// Pengaturan "hubungkan tanpa kode" untuk komputer ini -- padanan
/// QuickConnectSettingsActivity di Android. Mengaktifkan fitur ini
/// membuat komputer muncul di daftar perangkat akun yang sama, dan
/// mendaftarkan program untuk berjalan di tray + menyala otomatis saat
/// Windows start (padanan foreground service + BootReceiver Android).
/// </summary>
public class QuickConnectForm : Form
{
    private readonly CheckBox _chkEnable;
    private readonly RadioButton _radioNoPassword;
    private readonly RadioButton _radioWithPassword;
    private readonly TextBox _passwordBox;
    private readonly Label _statusLabel;
    private readonly Button _btnSave;
    private readonly CheckBox _chkAutostart;

    public QuickConnectForm()
    {
        Text = "Hubungkan tanpa kode";
        Width = 460;
        Height = 420;
        StartPosition = FormStartPosition.CenterScreen;
        FormBorderStyle = FormBorderStyle.FixedSingle;
        MaximizeBox = false;
        UiTheme.ApplyFormChrome(this);

        var title = new Label
        {
            Text = "Hubungkan tanpa kode",
            Top = 20, Left = 24, Width = Width - 68,
            Font = UiTheme.TitleFont, ForeColor = UiTheme.TextPrimary
        };
        var desc = new Label
        {
            Text = "Kalau diaktifkan, komputer ini muncul di \"Perangkat tersimpan\"\nsaat kamu buka Kontrol perangkat lain dari akun yang sama --\ntinggal pilih dan masukkan kata sandi, tanpa salin kode.",
            Top = 54, Left = 24, Width = Width - 68, Height = 56,
            Font = UiTheme.BodyFont, ForeColor = UiTheme.TextSecondary
        };

        _chkEnable = new CheckBox
        {
            Text = "Aktifkan fitur ini",
            Top = 120, Left = 24, Width = Width - 68,
            ForeColor = UiTheme.TextPrimary, Font = UiTheme.BodyFont
        };

        _radioNoPassword = new RadioButton
        {
            Text = "Tanpa kata sandi — langsung konek (aman, hanya akun ini yang bisa lihat)",
            Top = 156, Left = 24, Width = Width - 68,
            ForeColor = UiTheme.TextPrimary, Font = UiTheme.BodyFont
        };
        _radioWithPassword = new RadioButton
        {
            Text = "Pakai kata sandi tambahan",
            Top = 184, Left = 24, Width = Width - 68,
            ForeColor = UiTheme.TextPrimary, Font = UiTheme.BodyFont,
            Checked = true
        };

        _passwordBox = new TextBox
        {
            Top = 212, Left = 24, Width = Width - 68, Font = UiTheme.BodyFont,
            UseSystemPasswordChar = true,
            PlaceholderText = "Kata sandi komputer ini (minimal 6 karakter)"
        };

        _chkAutostart = new CheckBox
        {
            Text = "Nyalakan otomatis saat Windows start (disarankan)",
            Top = 250, Left = 24, Width = Width - 68,
            ForeColor = UiTheme.TextPrimary, Font = UiTheme.BodyFont,
            Checked = true
        };

        _statusLabel = new Label
        {
            Top = 282, Left = 24, Width = Width - 68, Height = 40,
            ForeColor = UiTheme.TextSecondary, Font = UiTheme.BodyFont
        };

        _btnSave = UiTheme.MakeButton("Simpan", UiTheme.Primary);
        _btnSave.SetBounds(24, 330, Width - 68, 40);
        _btnSave.Click += async (_, _) => await SaveAsync();

        Controls.Add(title);
        Controls.Add(desc);
        Controls.Add(_chkEnable);
        Controls.Add(_radioNoPassword);
        Controls.Add(_radioWithPassword);
        Controls.Add(_passwordBox);
        Controls.Add(_chkAutostart);
        Controls.Add(_statusLabel);
        Controls.Add(_btnSave);

        Load += (_, _) => LoadCurrentState();
    }

    private void LoadCurrentState()
    {
        _chkEnable.Checked = QuickConnectPrefs.IsEnabled;
        _chkAutostart.Checked = StartupManager.IsEnabled();
        if (!QuickConnectPrefs.RequiresPassword) _radioNoPassword.Checked = true;
    }

    private async Task SaveAsync()
    {
        if (!FirebaseRest.Instance.IsSignedIn)
        {
            _statusLabel.ForeColor = UiTheme.Danger;
            _statusLabel.Text = "Masuk dulu dengan akunmu sebelum mengaktifkan fitur ini.";
            return;
        }

        _btnSave.Enabled = false;
        _statusLabel.ForeColor = UiTheme.TextSecondary;
        _statusLabel.Text = "Menyimpan...";

        var enabled = _chkEnable.Checked;
        var requiresPassword = _radioWithPassword.Checked;

        if (enabled && requiresPassword && _passwordBox.Text.Length < 6)
        {
            _statusLabel.ForeColor = UiTheme.Danger;
            _statusLabel.Text = "Kata sandi minimal 6 karakter.";
            _btnSave.Enabled = true;
            return;
        }

        if (enabled)
        {
            // Hasil SUNGGUHAN dari server, bukan diasumsikan berhasil --
            // kalau ditolak (mis. aturan keamanan database belum
            // ter-publish dengan benar di Firebase Console), pengguna
            // langsung lihat pesan errornya di sini.
            var error = await DeviceStore.EnableQuickConnectAsync(
                requiresPassword, requiresPassword ? _passwordBox.Text : null);
            if (error != null)
            {
                _statusLabel.ForeColor = UiTheme.Danger;
                _statusLabel.Text = $"Gagal menyimpan ke server: {error}\n\n" +
                    "Kemungkinan besar aturan keamanan database (database.rules.json) " +
                    "belum ter-publish dengan benar di Firebase Console -- lihat README.";
                _btnSave.Enabled = true;
                return;
            }
            QuickConnectPrefs.Save(true, requiresPassword);
            PresenceService.Instance.Start();
        }
        else
        {
            // Cuma matikan akses "siapa saja boleh konek" -- PresenceService
            // TETAP jalan, karena komputer ini masih harus terlihat & bisa
            // diakses akun admin (DeviceStore.AdminEmail) selama masih
            // login. Layanan ini cuma berhenti total lewat tombol "🚪
            // Keluar" di jendela utama.
            await DeviceStore.DisableQuickConnectAsync();
            QuickConnectPrefs.Save(false, requiresPassword);
        }

        if (_chkAutostart.Checked) StartupManager.Enable();
        else StartupManager.Disable();

        _statusLabel.ForeColor = UiTheme.Success;
        _statusLabel.Text = "Tersimpan.";
        _btnSave.Enabled = true;
    }
}
