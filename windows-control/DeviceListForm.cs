namespace RemoteCtlAgent;

/// <summary>
/// Daftar "perangkat tersimpan" milik akun yang sedang login -- padanan
/// DeviceListActivity di Android. Pilih perangkat, masukkan kata sandi
/// kalau diminta, lalu langsung tersambung tanpa perlu kode 6 digit.
/// </summary>
public class DeviceListForm : Form
{
    /// <summary>Diisi kalau pengguna berhasil memilih & terhubung ke sebuah perangkat.</summary>
    public string? ConnectedSessionCode { get; private set; }

    private readonly FlowLayoutPanel _list;
    private readonly Label _empty;
    private readonly Label _loading;

    public DeviceListForm()
    {
        Text = "Perangkat tersimpan";
        Width = 480;
        Height = 520;
        StartPosition = FormStartPosition.CenterScreen;
        FormBorderStyle = FormBorderStyle.FixedSingle;
        MaximizeBox = false;
        UiTheme.ApplyFormChrome(this);

        var title = new Label
        {
            Text = "Perangkat tersimpan",
            Top = 20, Left = 24, Width = Width - 68,
            Font = UiTheme.TitleFont, ForeColor = UiTheme.TextPrimary
        };

        _loading = new Label
        {
            Text = "Memuat...",
            Top = 60, Left = 24, Width = Width - 68,
            ForeColor = UiTheme.TextSecondary, Font = UiTheme.BodyFont
        };

        _empty = new Label
        {
            Text = "Belum ada perangkat yang pernah login. Buka & login dulu di perangkat yang ingin kamu kontrol.",
            Top = 60, Left = 24, Width = Width - 68, Height = 60,
            ForeColor = UiTheme.TextSecondary, Font = UiTheme.BodyFont,
            Visible = false
        };

        _list = new FlowLayoutPanel
        {
            Top = 60, Left = 24, Width = Width - 68, Height = Height - 120,
            FlowDirection = FlowDirection.TopDown,
            AutoScroll = true,
            WrapContents = false
        };

        Controls.Add(title);
        Controls.Add(_loading);
        Controls.Add(_empty);
        Controls.Add(_list);

        Load += async (_, _) => await LoadDevicesAsync();
    }

    private async Task LoadDevicesAsync()
    {
        var devices = await DeviceStore.ListMyDevicesAsync();
        _loading.Visible = false;

        if (devices.Count == 0)
        {
            _empty.Visible = true;
            return;
        }

        foreach (var device in devices)
        {
            _list.Controls.Add(BuildDeviceRow(device));
        }
    }

    private Panel BuildDeviceRow(DeviceStore.DeviceEntry device)
    {
        var row = new Panel
        {
            Width = _list.Width - 20,
            Height = 72,
            BackColor = UiTheme.Surface,
            Margin = new Padding(0, 0, 0, 10)
        };

        var dot = new Label
        {
            Text = "●",
            Top = 14, Left = 14, Width = 16,
            ForeColor = device.Online ? UiTheme.Success : UiTheme.TextSecondary
        };
        var name = new Label
        {
            Text = device.Name,
            Top = 10, Left = 34, Width = row.Width - 150,
            Font = new Font("Segoe UI", 11, FontStyle.Bold),
            ForeColor = UiTheme.TextPrimary
        };
        var isAdmin = DeviceStore.IsAdmin;
        var modeText = isAdmin && !device.QuickConnectEnabled
            ? " · belum aktifkan fitur ini (akses admin)"
            : device.RequiresPassword ? "" : " · tanpa kata sandi";
        var status = new Label
        {
            Text = (device.Online ? "Online" : "Offline") + modeText,
            Top = 34, Left = 34, Width = row.Width - 150,
            ForeColor = UiTheme.TextSecondary, Font = UiTheme.BodyFont
        };

        var btnConnect = UiTheme.MakeButton("Hubungkan", UiTheme.Primary);
        btnConnect.SetBounds(row.Width - 130, 16, 110, 40);
        btnConnect.Click += async (_, _) => await ConnectAsync(device);

        row.Controls.Add(dot);
        row.Controls.Add(name);
        row.Controls.Add(status);
        row.Controls.Add(btnConnect);

        // Tombol perintah jarak jauh (matikan/restart/dst) -- cuma kelihatan
        // buat akun admin, dan jalan lepas dari status Online/Offline di
        // atas (perangkat tujuan cukup masih login & PresenceService-nya
        // jalan, tidak butuh sesi kontrol layar aktif sama sekali).
        if (isAdmin)
        {
            name.Width = row.Width - 190;
            status.Width = row.Width - 190;

            var btnPower = UiTheme.MakeButton("⚡", UiTheme.Surface);
            btnPower.SetBounds(row.Width - 170, 16, 36, 40);
            btnPower.Click += (_, _) => ShowCommandMenu(btnPower, device);
            row.Controls.Add(btnPower);
        }
        return row;
    }

