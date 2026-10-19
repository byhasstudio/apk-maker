using UnityEngine;

/// <summary>
/// Area lantai licin. Pasang di objek lantai dengan Collider (centang Is Trigger)
/// yang menutupi area licin. Survivor yang masuk -> isSlippery = true, keluar -> false.
/// </summary>
[RequireComponent(typeof(Collider))]
public class SlipperyZone : MonoBehaviour
{
    void Reset() { GetComponent<Collider>().isTrigger = true; }

    void OnTriggerEnter(Collider other)
    {
        var s = other.GetComponentInParent<SurvivorMovement>();
        if (s) s.SetSlippery(true);
    }

    void OnTriggerExit(Collider other)
    {
        var s = other.GetComponentInParent<SurvivorMovement>();
        if (s) s.SetSlippery(false);
    }
}
