package de.langerhans.odintools.models

/**
 * Who an external screen is, from the identity data (EDID) it sends, plus the mode currently used.
 */
data class ScreenIdentity(
    val name: String,
    val manufacturerPnpId: String?,
    val productId: String?,
    val manufactureDate: String?,
    val uniqueId: String?,
    val width: Int,
    val height: Int,
    val refreshRate: Float,
) {
    /** Recognises this screen again on later connections (same model, whatever port or mode). */
    val key: String
        get() = listOf(manufacturerPnpId, productId, name).joinToString(":")

    /** The maker's name ("Apple" for APP), when the code is a known one. */
    val vendorName: String?
        get() = DisplayVendors.nameOf(manufacturerPnpId)

    /** Name to show: the model name, prefixed with the maker unless the screen already includes it. */
    val displayName: String
        get() = vendorName?.takeUnless { name.startsWith(it, ignoreCase = true) }?.let { "$it $name" } ?: name

    /** Classic vendor:product notation ("APP:AE42"). */
    val vendorProductId: String?
        get() = DisplayVendors.vendorProductId(manufacturerPnpId, productId)
}
