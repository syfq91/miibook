package com.miireader.platform.epaper

import android.os.Build

enum class EpaperRefreshMode {
    NORMAL,
    HIGH_QUALITY,
    FAST
}

/**
 * Platform interface for e-paper display refresh control and hardware awareness.
 *
 * The default implementation performs no vendor-specific operations. This is deliberate:
 * as of the Phase 5 evaluation (documented in `plan.md`) there is **no refresh API that
 * can be confirmed for this app**, so nothing here may pretend to refresh a panel.
 *
 * Evaluation summary (see "Phase 5: E-Paper Optimization" in `plan.md` for sources):
 * - Onyx/BOOX ships a vendor SDK (`com.onyx.android.sdk:onyxsdk-device`, `EpdController`)
 *   on an HTTP-only Maven repository with pre-AndroidX dependencies. It covers BOOX only
 *   and cannot be exercised on a machine with no BOOX hardware.
 * - Bigme has no documented public refresh SDK; the community integrations use hidden,
 *   reflective vendor calls, which this project forbids.
 * - Android itself defines no e-ink refresh API.
 *
 * Consequences: [requestFullRefresh] and [setRefreshMode] are intentional no-ops, and no
 * Settings control exposes them. Add a device-specific implementation only after a
 * manufacturer SDK has been confirmed on real hardware, and keep this interface
 * vendor-neutral when you do.
 */
interface EpaperController {
    val isEpaperDevice: Boolean
    val deviceManufacturer: String
    val deviceModel: String

    /** No-op until a documented, hardware-confirmed vendor refresh API exists. */
    fun requestFullRefresh()

    /** No-op until a documented, hardware-confirmed vendor refresh API exists. */
    fun setRefreshMode(mode: EpaperRefreshMode)
}

/**
 * Vendor-neutral default. Device identity comes from [Build] and is used only for the
 * diagnostics block in Settings; refresh methods stay empty.
 *
 * Detection is a plain brand/manufacturer string match — it does not probe hardware and
 * must not be presented as an "e-ink profile" being active.
 */
class DefaultEpaperController(
    override val deviceManufacturer: String = Build.MANUFACTURER ?: "Android",
    override val deviceModel: String = Build.MODEL ?: "Generic Device",
    private val deviceBrand: String = Build.BRAND ?: ""
) : EpaperController {

    override val isEpaperDevice: Boolean
        get() {
            val brand = deviceBrand.lowercase()
            val manufacturer = deviceManufacturer.lowercase()
            return brand.contains("onyx") ||
                    manufacturer.contains("onyx") ||
                    brand.contains("boox") ||
                    brand.contains("bigme") ||
                    brand.contains("boyue") ||
                    brand.contains("meebook") ||
                    brand.contains("moan")
        }

    override fun requestFullRefresh() {
        // Intentionally empty: no vendor SDK has been confirmed (see interface docs).
    }

    override fun setRefreshMode(mode: EpaperRefreshMode) {
        // Intentionally empty: refresh behaviour stays with the device's own system
        // settings, which Settings points the user at instead of offering a control.
    }
}
