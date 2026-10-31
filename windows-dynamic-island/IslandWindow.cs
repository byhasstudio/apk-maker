using System.Diagnostics;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.Drawing.Text;
using System.Runtime.InteropServices;

namespace DynamicIsland;

public sealed class Note
{
    public string Title { get; set; } = "";
    public string Text { get; set; } = "";
    public string App { get; set; } = "";
    public long T { get; set; }
}

/// <summary>
/// Jendela pulau hitam di tepi layar. Digambar sendiri dengan GDI+ ke bitmap
/// ARGB lalu ditampilkan lewat UpdateLayeredWindow supaya tepinya halus dan
/// tidak mencuri fokus dari aplikasi lain. Gerakan buka/tutup memakai fisika pegas.
/// </summary>
public sealed class IslandWindow : Form
{
    private const int WS_EX_TOPMOST = 0x00000008;
    private const int WS_EX_TOOLWINDOW = 0x00000080;
    private const int WS_EX_LAYERED = 0x00080000;
    private const int WS_EX_NOACTIVATE = 0x08000000;
    private const int ULW_ALPHA = 0x00000002;

    [StructLayout(LayoutKind.Sequential)]
    private struct POINT { public int X; public int Y; }

    [StructLayout(LayoutKind.Sequential)]
    private struct SIZE { public int CX; public int CY; }

    [StructLayout(LayoutKind.Sequential, Pack = 1)]
    private struct BLENDFUNCTION
    {
        public byte BlendOp;
        public byte BlendFlags;
        public byte SourceConstantAlpha;
        public byte AlphaFormat;
    }

    [DllImport("user32.dll", SetLastError = true)]
    private static extern bool UpdateLayeredWindow(IntPtr hwnd, IntPtr hdcDst, ref POINT pptDst, ref SIZE psize,
        IntPtr hdcSrc, ref POINT pptSrc, int crKey, ref BLENDFUNCTION pblend, int dwFlags);

    [DllImport("user32.dll")] private static extern IntPtr GetDC(IntPtr hWnd);
    [DllImport("user32.dll")] private static extern int ReleaseDC(IntPtr hWnd, IntPtr hDC);
    [DllImport("gdi32.dll")] private static extern IntPtr CreateCompatibleDC(IntPtr hDC);
    [DllImport("gdi32.dll")] private static extern bool DeleteDC(IntPtr hdc);
    [DllImport("gdi32.dll")] private static extern IntPtr SelectObject(IntPtr hDC, IntPtr hObject);
    [DllImport("gdi32.dll")] private static extern bool DeleteObject(IntPtr hObject);

    private readonly Config _cfg;
    private readonly System.Windows.Forms.Timer _timer = new() { Interval = 16 };
    private readonly List<Note> _notes = new();

    private bool _open;
    private bool _initialized;
    private double _w, _h, _vw, _vh, _tw, _th;
    private double _kw = 420, _dw = 29, _kh = 420, _dh = 29;
    private double _expW = 340, _expH = 88;
    private float _a;
    private long _last;
    private long _closeAt;
    private float _scale = 1f;
    private float _fontScale = 0f;
    private Rectangle _rect;

    private Font _fTitle = null!;
    private Font _fText = null!;
    private Font _fTime = null!;
    private Font _fLetter = null!;

    public event Action? PillClicked;

    public IslandWindow(Config cfg)
    {
        _cfg = cfg;
        FormBorderStyle = FormBorderStyle.None;
        ShowInTaskbar = false;
        StartPosition = FormStartPosition.Manual;
        TopMost = true;
        Size = new Size(10, 10);
        _timer.Tick += (_, _) => Tick();
        BuildFonts();
    }

    protected override CreateParams CreateParams
    {
        get
        {
            var cp = base.CreateParams;
            cp.ExStyle |= WS_EX_LAYERED | WS_EX_TOOLWINDOW | WS_EX_NOACTIVATE | WS_EX_TOPMOST;
            return cp;
        }
    }

