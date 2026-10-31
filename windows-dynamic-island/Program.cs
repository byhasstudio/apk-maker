namespace DynamicIsland;

internal static class Program
{
    [STAThread]
    private static void Main(string[] args)
    {
        // Satu salinan saja yang boleh jalan.
        using var mutex = new Mutex(true, "DynamicIslandWindows_byhasstudio", out bool first);
        if (!first) return;

        // Program ini jalan diam-diam di tray: jangan pernah munculkan dialog crash yang menunggu diklik.
        Application.SetUnhandledExceptionMode(UnhandledExceptionMode.CatchException);
        Application.ThreadException += (_, e) => Log.Write(e.Exception);
        AppDomain.CurrentDomain.UnhandledException += (_, e) => Log.Write(e.ExceptionObject as Exception);

        ApplicationConfiguration.Initialize();
        bool background = args.Contains("--background");

        try
        {
            Application.Run(new TrayApp(background));
        }
        catch (Exception ex)
        {
            Log.Write(ex);
        }
    }
}
