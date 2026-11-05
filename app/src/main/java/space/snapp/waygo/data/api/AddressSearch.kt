package space.snapp.waygo.data.api

import space.snapp.waygo.data.api.models.AddressResult

/**
 * Address + place autocomplete shared by the planner and saved places, through the WayGo API
 * (/api/places), which runs our own Photon geocoder (or the public services until that's
 * set up). The app no longer calls Photon or Nominatim directly: their policies limit heavy and
 * commercial use, and one server-side cache serves everyone.
 */
object AddressSearch {
    private val api = TransitApiService.instance

    suspend fun search(query: String, lat: Double? = null, lon: Double? = null): List<AddressResult> =
        runCatching { api.places(query.trim(), lat, lon) }.getOrDefault(emptyList())
}
