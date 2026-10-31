using System.Runtime.InteropServices;
using System.Text.Json;

namespace DynamicIsland;

/// <summary>
/// Objek yang dibuka ke halaman pengaturan (WebView2). Semua panggilan lewat satu
/// pintu Dispatch(nama, argumenJson) supaya tidak ada ketidakcocokan tipe angka/teks.
/// </summary>
[ComVisible(true)]
[ClassInterface(ClassInterfaceType.AutoDual)]
public sealed class Bridge
{
    private readonly TrayApp _app;

    public Bridge(TrayApp app)
    {
        _app = app;
    }

    public string Dispatch(string name, string argsJson)
    {
        try
        {
            using var doc = JsonDocument.Parse(string.IsNullOrEmpty(argsJson) ? "[]" : argsJson);
            return _app.Handle(name, doc.RootElement) ?? "";
        }
        catch (Exception ex)
        {
            Log.Write(ex);
            return "";
        }
    }
}
