namespace RemoteCtlAgent;

/// <summary>
/// Jendela pertama yang muncul: pilih apakah komputer ini mau jadi
/// yang dikontrol, atau dipakai untuk mengontrol perangkat lain --
/// persis padanan MainActivity di aplikasi Android.
/// </summary>
public class ModeSelectForm : Form
{
    public ModeSelectForm()
    {
        Text = "Remote Ctl";
        Width = 440;
        Height = 320;
        StartPosition = FormStartPosition.CenterScreen;
        FormBorderStyle = FormBorderStyle.FixedSingle;
        MaximizeBox = false;
        UiTheme.ApplyFormChrome(this);

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

        Controls.Add(title);
        Controls.Add(subtitle);
        Controls.Add(btnHost);
        Controls.Add(btnControl);
    }
}
