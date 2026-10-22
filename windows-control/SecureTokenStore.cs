using System.Security.Cryptography;

namespace RemoteCtlAgent;

/// <summary>
/// Simpan refresh token Firebase terenkripsi lewat Windows DPAPI (terkunci
/// ke akun Windows pengguna yang sedang login, tidak bisa dibaca akun lain
/// atau komputer lain) -- supaya program bisa masuk lagi otomatis setelah
/// restart tanpa menyimpan kata sandi mentah di mana pun.
/// </summary>
public static class SecureTokenStore
{
    private static readonly string FilePath = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
        "RemoteCtlAgent", "session.bin");

    public static void Save(string refreshToken)
    {
        try
        {
            var dir = Path.GetDirectoryName(FilePath)!;
            Directory.CreateDirectory(dir);
            var plain = System.Text.Encoding.UTF8.GetBytes(refreshToken);
            var encrypted = ProtectedData.Protect(plain, null, DataProtectionScope.CurrentUser);
            File.WriteAllBytes(FilePath, encrypted);
        }
        catch
        {
            // Bukan fatal -- tinggal perlu login manual lagi kalau gagal tersimpan.
        }
    }

    public static string? Load()
    {
        try
        {
            if (!File.Exists(FilePath)) return null;
            var encrypted = File.ReadAllBytes(FilePath);
            var plain = ProtectedData.Unprotect(encrypted, null, DataProtectionScope.CurrentUser);
            return System.Text.Encoding.UTF8.GetString(plain);
        }
        catch
        {
            // File sesi rusak / tidak bisa didekripsi (mis. disalin dari
            // akun Windows atau komputer lain -- DPAPI mengunci isinya ke
            // akun Windows yang membuatnya) -- hapus supaya tidak terus
            // dicoba baca ulang tiap program dibuka, dan supaya pengguna
            // tinggal login manual sekali lagi alih-alih stuck permanen.
            Clear();
            return null;
        }
    }

    public static void Clear()
    {
        try { if (File.Exists(FilePath)) File.Delete(FilePath); } catch { }
    }
}
