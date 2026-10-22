using System.Runtime.InteropServices;

namespace RemoteCtlAgent;

/// <summary>
/// Mengeksekusi perintah klik/geser dari perangkat pengontrol sebagai
/// gerakan mouse nyata di Windows, lewat Win32 API SendInput -- ini
/// padanan Windows dari AccessibilityService di aplikasi Android.
///
/// Mouse dikirim lewat SendInput dengan koordinat ABSOLUT ternormalisasi
/// (0..65535, lihat ToAbsolute) -- BUKAN lewat SetCursorPos + mouse_event
/// seperti versi sebelumnya. SetCursorPos/mouse_event adalah API lama yang
/// kadang tidak konsisten di komputer dengan banyak monitor atau skala
/// tampilan (DPI) berbeda-beda, sehingga klik/geser bisa "tidak kerasa"
/// sama sekali di komputer tertentu walau tidak ada error yang terlihat.
/// SendInput adalah cara resmi Microsoft yang dipakai aplikasi remote
/// desktop sungguhan, dan konsisten dengan layar utama (primary monitor)
/// yang juga dipakai ScreenCapture.cs untuk mengambil cuplikan layar.
/// </summary>
public static class InputSimulator
{
    [StructLayout(LayoutKind.Sequential)]
    private struct INPUT
    {
        public uint type;
        public InputUnion U;
    }

    [StructLayout(LayoutKind.Explicit)]
    private struct InputUnion
    {
        [FieldOffset(0)] public MOUSEINPUT mi;
        [FieldOffset(0)] public KEYBDINPUT ki;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct MOUSEINPUT
    {
        public int dx;
        public int dy;
        public uint mouseData;
        public uint dwFlags;
        public uint time;
        public IntPtr dwExtraInfo;
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

    [DllImport("user32.dll")]
    private static extern int GetSystemMetrics(int nIndex);

    private const int SM_CXSCREEN = 0;
    private const int SM_CYSCREEN = 1;

    private const uint INPUT_MOUSE = 0;
    private const uint INPUT_KEYBOARD = 1;

    private const uint MOUSEEVENTF_MOVE = 0x0001;
    private const uint MOUSEEVENTF_LEFTDOWN = 0x0002;
    private const uint MOUSEEVENTF_LEFTUP = 0x0004;
    private const uint MOUSEEVENTF_ABSOLUTE = 0x8000;

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
            Send(down, up);
        }
    }

    /// <summary>Tekan satu tombol spesial (Enter, Backspace, dll) lewat kode virtual-key Windows.</summary>
    public static void SendSpecialKey(ushort virtualKeyCode)
    {
        var down = new INPUT { type = INPUT_KEYBOARD, U = new InputUnion { ki = new KEYBDINPUT { wVk = virtualKeyCode } } };
        var up = new INPUT { type = INPUT_KEYBOARD, U = new InputUnion { ki = new KEYBDINPUT { wVk = virtualKeyCode, dwFlags = KEYEVENTF_KEYUP } } };
        Send(down, up);
    }

    public static void Tap(int x, int y)
    {
        var (ax, ay) = ToAbsolute(x, y);
        var move = MouseInput(ax, ay, MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE);
        var down = MouseInput(ax, ay, MOUSEEVENTF_LEFTDOWN | MOUSEEVENTF_ABSOLUTE);
        var up = MouseInput(ax, ay, MOUSEEVENTF_LEFTUP | MOUSEEVENTF_ABSOLUTE);
        Send(move, down, up);
    }

    public static async Task SwipeAsync(int x1, int y1, int x2, int y2, int durationMs)
    {
        var (ax1, ay1) = ToAbsolute(x1, y1);
        Send(MouseInput(ax1, ay1, MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE));
        Send(MouseInput(ax1, ay1, MOUSEEVENTF_LEFTDOWN | MOUSEEVENTF_ABSOLUTE));

        const int steps = 12;
        var stepDelay = Math.Max(1, durationMs / steps);
        for (var i = 1; i <= steps; i++)
        {
            var x = x1 + (x2 - x1) * i / steps;
            var y = y1 + (y2 - y1) * i / steps;
            var (ax, ay) = ToAbsolute(x, y);
            Send(MouseInput(ax, ay, MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE));
            await Task.Delay(stepDelay);
        }

        var (axEnd, ayEnd) = ToAbsolute(x2, y2);
        Send(MouseInput(axEnd, ayEnd, MOUSEEVENTF_LEFTUP | MOUSEEVENTF_ABSOLUTE));
    }

    private static INPUT MouseInput(int absX, int absY, uint flags) =>
        new() { type = INPUT_MOUSE, U = new InputUnion { mi = new MOUSEINPUT { dx = absX, dy = absY, dwFlags = flags } } };

    private static void Send(params INPUT[] inputs)
    {
        var sent = SendInput((uint)inputs.Length, inputs, Marshal.SizeOf<INPUT>());
        if (sent != inputs.Length)
        {
            // SendInput menolak semua atau sebagian input -- biasanya karena
            // UIPI (jendela yang sedang fokus di komputer ini berjalan
            // sebagai Administrator sementara RemoteCtlAgent.exe berjalan
            // sebagai pengguna biasa, atau sebaliknya ada dialog UAC/layar
            // aman yang sedang aktif). Dilempar sebagai exception supaya
            // HostSession bisa melaporkan ini balik ke pengguna, alih-alih
            // diam-diam tidak berefek tanpa penjelasan sama sekali.
            var err = Marshal.GetLastWin32Error();
            throw new InvalidOperationException(
                $"SendInput ditolak sistem (kode {err}). Kemungkinan ada jendela yang berjalan sebagai Administrator sedang aktif di layar -- coba jalankan RemoteCtlAgent.exe ini juga \"Run as administrator\".");
        }
    }

    /// <summary>
    /// Ubah koordinat piksel asli (sesuai lebar/tinggi layar utama yang
    /// dikirim ScreenCapture) jadi koordinat absolut ternormalisasi
    /// 0..65535 yang diminta SendInput -- dihitung dari layar UTAMA saja
    /// (GetSystemMetrics SM_CXSCREEN/SM_CYSCREEN), supaya tetap sama
    /// persis dengan area yang dicuplik ScreenCapture.CaptureJpegBase64
    /// (yang juga cuma memakai Screen.PrimaryScreen).
    /// </summary>
    private static (int x, int y) ToAbsolute(int x, int y)
    {
        var screenW = Math.Max(1, GetSystemMetrics(SM_CXSCREEN));
        var screenH = Math.Max(1, GetSystemMetrics(SM_CYSCREEN));
        var ax = (int)Math.Clamp(x * 65536.0 / screenW, 0, 65535);
        var ay = (int)Math.Clamp(y * 65536.0 / screenH, 0, 65535);
        return (ax, ay);
    }
}
