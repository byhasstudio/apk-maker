using Microsoft.Win32;

namespace RemoteCtlAgent;

/// <summary>
/// Simpan status hubungkan-tanpa-kode secara lokal (registry pengguna,
/// padanan SharedPreferences di Android) supaya PresenceService bisa
/// langsung tahu harus menyala atau tidak saat program dibuka, tanpa
/// menunggu jaringan dulu.
/// </summary>
public static class QuickConnectPrefs
{
    private const string KeyPath = @"SOFTWARE\RemoteCtlAgent";

    public static bool IsEnabled
    {
        get => ReadBool("QuickConnectEnabled", false);
        private set => WriteBool("QuickConnectEnabled", value);
    }

    public static bool RequiresPassword
    {
        get => ReadBool("QuickConnectRequiresPassword", true);
        private set => WriteBool("QuickConnectRequiresPassword", value);
    }

    public static void Save(bool enabled, bool requiresPassword)
    {
        IsEnabled = enabled;
        RequiresPassword = requiresPassword;
    }

    private static bool ReadBool(string name, bool fallback)
    {
        try
        {
            using var key = Registry.CurrentUser.OpenSubKey(KeyPath);
            var value = key?.GetValue(name);
            return value is int i ? i != 0 : fallback;
        }
        catch { return fallback; }
    }

    private static void WriteBool(string name, bool value)
    {
        try
        {
            using var key = Registry.CurrentUser.CreateSubKey(KeyPath);
            key.SetValue(name, value ? 1 : 0, RegistryValueKind.DWord);
        }
        catch { /* bukan fatal -- tinggal tidak tersimpan, coba lagi lain kali */ }
    }
}
