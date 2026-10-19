using UnityEngine;

/// <summary>
/// Kamera third-person sederhana: orbit dengan mouse, mengikuti target,
/// dan menghindari tembus dinding. Pasang di Main Camera.
/// </summary>
public class ThirdPersonCamera : MonoBehaviour
{
    [Header("Target")]
    public Transform target;                 // Survivor yang diikuti
    public Vector3 targetOffset = new Vector3(0f, 1.6f, 0f); // titik fokus (setinggi kepala)

    [Header("Orbit")]
    public float distance = 4.5f;
    public float mouseSensitivity = 3f;
    public float minPitch = -20f;
    public float maxPitch = 60f;
    public float followSmooth = 15f;

    [Header("Tabrakan Kamera")]
    public LayerMask obstructionMask = ~0;
    public float cameraRadius = 0.25f;

    float yaw, pitch = 15f;

    void Start()
    {
        Cursor.lockState = CursorLockMode.Locked; // kunci kursor ke tengah layar
        Cursor.visible = false;
        if (target) yaw = target.eulerAngles.y;
    }

    void LateUpdate()
    {
        if (!target) return;

        // Putar kamera dengan mouse.
        yaw += Input.GetAxis("Mouse X") * mouseSensitivity;
        pitch -= Input.GetAxis("Mouse Y") * mouseSensitivity;
        pitch = Mathf.Clamp(pitch, minPitch, maxPitch);

        Quaternion rot = Quaternion.Euler(pitch, yaw, 0f);
        Vector3 focus = target.position + targetOffset;
        Vector3 desired = focus - rot * Vector3.forward * distance;

        // Jika ada dinding di antara fokus dan kamera, majukan kamera.
        if (Physics.SphereCast(focus, cameraRadius, (desired - focus).normalized, out RaycastHit hit,
                               distance, obstructionMask, QueryTriggerInteraction.Ignore))
            desired = focus + (desired - focus).normalized * Mathf.Max(hit.distance - 0.1f, 0.3f);

        transform.position = Vector3.Lerp(transform.position, desired, followSmooth * Time.deltaTime);
        transform.rotation = rot;
    }
}
