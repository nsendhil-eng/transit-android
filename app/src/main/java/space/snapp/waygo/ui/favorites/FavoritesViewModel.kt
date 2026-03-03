package space.snapp.waygo.ui.favorites

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import space.snapp.waygo.data.api.TransitApiService
import space.snapp.waygo.data.api.models.Stop
import space.snapp.waygo.data.local.FavoritesRepository
import space.snapp.waygo.data.models.FavoriteStop

class FavoritesViewModel(private val repo: FavoritesRepository) : ViewModel() {
    private val api = TransitApiService.instance

    private val _favorites = MutableStateFlow<List<FavoriteStop>>(emptyList())
    val favorites = _favorites.asStateFlow()

    init {
        viewModelScope.launch {
            repo.favoriteStops.collect { _favorites.value = it }
        }
    }

    fun saveFavorite(fav: FavoriteStop) {
        viewModelScope.launch {
            repo.save(_favorites.value + fav)
        }
    }

    fun updateFavorite(fav: FavoriteStop) {
        viewModelScope.launch {
            repo.save(_favorites.value.map { if (it.id == fav.id) fav else it })
        }
    }

    fun deleteFavorite(fav: FavoriteStop) {
        viewModelScope.launch {
            repo.save(_favorites.value.filter { it.id != fav.id })
        }
    }

    // Add-stop search helper used by AddFavouriteSheet
    suspend fun searchStops(query: String, lat: Double?, lon: Double?): List<Stop> =
        runCatching { api.search(query, lat, lon).stops }.getOrDefault(emptyList())

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            FavoritesViewModel(FavoritesRepository(context)) as T
    }
}
