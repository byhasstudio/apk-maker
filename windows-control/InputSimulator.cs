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

    // --- Keyboard: dipakai fitur "keyboard jarak jauh" (ketik teks dari
    // perangkat pengontrol). Dikirim lewat SendInput ke jendela yang
    // sedang fokus di komputer ini -- sama seperti mengetik langsung
    // di keyboard fisik, apa pun aplikasi yang sedang aktif.
    [StructLayout(LayoutKind.Sequential)]
    private struct INPUT
    {
        public uint type;
        public InputUnion U;
    }

    [StructLayout(LayoutKind.Explicit)]
    private struct InputUnion
    {
        [FieldOffset(0)] public KEYBDINPUT ki;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct KEYBDINPUT
    {
        public ushort wVk;
        public ushort wScan;
        public uint dwFlags;
        public uint time;
        public IntPtr dwExtraInfo;
    }

    [DllImport("user32.dll", SetLastError = true)]
    private static extern uint SendInput(uint nInputs, INPUT[] pInputs, int cbSize);

    private const uint INPUT_KEYBOARD = 1;
    private const uint KEYEVENTF_UNICODE = 0x0004;
    private const uint KEYEVENTF_KEYUP = 0x0002;

    public const ushort VK_RETURN = 0x0D;
    public const ushort VK_BACK = 0x08;
    public const ushort VK_TAB = 0x09;

    /// <summary>Ketik sebuah string persis seperti diketik dari keyboard asli (lewat SendInput Unicode).</summary>
    public static void TypeText(string text)
    {
        foreach (var ch in text)
        {
            var down = new INPUT { type = INPUT_KEYBOARD, U = new InputUnion { ki = new KEYBDINPUT { wScan = ch, dwFlags = KEYEVENTF_UNICODE } } };
            var up = new INPUT { type = INPUT_KEYBOARD, U = new InputUnion { ki = new KEYBDINPUT { wScan = ch, dwFlags = KEYEVENTF_UNICODE | KEYEVENTF_KEYUP } } };
            SendInput(2, new[] { down, up }, Marshal.SizeOf<INPUT>());
        }
    }

    /// <summary>Tekan satu tombol spesial (Enter, Backspace, dll) lewat kode virtual-key Windows.</summary>
    public static void SendSpecialKey(ushort virtualKeyCode)
    {
        var down = new INPUT { type = INPUT_KEYBOARD, U = new InputUnion { ki = new KEYBDINPUT { wVk = virtualKeyCode } } };
        var up = new INPUT { type = INPUT_KEYBOARD, U = new InputUnion { ki = new KEYBDINPUT { wVk = virtualKeyCode, dwFlags = KEYEVENTF_KEYUP } } };
        SendInput(2, new[] { down, up }, Marshal.SizeOf<INPUT>());
    }

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
