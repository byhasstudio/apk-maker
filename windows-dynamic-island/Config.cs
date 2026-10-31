using System.Text.Json;

namespace DynamicIsland;

/// <summary>Pengaturan yang disimpan di %APPDATA%\DynamicIsland\config.json.</summary>
public sealed class Config
{
    public bool Running { get; set; } = true;

    // Tampilan pulau
    public string Bg { get; set; } = "#000000";
    public double Op { get; set; } = 100;
    public double Top { get; set; } = 0;
    public double Cw { get; set; } = 110;
    public double Ew { get; set; } = 340;
    public double Cx { get; set; } = 0;
    public double Dh { get; set; } = 0;
    public string Edge { get; set; } = "top";
    public bool Pill { get; set; } = true;
    public string Mon { get; set; } = "primary";

    // Perilaku
    public double DurMs { get; set; } = 4500;
    public bool Snd { get; set; } = false;
    public bool TapOpen { get; set; } = true;
    public bool Bat { get; set; } = true;
    public bool Clip { get; set; } = false;
    public bool Dnd { get; set; } = false;
    public bool Qon { get; set; } = false;
    public string Qfrom { get; set; } = "22:00";
    public string Qto { get; set; } = "07:00";
    public bool HistOn { get; set; } = true;

    // Pilihan lain dari halaman pengaturan yang belum dipakai fitur apa pun
    public Dictionary<string, JsonElement> Extra { get; set; } = new();

    private static string FilePath => System.IO.Path.Combine(AppInfo.Dir, "config.json");

    public static Config Load()
    {
        try
        {
            if (File.Exists(FilePath))
            {
                var c = JsonSerializer.Deserialize<Config>(File.ReadAllText(FilePath));
                if (c != null) return c;
            }
        }
        catch (Exception ex)
        {
            Log.Write(ex);
        }
        return new Config();
    }

    public void Save()
    {
        try
        {
            File.WriteAllText(FilePath, JsonSerializer.Serialize(this, new JsonSerializerOptions { WriteIndented = true }));
        }
        catch (Exception ex)
        {
            Log.Write(ex);
        }
    }
}
