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
    // Sama persis dengan DeviceStore.ADMIN_EMAIL di Android -- akun khusus
    // ini bisa melihat & menghubungkan ke SEMUA perangkat yang sedang
    // login, walau belum mengaktifkan "hubungkan tanpa kode". Nilainya
    // adalah "email" internal hasil ubahan otomatis dari username admin
    // (lihat LoginForm.UsernameToFakeEmail) -- username admin saat ini:
    // "haerka949".
    public const string AdminEmail = "haerka949@remotectl.local";

    public static bool IsAdmin =>
        string.Equals(FirebaseRest.Instance.Email, AdminEmail, StringComparison.OrdinalIgnoreCase);

    public record DeviceEntry(string Id, string Name, bool Online, bool RequiresPassword, bool QuickConnectEnabled);

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

    /// <summary>Hasil berupa pesan error (null kalau berhasil) -- supaya kalau
    /// penyimpanan ini ditolak server (mis. aturan keamanan database belum
    /// ter-publish dengan benar), pengguna langsung lihat pesannya alih-alih
    /// aplikasi diam-diam bilang "Aktif" padahal gagal tersimpan.</summary>
    public static async Task<string?> EnableQuickConnectAsync(bool requirePassword, string? password)
    {
        if (!Fb.IsSignedIn) return "Belum login.";
        var path = $"{DevicesPath}/{DeviceId()}";
        return await Fb.PutAsync(path, new
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
        // Cuma matikan akses "siapa saja boleh konek" -- TIDAK menandai
        // offline, karena presensi (RegisterPresenceAsync) berjalan terus
        // selama login, lepas dari toggle ini (supaya akun admin tetap
        // bisa lihat & konek).
        await Fb.PatchAsync(path, new { enabled = false });
    }

    /// <summary>
    /// Daftarkan komputer ini supaya TERLIHAT di daftar (nama + status
    /// online), lepas dari apakah "hubungkan tanpa kode" sudah diaktifkan
    /// -- supaya akun admin (AdminEmail) selalu bisa melihat & konek ke
    /// komputer ini kapan pun sedang login. Dipanggil terus-menerus lewat
    /// PresenceService, bukan cuma dari menu Pengaturan.
    /// </summary>
    public static async Task RegisterPresenceAsync()
    {
        if (!Fb.IsSignedIn) return;
        var path = $"{DevicesPath}/{DeviceId()}";
        await Fb.PatchAsync(path, new
        {
            name = Environment.MachineName + " (Windows)",
            online = true,
            lastSeen = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
        });
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

    /// <summary>Hasil (code, passwordHash, requesterEmail) kalau ada permintaan konek masuk, null kalau tidak ada.</summary>
    public static async Task<(string code, string passwordHash, string requesterEmail)?> PollIncomingRequestAsync()
    {
        if (!Fb.IsSignedIn) return null;
        var path = $"{DevicesPath}/{DeviceId()}/incomingRequest";
        var data = await Fb.GetAsync(path);
        if (data is not JsonElement el || el.ValueKind != JsonValueKind.Object) return null;
        if (!el.TryGetProperty("code", out var codeEl)) return null;
        var code = codeEl.GetString();
        if (string.IsNullOrEmpty(code)) return null;
        var hash = el.TryGetProperty("passwordHash", out var h) ? h.GetString() ?? "" : "";
        var requesterEmail = el.TryGetProperty("requesterEmail", out var re) ? re.GetString() ?? "" : "";
        return (code, hash, requesterEmail);
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

    /// <summary>
    /// Daftar SEMUA perangkat yang sedang/pernah login (lepas dari apakah
    /// "hubungkan tanpa kode" diaktifkan) -- supaya akun admin bisa lihat
    /// dan konek ke perangkat mana pun. Untuk akun bukan admin, percobaan
    /// konek ke perangkat yang QuickConnectEnabled-nya false akan ditolak
    /// diam-diam di sisi perangkat tujuan.
    /// </summary>
    public static async Task<List<DeviceEntry>> ListMyDevicesAsync()
    {
        var result = new List<DeviceEntry>();
        if (!Fb.IsSignedIn) return result;
        var data = await Fb.GetAsync(DevicesPath);
        if (data is not JsonElement el || el.ValueKind != JsonValueKind.Object) return result;

        foreach (var prop in el.EnumerateObject())
        {
            var dev = prop.Value;
            var name = dev.TryGetProperty("name", out var n) ? n.GetString() ?? "Perangkat" : "Perangkat";
            var online = dev.TryGetProperty("online", out var o) && o.ValueKind == JsonValueKind.True;
            var requiresPassword = !dev.TryGetProperty("requiresPassword", out var rp) ||
                                    rp.ValueKind != JsonValueKind.False;
            var quickConnectEnabled = dev.TryGetProperty("enabled", out var en) && en.ValueKind == JsonValueKind.True;
            result.Add(new DeviceEntry(prop.Name, name, online, requiresPassword, quickConnectEnabled));
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
            requesterEmail = Fb.Email ?? "",
            ts = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
        });
    }

    /// <summary>
    /// Kirim perintah jarak jauh (matikan/restart/dst) ke perangkat lain --
    /// tidak butuh sesi kontrol aktif, cuma butuh perangkat tujuan masih
    /// login & PresenceService-nya jalan (lihat PollRemoteCommandAsync di
    /// PresenceService.cs / listenRemoteCommand di PresenceService.kt).
    /// Hanya berguna kalau pengirimnya akun admin (haerka949) -- akun lain
    /// tidak punya akses baca/tulis ke node sharedDevices sama sekali
    /// (lihat database.rules.json), jadi tombol ini di UI memang cuma
    /// ditampilkan untuk admin.
    /// </summary>
    public static async Task<string?> SendRemoteCommandAsync(string deviceId, string type, string? text = null)
    {
        if (!Fb.IsSignedIn) return "Belum login.";
        var path = $"{DevicesPath}/{deviceId}/remoteCommand";
        return await Fb.PutAsync(path, new
        {
            type,
            text = text ?? "",
            ts = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
        });
    }

    /// <summary>Hasil (type, text) kalau ada perintah jarak jauh masuk buat perangkat ini sendiri, null kalau tidak ada.</summary>
    public static async Task<(string type, string text)?> PollRemoteCommandAsync()
    {
        if (!Fb.IsSignedIn) return null;
        var path = $"{DevicesPath}/{DeviceId()}/remoteCommand";
        var data = await Fb.GetAsync(path);
        if (data is not JsonElement el || el.ValueKind != JsonValueKind.Object) return null;
        if (!el.TryGetProperty("type", out var typeEl)) return null;
        var type = typeEl.GetString();
        if (string.IsNullOrEmpty(type)) return null;
        var text = el.TryGetProperty("text", out var t) ? t.GetString() ?? "" : "";
        return (type, text);
    }

    public static async Task ClearRemoteCommandAsync()
    {
        if (!Fb.IsSignedIn) return;
        var path = $"{DevicesPath}/{DeviceId()}/remoteCommand";
        await Fb.DeleteAsync(path);
    }
}
