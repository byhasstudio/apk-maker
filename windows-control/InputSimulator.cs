using System.Runtime.InteropServices;

namespace RemoteCtlAgent;

/// <summary>
/// Mengeksekusi perintah klik/geser dari perangkat pengontrol sebagai
/// gerakan mouse nyata di Windows, lewat Win32 API SendInput -- ini
/// padanan Windows dari AccessibilityService di aplikasi Android.
/// </summary>
public static class InputSimulator
{
    [DllImport("user32.dll")]
    private static extern bool SetCursorPos(int x, int y);

    [DllImport("user32.dll")]
    private static extern void mouse_event(uint dwFlags, int dx, int dy, uint dwData, nuint dwExtraInfo);

    private const uint MOUSEEVENTF_LEFTDOWN = 0x0002;
    private const uint MOUSEEVENTF_LEFTUP = 0x0004;

    public static void Tap(int x, int y)
    {
        SetCursorPos(x, y);
        mouse_event(MOUSEEVENTF_LEFTDOWN, 0, 0, 0, 0);
        mouse_event(MOUSEEVENTF_LEFTUP, 0, 0, 0, 0);
    }

    public static async Task SwipeAsync(int x1, int y1, int x2, int y2, int durationMs)
    {
        SetCursorPos(x1, y1);
        mouse_event(MOUSEEVENTF_LEFTDOWN, 0, 0, 0, 0);

        const int steps = 12;
        var stepDelay = Math.Max(1, durationMs / steps);
        for (var i = 1; i <= steps; i++)
        {
            var x = x1 + (x2 - x1) * i / steps;
            var y = y1 + (y2 - y1) * i / steps;
            SetCursorPos(x, y);
            await Task.Delay(stepDelay);
        }

        mouse_event(MOUSEEVENTF_LEFTUP, 0, 0, 0, 0);
    }
}
