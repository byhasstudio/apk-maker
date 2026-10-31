using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Text.Json;
using Microsoft.Win32;

namespace DynamicIsland;

/// <summary>
/// Otak aplikasi: ikon tray, pulau, jendela pengaturan, pengamat baterai &amp; papan klip,
/// dan semua perintah yang dikirim halaman pengaturan lewat Bridge.
/// </summary>
public sealed class TrayApp : ApplicationContext
{
    [DllImport("user32.dll")]
    private static extern bool DestroyIcon(IntPtr hIcon);

    public Config Cfg { get; }
    public Icon AppIcon { get; }

    private readonly IslandWindow _island;
    private readonly NotifyIcon _tray;
    private readonly ToolStripMenuItem _miToggle;
    private readonly ToolStripMenuItem _miAuto;
    private readonly System.Windows.Forms.Timer _saveTimer = new() { Interval = 500 };
    private readonly System.Windows.Forms.Timer _watch = new() { Interval = 1000 };
    private readonly List<Note> _history = new();
    private SettingsForm? _settings;

    private PowerLineStatus _lastLine = PowerLineStatus.Unknown;
    private bool _lowSent, _critSent, _fullSent;
    private bool _clipArmed;
    private string _lastClip = "";

    public TrayApp(bool background)
    {
        Cfg = Config.Load();
        AppIcon = MakeIcon();

        _island = new IslandWindow(Cfg);
        _island.PillClicked += ShowSettings;
        _island.Apply();

        _miToggle = new ToolStripMenuItem("", null, (_, _) => SetRunning(!Cfg.Running));
        _miAuto = new ToolStripMenuItem("Jalan otomatis saat Windows menyala") { Checked = IsAutoStart() };
        _miAuto.Click += (_, _) =>
        {
            SetAutoStart(!IsAutoStart());
            _miAuto.Checked = IsAutoStart();
        };
        UpdateToggleText();

        var menu = new ContextMenuStrip();
        menu.Items.Add("Pengaturan", null, (_, _) => ShowSettings());
        menu.Items.Add(_miToggle);
        menu.Items.Add(_miAuto);
        menu.Items.Add(new ToolStripSeparator());
        menu.Items.Add("Keluar", null, (_, _) => ExitApp());

        _tray = new NotifyIcon
        {
            Icon = AppIcon,
            Text = "Dynamic Island",
            ContextMenuStrip = menu,
            Visible = true
        };
        _tray.DoubleClick += (_, _) => ShowSettings();

        _saveTimer.Tick += (_, _) =>
        {
            _saveTimer.Stop();
            Cfg.Save();
        };

        _watch.Tick += (_, _) => WatchTick();
        _watch.Start();

        if (!background) ShowSettings();
    }

    // ---------- jendela & tray ----------

    public void ShowSettings()
    {
        _settings ??= new SettingsForm(this);
        _settings.Show();
        _settings.WindowState = FormWindowState.Normal;
        _settings.Activate();
    }

    private void UpdateToggleText()
    {
        _miToggle.Text = Cfg.Running ? "Matikan Dynamic Island" : "Nyalakan Dynamic Island";
    }

    private void SetRunning(bool run)
    {
        Cfg.Running = run;
        UpdateToggleText();
        _island.Apply();
        QueueSave();
    }

    private void ExitApp()
    {
        _watch.Stop();
        _saveTimer.Stop();
        Cfg.Save();
        _tray.Visible = false;
        _tray.Dispose();
        _island.Close();
        _settings?.ForceClose();
        ExitThread();
    }

    private static Icon MakeIcon()
    {
        using var bmp = new Bitmap(32, 32);
        using (var g = Graphics.FromImage(bmp))
        {
            g.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.AntiAlias;
            g.Clear(Color.Transparent);
            using var dark = new SolidBrush(Color.FromArgb(24, 24, 28));
            g.FillEllipse(dark, 0, 0, 31, 31);
            using var white = new SolidBrush(Color.White);
            g.FillEllipse(white, 7, 11, 18, 10);
        }
        IntPtr h = bmp.GetHicon();
        try
        {
            using var tmp = Icon.FromHandle(h);
            return (Icon)tmp.Clone();
        }
        finally
        {
            DestroyIcon(h);
        }
    }

    // ---------- jalan otomatis ----------

    private const string RunKey = @"Software\Microsoft\Windows\CurrentVersion\Run";

    private static bool IsAutoStart()
    {
        try
        {
            using var k = Registry.CurrentUser.OpenSubKey(RunKey, false);
            return k?.GetValue("DynamicIsland") != null;
        }
        catch
        {
            return false;
        }
    }