    protected override bool ShowWithoutActivation => true;

    protected override void Dispose(bool disposing)
    {
        if (disposing)
        {
            _timer.Dispose();
            _fTitle?.Dispose();
            _fText?.Dispose();
            _fTime?.Dispose();
            _fLetter?.Dispose();
        }
        base.Dispose(disposing);
    }

    // ---------- ukuran ----------

    private bool Side => _cfg.Edge == "left" || _cfg.Edge == "right";
    private double CH => Math.Max(20.0, 32.0 + _cfg.Dh) * _scale;
    private double CW => Math.Max(40.0, _cfg.Cw) * _scale;
    private double PillW => Side ? CH : CW;
    private double PillH => Side ? CW : CH;

    private Rectangle ScreenBounds()
    {
        Screen s = _cfg.Mon == "cursor"
            ? Screen.FromPoint(Cursor.Position)
            : (Screen.PrimaryScreen ?? Screen.AllScreens[0]);
        return s.Bounds;
    }

    private double ExpW()
    {
        double max = ScreenBounds().Width - 24.0 * _scale;
        return Math.Min(Math.Max(160.0, _cfg.Ew) * _scale, max);
    }

    private double ExpH() => (24.0 + Math.Max(1, _notes.Count) * 64.0) * _scale;

    private void BuildFonts()
    {
        float s = _scale;
        _fTitle?.Dispose();
        _fText?.Dispose();
        _fTime?.Dispose();
        _fLetter?.Dispose();
        _fTitle = new Font("Segoe UI", 15f * s, FontStyle.Bold, GraphicsUnit.Pixel);
        _fText = new Font("Segoe UI", 14f * s, FontStyle.Regular, GraphicsUnit.Pixel);
        _fTime = new Font("Segoe UI", 12f * s, FontStyle.Regular, GraphicsUnit.Pixel);
        _fLetter = new Font("Segoe UI", 20f * s, FontStyle.Bold, GraphicsUnit.Pixel);
        _fontScale = s;
    }

    // ---------- kendali dari luar ----------

    /// <summary>Terapkan ulang pengaturan (warna, ukuran, posisi, nyala/mati).</summary>
    public void Apply()
    {
        if (!IsHandleCreated) CreateHandle();
        _scale = Math.Max(1f, DeviceDpi / 96f);
        if (Math.Abs(_scale - _fontScale) > 0.001f) BuildFonts();

        if (!_initialized)
        {
            _w = PillW;
            _h = PillH;
            _initialized = true;
        }

        if (_open)
        {
            _expW = ExpW();
            _expH = ExpH();
            _tw = _expW;
            _th = _expH;
            SetSpring(true);
        }
        else
        {
            _tw = PillW;
            _th = PillH;
            SetSpring(false);
        }

        UpdateVisibility();
        Kick();
    }

    /// <summary>Tampilkan notifikasi. Beberapa notifikasi beruntun ditumpuk (maksimal 3).</summary>
    public void Push(Note n)
    {
        if (!_cfg.Running) return;
        if (!IsHandleCreated) CreateHandle();
        if (!_open) _notes.Clear();
        _notes.Add(n);
        while (_notes.Count > 3) _notes.RemoveAt(0);

        _open = true;
        _expW = ExpW();
        _expH = ExpH();
        _tw = _expW;
        _th = _expH;
        SetSpring(true);
        _closeAt = Environment.TickCount64 + (long)Math.Clamp(_cfg.DurMs, 2000, 20000);
        UpdateVisibility();
        Kick();
    }

    public void Collapse()
    {
        if (!_open) return;
        _open = false;
        _tw = PillW;
        _th = PillH;
        SetSpring(false);
        Kick();
    }

    private void SetSpring(bool opening)
    {
        if (opening) { _kw = 230; _dw = 16; _kh = 270; _dh = 18; }
        else { _kw = 420; _dw = 29; _kh = 420; _dh = 29; }
    }

