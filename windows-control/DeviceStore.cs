using System.Security.Cryptography;
using System.Text;
using System.Text.Json;

namespace RemoteCtlAgent;

/// <summary>
/// Padanan DeviceStore.kt di aplikasi Android: menyimpan & membaca
/// daftar "perangkat tersimpan" untuk fitur hubungkan-tanpa-kode.
/// Disimpan di satu node bersama /sharedDevices/{deviceId} yang bisa
/// dibaca & ditulis oleh akun mana pun yang sudah login -- jadi
/// perangkat Android dan Windows tetap muncul sama-sama di daftar,
/// walaupun login pakai akun Firebase yang berbeda-beda.
/// </summary>
public static class DeviceStore
{
    public record DeviceEntry(string Id, string Name, bool Online, bool RequiresPassword);

    private static FirebaseRest Fb => FirebaseRest.Instance;

    /// <summary>ID stabil untuk komputer ini, dipakai sebagai key di bawah devices/.</summary>
    public static string DeviceId()
    {
        try
        {
            using var key = Microsoft.Win32.Registry.LocalMachine.OpenSubKey(
                @"SOFTWARE\Microsoft\Cryptography");
            var guid = key?.GetValue("MachineGuid") as string;
            if (!string.IsNullOrEmpty(guid)) return "win-" + guid;
        }
        catch { /* abaikan, pakai fallback di bawah */ }

        return "win-" + Environment.MachineName;
    }

    public static string Hash(string text)
    {
        var bytes = SHA256.HashData(Encoding.UTF8.GetBytes(text));
        var sb = new StringBuilder();
        foreach (var b in bytes) sb.Append(b.ToString("x2"));
        return sb.ToString();
    }

    private const string DevicesPath = "sharedDevices";

    public static async Task EnableQuickConnectAsync(bool requirePassword, string? password)
    {
        if (!Fb.IsSignedIn) return;
        var path = $"{DevicesPath}/{DeviceId()}";
        await Fb.PutAsync(path, new
        {
            name = Environment.MachineName + " (Windows)",
            passwordHash = requirePassword && password != null ? Hash(password) : "",
            requiresPassword = requirePassword,
            enabled = true,
            online = true,
            lastSeen = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
        });
    }

    public static async Task DisableQuickConnectAsync()
    {
        if (!Fb.IsSignedIn) return;
        var path = $"{DevicesPath}/{DeviceId()}";
        await Fb.PatchAsync(path, new { enabled = false, online = false });
    }

    public static async Task HeartbeatAsync()
    {
        if (!Fb.IsSignedIn) return;
        var path = $"{DevicesPath}/{DeviceId()}";
        await Fb.PatchAsync(path, new
        {
            online = true,
            lastSeen = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
        });
    }

    public static async Task SetOfflineAsync()
    {
        if (!Fb.IsSignedIn) return;
        var path = $"{DevicesPath}/{DeviceId()}";
        await Fb.PatchAsync(path, new { online = false });
    }

    /// <summary>Hasil (code, passwordHash) kalau ada permintaan konek masuk, null kalau tidak ada.</summary>
    public static async Task<(string code, string passwordHash)?> PollIncomingRequestAsync()
    {
        if (!Fb.IsSignedIn) return null;
        var path = $"{DevicesPath}/{DeviceId()}/incomingRequest";
        var data = await Fb.GetAsync(path);
        if (data is not JsonElement el || el.ValueKind != JsonValueKind.Object) return null;
        if (!el.TryGetProperty("code", out var codeEl)) return null;
        var code = codeEl.GetString();
        if (string.IsNullOrEmpty(code)) return null;
        var hash = el.TryGetProperty("passwordHash", out var h) ? h.GetString() ?? "" : "";
        return (code, hash);
    }

    public static async Task ClearIncomingRequestAsync()
    {
        if (!Fb.IsSignedIn) return;
        var path = $"{DevicesPath}/{DeviceId()}/incomingRequest";
        await Fb.DeleteAsync(path);
    }

    /// <summary>Hash kata sandi yang tersimpan untuk perangkat ini sendiri ("" kalau mode tanpa kata sandi).</summary>
    public static async Task<string?> GetMyStoredPasswordHashAsync()
    {
        if (!Fb.IsSignedIn) return null;
        var path = $"{DevicesPath}/{DeviceId()}/passwordHash";
        var data = await Fb.GetAsync(path);
        if (data is not JsonElement el || el.ValueKind != JsonValueKind.String) return null;
        return el.GetString();
    }

    public static async Task<List<DeviceEntry>> ListMyDevicesAsync()
    {
        var result = new List<DeviceEntry>();
        if (!Fb.IsSignedIn) return result;
        var data = await Fb.GetAsync(DevicesPath);
        if (data is not JsonElement el || el.ValueKind != JsonValueKind.Object) return result;

        foreach (var prop in el.EnumerateObject())
        {
            var dev = prop.Value;
            var enabled = dev.TryGetProperty("enabled", out var en) && en.ValueKind == JsonValueKind.True;
            if (!enabled) continue;
            var name = dev.TryGetProperty("name", out var n) ? n.GetString() ?? "Perangkat" : "Perangkat";
            var online = dev.TryGetProperty("online", out var o) && o.ValueKind == JsonValueKind.True;
            var requiresPassword = !dev.TryGetProperty("requiresPassword", out var rp) ||
                                    rp.ValueKind != JsonValueKind.False;
            result.Add(new DeviceEntry(prop.Name, name, online, requiresPassword));
        }

        return result;
    }

    public static async Task RequestConnectAsync(string deviceId, string code, string? password)
    {
        if (!Fb.IsSignedIn) return;
        var path = $"{DevicesPath}/{deviceId}/incomingRequest";
        await Fb.PutAsync(path, new
        {
            code,
            passwordHash = password != null ? Hash(password) : "",
            ts = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
        });
    }
}
