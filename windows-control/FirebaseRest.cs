using System.Net.Http;
using System.Net.Http.Json;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace RemoteCtlAgent;

/// <summary>
/// Jembatan ke Firebase lewat REST API biasa (bukan SDK resmi, karena
/// SDK Firebase Android/iOS tidak tersedia untuk .NET desktop). Memakai
/// alur yang sama persis dengan aplikasi Android: masuk anonim lewat
/// Firebase Authentication, lalu baca/tulis ke Realtime Database pakai
/// token hasil login itu, supaya lolos aturan keamanan "auth != null"
/// yang sama.
/// </summary>
public class FirebaseRest
{
    // Satu sesi login dipakai bersama oleh semua jendela selama
    // program berjalan -- supaya tidak perlu login ulang tiap buka
    // jendela Host/Kontrol/Pengaturan.
    public static readonly FirebaseRest Instance = new();

    // Web API key ini publik dan memang dimaksudkan untuk ditanam di
    // aplikasi klien (sama seperti yang dipakai aplikasi Android lewat
    // google-services.json) -- bukan kredensial rahasia.
    private const string ApiKey = "AIzaSyB9F4Hio2U0tVrFB2z6FHvCvjSpsjCoJHY";
    private const string DatabaseUrl =
        "https://apk-maker-ceb1f-default-rtdb.asia-southeast1.firebasedatabase.app";

    private readonly HttpClient _http = new();
    private string? _idToken;
    private string? _refreshToken;

    public string? Uid { get; private set; }
    public bool IsSignedIn => _idToken != null && Uid != null;

    /// <summary>
    /// Coba masuk lagi otomatis pakai refresh token yang tersimpan terenkripsi
    /// di komputer ini (DPAPI, terkunci ke akun Windows yang sedang login) --
    /// supaya "hubungkan tanpa kode" tetap jalan setelah komputer dinyalakan
    /// ulang, tanpa pengguna harus buka app dan ketik kata sandi lagi.
    /// </summary>
    public async Task<bool> TryRestoreSessionAsync()
    {
        var saved = SecureTokenStore.Load();
        if (saved == null) return false;

        try
        {
            var resp = await _http.PostAsync(
                $"https://securetoken.googleapis.com/v1/token?key={ApiKey}",
                new FormUrlEncodedContent(new Dictionary<string, string>
                {
                    ["grant_type"] = "refresh_token",
                    ["refresh_token"] = saved
                }));
            if (!resp.IsSuccessStatusCode) return false;

            var json = await resp.Content.ReadFromJsonAsync<JsonElement>();
            _idToken = json.GetProperty("id_token").GetString();
            _refreshToken = json.GetProperty("refresh_token").GetString();
            Uid = json.GetProperty("user_id").GetString();
            if (_refreshToken != null) SecureTokenStore.Save(_refreshToken);
            return _idToken != null;
        }
        catch
        {
            return false;
        }
    }

    public async Task<bool> SignInAnonymouslyAsync()
    {
        try
        {
            var resp = await _http.PostAsJsonAsync(
                $"https://identitytoolkit.googleapis.com/v1/accounts:signUp?key={ApiKey}",
                new { returnSecureToken = true });
            resp.EnsureSuccessStatusCode();
            var json = await resp.Content.ReadFromJsonAsync<JsonElement>();
            _idToken = json.GetProperty("idToken").GetString();
            Uid = json.GetProperty("localId").GetString();
            return _idToken != null;
        }
        catch
        {
            return false;
        }
    }

    /// <summary>Hasil berupa pesan error (null kalau berhasil).</summary>
    public async Task<string?> SignInEmailAsync(string email, string password)
        => await EmailAuthAsync("accounts:signInWithPassword", email, password);

    public async Task<string?> SignUpEmailAsync(string email, string password)
        => await EmailAuthAsync("accounts:signUp", email, password);

