package space.snapp.waygo.data.api

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import space.snapp.waygo.data.api.models.NominatimResult

interface NominatimService {

    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("countrycodes") countrycodes: String = "au",
        // Bounding box covering Greater SEQ: Gold Coast → Sunshine Coast
        @Query("viewbox") viewbox: String = "152.3,-28.4,153.6,-26.3",
        @Query("bounded") bounded: Int = 1,
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 6
    ): List<NominatimResult>

    companion object {
        val instance: NominatimService by lazy {
            val client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    // Nominatim usage policy requires a descriptive User-Agent
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("User-Agent", "WayGo/1.0 (space.snapp.waygo; Brisbane transit app)")
                            .build()
                    )
                }
                .build()
            Retrofit.Builder()
                .baseUrl("https://nominatim.openstreetmap.org/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(NominatimService::class.java)
        }
    }
}