    /// <summary>Menu kecil berisi perintah jarak jauh -- khusus akun admin (lihat BuildDeviceRow).</summary>
    private void ShowCommandMenu(Control anchor, DeviceStore.DeviceEntry device)
    {
        var menu = new ContextMenuStrip();
        menu.Items.Add("⏻ Matikan", null, async (_, _) => await SendCommandAsync(device, "shutdown",
            confirm: $"Matikan \"{device.Name}\" sekarang?"));
        menu.Items.Add("🔄 Restart", null, async (_, _) => await SendCommandAsync(device, "restart",
            confirm: $"Restart \"{device.Name}\" sekarang?"));
        menu.Items.Add("🚪 Keluar akun Windows", null, async (_, _) => await SendCommandAsync(device, "signout",
            confirm: $"Keluarkan sesi Windows yang sedang aktif di \"{device.Name}\"?"));
        menu.Items.Add("💤 Tidurkan", null, async (_, _) => await SendCommandAsync(device, "sleep",
            confirm: $"Tidurkan (sleep) \"{device.Name}\" sekarang?"));
        menu.Items.Add(new ToolStripSeparator());
        menu.Items.Add("💬 Kirim teks...", null, async (_, _) => await SendTextAsync(device));
        menu.Show(anchor, new Point(0, anchor.Height));
    }

    private async Task SendCommandAsync(DeviceStore.DeviceEntry device, string type, string confirm)
    {
        if (MessageBox.Show(this, confirm, "Remote Ctl", MessageBoxButtons.YesNo, MessageBoxIcon.Warning) != DialogResult.Yes)
            return;
        var error = await DeviceStore.SendRemoteCommandAsync(device.Id, type);
        if (error != null)
        {
            MessageBox.Show(this, $"Gagal mengirim perintah: {error}", "Remote Ctl", MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
    }

    private async Task SendTextAsync(DeviceStore.DeviceEntry device)
    {
        using var prompt = new TextPromptForm($"Teks buat \"{device.Name}\"",
            "Teks ini akan muncul di jendela Command Prompt di perangkat tujuan.");
        if (prompt.ShowDialog(this) != DialogResult.OK) return;
        if (string.IsNullOrWhiteSpace(prompt.Value)) return;

        var error = await DeviceStore.SendRemoteCommandAsync(device.Id, "text", prompt.Value);
        if (error != null)
        {
            MessageBox.Show(this, $"Gagal mengirim teks: {error}", "Remote Ctl", MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
    }

    private async Task ConnectAsync(DeviceStore.DeviceEntry device)
    {
        // Akun admin selalu langsung konek tanpa diminta kata sandi sama
        // sekali -- perangkat tujuan yang memutuskan izinkan atau tidak
        // berdasarkan email akun ini (lihat PresenceService.HandleIncomingRequestAsync
        // di perangkat tujuan).
        string? password = null;
        if (device.RequiresPassword && !DeviceStore.IsAdmin)
        {
            using var prompt = new PasswordPromptForm(device.Name);
            if (prompt.ShowDialog(this) != DialogResult.OK) return;
            password = prompt.Password;
        }

        var code = new Random().Next(100000, 999999).ToString();
        await DeviceStore.RequestConnectAsync(device.Id, code, password);

        ConnectedSessionCode = code;
        DialogResult = DialogResult.OK;
        Close();
    }
}

/// <summary>Dialog kecil untuk memasukkan kata sandi sebuah perangkat tersimpan.</summary>
internal class PasswordPromptForm : Form
{
    public string Password => _box.Text;
    private readonly TextBox _box;

    public PasswordPromptForm(string deviceName)
    {
        Text = "Kata sandi";
        Width = 360;
        Height = 190;
        StartPosition = FormStartPosition.CenterParent;
        FormBorderStyle = FormBorderStyle.FixedDialog;
        MaximizeBox = false;
        MinimizeBox = false;
        UiTheme.ApplyFormChrome(this);

        var label = new Label
        {
            Text = $"Masukkan kata sandi untuk \"{deviceName}\"",
            Top = 20, Left = 20, Width = Width - 60, Height = 40,
            ForeColor = UiTheme.TextPrimary, Font = UiTheme.BodyFont
        };
        _box = new TextBox
        {
            Top = 64, Left = 20, Width = Width - 60, Font = UiTheme.BodyFont,
            UseSystemPasswordChar = true
        };
        _box.KeyDown += (_, e) => { if (e.KeyCode == Keys.Enter) Submit(); };

        var btnOk = UiTheme.MakeButton("Hubungkan", UiTheme.Primary);
        btnOk.SetBounds(20, 100, Width - 60, 38);
        btnOk.Click += (_, _) => Submit();

        Controls.Add(label);
        Controls.Add(_box);
        Controls.Add(btnOk);
    }

    private void Submit()
    {
        DialogResult = DialogResult.OK;
        Close();
    }
}

/// <summary>Dialog kecil untuk mengetik teks bebas yang nanti dikirim ke perangkat lain.</summary>
internal class TextPromptForm : Form
{
    public string Value => _box.Text;
    private readonly TextBox _box;

    public TextPromptForm(string title, string description)
    {
        Text = title;
        Width = 400;
        Height = 220;
        StartPosition = FormStartPosition.CenterParent;
        FormBorderStyle = FormBorderStyle.FixedDialog;
        MaximizeBox = false;
        MinimizeBox = false;
        UiTheme.ApplyFormChrome(this);

        var label = new Label
        {
            Text = description,
            Top = 20, Left = 20, Width = Width - 60, Height = 40,
            ForeColor = UiTheme.TextSecondary, Font = UiTheme.BodyFont
        };
        _box = new TextBox
        {
            Top = 64, Left = 20, Width = Width - 60, Height = 60, Font = UiTheme.BodyFont,
            Multiline = true
        };

        var btnOk = UiTheme.MakeButton("Kirim", UiTheme.Primary);
        btnOk.SetBounds(20, 134, Width - 60, 38);
        btnOk.Click += (_, _) => { DialogResult = DialogResult.OK; Close(); };

        Controls.Add(label);
        Controls.Add(_box);
        Controls.Add(btnOk);
    }
}
