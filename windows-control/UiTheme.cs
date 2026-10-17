namespace RemoteCtlAgent;

/// <summary>
/// Satu tempat untuk warna dan font supaya semua jendela terlihat
/// konsisten -- tema gelap sederhana, bukan form bawaan Windows yang polos.
/// </summary>
public static class UiTheme
{
    public static readonly Color Background = Color.FromArgb(18, 18, 24);
    public static readonly Color Surface = Color.FromArgb(28, 28, 38);
    public static readonly Color Primary = Color.FromArgb(99, 102, 241); // indigo
    public static readonly Color PrimaryDark = Color.FromArgb(79, 82, 221);
    public static readonly Color TextPrimary = Color.FromArgb(240, 240, 245);
    public static readonly Color TextSecondary = Color.FromArgb(160, 160, 175);
    public static readonly Color Success = Color.FromArgb(52, 211, 153);
    public static readonly Color Danger = Color.FromArgb(248, 113, 113);

    public static readonly Font TitleFont = new("Segoe UI", 18, FontStyle.Bold);
    public static readonly Font CodeFont = new("Segoe UI", 36, FontStyle.Bold);
    public static readonly Font BodyFont = new("Segoe UI", 10);
    public static readonly Font ButtonFont = new("Segoe UI", 10.5f, FontStyle.Bold);

    public static Button MakeButton(string text, Color background)
    {
        var btn = new Button
        {
            Text = text,
            FlatStyle = FlatStyle.Flat,
            BackColor = background,
            ForeColor = Color.White,
            Font = ButtonFont,
            Height = 42,
            Cursor = Cursors.Hand
        };
        btn.FlatAppearance.BorderSize = 0;
        btn.FlatAppearance.MouseOverBackColor = ControlPaint.Light(background, 0.1f);
        return btn;
    }

    public static void ApplyFormChrome(Form form)
    {
        form.BackColor = Background;
        form.ForeColor = TextPrimary;
        form.Font = BodyFont;
    }
}
