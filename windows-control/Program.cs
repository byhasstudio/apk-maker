namespace RemoteCtlAgent;

internal static class Program
{
    [STAThread]
    private static void Main(string[] args)
    {
        ApplicationConfiguration.Initialize();
        // Dipakai saat dinyalakan otomatis oleh Windows startup (lihat StartupManager):
        // langsung ke tray tanpa menampilkan jendela, supaya tidak mengganggu.
        var startHidden = args.Contains("--background");
        Application.Run(new ModeSelectForm(startHidden));
    }
}
