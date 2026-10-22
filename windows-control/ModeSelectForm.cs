namespace RemoteCtlAgent;

/// <summary>
/// Jendela pertama yang muncul: pilih apakah komputer ini mau jadi
/// yang dikontrol, atau dipakai untuk mengontrol perangkat lain --
/// persis padanan MainActivity di aplikasi Android.
/// </summary>
public class ModeSelectForm : Form
{
    private readonly NotifyIcon _tray;
    private readonly Label _accountLabel;
    private readonly Label _btnLogout;
    private bool _allowClose;

    // Dipakai bareng SetVisibleCore di bawah supaya jendela ini BENAR-BENAR
    // tidak pernah tampil ke layar sama sekali kalau dinyalakan otomatis
    // lewat Windows startup -- murni jalan diam-diam di tray, bukan sempat
    // kebuka sekilas lalu disembunyikan lagi (yang sebelumnya terjadi kalau
    // Hide() dipanggil setelah jendelanya sempat ditampilkan).
    private readonly bool _startHidden;
    private bool _everAllowedToShow;

    public ModeSelectForm(bool startHidden = false)
    {
        _startHidden = startHidden;
        _everAllowedToShow = !startHidden;
        Text = "Remote Ctl";
        Width = 440;
        Height = 380;
        StartPosition = FormStartPosition.CenterScreen;
        FormBorderStyle = FormBorderStyle.FixedSingle;
        MaximizeBox = false;
        UiTheme.ApplyFormChrome(this);

        _tray = new NotifyIcon
        {
            Icon = SystemIcons.Application,
            Text = "Remote Ctl",
            Visible = true
        };
        var trayMenu = new ContextMenuStrip();
        trayMenu.Items.Add("Buka Remote Ctl", null, (_, _) => ShowFromTray());
        trayMenu.Items.Add("⚙ Pengaturan hubungkan tanpa kode", null, (_, _) => { ShowFromTray(); OpenQuickConnectSettings(); });
        trayMenu.Items.Add(new ToolStripSeparator());
        trayMenu.Items.Add("Keluar", null, (_, _) => { _allowClose = true; _tray.Visible = false; Application.Exit(); });
        _tray.ContextMenuStrip = trayMenu;
        _tray.DoubleClick += (_, _) => ShowFromTray();

        var title = new Label
        {
            Text = "Remote Ctl",
            Top = 36, Left = 0, Width = Width - 20,
            Font = UiTheme.TitleFont,
            ForeColor = UiTheme.TextPrimary,
            TextAlign = ContentAlignment.MiddleCenter
        };
        var subtitle = new Label
        {
            Text = "Kontrol perangkat dari jarak jauh",
            Top = 72, Left = 0, Width = Width - 20,
            Font = UiTheme.BodyFont,
            ForeColor = UiTheme.TextSecondary,
            TextAlign = ContentAlignment.MiddleCenter
        };

        var btnHost = UiTheme.MakeButton("Jadikan komputer ini yang dikontrol", UiTheme.Primary);
        btnHost.SetBounds(32, 130, Width - 84, 44);
        btnHost.Click += (_, _) =>
        {
            Hide();
            new HostForm().ShowDialog(this);
            Show();
        };

        var btnControl = UiTheme.MakeButton("Kontrol perangkat lain", UiTheme.Surface);
        btnControl.SetBounds(32, 184, Width - 84, 44);
        btnControl.Click += (_, _) =>
        {
            Hide();
            new ControlForm().ShowDialog(this);
            Show();
        };

        _accountLabel = new Label
        {
            Top = 238, Left = 32, Width = Width - 84, Height = 20,
            ForeColor = UiTheme.TextSecondary, Font = UiTheme.BodyFont,
            TextAlign = ContentAlignment.MiddleCenter
        };
        var btnAccount = UiTheme.MakeButton("Masuk dengan akun", UiTheme.Surface);
        btnAccount.SetBounds(32, 262, (Width - 100) / 2, 36);
        btnAccount.Click += async (_, _) => await OpenLoginAsync();

        var btnQuickConnect = UiTheme.MakeButton("⚙ Hubungkan tanpa kode", UiTheme.Surface);
        btnQuickConnect.SetBounds(32 + (Width - 100) / 2 + 16, 262, (Width - 100) / 2, 36);
        btnQuickConnect.Click += (_, _) => OpenQuickConnectSettings();

        _btnLogout = new Label
        {
            Text = "🚪 Keluar",
            Top = 304, Left = 32, Width = Width - 84, Height = 20,
            ForeColor = UiTheme.Danger, Font = UiTheme.BodyFont,
            TextAlign = ContentAlignment.MiddleCenter, Cursor = Cursors.Hand,
            Visible = false
        };
        _btnLogout.Click += async (_, _) => await LogoutAsync();

        Controls.Add(title);
        Controls.Add(subtitle);
        Controls.Add(btnHost);
        Controls.Add(btnControl);
        Controls.Add(_accountLabel);
        Controls.Add(btnAccount);
        Controls.Add(btnQuickConnect);
        Controls.Add(_btnLogout);

        FormClosing += (_, e) =>
        {
            if (_allowClose) return;
            // Tutup jendela hanya menyembunyikan ke tray, bukan keluar -- supaya
            // "hubungkan tanpa kode" tetap jalan di latar belakang.
            e.Cancel = true;
            Hide();
        };
        RefreshAccountLabel();

        // PENTING: pakai Load, bukan Shown -- Load tetap jalan walau
        // jendelanya tidak pernah benar-benar ditampilkan ke layar (lihat
        // SetVisibleCore di bawah), sedangkan Shown cuma jalan kalau
        // jendelanya benar-benar tampil. Kalau ini dipasang ke Shown,
        // program yang dinyalakan otomatis lewat Windows startup TIDAK
        // AKAN pernah login ulang / menyalakan PresenceService, karena
        // jendelanya memang sengaja tidak pernah ditampilkan.
        Load += async (_, _) => await CheckForUpdateAsync();

        // Coba masuk lagi otomatis pakai sesi tersimpan (refresh token,
        // tanpa minta kata sandi lagi) setiap kali program dibuka --
        // supaya "Masuk dengan akun" terasa seperti login SEKALI SAJA,
        // bukan harus diulang tiap buka program. Sesi ini baru dihapus
        // kalau pengguna sendiri menekan tombol "🚪 Keluar" (lihat
        // LogoutAsync). Jalan selalu begitu program dimulai -- baik
        // dibuka manual maupun otomatis lewat Windows startup.
        Load += async (_, _) =>
        {
            if (!FirebaseRest.Instance.IsSignedIn)
                await FirebaseRest.Instance.TryRestoreSessionAsync();
            RefreshAccountLabel();
            // Jalankan PresenceService SELALU begitu login -- bukan cuma
            // kalau "hubungkan tanpa kode" sudah diaktifkan -- supaya akun
            // admin (DeviceStore.AdminEmail) selalu bisa melihat & konek ke
            // komputer ini kapan pun, walau fitur itu belum diaktifkan
            // untuk akun lain.
            if (FirebaseRest.Instance.IsSignedIn)
            {
                PresenceService.Instance.Start();
                // Sama seperti di OpenLoginAsync: pastikan autostart aktif
                // begitu ada sesi login tersimpan, tanpa pengguna perlu
                // buka menu "⚙ Hubungkan tanpa kode" dan centang manual.
                if (!StartupManager.IsEnabled()) StartupManager.Enable();
            }
        };
    }

