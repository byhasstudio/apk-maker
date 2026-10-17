namespace RemoteCtlAgent;

/// <summary>
/// Pop-up pembaruan dengan animasi muncul (fade-in + sedikit geser
/// dari bawah) -- bukan MessageBox polos bawaan Windows.
/// </summary>
public class UpdateForm : Form
{
    public bool UpdateNow { get; private set; }

    public UpdateForm(UpdateInfo info)
    {
        Text = "Pembaruan tersedia";
        Width = 420;
        Height = 360;
        StartPosition = FormStartPosition.CenterScreen;
        FormBorderStyle = FormBorderStyle.FixedSingle;
        MaximizeBox = false;
        MinimizeBox = false;
        Opacity = 0;
        UiTheme.ApplyFormChrome(this);

        var title = new Label
        {
            Text = "🚀 Pembaruan tersedia",
            Top = 24, Left = 24, Width = Width - 60,
            Font = new Font("Segoe UI", 15, FontStyle.Bold),
            ForeColor = UiTheme.TextPrimary
        };
        var version = new Label
        {
            Text = $"Versi baru: {info.Version}",
            Top = 56, Left = 24, Width = Width - 60,
            Font = new Font("Segoe UI", 9.5f, FontStyle.Bold),
            ForeColor = UiTheme.Primary
        };
        var changelog = new TextBox
        {
            Text = info.Changelog,
            Top = 90, Left = 24, Width = Width - 64, Height = 150,
            Multiline = true,
            ReadOnly = true,
            BorderStyle = BorderStyle.None,
            BackColor = UiTheme.Surface,
            ForeColor = UiTheme.TextSecondary,
            ScrollBars = ScrollBars.Vertical
        };

        var btnLater = UiTheme.MakeButton("Nanti", UiTheme.Surface);
        btnLater.SetBounds(24, 262, 160, 40);
        btnLater.Click += (_, _) => { UpdateNow = false; Close(); };

        var btnUpdate = UiTheme.MakeButton("Update Sekarang", UiTheme.Primary);
        btnUpdate.SetBounds(196, 262, 164, 40);
        btnUpdate.Click += (_, _) => { UpdateNow = true; Close(); };

        Controls.Add(title);
        Controls.Add(version);
        Controls.Add(changelog);
        Controls.Add(btnLater);
        Controls.Add(btnUpdate);

        Shown += async (_, _) => await AnimateInAsync();
    }

    private async Task AnimateInAsync()
    {
        var startTop = Top + 24;
        Top = startTop;
        for (var i = 0; i <= 10; i++)
        {
            Opacity = i / 10.0;
            Top = startTop - (int)(24 * (i / 10.0));
            await Task.Delay(16);
        }
        Opacity = 1;
    }
}