    private void Kick()
    {
        _last = 0;
        if (!_timer.Enabled) _timer.Start();
    }

    private bool Animating() =>
        Math.Abs(_w - _tw) > 0.4 || Math.Abs(_h - _th) > 0.4 || Math.Abs(_vw) > 3 || Math.Abs(_vh) > 3;

    private void UpdateVisibility()
    {
        bool want = _cfg.Running && (_cfg.Pill || _open || Animating());
        if (want && !Visible)
        {
            Show();
            Render();
        }
        else if (!want && Visible)
        {
            Hide();
        }
    }

    // ---------- animasi ----------

    private void Tick()
    {
        long now = Stopwatch.GetTimestamp();
        double dt = _last == 0 ? 1.0 / 60.0 : (now - _last) / (double)Stopwatch.Frequency;
        _last = now;
        dt = Math.Min(dt, 1.0 / 30.0);

        for (int i = 0; i < 4; i++)
        {
            double d = dt / 4.0;
            _vw += (-_kw * (_w - _tw) - _dw * _vw) * d;
            _w += _vw * d;
            _vh += (-_kh * (_h - _th) - _dh * _vh) * d;
            _h += _vh * d;
        }

        bool settled = Math.Abs(_w - _tw) < 0.4 && Math.Abs(_h - _th) < 0.4 &&
                       Math.Abs(_vw) < 3 && Math.Abs(_vh) < 3;
        if (settled)
        {
            _w = _tw;
            _h = _th;
            _vw = 0;
            _vh = 0;
        }

        // Isi notifikasi memudar masuk saat pulau melebar, memudar keluar saat mengecil.
        double span = _expW - PillW;
        double p = span <= 1 ? (_open ? 1.0 : 0.0) : (_w - PillW) / span;
        double t = Math.Clamp((p - 0.30) / 0.45, 0.0, 1.0);
        _a = (float)(t * t * (3.0 - 2.0 * t));

        if (_open)
        {
            long nowMs = Environment.TickCount64;
            long dur = (long)Math.Clamp(_cfg.DurMs, 2000, 20000);
            if (_rect.Contains(Cursor.Position))
            {
                _closeAt = nowMs + dur; // kursor di atas pulau: jangan menutup sendiri
            }
            else if (nowMs >= _closeAt && settled)
            {
                Collapse();
            }
        }

        Render();

        if (settled && !_open)
        {
            _timer.Stop();
            UpdateVisibility();
        }
    }

    // ---------- gambar ----------

    private void Render()
    {
        if (!IsHandleCreated || !Visible) return;

        int w = Math.Max(2, (int)Math.Round(_w));
        int h = Math.Max(2, (int)Math.Round(_h));
        Rectangle b = ScreenBounds();
        int margin = (int)Math.Round((12.0 + _cfg.Top) * _scale);
        int shift = (int)Math.Round(_cfg.Cx * _scale);
        int x, y;
        switch (_cfg.Edge)
        {
            case "bottom":
                x = b.Left + (b.Width - w) / 2 + shift;
                y = b.Bottom - margin - h;
                break;
            case "left":
                x = b.Left + margin;
                y = b.Top + (b.Height - h) / 2 + shift;
                break;
            case "right":
                x = b.Right - margin - w;
                y = b.Top + (b.Height - h) / 2 + shift;
                break;
            default:
                x = b.Left + (b.Width - w) / 2 + shift;
                y = b.Top + margin;
                break;
        }
        _rect = new Rectangle(x, y, w, h);

        using var bmp = new Bitmap(w, h, PixelFormat.Format32bppArgb);
        using (var g = Graphics.FromImage(bmp))
        {
            Draw(g, w, h);
        }
        Present(bmp, x, y);
    }

