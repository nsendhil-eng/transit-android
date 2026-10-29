package space.snapp.waygo.data.api

import space.snapp.waygo.data.api.models.AddressResult
import space.snapp.waygo.data.api.models.PhotonFeature

/**
 * Address + place autocomplete shared by the planner and favourite places.
 *
 * - Queries starting with a house number ("45 Station Rd") → Nominatim, which is
 *   better at exact street addresses.
 * - Everything else (place names, partial words) → Photon, biased to the user's
 *   location; falls back to Nominatim if Photon fails or returns nothing.
 */
object AddressSearch {
    private val photon = PhotonService.instance
    private val nominatim = NominatimService.instance

    // Brisbane CBD — bias point when the user's location is unknown
    private const val DEFAULT_LAT = -27.4698
    private const val DEFAULT_LON = 153.0251

    private val houseNumberPrefix = Regex("^\\d+[A-Za-z]?(/\\d+)?\\s")

    suspend fun search(query: String, lat: Double? = null, lon: Double? = null): List<AddressResult> {
        val q = query.trim()
        if (houseNumberPrefix.containsMatchIn(q)) return searchNominatim(q)
        val places = runCatching { searchPhoton(q, lat ?: DEFAULT_LAT, lon ?: DEFAULT_LON) }
            .getOrDefault(emptyList())
        return places.ifEmpty { searchNominatim(q) }
    }

    private suspend fun searchNominatim(q: String): List<AddressResult> =
        runCatching {
            nominatim.search(q).map {
                AddressResult(
                    placeId = it.placeId,
                    displayName = it.displayName,
                    lat = it.lat.toDoubleOrNull() ?: 0.0,
                    lon = it.lon.toDoubleOrNull() ?: 0.0
                )
            }
        }.getOrDefault(emptyList())

    private suspend fun searchPhoton(q: String, lat: Double, lon: Double): List<AddressResult> =
        photon.search(q, lat, lon).features.orEmpty()
            .filterNot { it.isTransitStop() }
            .mapNotNull { it.toAddressResult() }
            // Photon often returns the same place twice (e.g. node + building way)
            .distinctBy { it.displayName }
            .take(6)

    // Transit stops are already covered by the app's own stop search
    private fun PhotonFeature.isTransitStop(): Boolean {
        val key = properties?.osmKey
        val value = properties?.osmValue
        return (key == "highway" && value == "bus_stop") ||
            (key == "public_transport" && (value == "platform" || value == "stop_position"))
    }

    private fun PhotonFeature.toAddressResult(): AddressResult? {
        val p = properties ?: return null
        val coords = geometry?.coordinates ?: return null
        if (coords.size < 2) return null
        // Same comma-separated shape as Nominatim so AddressResult.shortName/subtitle work unchanged
        val area = p.district ?: p.locality
        val displayName = listOfNotNull(
            p.name,
            p.housenumber,
            p.street,
            area,
            p.city?.takeIf { it != area },
            p.state,
            p.postcode
        ).filter { it.isNotBlank() }.joinToString(", ")
        if (displayName.isEmpty()) return null
        // osm_id is only unique per element type (node/way/relation), so fold the type in
        val typeIndex = when (p.osmType) { "N" -> 0L; "W" -> 1L; else -> 2L }
        return AddressResult(
            placeId = (p.osmId ?: displayName.hashCode().toLong()) * 3 + typeIndex,
            displayName = displayName,
            lat = coords[1],
            lon = coords[0]
        )
    }
}
