using System.Diagnostics;

namespace RemoteCtlAgent;

/// <summary>
/// Eksekusi perintah jarak jauh (matikan, restart, keluar akun, tidur,
/// tampilkan teks) yang dikirim akun admin lewat DeviceStore.SendRemoteCommandAsync
/// -- dijalankan dari PresenceService begitu perintah terdeteksi, tidak
/// butuh sesi kontrol layar aktif sama sekali.
/// </summary>
public static class RemoteCommandRunner
{
    public static void Run(string type, string text)
    {
        try
        {
            switch (type)
            {
                case "shutdown":
                    // /s = matikan, /t 0 = langsung tanpa jeda, /f = paksa
                    // tutup aplikasi yang masih terbuka supaya tidak nyangkut.
                    Process.Start(new ProcessStartInfo("shutdown", "/s /t 0 /f") { UseShellExecute = true, CreateNoWindow = true });
                    break;

                case "restart":
                    Process.Start(new ProcessStartInfo("shutdown", "/r /t 0 /f") { UseShellExecute = true, CreateNoWindow = true });
                    break;

                case "signout":
                    // /l = log off pengguna yang sedang aktif di sesi ini.
                    Process.Start(new ProcessStartInfo("shutdown", "/l") { UseShellExecute = true, CreateNoWindow = true });
                    break;

                case "sleep":
                    // Tidak ada flag "tidur" di shutdown.exe -- lewat
                    // powrprof.dll (SetSuspendState), dipanggil lewat rundll32
                    // supaya tidak perlu P/Invoke langsung dari proses ini.
                    Process.Start(new ProcessStartInfo("rundll32.exe", "powrprof.dll,SetSuspendState 0,1,0") { UseShellExecute = true, CreateNoWindow = true });
                    break;

                case "text":
                    ShowText(text);
                    break;
            }
        }
        catch
        {
            // Perintah gagal dijalankan (mis. tidak punya izin) -- tidak ada
            // yang bisa dilaporkan balik ke pengirim di versi ini, jadi
            // cukup dibiarkan, jangan sampai mematikan PresenceService.
        }
    }

    /// <summary>
    /// Munculkan teks yang diketik admin lewat jendela Command Prompt yang
    /// tetap terbuka di komputer tujuan -- paling sederhana & selalu bisa
    /// dilihat lepas dari aplikasi apa yang sedang dibuka di layar itu.
    /// </summary>
    private static void ShowText(string text)
    {
        var safe = (string.IsNullOrWhiteSpace(text) ? "(tidak ada teks)" : text)
            .Replace("^", "^^").Replace("&", "^&").Replace("|", "^|")
            .Replace("<", "^<").Replace(">", "^>").Replace("\"", "'")
            .Replace("\r\n", " ").Replace("\n", " ");
        Process.Start(new ProcessStartInfo
        {
            FileName = "cmd.exe",
            Arguments = $"/k title Pesan dari Remote Ctl && color 0A && echo {safe}",
            UseShellExecute = true
        });
    }
}
