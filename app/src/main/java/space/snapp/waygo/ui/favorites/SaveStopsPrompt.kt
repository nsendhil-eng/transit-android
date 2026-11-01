package space.snapp.waygo.ui.favorites

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Gold used for saving/favourites on the home screen */
val SaveGold = Color(0xFFF5C04A)
private val CardBg = Color(0xFF1F242B)
private val ChipBg = Color(0xFF3A2F12)
private val ChipBorder = Color(0xFF5A4A1C)

/**
 * Tells people they can save stops and group them into a place (Home = the stops near home).
 * Expanded: a card with Home / Work / Other. Collapsed: a small "Save stops" pill, so the ability
 * stays visible once the card is closed or something is already saved.
 * [onAdd] gets the suggested name ("Home", "Work", or null for Other).
 */
@Composable
fun SaveStopsPrompt(
    expanded: Boolean,
    onAdd: (String?) -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = expanded,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "saveStopsPrompt",
        modifier = modifier
    ) { isExpanded ->
        if (isExpanded) PromptCard(onAdd, onCollapse) else PromptPill { onAdd(null) }
    }
}

@Composable
private fun PromptCard(onAdd: (String?) -> Unit, onCollapse: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = CardBg,
        border = BorderStroke(1.dp, ChipBorder),
        shadowElevation = 10.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.StarBorder, null, tint = SaveGold, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Save the stops you use",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onCollapse) {
                    Icon(Icons.Default.Close, contentDescription = "Not now", tint = Color(0xFF8A96A3))
                }
            }
            Text(
                "Group the stops near home or work into one place, and see all their departures together.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFC3CAD2),
                modifier = Modifier.padding(end = 8.dp)
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PromptChip("Home", Icons.Default.Home) { onAdd("Home") }
                PromptChip("Work", Icons.Default.Work) { onAdd("Work") }
                PromptChip("Other", Icons.Default.Add) { onAdd(null) }
            }
        }
    }
}

@Composable
private fun PromptChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = ChipBg,
        border = BorderStroke(1.dp, ChipBorder)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Icon(icon, null, tint = SaveGold, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = SaveGold, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun PromptPill(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = CardBg,
        border = BorderStroke(1.dp, ChipBorder),
        shadowElevation = 8.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Icon(Icons.Default.StarBorder, null, tint = SaveGold, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Save stops", color = SaveGold, style = MaterialTheme.typography.labelLarge)
        }
    }
}