    private void ShowFromTray()
    {
        _everAllowedToShow = true;
        Show();
        WindowState = FormWindowState.Normal;
        Activate();
    }

    /// <summary>
    /// Dipanggil WinForms setiap kali ada yang mencoba mengubah visibilitas
    /// jendela ini (termasuk Application.Run yang otomatis memanggil Show()
    /// di awal). Kalau program baru dinyalakan otomatis lewat Windows
    /// startup DAN belum pernah diizinkan tampil (lewat ShowFromTray),
    /// permintaan "tampilkan" ditolak di sini -- jendelanya tidak pernah
    /// benar-benar dirender ke layar (tidak ada flash kebuka sekilas),
    /// cuma handle-nya dipaksa dibuat (CreateControl) supaya Load tetap
    /// jalan dan NotifyIcon + logika latar belakang lainnya tetap aktif.
    /// </summary>
    protected override void SetVisibleCore(bool value)
    {
        if (value && _startHidden && !_everAllowedToShow)
        {
            if (!IsHandleCreated)
            {
                CreateHandle();
                CreateControl();
            }
            base.SetVisibleCore(false);
            return;
        }
        base.SetVisibleCore(value);
    }

    private void RefreshAccountLabel()
    {
        var signedIn = FirebaseRest.Instance.IsSignedIn;
        _accountLabel.Text = signedIn
            ? "Masuk"
            : "Belum masuk — perlu akun untuk \"hubungkan tanpa kode\"";
        _btnLogout.Visible = signedIn;
    }

    private async Task OpenLoginAsync()
    {
        using var login = new LoginForm();
        login.ShowDialog(this);
        RefreshAccountLabel();

        // Begitu login berhasil (baik sekarang maupun lewat sesi
        // tersimpan di Load), LANGSUNG jalankan PresenceService dan
        // daftarkan autostart Windows -- supaya komputer ini bisa
        // dikontrol tanpa kode kapan pun, bahkan kalau nanti aplikasi
        // ini tidak pernah dibuka manual lagi, cukup Windows-nya
        // dinyalakan & ada yang login ke akun Windows ini. Sebelumnya
        // ini cuma jalan otomatis lewat sesi tersimpan saat app dibuka
        // ulang -- login manual yang baru pertama kali tidak langsung
        // menyalakan keduanya sampai app ditutup-buka lagi.
        if (FirebaseRest.Instance.IsSignedIn)
        {
            PresenceService.Instance.Start();
            if (!StartupManager.IsEnabled()) StartupManager.Enable();
        }
        await Task.CompletedTask;
    }

    /// <summary>
    /// Padanan tombol "🚪 Keluar" di versi Android -- matikan juga fitur
    /// hubungkan-tanpa-kode di komputer ini supaya tidak ada layanan latar
    /// belakang yang masih jalan atas nama akun yang baru saja keluar.
    /// </summary>
    private async Task LogoutAsync()
    {
        QuickConnectPrefs.Save(false, QuickConnectPrefs.RequiresPassword);
        PresenceService.Instance.Stop();
        await DeviceStore.DisableQuickConnectAsync();
        FirebaseRest.Instance.SignOut();
        RefreshAccountLabel();
    }

    private void OpenQuickConnectSettings()
    {
        if (!FirebaseRest.Instance.IsSignedIn)
        {
            MessageBox.Show(this, "Masuk dengan akunmu dulu sebelum mengaktifkan fitur ini.",
                "Remote Ctl", MessageBoxButtons.OK, MessageBoxIcon.Information);
            return;
        }
        new QuickConnectForm().ShowDialog(this);
    }

    private async Task CheckForUpdateAsync()
    {
        var info = await UpdateChecker.CheckForUpdateAsync();
        if (info == null) return;

        using var dialog = new UpdateForm(info);
        dialog.ShowDialog(this);
        if (dialog.UpdateNow)
        {
            await UpdateChecker.DownloadAndSelfReplaceAsync(info);
        }
    }
}
