namespace RemoteCtlAgent;

/// <summary>
/// Padanan PresenceService.kt (foreground service) di Android, tapi di
/// Windows tidak ada listener realtime lewat REST, jadi dikerjakan lewat
/// polling berkala: kirim heartbeat supaya status "online" tetap benar,
/// dan cek apakah ada permintaan konek masuk -- kalau ada dan kata
/// sandinya cocok (atau memang mode tanpa kata sandi), langsung mulai
/// kirim layar lewat HostSession, tanpa perlu membuka jendela apa pun.
/// </summary>
public sealed class PresenceService
{
    public static readonly PresenceService Instance = new();

    private CancellationTokenSource? _cts;
    private HostSession? _activeSession;
    private readonly FirebaseRest _firebase = FirebaseRest.Instance;

    public bool IsRunning => _cts != null;

    public void Start()
    {
        if (_cts != null) return; // sudah jalan
        _cts = new CancellationTokenSource();
        _ = LoopAsync(_cts.Token);
    }

    public void Stop()
    {
        _cts?.Cancel();
        _cts = null;
        _activeSession?.Stop();
        _activeSession = null;
        _ = DeviceStore.SetOfflineAsync();
    }

    private async Task LoopAsync(CancellationToken token)
    {
        var lastHeartbeat = DateTime.MinValue;

        while (!token.IsCancellationRequested)
        {
            try
            {
                if (!_firebase.IsSignedIn)
                {
                    // Belum login di sesi ini (mis. baru dinyalakan dari startup Windows) --
                    // tidak bisa polling tanpa token, tunggu sampai pengguna login dari jendela manapun.
                    await Task.Delay(5000, token);
                    continue;
                }

                if ((DateTime.UtcNow - lastHeartbeat).TotalSeconds >= 30)
                {
                    await DeviceStore.HeartbeatAsync();
                    lastHeartbeat = DateTime.UtcNow;
                }

                var request = await DeviceStore.PollIncomingRequestAsync();
                if (request != null)
                {
                    await HandleIncomingRequestAsync(request.Value.code, request.Value.passwordHash);
                }
            }
            catch
            {
                // Satu putaran gagal (mis. jaringan putus sesaat) tidak boleh menghentikan service.
            }

            try { await Task.Delay(3000, token); } catch (TaskCanceledException) { break; }
        }
    }

    private async Task HandleIncomingRequestAsync(string code, string passwordHash)
    {
        await DeviceStore.ClearIncomingRequestAsync();

        var requiresPassword = QuickConnectPrefs.RequiresPassword;
        if (requiresPassword)
        {
            var storedHash = await DeviceStore.GetMyStoredPasswordHashAsync();
            if (string.IsNullOrEmpty(storedHash) || storedHash != passwordHash) return;
        }

        _activeSession?.Stop();
        _activeSession = new HostSession(_firebase, code);
        _activeSession.Start();
    }
}
