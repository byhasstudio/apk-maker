using System.Text;
using Microsoft.Web.WebView2.Core;
using Microsoft.Web.WebView2.WinForms;

namespace DynamicIsland;

/// <summary>Jendela pengaturan: menampilkan halaman index.html dari repo dynamicisland_windows lewat WebView2.</summary>
public sealed class SettingsForm : Form
{
    private readonly TrayApp _app;
    private readonly WebView2 _web = new();
    private bool _reallyClose;

    // Menjembatani halaman ke aplikasi: window.WinIsland.<fungsi>() -> Bridge.Dispatch(...).
    // Bagian bawah menyembunyikan kartu fitur yang belum ada di versi ini.
    private const string Shim = """
        (function () {
          try {
            var h = window.chrome.webview.hostObjects.sync.bridge;
            var names = ['getInfo','getState','start','stop','openWinSettings','test','setStyle','setPillAlways','setEdge',
              'getFilter','setFilter','getApps','setAnim','setAnimMin','setSound','setTapOpen','setDuration','setSoundVol',
              'previewSound','setSoundUri','getSounds','setGlow','setBat','setClip','setHotkeys','setDnd','setQuiet',
              'setHistOn','setMonitor','getHistory','reshow','clearHistory','setOpt','grantFps','openCal',
              'getGameApps','setGameMark','demoAnim'];
            var W = { platform: 'windows' };
            names.forEach(function (n) {
              W[n] = function () {
                var a = Array.prototype.slice.call(arguments);
                try { return h.Dispatch(n, JSON.stringify(a)); } catch (e) { return ''; }
              };
            });
            window.WinIsland = W;
          } catch (e) {}

          document.addEventListener('DOMContentLoaded', function () {
            var hide = function (el) { if (el) el.style.setProperty('display', 'none', 'important'); };
            ['anm', 'fx2', 'fx3'].forEach(function (id) { hide(document.getElementById(id)); });
            ['glow', 'hot', 'tap'].forEach(function (id) {
              var e = document.getElementById(id);
              hide(e && e.closest ? e.closest('.row') : null);
            });
            var st = document.createElement('style');
            st.textContent = '#flt > .two, #flt > .note, #flt > #asr, #flt > #alist, #sbox { display: none !important; }';
            document.head.appendChild(st);
          });
        })();
        """;

    public SettingsForm(TrayApp app)
    {
        _app = app;
        Text = "Dynamic Island untuk Windows";
        Icon = app.AppIcon;
        ClientSize = new Size(1100, 780);
        MinimumSize = new Size(480, 520);
        StartPosition = FormStartPosition.CenterScreen;

        _web.Dock = DockStyle.Fill;
        Controls.Add(_web);

        Load += async (_, _) => await InitWebAsync();
        FormClosing += (_, e) =>
        {
            if (!_reallyClose && e.CloseReason == CloseReason.UserClosing)
            {
                e.Cancel = true;
                Hide();
            }
        };
    }

    public void ForceClose()
    {
        _reallyClose = true;
        Close();
    }

    private async Task InitWebAsync()
    {
        try
        {
            string dataDir = Path.Combine(AppInfo.Dir, "webview");
            var env = await CoreWebView2Environment.CreateAsync(null, dataDir);
            await _web.EnsureCoreWebView2Async(env);

            var core = _web.CoreWebView2;
            core.Settings.AreDevToolsEnabled = false;
            core.Settings.IsStatusBarEnabled = false;
            core.AddHostObjectToScript("bridge", new Bridge(_app));
            await core.AddScriptToExecuteOnDocumentCreatedAsync(Shim);

            string uiDir = Path.Combine(AppInfo.Dir, "ui");
            Directory.CreateDirectory(uiDir);
            File.WriteAllText(Path.Combine(uiDir, "index.html"), LoadHtml(), new UTF8Encoding(false));
            core.SetVirtualHostNameToFolderMapping("dynamicisland.local", uiDir, CoreWebView2HostResourceAccessKind.Allow);
            core.Navigate("https://dynamicisland.local/index.html");
        }
        catch (Exception ex)
        {
            Log.Write(ex);
            MessageBox.Show(
                "Halaman pengaturan gagal dibuka. Pastikan Microsoft Edge WebView2 Runtime terpasang (sudah ada di Windows 11 dan sebagian besar Windows 10).\n\n" + ex.Message,
                "Dynamic Island", MessageBoxButtons.OK, MessageBoxIcon.Warning);
        }
    }

    private static string LoadHtml()
    {
        using var s = typeof(SettingsForm).Assembly.GetManifestResourceStream("settings.html");
        if (s == null) return "<html><body>settings.html tidak ditemukan di dalam aplikasi.</body></html>";
        using var r = new StreamReader(s, Encoding.UTF8);
        string html = r.ReadToEnd().TrimStart('﻿', ' ', '\r', '\n');
        // File prototipe diawali tanda "}" nyasar; buang supaya halaman tidak masuk mode quirks.
        if (html.StartsWith("}")) html = html.Substring(1);
        return html;
    }
}
