package de.langerhans.odintools.models

import java.util.Locale

/**
 * Names for the 3-letter manufacturer codes screens send in their identity data (EDID). The codes come from the
 * PNP ID registry; Android only gives us the code, so the common display makers are listed here.
 */
object DisplayVendors {
    private val names = mapOf(
        "ACR" to "Acer",
        "AOC" to "AOC",
        "APP" to "Apple",
        "AUO" to "AU Optronics",
        "AUS" to "ASUS",
        "BNQ" to "BenQ",
        "BOE" to "BOE",
        "CMN" to "Innolux",
        "DEL" to "Dell",
        "DEN" to "Denon",
        "ENC" to "EIZO",
        "FUS" to "Fujitsu",
        "GBT" to "Gigabyte",
        "GSM" to "LG",
        "HEC" to "Hisense",
        "HPN" to "HP",
        "HWP" to "HP",
        "IVM" to "iiyama",
        "LEN" to "Lenovo",
        "LGD" to "LG Display",
        "MEI" to "Panasonic",
        "MSI" to "MSI",
        "NEC" to "NEC",
        "ONK" to "Onkyo",
        "PHL" to "Philips",
        "PIO" to "Pioneer",
        "SAM" to "Samsung",
        "SDC" to "Samsung Display",
        "SHP" to "Sharp",
        "SNY" to "Sony",
        "TOS" to "Toshiba",
        "TSB" to "Toshiba",
        "VIZ" to "Vizio",
        "VSC" to "ViewSonic",
        "XGM" to "XGIMI",
        "YMH" to "Yamaha",
    )

    fun nameOf(pnpId: String?): String? = pnpId?.let { names[it.uppercase()] }

    /**
     * Classic vendor:product notation, as Linux EDID tools show it ("APP:AE42"). Android gives the product code
     * in decimal (44610), EDID tools write it as 4 hex digits.
     */
    fun vendorProductId(pnpId: String?, productId: String?): String? {
        val vendor = pnpId ?: return null
        val product = productId?.toIntOrNull()?.let { String.format(Locale.US, "%04X", it) } ?: productId
        return if (product == null) vendor else "$vendor:$product"
    }
}
