package space.snapp.waygo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Tram
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import space.snapp.waygo.data.api.models.Departure

@Composable
fun DepartureRow(departure: Departure, onClick: (() -> Unit)? = null) {
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
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Route badge — minWidth 52dp, 8dp corners matching iOS RouteBadge
        Surface(color = bgColor, shape = RoundedCornerShape(8.dp)) {
            Text(
                departure.routeNumber,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = txtColor,
                modifier = Modifier
                    .widthIn(min = 52.dp)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Direction dot — solid circle (iOS uses Circle().fill)
                val dotColor = if (departure.directionId == 0) MaterialTheme.colorScheme.primary else Color(0xFFE65100)
                Box(modifier = Modifier.size(8.dp).background(dotColor, CircleShape))
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
                        "train" -> Icons.Default.Train
                        "ferry" -> Icons.Default.DirectionsBoat
                        "tram"  -> Icons.Default.Tram
                        else    -> Icons.Default.DirectionsBus
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
                Text("Delayed", style = MaterialTheme.typography.labelSmall, color = Color(0xFFE65100))
            }
        }
    }
}
