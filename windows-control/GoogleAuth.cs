using System.Diagnostics;
using System.Net;
using System.Net.Http.Json;
using System.Text.Json;

namespace RemoteCtlAgent;

/// <summary>
/// Login Google untuk aplikasi desktop lewat alur OAuth "loopback":
/// buka browser default Windows ke halaman login Google, tunggu Google
/// mengarahkan balik ke http://127.0.0.1:{port} bawaan program ini
/// (bukan server beneran, cuma listener sementara di komputer sendiri),
/// lalu tukar kode yang didapat jadi ID token Google -- yang kemudian
/// dipakai FirebaseRest buat masuk ke akun yang sama seperti di Android.
///
/// Butuh OAuth Client ID tipe "Desktop app" dari Google Cloud Console
/// (proyek yang sama dengan Firebase) -- lihat README bagian "Login
/// Google". CLIENT_ID dan CLIENT_SECRET di bawah diisi otomatis oleh
/// workflow build lewat GitHub secret, bukan rahasia yang perlu
/// disembunyikan ketat (client desktop memang begini menurut dokumentasi
/// resmi Google -- ini bukan client *confidential*).
/// </summary>
public static class GoogleAuth
{
    private const string ClientId = "ISI_GOOGLE_DESKTOP_CLIENT_ID";
    private const string ClientSecret = "ISI_GOOGLE_DESKTOP_CLIENT_SECRET";

    /// <summary>Hasil berupa ID token Google (buat ditukar ke Firebase), atau null kalau dibatalkan/gagal.</summary>
    public static async Task<string?> SignInAsync()
    {
        if (ClientId.StartsWith("ISI_")) return null; // belum di-setup, lihat README

        using var listener = new HttpListener();
        var port = GetFreePort();
        var redirectUri = $"http://127.0.0.1:{port}/";
        listener.Prefixes.Add(redirectUri);
        listener.Start();

        var authUrl = "https://accounts.google.com/o/oauth2/v2/auth" +
                      $"?client_id={Uri.EscapeDataString(ClientId)}" +
                      $"&redirect_uri={Uri.EscapeDataString(redirectUri)}" +
                      "&response_type=code" +
                      "&scope=openid%20email%20profile" +
                      "&prompt=select_account";

        try
        {
            Process.Start(new ProcessStartInfo(authUrl) { UseShellExecute = true });
        }
        catch
        {
            listener.Stop();
            return null;
        }

        string? code = null;
        try
        {
            // Tunggu Google mengarahkan balik ke sini, maksimal 2 menit
            // (kalau pengguna tidak kunjung menyelesaikan login di browser).
            var contextTask = listener.GetContextAsync();
            var completed = await Task.WhenAny(contextTask, Task.Delay(TimeSpan.FromMinutes(2)));
            if (completed != contextTask) return null;

            var context = contextTask.Result;
            code = context.Request.QueryString["code"];
            var error = context.Request.QueryString["error"];

            var html = error == null
                ? "<html><body style='font-family:sans-serif;text-align:center;padding-top:80px'><h2>Berhasil masuk</h2><p>Boleh tutup tab ini dan kembali ke Remote Ctl.</p></body></html>"
                : "<html><body style='font-family:sans-serif;text-align:center;padding-top:80px'><h2>Login dibatalkan</h2></body></html>";
            var buffer = System.Text.Encoding.UTF8.GetBytes(html);
            context.Response.ContentType = "text/html; charset=utf-8";
            context.Response.ContentLength64 = buffer.Length;
            await context.Response.OutputStream.WriteAsync(buffer);
            context.Response.OutputStream.Close();

            if (error != null || code == null) return null;
        }
        finally
        {
            listener.Stop();
        }

        return await ExchangeCodeForIdTokenAsync(code, redirectUri);
    }

    private static async Task<string?> ExchangeCodeForIdTokenAsync(string code, string redirectUri)
    {
        try
        {
            using var http = new HttpClient();
            var resp = await http.PostAsync("https://oauth2.googleapis.com/token",
                new FormUrlEncodedContent(new Dictionary<string, string>
                {
                    ["code"] = code,
                    ["client_id"] = ClientId,
                    ["client_secret"] = ClientSecret,
                    ["redirect_uri"] = redirectUri,
                    ["grant_type"] = "authorization_code"
                }));
            if (!resp.IsSuccessStatusCode) return null;

            var json = await resp.Content.ReadFromJsonAsync<JsonElement>();
            return json.TryGetProperty("id_token", out var idToken) ? idToken.GetString() : null;
        }
        catch
        {
            return null;
        }
    }

    private static int GetFreePort()
    {
        var listener = new System.Net.Sockets.TcpListener(IPAddress.Loopback, 0);
        listener.Start();
        var port = ((IPEndPoint)listener.LocalEndpoint).Port;
        listener.Stop();
        return port;
    }
}