    private async Task<string?> EmailAuthAsync(string endpoint, string email, string password)
    {
        try
        {
            var resp = await _http.PostAsJsonAsync(
                $"https://identitytoolkit.googleapis.com/v1/{endpoint}?key={ApiKey}",
                new { email, password, returnSecureToken = true });

            if (!resp.IsSuccessStatusCode)
            {
                var errJson = await resp.Content.ReadFromJsonAsync<JsonElement>();
                var message = errJson.TryGetProperty("error", out var err) &&
                              err.TryGetProperty("message", out var msg)
                    ? msg.GetString() : "Gagal masuk.";
                return TranslateError(message ?? "Gagal masuk.");
            }

            var json = await resp.Content.ReadFromJsonAsync<JsonElement>();
            _idToken = json.GetProperty("idToken").GetString();
            Uid = json.GetProperty("localId").GetString();
            _refreshToken = json.TryGetProperty("refreshToken", out var rt) ? rt.GetString() : null;
            if (_refreshToken != null) SecureTokenStore.Save(_refreshToken);
            return null;
        }
        catch (Exception ex)
        {
            return ex.Message;
        }
    }

    /// <summary>Tukar ID token Google (dari GoogleAuth.SignInAsync) jadi sesi Firebase. Hasil = pesan error, null kalau berhasil.</summary>
    public async Task<string?> SignInWithGoogleIdTokenAsync(string googleIdToken)
    {
        try
        {
            var resp = await _http.PostAsJsonAsync(
                $"https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key={ApiKey}",
                new
                {
                    postBody = $"id_token={googleIdToken}&providerId=google.com",
                    requestUri = "http://localhost",
                    returnIdpCredential = true,
                    returnSecureToken = true
                });

            if (!resp.IsSuccessStatusCode)
            {
                var errJson = await resp.Content.ReadFromJsonAsync<JsonElement>();
                var message = errJson.TryGetProperty("error", out var err) &&
                              err.TryGetProperty("message", out var msg)
                    ? msg.GetString() : "Gagal masuk dengan Google.";
                return TranslateError(message ?? "Gagal masuk dengan Google.");
            }

            var json = await resp.Content.ReadFromJsonAsync<JsonElement>();
            _idToken = json.GetProperty("idToken").GetString();
            Uid = json.GetProperty("localId").GetString();
            _refreshToken = json.TryGetProperty("refreshToken", out var rt) ? rt.GetString() : null;
            if (_refreshToken != null) SecureTokenStore.Save(_refreshToken);
            return null;
        }
        catch (Exception ex)
        {
            return ex.Message;
        }
    }

    public void SignOut()
    {
        _idToken = null;
        _refreshToken = null;
        Uid = null;
        SecureTokenStore.Clear();
    }

    private static string TranslateError(string code) => code switch
    {
        "EMAIL_NOT_FOUND" or "INVALID_PASSWORD" or "INVALID_LOGIN_CREDENTIALS" => "Email atau kata sandi salah",
        "EMAIL_EXISTS" => "Email sudah terdaftar, coba Masuk",
        "WEAK_PASSWORD : Password should be at least 6 characters" => "Kata sandi minimal 6 karakter",
        _ => code
    };

    public async Task PutAsync(string path, object value)
    {
        if (_idToken == null) return;
        var content = new StringContent(
            JsonSerializer.Serialize(value), Encoding.UTF8, "application/json");
        await _http.PutAsync($"{DatabaseUrl}/{path}.json?auth={_idToken}", content);
    }

    /// <summary>Update sebagian field saja (padanan updateChildren() di Android), tidak menimpa field lain.</summary>
    public async Task PatchAsync(string path, object value)
    {
        if (_idToken == null) return;
        var content = new StringContent(
            JsonSerializer.Serialize(value), Encoding.UTF8, "application/json");
        var req = new HttpRequestMessage(HttpMethod.Patch, $"{DatabaseUrl}/{path}.json?auth={_idToken}")
        {
            Content = content
        };
        try { await _http.SendAsync(req); } catch { /* abaikan, coba lagi di putaran berikutnya */ }
    }

    public async Task DeleteAsync(string path)
    {
        if (_idToken == null) return;
        try { await _http.DeleteAsync($"{DatabaseUrl}/{path}.json?auth={_idToken}"); } catch { }
    }

    public async Task<JsonElement?> GetAsync(string path)
    {
        if (_idToken == null) return null;
        try
        {
            var resp = await _http.GetAsync($"{DatabaseUrl}/{path}.json?auth={_idToken}");
            if (!resp.IsSuccessStatusCode) return null;
            var text = await resp.Content.ReadAsStringAsync();
            if (string.IsNullOrWhiteSpace(text) || text == "null") return null;
            return JsonSerializer.Deserialize<JsonElement>(text);
        }
        catch
        {
            return null;
        }
    }
}
