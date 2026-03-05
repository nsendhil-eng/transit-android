package space.snapp.waygo.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.snapp.waygo.data.api.models.AddressResult
import space.snapp.waygo.ui.plan.PlanViewModel.ActiveField

@Composable
fun PlanCard(
    viewModel: PlanViewModel,
    userLat: Double?,
    userLon: Double?,
    onClose: () -> Unit
) {
    val fromQuery by viewModel.fromQuery.collectAsState()
    val toQuery by viewModel.toQuery.collectAsState()
    val fromSelected by viewModel.fromSelected.collectAsState()
    val toSelected by viewModel.toSelected.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val activeField by viewModel.activeField.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()

    val fromFocusRequester = remember { FocusRequester() }
    val toFocusRequester = remember { FocusRequester() }

    val textColor = Color(0xFF1A1A1A)
    val hintColor = Color(0xFF888888)
    val accentBlue = Color(0xFF1A73E8)
    val cardBg = Color.White

    LaunchedEffect(Unit) {
        fromFocusRequester.requestFocus()
    }

    // Sync user location into VM
    LaunchedEffect(userLat, userLon) {
        viewModel.userLat = userLat
        viewModel.userLon = userLon
    }

    // Auto-shift focus to To field when From is confirmed
    LaunchedEffect(activeField) {
        when (activeField) {
            ActiveField.TO -> toFocusRequester.requestFocus()
            else -> {}
        }
    }

    Column {
        // ── From / To card ───────────────────────────────────────────────
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = cardBg,
            shadowElevation = 10.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Timeline dots
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(end = 12.dp, top = 6.dp, bottom = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(accentBlue, CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(22.dp)
                            .background(Color(0xFFCCCCCC))
                    )
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .border(2.dp, accentBlue, CircleShape)
                    )
                }

                // Text fields
                Column(modifier = Modifier.weight(1f)) {
                    PlanField(
                        value = fromQuery,
                        onValueChange = viewModel::onFromQueryChange,
                        placeholder = "From: address or stop",
                        isActive = activeField == ActiveField.FROM,
                        focusRequester = fromFocusRequester,
                        onFocused = { viewModel.activateField(ActiveField.FROM) },
                        textColor = textColor,
                        hintColor = hintColor,
                        trailingContent = {
                            if (fromQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = viewModel::clearFrom,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear from",
                                        modifier = Modifier.size(16.dp),
                                        tint = hintColor
                                    )
                                }
                            }
                        }
                    )

                    HorizontalDivider(color = Color(0xFFEEEEEE))

                    PlanField(
                        value = toQuery,
                        onValueChange = viewModel::onToQueryChange,
                        placeholder = "To: address or stop",
                        isActive = activeField == ActiveField.TO,
                        focusRequester = toFocusRequester,
                        onFocused = { viewModel.activateField(ActiveField.TO) },
                        textColor = textColor,
                        hintColor = hintColor,
                        trailingContent = {
                            if (toQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = viewModel::clearTo,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear to",
                                        modifier = Modifier.size(16.dp),
                                        tint = hintColor
                                    )
                                }
                            }
                        }
                    )
                }

                // Action buttons column
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Exit plan mode",
                            tint = Color(0xFF555555),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = viewModel::swap, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Default.SwapVert,
                            contentDescription = "Swap",
                            tint = Color(0xFF555555),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // ── "My current location" quick pick (From field, empty) ─────────
        if (activeField == ActiveField.FROM && fromQuery.isEmpty() && userLat != null) {
            Spacer(Modifier.height(4.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = cardBg,
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.useMyLocationAsFrom() }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.MyLocation,
                        contentDescription = null,
                        tint = accentBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "My current location",
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor
                    )
                }
            }
        }

        // ── Address suggestions ──────────────────────────────────────────
        if (suggestions.isNotEmpty() || isSearching) {
            Spacer(Modifier.height(4.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSearching && suggestions.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(suggestions, key = { it.placeId }) { result ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.selectSuggestion(result) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Place,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        result.shortName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1
                                    )
                                    if (result.subtitle.isNotEmpty()) {
                                        Text(
                                            result.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
        }

        // ── Both endpoints selected — ready for Phase 2 routing ──────────
        if (fromSelected != null && toSelected != null && suggestions.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Directions,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Route planning coming soon",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "From: ${fromSelected!!.shortName}\nTo: ${toSelected!!.shortName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "OTP integration (walk + bus + train + ferry) coming in Phase 2",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isActive: Boolean,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    textColor: Color,
    hintColor: Color,
    trailingContent: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = textColor, fontSize = 15.sp),
            cursorBrush = SolidColor(Color(0xFF1A73E8)),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onFocusChanged { if (it.isFocused) onFocused() },
            decorationBox = { innerTextField ->
                if (value.isEmpty()) {
                    Text(placeholder, color = hintColor, fontSize = 15.sp)
                }
                innerTextField()
            }
        )
        trailingContent()
    }
}