    private void Draw(Graphics g, int w, int h)
    {
        g.SmoothingMode = SmoothingMode.AntiAlias;
        g.TextRenderingHint = TextRenderingHint.AntiAlias;
        g.Clear(Color.Transparent);

        Color bg = ParseColor(_cfg.Bg);
        int alpha = (int)Math.Round(Math.Clamp(_cfg.Op, 40.0, 100.0) / 100.0 * 255.0);
        float rad = Math.Min(Math.Min(w, h) / 2f, 42f * _scale);
        using var path = RoundRect(new RectangleF(0.5f, 0.5f, w - 1f, h - 1f), rad);

        using (var br = new SolidBrush(Color.FromArgb(alpha, bg)))
        {
            g.FillPath(br, path);
        }

        double lum = (0.299 * bg.R + 0.587 * bg.G + 0.114 * bg.B) / 255.0;
        if (lum > 0.12)
        {
            using var pen = new Pen(Color.FromArgb(70, 255, 255, 255), 1f);
            g.DrawPath(pen, path);
        }

        if (_a < 0.02f || _notes.Count == 0) return;

        bool light = lum > 0.6;
        int ta = (int)Math.Round(255 * _a);
        Color tc = Color.FromArgb(ta, light ? Color.FromArgb(28, 28, 30) : Color.White);
        Color sc = Color.FromArgb(ta, light ? Color.FromArgb(85, 85, 92) : Color.FromArgb(176, 176, 184));
        Color dc = Color.FromArgb(ta, light ? Color.FromArgb(119, 119, 126) : Color.FromArgb(142, 142, 147));
        Color wc = Color.FromArgb(ta, Color.White);

        g.SetClip(path);

        float s = _scale;
        float cw = (float)_expW;
        float cx0 = (w - cw) / 2f;
        float padX = 18f * s;
        float rowH = 64f * s;
        float top0 = 12f * s;

        using var sfTitle = new StringFormat(StringFormatFlags.NoWrap) { Trimming = StringTrimming.EllipsisCharacter };
        using var sfText = new StringFormat { Trimming = StringTrimming.EllipsisWord };
        using var sfTime = new StringFormat(StringFormatFlags.NoWrap) { Alignment = StringAlignment.Far };
        using var sfCenter = new StringFormat { Alignment = StringAlignment.Center, LineAlignment = StringAlignment.Center };
        using var bTitle = new SolidBrush(tc);
        using var bText = new SolidBrush(sc);
        using var bTime = new SolidBrush(dc);
        using var bWhite = new SolidBrush(wc);

        for (int i = 0; i < _notes.Count; i++)
        {
            Note n = _notes[i];
            float ry = top0 + i * rowH;
            float isz = 44f * s;
            var ir = new RectangleF(cx0 + padX, ry + 10f * s, isz, isz);

            int hue = HueOf(n.App + n.Title);
            Color c1 = FromHsl(hue, 0.72, 0.64);
            Color c2 = FromHsl((hue + 28) % 360, 0.62, 0.44);
            using (var ip = RoundRect(ir, 11f * s))
            using (var ib = new LinearGradientBrush(ir, Color.FromArgb(ta, c1), Color.FromArgb(ta, c2), 45f))
            {
                g.FillPath(ib, ip);
            }
            string letter = FirstLetter(string.IsNullOrWhiteSpace(n.App) ? n.Title : n.App);
            g.DrawString(letter, _fLetter, bWhite, ir, sfCenter);

            float tx = ir.Right + 12f * s;
            float right = cx0 + cw - padX;
            var titleRect = new RectangleF(tx, ry + 9f * s, Math.Max(10f, right - tx - 62f * s), 22f * s);
            var timeRect = new RectangleF(right - 60f * s, ry + 12f * s, 60f * s, 18f * s);
            var textRect = new RectangleF(tx, ry + 30f * s, Math.Max(10f, right - tx), 34f * s);

            g.DrawString(n.Title, _fTitle, bTitle, titleRect, sfTitle);
            g.DrawString(Ago(n.T), _fTime, bTime, timeRect, sfTime);
            if (!string.IsNullOrEmpty(n.Text))
            {
                g.DrawString(n.Text, _fText, bText, textRect, sfText);
            }
        }
    }