    private static void SetAutoStart(bool on)
    {
        try
        {
            using var k = Registry.CurrentUser.OpenSubKey(RunKey, true);
            if (k == null) return;
            if (on) k.SetValue("DynamicIsland", "\"" + Environment.ProcessPath + "\" --background");
            else k.DeleteValue("DynamicIsland", false);
        }
        catch (Exception ex)
        {
            Log.Write(ex);
        }
    }

    // ---------- penyimpanan ----------

    private void QueueSave()
    {
        _saveTimer.Stop();
        _saveTimer.Start();
    }

    private void ApplyAndSave()
    {
        _island.Apply();
        QueueSave();
    }

    // ---------- notifikasi ----------

    private bool IsQuiet()
    {
        if (Cfg.Dnd) return true;
        if (!Cfg.Qon) return false;
        if (!TimeSpan.TryParse(Cfg.Qfrom, out var f) || !TimeSpan.TryParse(Cfg.Qto, out var t)) return false;
        var now = DateTime.Now.TimeOfDay;
        return f <= t ? (now >= f && now < t) : (now >= f || now < t);
    }

    public void Notify(string title, string text, string app, bool force = false)
    {
        var n = new Note
        {
            Title = title,
            Text = text,
            App = app,
            T = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
        };
        if (Cfg.HistOn)
        {
            _history.Insert(0, n);
            while (_history.Count > 40) _history.RemoveAt(_history.Count - 1);
        }
        if (!Cfg.Running) return;
        if (!force && IsQuiet()) return;
        _island.Push(n);
        if (Cfg.Snd) System.Media.SystemSounds.Asterisk.Play();
    }

    // ---------- pengamat ----------

    private void WatchTick()
    {
        try
        {
            if (Cfg.Bat) CheckBattery();
            else _lastLine = PowerLineStatus.Unknown;

            if (Cfg.Clip) CheckClipboard();
            else _clipArmed = false;
        }
        catch (Exception ex)
        {
            Log.Write(ex);
        }
    }

    private void CheckBattery()
    {
        var ps = SystemInformation.PowerStatus;
        if ((ps.BatteryChargeStatus & BatteryChargeStatus.NoSystemBattery) != 0) return;
        float frac = ps.BatteryLifePercent;
        if (frac < 0f || frac > 1f) return;
        int pct = (int)Math.Round(frac * 100f);
        var line = ps.PowerLineStatus;

        if (line != PowerLineStatus.Unknown)
        {
            if (_lastLine != PowerLineStatus.Unknown && line != _lastLine)
            {
                if (line == PowerLineStatus.Online) Notify("Mengisi daya", "Baterai " + pct + "%", "Baterai");
                else Notify("Charger dicabut", "Baterai " + pct + "%", "Baterai");
            }
            _lastLine = line;
        }

        if (line == PowerLineStatus.Online)
        {
            _lowSent = false;
            _critSent = false;
            if (pct >= 100 && !_fullSent)
            {
                _fullSent = true;
                Notify("Baterai penuh", "Charger bisa dicabut", "Baterai");
            }
            if (pct < 95) _fullSent = false;
        }
        else if (line == PowerLineStatus.Offline)
        {
            _fullSent = false;
            if (pct <= 10)
            {
                if (!_critSent)
                {
                    _critSent = true;
                    _lowSent = true;
                    Notify("Baterai kritis", "Sisa " + pct + "%", "Baterai");
                }
            }
            else if (pct <= 20)
            {
                if (!_lowSent)
                {
                    _lowSent = true;
                    Notify("Baterai lemah", "Sisa " + pct + "%", "Baterai");
                }
            }
            else
            {
                _lowSent = false;
                _critSent = false;
            }
        }
    }

    private void CheckClipboard()
    {
        string cur;
        try
        {
            cur = Clipboard.ContainsText() ? Clipboard.GetText() : "";
        }
        catch
        {
            return; // papan klip sedang dipakai aplikasi lain, coba lagi detik berikutnya
        }

        if (!_clipArmed)
        {
            _lastClip = cur;
            _clipArmed = true;
            return;
        }
        if (cur == _lastClip) return;
        _lastClip = cur;
        if (cur.Length == 0) return;

        string t = System.Text.RegularExpressions.Regex.Replace(cur, @"\s+", " ").Trim();
        if (t.Length > 90) t = t.Substring(0, 90) + "...";
        Notify("Teks disalin", t, "Papan klip");
    }

    // ---------- perintah dari halaman pengaturan ----------

    private static string Json(object o) => JsonSerializer.Serialize(o);

