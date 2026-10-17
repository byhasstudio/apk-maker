using Microsoft.Win32;

namespace RemoteCtlAgent;

/// <summary>
/// Daftarkan/batalkan program supaya menyala otomatis saat Windows
/// start -- padanan BootReceiver di Android, karena Windows tidak
/// punya broadcast "boot selesai" bawaan untuk aplikasi biasa.
/// </summary>
public static class StartupManager
{
    private const string RunKeyPath = @"SOFTWARE\Microsoft\Windows\CurrentVersion\Run";
    private const string ValueName = "RemoteCtlAgent";

    public static bool IsEnabled()
    {
        try
        {
            using var key = Registry.CurrentUser.OpenSubKey(RunKeyPath);
            return key?.GetValue(ValueName) != null;
        }
        catch { return false; }
    }

    public static void Enable()
    {
        try
        {
            var exePath = Environment.ProcessPath ?? Application.ExecutablePath;
            using var key = Registry.CurrentUser.CreateSubKey(RunKeyPath);
            key.SetValue(ValueName, $"\"{exePath}\" --background", RegistryValueKind.String);
        }
        catch { /* bukan fatal -- fitur autostart saja yang tidak jalan */ }
    }

    public static void Disable()
    {
        try
        {
            using var key = Registry.CurrentUser.OpenSubKey(RunKeyPath, writable: true);
            key?.DeleteValue(ValueName, throwOnMissingValue: false);
        }
        catch { }
    }
}
