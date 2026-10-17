using System.Drawing;
using System.Drawing.Imaging;

namespace RemoteCtlAgent;

/// <summary>
/// Mengambil cuplikan layar utama secara berkala, diperkecil dan
/// dikompres jadi JPEG kualitas rendah -- sama persis dengan
/// pendekatan di aplikasi Android (cuplikan foto berkala, bukan video
/// real-time), supaya ukurannya aman dikirim lewat Realtime Database.
/// </summary>
public static class ScreenCapture
{
    private const int TargetWidth = 480;

    public static (string base64Jpeg, int fullWidth, int fullHeight) CaptureJpegBase64()
    {
        var bounds = Screen.PrimaryScreen!.Bounds;
        var scale = (float)TargetWidth / bounds.Width;
        var targetHeight = Math.Max(1, (int)(bounds.Height * scale));

        using var full = new Bitmap(bounds.Width, bounds.Height);
        using (var g = Graphics.FromImage(full))
        {
            g.CopyFromScreen(bounds.Location, Point.Empty, bounds.Size);
        }

        using var resized = new Bitmap(TargetWidth, targetHeight);
        using (var g = Graphics.FromImage(resized))
        {
            g.InterpolationMode = System.Drawing.Drawing2D.InterpolationMode.Low;
            g.DrawImage(full, 0, 0, TargetWidth, targetHeight);
        }

        using var ms = new MemoryStream();
        var jpegEncoder = ImageCodecInfo.GetImageEncoders().First(c => c.FormatID == ImageFormat.Jpeg.Guid);
        var parameters = new EncoderParameters(1);
        parameters.Param[0] = new EncoderParameter(Encoder.Quality, 40L);
        resized.Save(ms, jpegEncoder, parameters);

        return (Convert.ToBase64String(ms.ToArray()), bounds.Width, bounds.Height);
    }
}