    private string GetFilter()
    {
        var d = new Dictionary<string, object?>();
        foreach (var kv in Cfg.Extra) d[kv.Key] = kv.Value;
        d["mode"] = 0;
        d["apps"] = Array.Empty<string>();
        d["anim"] = false;
        d["animmin"] = false;
        d["snd"] = Cfg.Snd;
        d["tapopen"] = Cfg.TapOpen;
        d["dur"] = Cfg.DurMs;
        d["suri"] = "";
        d["sname"] = "Bawaan Windows";
        d["svol"] = 100;
        d["pill"] = Cfg.Pill;
        d["edge"] = Cfg.Edge;
        d["glow"] = false;
        d["bat"] = Cfg.Bat;
        d["clip"] = Cfg.Clip;
        d["hot"] = false;
        d["dnd"] = Cfg.Dnd;
        d["qon"] = Cfg.Qon;
        d["qfrom"] = Cfg.Qfrom;
        d["qto"] = Cfg.Qto;
        d["hist"] = Cfg.HistOn;
        d["mon"] = Cfg.Mon;
        return JsonSerializer.Serialize(d);
    }

    public string Handle(string name, JsonElement a)
    {
        int n = a.ValueKind == JsonValueKind.Array ? a.GetArrayLength() : 0;
        string S(int i) => i < n ? (a[i].ValueKind == JsonValueKind.String ? (a[i].GetString() ?? "") : a[i].ToString()) : "";
        double D(int i) => i < n && a[i].ValueKind == JsonValueKind.Number ? a[i].GetDouble() : 0;
        bool B(int i) => i < n && a[i].ValueKind == JsonValueKind.True;

        switch (name)
        {
            case "getInfo":
                return Json(new
                {
                    helper = true,
                    enabled = Cfg.Running,
                    notif = new
                    {
                        ok = false,
                        err = "Membaca notifikasi aplikasi lain belum ada di versi ini. Island menampilkan notifikasi tes, baterai, dan teks yang disalin."
                    },
                    anim = false,
                    sys = new { ok = false, exists = false },
                    pm = new { exists = false, ok = false, grp = 0, game = false },
                    appsReady = true,
                    nApps = 0
                });

            case "getState":
                return Json(new { running = Cfg.Running });

            case "start":
                SetRunning(true);
                return "";

            case "stop":
                SetRunning(false);
                return "";

            case "openWinSettings":
                try
                {
                    Process.Start(new ProcessStartInfo("ms-settings:notifications") { UseShellExecute = true });
                }
                catch (Exception ex)
                {
                    Log.Write(ex);
                }
                return "";

            case "test":
                Notify(S(0), S(1), "Tes", true);
                return "";

            case "setStyle":
                if (S(0).StartsWith("#")) Cfg.Bg = S(0);
                Cfg.Op = D(1);
                Cfg.Top = D(2);
                Cfg.Cw = D(3);
                Cfg.Ew = D(4);
                Cfg.Cx = D(5);
                Cfg.Dh = D(6);
                ApplyAndSave();
                return "";

            case "setPillAlways":
                Cfg.Pill = B(0);
                ApplyAndSave();
                return "";

            case "setEdge":
            {
                string e = S(0);
                if (e == "top" || e == "bottom" || e == "left" || e == "right") Cfg.Edge = e;
                ApplyAndSave();
                return "";
            }

            case "getFilter":
                return GetFilter();

            case "setDuration":
                Cfg.DurMs = D(0);
                QueueSave();
                return "";

            case "setSound":
                Cfg.Snd = B(0);
                QueueSave();
                return "";

            case "previewSound":
                System.Media.SystemSounds.Asterisk.Play();
                return "";

            case "setTapOpen":
                Cfg.TapOpen = B(0);
                QueueSave();
                return "";

            case "setBat":
                Cfg.Bat = B(0);
                QueueSave();
                return "";

            case "setClip":
                Cfg.Clip = B(0);
                QueueSave();
                return "";

            case "setDnd":
                Cfg.Dnd = B(0);
                QueueSave();
                return "";

            case "setQuiet":
                Cfg.Qon = B(0);
                Cfg.Qfrom = S(1);
                Cfg.Qto = S(2);
                QueueSave();
                return "";

            case "setHistOn":
                Cfg.HistOn = B(0);
                QueueSave();
                return "";

            case "setMonitor":
                Cfg.Mon = S(0) == "cursor" ? "cursor" : "primary";
                ApplyAndSave();
                return "";

            case "getHistory":
                return Json(_history.Select(x => new { t = x.T, title = x.Title, app = x.App, text = x.Text }).ToList());

            case "reshow":
            {
                int i = (int)D(0);
                if (i >= 0 && i < _history.Count)
                {
                    var h = _history[i];
                    _island.Push(new Note { Title = h.Title, Text = h.Text, App = h.App, T = h.T });
                }
                return "";
            }

            case "clearHistory":
                _history.Clear();
                return "";

            case "setOpt":
                if (n >= 2)
                {
                    Cfg.Extra[S(0)] = a[1].Clone();
                    QueueSave();
                }
                return "";

            case "getApps":
            case "getSounds":
                return "[]";

            case "getGameApps":
                return "{}";

            default:
                return "";
        }
    }
}
