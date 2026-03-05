package space.snapp.waygo

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import space.snapp.waygo.data.api.models.Departure
import space.snapp.waygo.ui.departures.DeparturesViewModel
import space.snapp.waygo.ui.favorites.FavoritesViewModel
import space.snapp.waygo.ui.nearme.NearMeScreen
import space.snapp.waygo.ui.nearme.NearMeViewModel
import space.snapp.waygo.ui.search.SearchScreen
import space.snapp.waygo.ui.search.SearchViewModel
import space.snapp.waygo.ui.favorites.FavoritesScreen
import space.snapp.waygo.ui.theme.WayGoTheme
import space.snapp.waygo.ui.tripdetail.TripDetailArgs
import space.snapp.waygo.ui.tripdetail.TripDetailScreen
import space.snapp.waygo.ui.tripdetail.TripDetailViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WayGoTheme {
                WayGoApp()
            }
        }
    }
}

enum class Screen(val label: String, val icon: ImageVector) {
    Search("Search", Icons.Default.Search),
    Favourites("Favourites", Icons.Default.Favorite),
    NearMe("Near Me", Icons.Default.NearMe)
}

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WayGoApp() {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(Screen.Search) }

    // Location
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    var userLat by remember { mutableStateOf<Double?>(null) }
    var userLon by remember { mutableStateOf<Double?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasLocationPermission = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            val client = LocationServices.getFusedLocationProviderClient(context)
            client.lastLocation.addOnSuccessListener { loc ->
                loc?.let { userLat = it.latitude; userLon = it.longitude }
            }
        }
    }

    // ViewModels
    val searchVM: SearchViewModel = viewModel()
    val departuresVM: DeparturesViewModel = viewModel()
    val favoritesVM: FavoritesViewModel = viewModel(factory = FavoritesViewModel.Factory(context))
    val nearMeVM: NearMeViewModel = viewModel()

    // Trip detail navigation
    var tripDetailArgs by remember { mutableStateOf<TripDetailArgs?>(null) }

    // Dismiss trip detail on system back
    BackHandler(enabled = tripDetailArgs != null) { tripDetailArgs = null }
    val tripDetailVM = remember { TripDetailViewModel() }

    fun Departure.toTripDetailArgs() = TripDetailArgs(
        tripId = tripId,
        routeId = routeId,
        directionId = directionId,
        routeShortName = routeNumber,
        headsign = headsign
    )

    // Pass location to search VM
    LaunchedEffect(userLat, userLon) {
        searchVM.userLat = userLat
        searchVM.userLon = userLon
    }

    // Request location on launch
    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "WayGo",
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    )
                }
            )
        },
        bottomBar = {
            NavigationBar {
                Screen.entries.forEach { screen ->
                    NavigationBarItem(
                        selected = currentScreen == screen,
                        onClick = { currentScreen = screen; tripDetailArgs = null },
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (currentScreen) {
                Screen.Search -> SearchScreen(
                    viewModel = searchVM,
                    departuresVM = departuresVM,
                    favoritesVM = favoritesVM,
                    userLat = userLat,
                    userLon = userLon,
                    onTripSelected = { tripDetailArgs = it }
                )
                Screen.Favourites -> FavoritesScreen(
                    viewModel = favoritesVM,
                    userLat = userLat,
                    userLon = userLon,
                    onDepartureClick = { dep -> tripDetailArgs = dep.toTripDetailArgs() }
                )
                Screen.NearMe -> NearMeScreen(
                    viewModel = nearMeVM,
                    hasLocationPermission = hasLocationPermission,
                    userLat = userLat,
                    userLon = userLon,
                    onRequestPermission = {
                        permissionLauncher.launch(arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ))
                    },
                    onDepartureClick = { dep -> tripDetailArgs = dep.toTripDetailArgs() }
                )
            }

            // Trip detail overlay — slides up from bottom
            AnimatedVisibility(
                visible = tripDetailArgs != null,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                tripDetailArgs?.let { args ->
                    Surface(modifier = Modifier.fillMaxSize()) {
                        TripDetailScreen(
                            args = args,
                            userLat = userLat,
                            userLon = userLon,
                            viewModel = tripDetailVM,
                            onBack = { tripDetailArgs = null }
                        )
                    }
                }
            }
        }
    }
}
