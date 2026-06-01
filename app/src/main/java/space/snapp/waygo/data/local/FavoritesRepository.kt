package space.snapp.waygo.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import space.snapp.waygo.data.models.FavoritePlace
import space.snapp.waygo.data.models.FavoriteStop

private val Context.dataStore by preferencesDataStore(name = "favorites")

class FavoritesRepository(private val context: Context) {
    private val gson = Gson()
    private val stopsKey = stringPreferencesKey("favorite_stops_v2")
    private val placesKey = stringPreferencesKey("favorite_places_v1")

    val favoriteStops: Flow<List<FavoriteStop>> = context.dataStore.data.map { prefs ->
        val json = prefs[stopsKey] ?: return@map emptyList()
        val type = object : TypeToken<List<FavoriteStop>>() {}.type
        runCatching { gson.fromJson<List<FavoriteStop>>(json, type) }.getOrDefault(emptyList())
    }

    val favoritePlaces: Flow<List<FavoritePlace>> = context.dataStore.data.map { prefs ->
        val json = prefs[placesKey] ?: return@map emptyList()
        val type = object : TypeToken<List<FavoritePlace>>() {}.type
        runCatching { gson.fromJson<List<FavoritePlace>>(json, type) }.getOrDefault(emptyList())
    }

    suspend fun save(stops: List<FavoriteStop>) {
        context.dataStore.edit { prefs ->
            prefs[stopsKey] = gson.toJson(stops)
        }
    }

    suspend fun savePlaces(places: List<FavoritePlace>) {
        context.dataStore.edit { prefs ->
            prefs[placesKey] = gson.toJson(places)
        }
    }
}
