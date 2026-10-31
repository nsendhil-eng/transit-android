package space.snapp.waygo.ui.alerts

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Accessible
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WrongLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import space.snapp.waygo.data.api.TransitApiService
import space.snapp.waygo.data.api.models.ServiceAlert
import space.snapp.waygo.data.local.AlertDismissals
import java.util.Calendar
import java.util.Date

private const val ALERTS_REFRESH_MS = 60_000L

/** Alerts for what a screen shows. [visible] excludes dismissed ones; [all] is for flags. */
class AlertsUi(
    val visible: List<ServiceAlert>,
    val all: List<ServiceAlert>,
    val dismiss: (ServiceAlert) -> Unit
) {
    /** Routes whose service is disrupted right now — flagged on route cards even if dismissed. */
    val disruptedRouteIds: Set<String> =
        all.filter { it.disruptsService }.flatMap { it.matchedRouteIds }.toSet()
}

/**
 * Loads alerts for [routeIds]/[stopIds] and keeps them fresh while on screen.
 * Ids are sorted into the effect key so a re-ordered list doesn't refetch.
 */
@Composable
fun rememberServiceAlerts(routeIds: Collection<String>, stopIds: Collection<String>): AlertsUi {
    val context = LocalContext.current
    val dismissals = remember { AlertDismissals(context.applicationContext) }
    val dismissed by dismissals.dismissedIds.collectAsState(initial = emptySet())
    val scope = rememberCoroutineScope()
    var alerts by remember { mutableStateOf<List<ServiceAlert>>(emptyList()) }

    val routeKey = routeIds.filter { it.isNotBlank() }.distinct().sorted().joinToString(",")
    val stopKey = stopIds.filter { it.isNotBlank() }.distinct().sorted().joinToString(",")
    LaunchedEffect(routeKey, stopKey) {
        if (routeKey.isEmpty() && stopKey.isEmpty()) { alerts = emptyList(); return@LaunchedEffect }
        val api = TransitApiService.instance
        while (true) {
            try {
                alerts = api.alerts(routeKey.ifEmpty { null }, stopKey.ifEmpty { null }).alerts
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Alerts are supplementary: keep what we had
            }
            delay(ALERTS_REFRESH_MS)
        }
    }

    return AlertsUi(
        visible = alerts.filter { it.id !in dismissed },
        all = alerts,
        dismiss = { alert -> scope.launch { dismissals.dismiss(alert.id, alert.untilDate?.time) } }
    )
}

/**
 * The most important alert as a banner. Tapping it expands in place to show every alert's
 * details (description, Translink link, dismiss). In-place rather than a bottom sheet because
 * the banner often already sits inside a sheet, and stacked modal sheets close each other.
 */
@Composable
fun AlertBannerArea(state: AlertsUi, modifier: Modifier = Modifier) {
    val top = state.visible.firstOrNull() ?: return
    var expanded by remember { mutableStateOf(false) }
    val accent = kindColor(top.kind)
    val shape = RoundedCornerShape(14.dp)
    Surface(
        shape = shape,
        color = accent.copy(alpha = 0.14f),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, accent.copy(alpha = 0.45f), shape)
            .animateContentSize()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .clickable { expanded = !expanded }
                    .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(kindIcon(top.kind), contentDescription = null, tint = accent, modifier = Modifier.padding(top = 2.dp).size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        top.header,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    val more = state.visible.size - 1
                    Text(
                        buildString {
                            append(timingLabel(top))
                            if (!expanded && more > 0) append(" · +$more more")
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = accent
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Hide details" else "Show details",
                    modifier = Modifier.padding(8.dp).size(20.dp)
                )
            }
            if (expanded) {
                AlertDetails(top, onDismiss = { state.dismiss(top) })
                state.visible.drop(1).forEach { alert ->
                    HorizontalDivider(color = accent.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 12.dp))
                    AlertHeaderRow(alert)
                    AlertDetails(alert, onDismiss = { state.dismiss(alert) })
                }
            }
        }
    }
}

@Composable
private fun AlertHeaderRow(alert: ServiceAlert) {
    val accent = kindColor(alert.kind)
    Row(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.Top) {
        Icon(kindIcon(alert.kind), contentDescription = null, tint = accent, modifier = Modifier.padding(top = 2.dp).size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(alert.header, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            Text(timingLabel(alert), style = MaterialTheme.typography.labelMedium, color = accent)
        }
    }
}

@Composable
internal fun AlertDetails(alert: ServiceAlert, onDismiss: (() -> Unit)?) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier.padding(start = 42.dp, end = 12.dp, top = 4.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (alert.description.isNotBlank()) {
            Text(alert.description, style = MaterialTheme.typography.bodyMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            alert.url?.let { url ->
                TextButton(onClick = { runCatching { uriHandler.openUri(url) } }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("More on translink.com.au")
                }
            }
            if (onDismiss != null) {
                TextButton(onClick = onDismiss, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Dismiss")
                }
            }
        }
    }
}

// ── Presentation helpers ───────────────────────────────────────────────────

internal fun kindColor(kind: String): Color = when (kind) {
    "disruption" -> Color(0xFFFFA726)
    "change" -> Color(0xFFFFCA28)
    "stopMoved", "accessibility" -> Color(0xFF64B5F6)
    "extra" -> Color(0xFF66BB6A)
    else -> Color(0xFFB0BEC5)
}

internal fun kindIcon(kind: String): ImageVector = when (kind) {
    "disruption" -> Icons.Default.Warning
    "change" -> Icons.Default.Schedule
    "stopMoved" -> Icons.Default.WrongLocation
    "accessibility" -> Icons.AutoMirrored.Filled.Accessible
    "extra" -> Icons.Default.AddCircle
    else -> Icons.Default.Info
}

/** "Until Sun 1 Nov, 10:59 PM", "Starts today 7:45 PM", "Ongoing". */
fun timingLabel(alert: ServiceAlert, now: Date = Date()): String {
    if (alert.isUpcoming) {
        val from = alert.fromDate ?: return "Starting soon"
        return "Starts ${dayAndTime(from, now)}"
    }
    val until = alert.untilDate ?: return "Ongoing"
    val days = (until.time - now.time) / (24 * 3600 * 1000)
    return if (days > 7) "Until ${dayMonth(until)}" else "Until ${dayAndTime(until, now)}"
}

private fun dayAndTime(date: Date, now: Date): String {
    val cal = Calendar.getInstance().apply { time = date }
    val today = Calendar.getInstance().apply { time = now }
    val tomorrow = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
    val sameDay = { a: Calendar, b: Calendar -> a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR) }
    val day = when {
        sameDay(cal, today) -> "today"
        sameDay(cal, tomorrow) -> "tomorrow"
        else -> "%ta %d %tb".format(cal, cal.get(Calendar.DAY_OF_MONTH), cal)
    }
    val h = cal.get(Calendar.HOUR).let { if (it == 0) 12 else it }
    val ampm = if (cal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
    return "$day, %d:%02d %s".format(h, cal.get(Calendar.MINUTE), ampm)
}

private fun dayMonth(date: Date): String {
    val cal = Calendar.getInstance().apply { time = date }
    return "%d %tb".format(cal.get(Calendar.DAY_OF_MONTH), cal)
}