    private void Present(Bitmap bmp, int x, int y)
    {
        IntPtr screenDc = GetDC(IntPtr.Zero);
        IntPtr memDc = CreateCompatibleDC(screenDc);
        IntPtr hBmp = IntPtr.Zero;
        IntPtr old = IntPtr.Zero;
        try
        {
            hBmp = bmp.GetHbitmap(Color.FromArgb(0));
            old = SelectObject(memDc, hBmp);
            var size = new SIZE { CX = bmp.Width, CY = bmp.Height };
            var src = new POINT { X = 0, Y = 0 };
            var dst = new POINT { X = x, Y = y };
            var blend = new BLENDFUNCTION
            {
                BlendOp = 0,
                BlendFlags = 0,
                SourceConstantAlpha = 255,
                AlphaFormat = 1
            };
            UpdateLayeredWindow(Handle, screenDc, ref dst, ref size, memDc, ref src, 0, ref blend, ULW_ALPHA);
        }
        finally
        {
            ReleaseDC(IntPtr.Zero, screenDc);
            if (hBmp != IntPtr.Zero)
            {
                SelectObject(memDc, old);
                DeleteObject(hBmp);
            }
            DeleteDC(memDc);
        }
    }

    // ---------- sentuhan ----------

    protected override void OnMouseUp(MouseEventArgs e)
    {
        base.OnMouseUp(e);
        if (e.Button != MouseButtons.Left) return;
        if (_open) Collapse();
        else PillClicked?.Invoke();
    }

    // ---------- util ----------

    private static GraphicsPath RoundRect(RectangleF r, float rad)
    {
        var p = new GraphicsPath();
        float d = Math.Min(rad * 2f, Math.Min(r.Width, r.Height));
        if (d < 1f)
        {
            p.AddRectangle(r);
            return p;
        }
        p.AddArc(r.X, r.Y, d, d, 180, 90);
        p.AddArc(r.Right - d, r.Y, d, d, 270, 90);
        p.AddArc(r.Right - d, r.Bottom - d, d, d, 0, 90);
        p.AddArc(r.X, r.Bottom - d, d, d, 90, 90);
        p.CloseFigure();
        return p;
    }

    private static Color ParseColor(string hex)
    {
        try { return ColorTranslator.FromHtml(hex); }
        catch { return Color.Black; }
    }

    private static int HueOf(string s)
    {
        int h = 0;
        foreach (char ch in s) h = (h * 31 + ch) % 360;
        return h;
    }

    private static string FirstLetter(string s)
    {
        s = (s ?? "").Trim();
        return s.Length == 0 ? "?" : s.Substring(0, 1).ToUpperInvariant();
    }

    private static string Ago(long t)
    {
        long sec = (DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() - t) / 1000;
        if (sec < 60) return "sekarang";
        if (sec < 3600) return (sec / 60) + " mnt";
        return (sec / 3600) + " j";
    }

    private static Color FromHsl(double h, double s, double l)
    {
        double c = (1 - Math.Abs(2 * l - 1)) * s;
        double hp = (h % 360) / 60.0;
        double x = c * (1 - Math.Abs(hp % 2 - 1));
        double r = 0, g = 0, b = 0;
        if (hp < 1) { r = c; g = x; }
        else if (hp < 2) { r = x; g = c; }
        else if (hp < 3) { g = c; b = x; }
        else if (hp < 4) { g = x; b = c; }
        else if (hp < 5) { r = x; b = c; }
        else { r = c; b = x; }
        double m = l - c / 2;
        return Color.FromArgb((int)((r + m) * 255), (int)((g + m) * 255), (int)((b + m) * 255));
    }
}
