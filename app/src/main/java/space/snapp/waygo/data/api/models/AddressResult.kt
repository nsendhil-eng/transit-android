package space.snapp.waygo.data.api.models

import com.google.gson.annotations.SerializedName

data class AddressResult(
    val placeId: Long,
    val displayName: String,
    val lat: Double,
    val lon: Double
) {
    // First segment before the first comma — used as the short label in chips/fields
    // Nominatim sometimes formats as "123, Queen Street, ..." so if the first part is
    // purely a house number, combine it with the street name segment.
    val shortName: String get() {
        val parts = displayName.split(",").map { it.trim() }
        val first = parts.getOrElse(0) { displayName }
        return if (first.matches(Regex("\\d+[A-Za-z]?(/\\d+)?"))) {
            parts.getOrNull(1)?.let { "$first $it" } ?: first
        } else first
    }

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
