package space.snapp.waygo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import space.snapp.waygo.data.api.models.Departure

@Composable
fun DepartureRow(departure: Departure) {
    // Live countdown — ticks every second
    var liveSeconds by remember { mutableIntStateOf(departure.liveSeconds) }
    LaunchedEffect(departure.id) {
        while (true) {
            delay(1000)
            liveSeconds = departure.liveSeconds
        }
    }

    val countdownText = when {
        liveSeconds < 60 -> "Now"
        liveSeconds < 3600 -> "${liveSeconds / 60} min"
        else -> "${liveSeconds / 3600}h ${(liveSeconds % 3600) / 60}m"
    }
    val countdownColor = when {
        liveSeconds < 120 -> MaterialTheme.colorScheme.error
        liveSeconds < 300 -> Color(0xFFE65100)
        else -> MaterialTheme.colorScheme.onSurface
    }

    val bgColor = departure.routeColorInt?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    val txtColor = departure.routeTextColorInt?.let { Color(it) } ?: MaterialTheme.colorScheme.onPrimary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Route badge
        Surface(color = bgColor, shape = RoundedCornerShape(6.dp)) {
            Text(
                departure.routeNumber,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = txtColor,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Direction dot
                val dotColor = if (departure.directionId == 0) MaterialTheme.colorScheme.primary else Color(0xFFE65100)
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .then(Modifier.padding(end = 4.dp))
                ) {
                    Surface(color = dotColor, shape = RoundedCornerShape(4.dp), modifier = Modifier.fillMaxSize()) {}
                }
                Spacer(Modifier.width(5.dp))
                Text(
                    departure.headsign,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (departure.vehicleType.lowercase()) {
                        "train" -> androidx.compose.material.icons.Icons.Default.Train
                        "ferry" -> androidx.compose.material.icons.Icons.Default.DirectionsBoat
                        "tram"  -> androidx.compose.material.icons.Icons.Default.Tram
                        else    -> androidx.compose.material.icons.Icons.Default.DirectionsBus
                    },
                    contentDescription = departure.vehicleType,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    departure.stopName + if (departure.platformCode != null) " · Plat ${departure.platformCode}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(countdownText, style = MaterialTheme.typography.titleMedium, color = countdownColor)
            if (departure.isDelayed) {
                Text("Delayed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
