namespace DynamicIsland;

internal static class AppInfo
{
    public const string Version = "0.0.1";

    public static string Dir
    {
        get
        {
            var d = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
                "DynamicIsland");
            Directory.CreateDirectory(d);
            return d;
        }
    }
}

internal static class Log
{
    public static void Write(Exception? ex)
    {
        try
        {
            File.AppendAllText(
                Path.Combine(AppInfo.Dir, "crash.log"),
                $"[{DateTime.Now:yyyy-MM-dd HH:mm:ss}] {ex}\n\n");
        }
        catch
        {
            // Kalau mencatat log pun gagal, tidak ada yang bisa dilakukan lagi.
        }
    }
}
