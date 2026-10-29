package space.snapp.waygo.data.api

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import space.snapp.waygo.data.api.models.PhotonResponse

/**
 * Photon (komoot) — OSM geocoder built for search-as-you-type.
 * Unlike Nominatim it matches partial words ("Westfield Cherm") and ranks by proximity.
 */
interface PhotonService {

    @GET("api/")
    suspend fun search(
        @Query("q") query: String,
        // Location bias — results nearer this point rank higher
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        // Bounding box covering Greater SEQ: Gold Coast → Sunshine Coast (minLon,minLat,maxLon,maxLat)
        @Query("bbox") bbox: String = "152.3,-28.4,153.6,-26.3",
        @Query("lang") lang: String = "en",
        // Over-fetch so filtering out bus stops still leaves enough results
        @Query("limit") limit: Int = 10
    ): PhotonResponse

    companion object {
        val instance: PhotonService by lazy {
            val client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("User-Agent", "WayGo/1.0 (space.snapp.waygo; Brisbane transit app)")
                            .build()
                    )
                }
                .build()
            Retrofit.Builder()
                .baseUrl("https://photon.komoot.io/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(PhotonService::class.java)
        }
    }
}
