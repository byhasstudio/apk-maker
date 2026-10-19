using UnityEngine;

/// <summary>
/// Pergerakan dasar Survivor (Rigidbody 3D) dengan mekanik "Lantai Licin".
/// Pasang di GameObject Survivor yang punya: Rigidbody + CapsuleCollider.
/// Mode normal  : WASD responsif, kecepatan standar.
/// Mode licin   : WASD jadi gaya dorong (momentum), friksi hampir nol, karakter meluncur.
/// Tabrak dinding saat licin -> karakter jatuh (tidak bisa bergerak) selama 3 detik.
/// </summary>
[RequireComponent(typeof(Rigidbody))]
[RequireComponent(typeof(CapsuleCollider))]
public class SurvivorMovement : MonoBehaviour
{
    // ===================== PENGATURAN =====================

    [Header("Status Lantai")]
    [Tooltip("TRUE = lantai licin (meluncur seperti es). FALSE = jalan normal.")]
    public bool isSlippery = false;

    [Header("Gerak Normal")]
    public float moveSpeed = 5f;          // kecepatan jalan standar
    public float rotationSpeed = 12f;     // kecepatan karakter menghadap arah gerak

    [Header("Gerak Licin (Es)")]
    public float slipAcceleration = 18f;  // dorongan WASD saat licin (besar = makin liar)
    public float slipMaxSpeed = 11f;      // batas kecepatan maksimum saat meluncur
    public float slipDrift = 0.6f;        // goyangan acak kecil agar kontrol terasa liar

    [Header("Friksi")]
    public float normalDrag = 6f;         // drag Rigidbody saat normal (cepat berhenti)
    public float slipperyDrag = 0.15f;    // drag Rigidbody saat licin (hampir tidak melambat)
    public PhysicMaterial normalMaterial; // opsional: friction tinggi (0.6)
    public PhysicMaterial slipperyMaterial; // opsional: friction ~0 (Friction Combine = Minimum)

    [Header("Deteksi Tanah")]
    public float groundCheckDistance = 0.2f;
    public LayerMask groundMask = ~0;     // layer yang dianggap tanah

    [Header("Jatuh (Knockdown)")]
    public float knockdownDuration = 3f;  // lama tidak bisa bergerak setelah menabrak
    public float minImpactSpeed = 3f;     // kecepatan minimum tabrakan agar dianggap menabrak
    public float fallTorque = 4f;         // dorongan putar agar karakter tumbang

    [Header("Opsional")]
    [Tooltip("Animator model. Parameter trigger 'Fall' & 'GetUp' dipakai jika ada.")]
    public Animator animator;
    [Tooltip("Kalau model punya ragdoll (Rigidbody di tulang), isi di sini. Akan diaktifkan saat jatuh.")]
    public Rigidbody[] ragdollBodies;
    public Collider[] ragdollColliders;
    [Tooltip("Transform kamera third-person. Kosongkan = Camera.main.")]
    public Transform cameraTransform;

    // ===================== STATE INTERNAL =====================

    Rigidbody rb;
    CapsuleCollider capsule;
    Vector2 input;              // input WASD (x = A/D, y = W/S)
    bool isGrounded;
    bool isKnockedDown;
    float knockdownTimer;

    // Properti publik agar skrip lain (UI, jaringan) bisa membaca status.
    public bool IsKnockedDown => isKnockedDown;
    public Vector3 Velocity => rb.velocity;

    // ===================== SIKLUS UNITY =====================

    void Awake()
    {
        rb = GetComponent<Rigidbody>();
        capsule = GetComponent<CapsuleCollider>();
        if (cameraTransform == null && Camera.main != null) cameraTransform = Camera.main.transform;

        // Kunci rotasi X/Z supaya kapsul tidak terguling saat normal.
        rb.constraints = RigidbodyConstraints.FreezeRotationX | RigidbodyConstraints.FreezeRotationZ;
        rb.interpolation = RigidbodyInterpolation.Interpolate;
        rb.collisionDetectionMode = CollisionDetectionMode.Continuous;

        SetRagdoll(false);
        ApplyFloorType();
    }

