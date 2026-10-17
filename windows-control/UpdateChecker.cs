using System.Net.Http;
using System.Net.Http.Headers;
using System.Text.Json;

namespace RemoteCtlAgent;

public record UpdateInfo(string Version, string Changelog, string ExeUrl);

/// <summary>
/// Mengecek rilis terbaru di GitHub Releases (repo publik, tidak perlu
/// token apa pun). Berbeda dari Android, di Windows tidak ada
/// penjaga sistem yang memblokir penggantian file sendiri, jadi
/// pembaruan di sini bisa benar-benar otomatis sampai aplikasi
/// restart sendiri dengan versi baru.
/// </summary>
public static class UpdateChecker
{
    public const string CurrentVersion = "0.2.0";

    private const string ReleasesUrl =
        "https://api.github.com/repos/byhasstudio/apk-maker/releases";
    private const string TagPrefix = "windows-v";

    public static async Task<UpdateInfo?> CheckForUpdateAsync()
    {
        try
        {
            using var http = new HttpClient();
            http.DefaultRequestHeaders.UserAgent.Add(new ProductInfoHeaderValue("RemoteCtlAgent", "1.0"));
            var json = await http.GetStringAsync(ReleasesUrl);
            using var doc = JsonDocument.Parse(json);

            foreach (var release in doc.RootElement.EnumerateArray())
            {
                var tag = release.GetProperty("tag_name").GetString() ?? "";
                if (!tag.StartsWith(TagPrefix)) continue;

                var remoteVersion = tag[TagPrefix.Length..];
                if (!IsNewer(remoteVersion, CurrentVersion)) return null;

                string? exeUrl = null;
                foreach (var asset in release.GetProperty("assets").EnumerateArray())
                {
                    var name = asset.GetProperty("name").GetString() ?? "";
                    if (name.EndsWith(".exe"))
                    {
                        exeUrl = asset.GetProperty("browser_download_url").GetString();
                        break;
                    }
                }
                if (exeUrl == null) return null;

                var changelog = release.TryGetProperty("body", out var b) ? b.GetString() : null;
                return new UpdateInfo(remoteVersion, string.IsNullOrWhiteSpace(changelog) ? "Pembaruan tersedia." : changelog!, exeUrl);
            }

            return null;
        }
        catch
        {
            return null;
        }
    }

    private static bool IsNewer(string remote, string current)
    {
        static int[] Parts(string v) => v.Split('.').Select(p => int.TryParse(p, out var n) ? n : 0).ToArray();
        var r = Parts(remote);
        var c = Parts(current);
        for (var i = 0; i < Math.Max(r.Length, c.Length); i++)
        {
            var rv = i < r.Length ? r[i] : 0;
            var cv = i < c.Length ? c[i] : 0;
            if (rv != cv) return rv > cv;
        }
        return false;
    }

    /// <summary>
    /// Mengunduh EXE baru, lalu menulis skrip batch kecil yang menunggu
    /// aplikasi ini benar-benar tertutup (supaya file tidak terkunci),
    /// menimpa file lama dengan yang baru, menjalankannya lagi, dan
    /// membersihkan dirinya sendiri. Aplikasi lalu keluar supaya skrip
    /// bisa menimpa file-nya.
    /// </summary>
    public static async Task DownloadAndSelfReplaceAsync(UpdateInfo info)
    {
        using var http = new HttpClient();
        var bytes = await http.GetByteArrayAsync(info.ExeUrl);

        var currentExe = Environment.ProcessPath!;
        var dir = Path.GetDirectoryName(currentExe)!;
        var newExe = Path.Combine(dir, "RemoteCtlAgent.new.exe");
        await File.WriteAllBytesAsync(newExe, bytes);

        var batPath = Path.Combine(Path.GetTempPath(), "remotectl_update.bat");
        var batContent = $"""
            @echo off
            timeout /t 2 /nobreak > NUL
            :retry
            copy /y "{newExe}" "{currentExe}" > NUL
            if errorlevel 1 (
                timeout /t 1 /nobreak > NUL
                goto retry
            )
            del "{newExe}"
            start "" "{currentExe}"
            del "%~f0"
            """;
        await File.WriteAllTextAsync(batPath, batContent);

        var psi = new System.Diagnostics.ProcessStartInfo
        {
            FileName = batPath,
            WindowStyle = System.Diagnostics.ProcessWindowStyle.Hidden,
            CreateNoWindow = true,
            UseShellExecute = true
        };
        System.Diagnostics.Process.Start(psi);

        Environment.Exit(0);
    }
}
