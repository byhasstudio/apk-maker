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
    // Web API key ini publik dan memang dimaksudkan untuk ditanam di
    // aplikasi klien (sama seperti yang dipakai aplikasi Android lewat
    // google-services.json) -- bukan kredensial rahasia.
    private const string ApiKey = "AIzaSyB9F4Hio2U0tVrFB2z6FHvCvjSpsjCoJHY";
    private const string DatabaseUrl =
        "https://apk-maker-ceb1f-default-rtdb.asia-southeast1.firebasedatabase.app";

    private readonly HttpClient _http = new();
    private string? _idToken;

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
            return _idToken != null;
        }
        catch
        {
            return false;
        }
    }

    public async Task PutAsync(string path, object value)
    {
        if (_idToken == null) return;
        var content = new StringContent(
            JsonSerializer.Serialize(value), Encoding.UTF8, "application/json");
        await _http.PutAsync($"{DatabaseUrl}/{path}.json?auth={_idToken}", content);
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