    void Update()
    {
        // Baca input di Update (frame), terapkan fisika di FixedUpdate.
        input = new Vector2(Input.GetAxisRaw("Horizontal"), Input.GetAxisRaw("Vertical")); // W,A,S,D
        input = Vector2.ClampMagnitude(input, 1f);

        // Hitung timer jatuh; setelah selesai, berdiri lagi.
        if (isKnockedDown)
        {
            knockdownTimer -= Time.deltaTime;
            if (knockdownTimer <= 0f) GetUp();
        }
    }

    void FixedUpdate()
    {
        CheckGround();
        if (isKnockedDown) return;   // saat jatuh: tidak ada kontrol sama sekali

        Vector3 dir = GetCameraRelativeDirection();

        if (isSlippery) MoveSlippery(dir);
        else MoveNormal(dir);

        FaceDirection(dir);
    }

    // ===================== PERGERAKAN =====================

    /// <summary>Arah gerak relatif kamera (W = maju menjauhi kamera).</summary>
    Vector3 GetCameraRelativeDirection()
    {
        Vector3 fwd = cameraTransform ? cameraTransform.forward : Vector3.forward;
        Vector3 right = cameraTransform ? cameraTransform.right : Vector3.right;
        fwd.y = 0f; right.y = 0f;
        fwd.Normalize(); right.Normalize();
        return (fwd * input.y + right * input.x);
    }

    /// <summary>Gerak normal: kecepatan langsung mengikuti input, berhenti cepat.</summary>
    void MoveNormal(Vector3 dir)
    {
        Vector3 v = rb.velocity;
        Vector3 target = dir * moveSpeed;
        // Pertahankan kecepatan vertikal (gravitasi/lompat), ganti hanya horizontal.
        rb.velocity = new Vector3(target.x, v.y, target.z);
    }

    /// <summary>
    /// Gerak licin: input hanya menambah gaya (momentum). Drag sangat kecil sehingga
    /// karakter terus meluncur, sulit berbelok, dan sedikit "liar" oleh drift acak.
    /// </summary>
    void MoveSlippery(Vector3 dir)
    {
        if (!isGrounded) return; // di udara tidak ada dorongan tambahan

        // Gaya dorong dari WASD (mode Acceleration = abaikan massa).
        rb.AddForce(dir * slipAcceleration, ForceMode.Acceleration);

        // Goyangan acak halus agar kontrol terasa tidak stabil.
        if (dir.sqrMagnitude > 0.01f)
        {
            Vector3 wobble = new Vector3(Random.Range(-1f, 1f), 0f, Random.Range(-1f, 1f)) * slipDrift;
            rb.AddForce(wobble, ForceMode.Acceleration);
        }

        // Batasi kecepatan horizontal maksimum.
        Vector3 flat = new Vector3(rb.velocity.x, 0f, rb.velocity.z);
        if (flat.magnitude > slipMaxSpeed)
        {
            flat = flat.normalized * slipMaxSpeed;
            rb.velocity = new Vector3(flat.x, rb.velocity.y, flat.z);
        }
    }

    /// <summary>Putar model menghadap arah gerak (normal) atau arah luncur (licin).</summary>
    void FaceDirection(Vector3 dir)
    {
        Vector3 look = isSlippery ? new Vector3(rb.velocity.x, 0f, rb.velocity.z) : dir;
        if (look.sqrMagnitude < 0.05f) return;
        Quaternion target = Quaternion.LookRotation(look.normalized, Vector3.up);
        rb.MoveRotation(Quaternion.Slerp(rb.rotation, target, rotationSpeed * Time.fixedDeltaTime));
    }

    // ===================== LANTAI LICIN =====================

