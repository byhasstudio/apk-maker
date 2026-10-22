namespace RemoteCtlAgent;

internal static class Program
{
    [STAThread]
    private static void Main(string[] args)
    {
        // PENTING: program ini dirancang jalan diam-diam di tray/latar
        // belakang (lihat ModeSelectForm.SetVisibleCore) -- kalau ada
        // exception yang lolos tidak tertangani, defaultnya .NET
        // menampilkan dialog crash "Unhandled exception..." yang
        // MENGUNCI aplikasi sampai ada yang klik Continue/Quit. Kalau ini
        // terjadi saat dinyalakan otomatis lewat Windows startup (tidak
        // ada orang di depan komputer), "hubungkan tanpa kode" jadi mati
        // total tanpa ada yang tahu kenapa. Daftarkan handler di sini
        // SEBELUM apa pun lain dijalankan supaya tertangkap sedini mungkin:
        // dicatat ke file log, lalu program tetap dilanjutkan/ditutup diam-diam,
        // tidak pernah menampilkan dialog yang menunggu diklik manusia.
        Application.SetUnhandledExceptionMode(UnhandledExceptionMode.CatchException);
        Application.ThreadException += (_, e) => LogCrash(e.Exception);
        AppDomain.CurrentDomain.UnhandledException += (_, e) => LogCrash(e.ExceptionObject as Exception);

        ApplicationConfiguration.Initialize();
        // Dipakai saat dinyalakan otomatis oleh Windows startup (lihat StartupManager):
        // langsung ke tray tanpa menampilkan jendela, supaya tidak mengganggu.
        var startHidden = args.Contains("--background");
        Application.Run(new ModeSelectForm(startHidden));
    }

    private static void LogCrash(Exception? ex)
    {
        try
        {
            var dir = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
                "RemoteCtlAgent");
            Directory.CreateDirectory(dir);
            File.AppendAllText(Path.Combine(dir, "crash.log"),
                $"[{DateTime.Now:yyyy-MM-dd HH:mm:ss}] {ex}\n\n");
        }
        catch
        {
            // Kalau mencatat log pun gagal, tidak ada lagi yang bisa
            // dilakukan -- yang penting di atas (ThreadException handler)
            // tetap mencegah dialog crash muncul.
        }
    }
}
