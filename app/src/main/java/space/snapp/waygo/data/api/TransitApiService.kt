package space.snapp.waygo.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import space.snapp.waygo.BuildConfig
import space.snapp.waygo.data.api.models.AddressResult
import space.snapp.waygo.data.api.models.AlertsResponse
import space.snapp.waygo.data.api.models.Departure
import space.snapp.waygo.data.api.models.EtaResponse
import space.snapp.waygo.data.api.models.NextTripsResponse
import space.snapp.waygo.data.api.models.PlanDelayResponse
import space.snapp.waygo.data.api.models.PlanResponse
import space.snapp.waygo.data.api.models.RouteShape
import space.snapp.waygo.data.api.models.SearchResponse
import space.snapp.waygo.data.api.models.ShapesResponse
import space.snapp.waygo.data.api.models.Stop
import space.snapp.waygo.data.api.models.StopLiveResponse
import space.snapp.waygo.data.api.models.TripDetailResponse
import space.snapp.waygo.data.api.models.TripVehiclesResponse
import space.snapp.waygo.data.api.models.VehiclePosition

interface TransitApiService {

    @GET("/api/v2/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("lat") lat: Double? = null,
        @Query("lon") lon: Double? = null
    ): SearchResponse

    @GET("/api/departures")
    suspend fun departures(
        @Query("stops") stops: String,
        @Query("per_stop") perStop: Int? = null,
        @Query("get_off_stop") getOffStop: String? = null,
        // 1 = include each departure's heading (which way it leaves the stop)
        @Query("with_heading") withHeading: Int? = null,
        // Each route + direction's next N departures from its stop nearest lat/lon
        @Query("per_route") perRoute: Int? = null,
        @Query("lat") lat: Double? = null,
        @Query("lon") lon: Double? = null
    ): List<Departure>

    /** Whole route shapes, simplified: `ids` is "shapeId,shapeId" */
    @GET("/api/shapes")
    suspend fun shapes(@Query("ids") ids: String): ShapesResponse

    /** Live vehicles on the given trips ("T1,T2"); trips with none reporting are left out */
    @GET("/api/trip-vehicles")
    suspend fun tripVehicles(@Query("trip_ids") tripIds: String): TripVehiclesResponse

    /** Travel time from here to a place, now: a saved-place chip's answer */
    @GET("/api/eta")
    suspend fun eta(
        @Query("fromLat") fromLat: Double,
        @Query("fromLon") fromLon: Double,
        @Query("toLat") toLat: Double,
        @Query("toLon") toLon: Double
    ): EtaResponse

    /** Places and addresses in SEQ for "Where to?" and saved places, biased towards lat/lon */
    @GET("/api/places")
    suspend fun places(
        @Query("q") query: String,
        @Query("lat") lat: Double? = null,
        @Query("lon") lon: Double? = null
    ): List<AddressResult>

    @GET("/api/stops-near-me")
    suspend fun stopsNearMe(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("radius") radius: Int = 500,
        // Trains and ferries this far away too (a station worth the walk)
        @Query("far_radius") farRadius: Int? = null
    ): List<Stop>

    @GET("/api/shapes-near-me")
    suspend fun shapesNearMe(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double
    ): List<RouteShape>

    @GET("/api/vehicles-near-me")
    suspend fun vehiclesNearMe(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("radius") radius: Int = 1000
    ): List<VehiclePosition>

    @GET("/api/stop-live")
    suspend fun stopLive(
        @Query("stop_ids") stopIds: String
    ): StopLiveResponse

    @GET("/api/trip-stops")
    suspend fun tripStops(
        @Query("trip_id") tripId: String? = null,
        @Query("route_id") routeId: String? = null,
        @Query("direction_id") directionId: Int? = null,
        @Query("user_lat") userLat: Double? = null,
        @Query("user_lon") userLon: Double? = null
    ): TripDetailResponse

    @GET("/api/next-trips")
    suspend fun nextTrips(
        @Query("route_id") routeId: String,
        @Query("direction_id") directionId: Int,
        @Query("stop_id") stopId: String
    ): NextTripsResponse

    @GET("/api/alerts")
    suspend fun alerts(
        @Query("route_ids") routeIds: String? = null,
        @Query("stop_ids") stopIds: String? = null
    ): AlertsResponse

    @GET("/api/alerts/all")
    suspend fun allAlerts(
        @Query("lat") lat: Double? = null,
        @Query("lon") lon: Double? = null
    ): AlertsResponse

    @GET("/api/plan-delays")
    suspend fun planDelays(
        @Query("trip_ids") tripIds: String
    ): PlanDelayResponse

    @GET("/api/plan")
    suspend fun plan(
        @Query("fromLat") fromLat: Double,
        @Query("fromLon") fromLon: Double,
        @Query("toLat") toLat: Double,
        @Query("toLon") toLon: Double,
        @Query("date") date: String? = null,
        @Query("time") time: String? = null
    ): PlanResponse

    companion object {
        val instance: TransitApiService by lazy {
            val logging = HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            Retrofit.Builder()
                .baseUrl("https://transit.sn-app.space")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(TransitApiService::class.java)
        }
    }
}
