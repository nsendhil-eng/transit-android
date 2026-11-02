package space.snapp.waygo.ui.alerts

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import space.snapp.waygo.data.api.models.ServiceAlert

private val Amber = Color(0xFFFFA726)

/**
 * Home-screen alert control. On launch it shows the nearby headline alert as a banner;
 * closing it morphs the banner into a round warning button in the same corner: one surface
 * shrinks (width, height and corner radius spring together) while its contents cross-fade.
 * The button, or the banner itself, opens the full alerts page.
 */
@Composable
fun HomeAlertControl(
    alerts: List<ServiceAlert>,
    collapsed: Boolean,
    onCollapse: () -> Unit,
    onOpenAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (alerts.isEmpty()) return
    val headline = remember(alerts) { headlineAlert(alerts) }
    val nearCount = alerts.count { (it.distanceM ?: Int.MAX_VALUE) <= NEAR_YOU_M }
    val showBanner = headline != null && !collapsed
    // Amber while there's a current disruption nearby; quieter otherwise
    val accent = if (headline != null) kindColor(headline.kind) else MaterialTheme.colorScheme.onSurfaceVariant

    val morph = spring<Dp>(dampingRatio = 0.8f, stiffness = 300f)
    val corner by animateDpAsState(if (showBanner) 20.dp else ButtonSize / 2, morph, label = "corner")
    val shape = RoundedCornerShape(corner)

    // Outer box leaves room for the count badge so nothing gets clipped
    Box(modifier = modifier.padding(top = 6.dp, end = 6.dp), contentAlignment = Alignment.TopEnd) {
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 10.dp,
            modifier = Modifier
                .animateContentSize(spring(dampingRatio = 0.8f, stiffness = 300f))
                .then(if (showBanner) Modifier.fillMaxWidth() else Modifier.size(ButtonSize))
                .border(1.dp, accent.copy(alpha = 0.55f), shape)
                .clickable(onClick = onOpenAll)
        ) {
            AnimatedContent(
                targetState = showBanner,
                transitionSpec = { fadeIn(tween(200, delayMillis = 120)).togetherWith(fadeOut(tween(120))) },
                contentAlignment = Alignment.TopEnd,
                label = "homeAlertContent"
            ) { banner ->
                if (banner && headline != null) {
                    BannerContent(headline, moreCount = alerts.size - 1, onClose = onCollapse)
                } else {
                    Box(Modifier.size(ButtonSize), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Warning, contentDescription = "Service alerts", tint = accent, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
        // Count of alerts near you, on the button only
        androidx.compose.animation.AnimatedVisibility(
            visible = !showBanner && nearCount > 0,
            enter = fadeIn(tween(200, delayMillis = 200)) + scaleIn(initialScale = 0.5f, animationSpec = tween(200, delayMillis = 200)),
            exit = fadeOut(tween(80)),
            modifier = Modifier.offset(x = 6.dp, y = (-6).dp)
        ) {
            Surface(
                shape = CircleShape,
                color = if (headline != null) Amber else MaterialTheme.colorScheme.secondary,
                modifier = Modifier.defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 5.dp)) {
                    Text(
                        "$nearCount",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF1D1D1D)
                    )
                }
            }
        }
    }
}

private val ButtonSize = 48.dp

@Composable
private fun BannerContent(alert: ServiceAlert, moreCount: Int, onClose: () -> Unit) {
    val accent = kindColor(alert.kind)
    Row(modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
        Icon(kindIcon(alert.kind), null, tint = accent, modifier = Modifier.padding(top = 2.dp).size(22.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                alert.header,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                listOfNotNull(timingLabel(alert), alert.distanceM?.let { distanceLabel(it) }).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            alert.stationsSummary?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (moreCount > 0) {
                Text(
                    "Tap to see all $moreCount other alerts",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Close alert", modifier = Modifier.size(18.dp))
        }
    }
}

/** Full-screen list of every network alert, nearest first, grouped into near you / elsewhere. */
@Composable
fun AlertsPage(alerts: List<ServiceAlert>, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val (near, far) = remember(alerts) { alerts.partition { (it.distanceM ?: Int.MAX_VALUE) <= NEAR_YOU_M } }
    var expandedId by remember { mutableStateOf<String?>(near.firstOrNull()?.id) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                Column {
                    Text("Service alerts", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    Text(
                        "${alerts.size} across the network · nearest first",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (near.isNotEmpty()) {
                    item(key = "near") { SectionLabel("Near you") }
                    items(near, key = { it.id }) { alert ->
                        AlertCard(alert, expanded = expandedId == alert.id) { expandedId = if (expandedId == alert.id) null else alert.id }
                    }
                }
                if (far.isNotEmpty()) {
                    item(key = "far") { SectionLabel(if (near.isEmpty()) "All alerts" else "Elsewhere") }
                    items(far, key = { it.id }) { alert ->
                        AlertCard(alert, expanded = expandedId == alert.id) { expandedId = if (expandedId == alert.id) null else alert.id }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = MaterialTheme.typography.labelMedium.letterSpacing * 1.5f),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp)
    )
}

@Composable
private fun AlertCard(alert: ServiceAlert, expanded: Boolean, onToggle: () -> Unit) {
    val accent = kindColor(alert.kind)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth().animateContentSize(spring(dampingRatio = 0.85f, stiffness = 420f))
    ) {
        Column {
            Row(
                modifier = Modifier.clickable(onClick = onToggle).padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(kindIcon(alert.kind), null, tint = accent, modifier = Modifier.padding(top = 2.dp).size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(alert.header, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    Text(timingLabel(alert), style = MaterialTheme.typography.labelMedium, color = accent)
                    // Collapsed only: expanded, the details list every station
                    if (!expanded) alert.stationsSummary?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp))
                    }
                    if (alert.distanceM != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                            Icon(Icons.Default.Place, null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(2.dp))
                            Text(
                                listOfNotNull(distanceLabel(alert.distanceM), alert.nearestStopName).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Hide details" else "Show details",
                    modifier = Modifier.padding(8.dp).size(20.dp)
                )
            }
            if (expanded) {
                if (alert.routeCodes.isNotEmpty()) {
                    val extra = (alert.routeCount ?: alert.routeCodes.size) - alert.routeCodes.size
                    Text(
                        "Routes: " + alert.routeCodes.joinToString(", ") + if (extra > 0) " +$extra more" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 42.dp, end = 12.dp)
                    )
                }
                // Dismissal belongs to the per-stop banners; here it's a reference list
                AlertDetails(alert, onDismiss = null)
            }
        }
    }
}

private fun distanceLabel(m: Int): String = if (m < 1000) "$m m away" else "%.1f km away".format(m / 1000f)
