# WayGo — Brisbane Transit Android App

Jetpack Compose Android app for Brisbane public transport. Package: `space.snapp.waygo`. Connects to `https://transit.sn-app.space`.

## Building

Android Studio is required. From terminal:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
cd transit-android
./gradlew assembleDebug
```

Install to connected device:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Architecture

- **Retrofit singleton**: `TransitApiService.instance` — all API calls to `https://transit.sn-app.space`
- **MVVM**: ViewModels in `ui/*/`, Compose screens in same packages
- **Navigation**: No NavGraph for detail screens — `TripDetailScreen` and `ItineraryDetailScreen` are `AnimatedVisibility` overlays inside the root `WayGoApp` Box
- **Map**: osmdroid `MapView` wrapped in `AndroidView` (`NearbyStopsMapView.kt`)
- **Favorites**: DataStore Preferences via `FavoritesRepository`

## Key Screens

| Screen | Entry point |
|--------|-------------|
| Search + departures | `SearchScreen.kt` |
| Trip detail (stop list + shape) | `TripDetailScreen.kt` (AnimatedVisibility overlay) |
| Journey planner | `PlanCard.kt` inside SearchScreen (swipe-to-refresh, sort chips) |
| Itinerary detail | `ItineraryDetailScreen.kt` (AnimatedVisibility overlay, osmdroid map, traffic delay banner) |
| Favourite routes | `FavoritesScreen.kt` |

## Journey Planner

- **Geocoding**: Nominatim OSM with Brisbane viewbox
- **Planning**: `/api/plan` → OTP 2.6.0 on Hetzner (`65.109.234.125:8080`)
- **Sort**: Fastest / Least walking chips
- **Refresh**: Pull-to-refresh or tap the ↻ button
- **Traffic delays**: When itinerary detail opens, calls `/api/plan-delays` for bus/tram legs. Shows a warning banner if any trip is running > 1 min late (GTFS-RT)

## API Server

Live at `https://transit.sn-app.space` (Vercel). Source: [translink-api](https://github.com/nsendhil-eng/translink-api).

## OTP Server (Hetzner)

| Item | Value |
|------|-------|
| IP | `65.109.234.125` |
| SSH | `ssh root@65.109.234.125` |
| OTP version | 2.6.0, Java 21 |
| Service | `systemctl status otp` / `systemctl restart otp` |
| Logs | `journalctl -u otp -f` |
| Graph | `/root/otp/graphs/seq/graph.obj` |
| Auto-rebuild | Weekly cron Sun 3am: `bash /root/otp/rebuild.sh` |

## Notes

- `Route.id` is `"$routeId-${tripHeadsign}-${directionId}"` — needed to distinguish headsign variants of the same route number
- `VehicleType` derives from GTFS `route_type`: 0=tram, 1/2=rail, 3=bus, 4=ferry
- Compose BOM: `2024.10.01` (Material3 1.3.0 stable) — required for `PullToRefreshBox` from `androidx.compose.material3.pulltorefresh`
