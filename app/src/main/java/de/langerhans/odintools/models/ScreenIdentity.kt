package de.langerhans.odintools.models

import java.util.Locale

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

    /**
     * Classic vendor:product notation, as Linux EDID tools show it ("APP:AE42"). Android gives the product code
     * in decimal (44610), EDID tools write it as 4 hex digits.
     */
    val vendorProductId: String?
        get() {
            val vendor = manufacturerPnpId ?: return null
            val product = productId?.toIntOrNull()?.let { String.format(Locale.US, "%04X", it) } ?: productId
            return if (product == null) vendor else "$vendor:$product"
        }
}