    /// <summary>Panggil dari skrip lain (mis. SlipperyZone) untuk mengganti jenis lantai.</summary>
    public void SetSlippery(bool value)
    {
        if (isSlippery == value) return;
        isSlippery = value;
        ApplyFloorType();
    }

    /// <summary>Ganti drag & physic material sesuai status lantai.</summary>
    void ApplyFloorType()
    {
        rb.drag = isSlippery ? slipperyDrag : 0f;      // normal: kecepatan diatur langsung
        capsule.material = isSlippery ? slipperyMaterial : normalMaterial;
    }

#if UNITY_EDITOR
    // Agar centang isSlippery di Inspector langsung berefek saat Play.
    void OnValidate()
    {
        if (Application.isPlaying && rb != null && capsule != null) ApplyFloorType();
    }
#endif

    // ===================== TABRAKAN & JATUH =====================

    void OnCollisionEnter(Collision col)
    {
        if (!isSlippery || isKnockedDown) return;

        // Cek apakah ini dinding: normal tabrakan hampir horizontal.
        foreach (ContactPoint c in col.contacts)
        {
            bool isWall = Mathf.Abs(c.normal.y) < 0.3f;
            if (isWall && col.relativeVelocity.magnitude >= minImpactSpeed)
            {
                Knockdown(-c.normal);   // jatuh searah pantulan dari dinding
                return;
            }
        }
    }

    /// <summary>Karakter jatuh dan tidak bisa bergerak selama knockdownDuration.</summary>
    public void Knockdown(Vector3 pushDir)
    {
        if (isKnockedDown) return;
        isKnockedDown = true;
        knockdownTimer = knockdownDuration;

        if (animator) animator.SetTrigger("Fall");

        if (ragdollBodies != null && ragdollBodies.Length > 0)
        {
            // Mode ragdoll penuh: nonaktifkan animator, aktifkan fisika tulang.
            SetRagdoll(true);
            foreach (var b in ragdollBodies) b.velocity = rb.velocity * 0.5f;
        }
        else
        {
            // Mode sederhana: lepas kunci rotasi, kapsul tumbang.
            rb.constraints = RigidbodyConstraints.None;
            Vector3 axis = Vector3.Cross(Vector3.up, pushDir.sqrMagnitude > 0 ? pushDir : transform.forward);
            rb.AddTorque(axis * fallTorque, ForceMode.VelocityChange);
        }
    }

    /// <summary>Berdiri lagi: rotasi dikembalikan tegak, kontrol aktif.</summary>
    void GetUp()
    {
        isKnockedDown = false;

        // Tegakkan karakter, pertahankan arah hadap (yaw) saja.
        float yaw = transform.eulerAngles.y;
        rb.angularVelocity = Vector3.zero;
        rb.velocity = Vector3.zero;
        transform.SetPositionAndRotation(transform.position + Vector3.up * 0.1f, Quaternion.Euler(0f, yaw, 0f));
        rb.constraints = RigidbodyConstraints.FreezeRotationX | RigidbodyConstraints.FreezeRotationZ;

        SetRagdoll(false);
        if (animator) animator.SetTrigger("GetUp");
    }

    /// <summary>Aktif/nonaktifkan ragdoll (jika model punya Rigidbody di tulang).</summary>
    void SetRagdoll(bool active)
    {
        if (ragdollBodies != null)
            foreach (var b in ragdollBodies) { if (b) b.isKinematic = !active; }
        if (ragdollColliders != null)
            foreach (var c in ragdollColliders) { if (c) c.enabled = active; }
        if (animator && ragdollBodies != null && ragdollBodies.Length > 0) animator.enabled = !active;
    }

    // ===================== DETEKSI TANAH =====================

    void CheckGround()
    {
        Vector3 origin = transform.position + capsule.center;
        float dist = capsule.height * 0.5f + groundCheckDistance;
        isGrounded = Physics.Raycast(origin, Vector3.down, dist, groundMask, QueryTriggerInteraction.Ignore);
    }
}
