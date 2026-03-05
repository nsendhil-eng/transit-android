package space.snapp.waygo.data.api.models

import com.google.gson.annotations.SerializedName

data class AddressResult(
    val placeId: Long,
    val displayName: String,
    val lat: Double,
    val lon: Double
) {
    // First segment before the first comma — used as the short label in chips/fields
    val shortName: String get() = displayName.substringBefore(",").trim()

    // Remainder of the address after the short name — shown as subtitle
    val subtitle: String get() = displayName.removePrefix(shortName).removePrefix(", ").trim()
}

/** Raw shape returned by the Nominatim JSON API */
data class NominatimResult(
    @SerializedName("place_id") val placeId: Long,
    @SerializedName("display_name") val displayName: String,
    @SerializedName("lat") val lat: String,
    @SerializedName("lon") val lon: String
)
