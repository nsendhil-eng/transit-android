package space.snapp.waygo

import space.snapp.waygo.data.AppVisibility
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import space.snapp.waygo.data.api.models.Departure
import space.snapp.waygo.ui.departures.DeparturesViewModel
import space.snapp.waygo.ui.favorites.FavoritesViewModel
import space.snapp.waygo.ui.map.MovementTracker
import space.snapp.waygo.ui.map.UserFix
import space.snapp.waygo.ui.nearme.NearMeScreen
import space.snapp.waygo.ui.nearme.NearMeViewModel
import space.snapp.waygo.ui.search.SearchScreen
import space.snapp.waygo.ui.search.SearchViewModel
import space.snapp.waygo.ui.favorites.FavoritesScreen
import space.snapp.waygo.ui.theme.WayGoTheme
import space.snapp.waygo.ui.plan.PlanViewModel
import space.snapp.waygo.ui.alerts.NetworkAlertsViewModel
import space.snapp.waygo.ui.tripdetail.TripDetailArgs
import space.snapp.waygo.ui.tripdetail.TripDetailScreen
import space.snapp.waygo.ui.tripdetail.TripDetailViewModel

/** GPS update interval while you're moving */
private const val MOVING_FIX_MS = 5_000L
/** …and while you're standing still */
private const val STILL_FIX_MS = 30_000L

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Debug and fast builds only: pretend to be somewhere else, for testing a place without going there
        //   adb shell am start -n space.snapp.waygo/.MainActivity --ef fake_lat -27.4658 --ef fake_lon 153.0195
        val fakeLocation = intent.takeIf { BuildConfig.FAKE_LOCATION && it.hasExtra("fake_lat") && it.hasExtra("fake_lon") }
            ?.let { it.getFloatExtra("fake_lat", 0f).toDouble() to it.getFloatExtra("fake_lon", 0f).toDouble() }
        setContent {
            WayGoTheme {
                WayGoApp(fakeLocation)
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
fun WayGoApp(
    /** Debug: a fixed location used instead of GPS */
    fakeLocation: Pair<Double, Double>? = null
) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(Screen.Search) }

    // Location
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    var userFix by remember {
        mutableStateOf(fakeLocation?.let { UserFix(it.first, it.second, timeMs = System.currentTimeMillis()) })
    }
    val userLat = userFix?.lat
    val userLon = userFix?.lon
    // GPS works hard while you're moving and eases off when you aren't
    val movement = remember { MovementTracker() }
    var moving by remember { mutableStateOf(true) }
    fun takeFix(loc: Location) {
        // A cached fix can arrive after a fresher one
        if (loc.time < (userFix?.timeMs ?: 0L)) return
        val fix = UserFix(
            lat = loc.latitude,
            lon = loc.longitude,
            accuracyM = loc.accuracy.takeIf { loc.hasAccuracy() },
            speedMps = loc.speed.takeIf { loc.hasSpeed() },
            timeMs = loc.time
        )
        userFix = fix
        moving = movement.update(fix, System.currentTimeMillis())
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasLocationPermission = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    // On opening: the cached fix at once, for speed, and a fresh one straight after, because the
    // cached one may be from before you moved
    val appVisible by AppVisibility.visible.collectAsState()
    DisposableEffect(hasLocationPermission, appVisible) {
        if (!hasLocationPermission || !appVisible || fakeLocation != null) return@DisposableEffect onDispose {}
        movement.reset(System.currentTimeMillis())
        moving = true
        val client = LocationServices.getFusedLocationProviderClient(context)
        client.lastLocation.addOnSuccessListener { loc -> loc?.let(::takeFix) }
        val cancel = CancellationTokenSource()
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancel.token)
            .addOnSuccessListener { loc -> loc?.let(::takeFix) }
        onDispose { cancel.cancel() }
    }
    // Then updates while the app is on screen: often while moving, gently while still
    DisposableEffect(hasLocationPermission, appVisible, moving) {
        if (!hasLocationPermission || !appVisible || fakeLocation != null) return@DisposableEffect onDispose {}
        val client = LocationServices.getFusedLocationProviderClient(context)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, if (moving) MOVING_FIX_MS else STILL_FIX_MS)
            .setMinUpdateIntervalMillis(if (moving) MOVING_FIX_MS / 2 else STILL_FIX_MS / 2)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let(::takeFix)
            }
        }
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        onDispose { client.removeLocationUpdates(callback) }
    }

    // ViewModels
    val searchVM: SearchViewModel = viewModel()
    val departuresVM: DeparturesViewModel = viewModel()
    val favoritesVM: FavoritesViewModel = viewModel(factory = FavoritesViewModel.Factory(context))
    val nearMeVM: NearMeViewModel = viewModel()
    val planVM: PlanViewModel = viewModel()
    // App-session scoped so the home alert banner stays collapsed across tab switches
    val networkAlertsVM: NetworkAlertsViewModel = viewModel()

    // Trip detail navigation
    var tripDetailArgs by remember { mutableStateOf<TripDetailArgs?>(null) }

    // Dismiss trip detail on system back
    BackHandler(enabled = tripDetailArgs != null) { tripDetailArgs = null }
    // Back from Favourites / Near Me returns to the Search tab before leaving the app
    BackHandler(enabled = tripDetailArgs == null && currentScreen != Screen.Search) {
        currentScreen = Screen.Search
    }
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
        // The map tab has no title bar: the map runs up behind the status bar
        topBar = {
            if (currentScreen != Screen.Search) CenterAlignedTopAppBar(
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
        val edgeToEdge = currentScreen == Screen.Search
        Box(
            modifier = Modifier.fillMaxSize().padding(
                top = if (edgeToEdge) 0.dp else padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding()
            )
        ) {
            when (currentScreen) {
                Screen.Search -> SearchScreen(
                    viewModel = searchVM,
                    departuresVM = departuresVM,
                    favoritesVM = favoritesVM,
                    planViewModel = planVM,
                    userLat = userLat,
                    userLon = userLon,
                    userFix = userFix,
                    hasLocationPermission = hasLocationPermission || fakeLocation != null,
                    onRequestLocation = {
                        permissionLauncher.launch(arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ))
                    },
                    networkAlertsVM = networkAlertsVM,
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
                        // Over the edge-to-edge map tab, keep clear of the status bar
                        Box(if (edgeToEdge) Modifier.statusBarsPadding() else Modifier) {
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
}
