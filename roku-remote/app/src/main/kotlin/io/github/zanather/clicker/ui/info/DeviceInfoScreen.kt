package io.github.zanather.clicker.ui.info

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.TvOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import io.github.zanather.clicker.R
import io.github.zanather.clicker.ui.components.ClickerScaffold
import io.github.zanather.clicker.ui.components.InlineMessage
import io.github.zanather.clicker.ui.components.LoadingState
import io.github.zanather.clicker.ui.components.MessageState
import io.github.zanather.clicker.ui.components.ReadableColumnPadding
import io.github.zanather.clicker.ui.model.DeviceInfoUiState
import io.github.zanather.clicker.ui.theme.ClickerTheme

/** What the TV says about itself: the useful facts up top, every raw device-info field behind a toggle. */
@Composable
fun DeviceInfoScreen(
    state: DeviceInfoUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ClickerScaffold(
        title = state.name.ifBlank { stringResource(R.string.info_title_fallback) },
        onBack = onBack,
        modifier = modifier,
    ) { padding ->
        when {
            state.rows.isNotEmpty() -> InfoList(state, padding)
            state.loading -> LoadingState(stringResource(R.string.info_loading), padding)
            else -> MessageState(
                icon = Icons.Rounded.TvOff,
                title = stringResource(R.string.info_error_title),
                body = state.error ?: stringResource(R.string.info_no_details),
                contentPadding = padding,
                carded = true,
            )
        }
    }
}

@Composable
private fun InfoList(state: DeviceInfoUiState, padding: PaddingValues) {
    val (friendly, raw) = remember(state.rows) { splitRows(state.rows) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    ReadableColumnPadding(padding) { contentPadding ->
        LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
            if (state.loading) {
                item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 8.dp)) }
            }
            state.error?.let { error ->
                item(key = "error") { InlineMessage(Icons.Rounded.Info, error, Modifier.padding(bottom = 8.dp)) }
            }
            item(key = "top") { Spacer(Modifier.height(8.dp)) }
            itemsIndexed(friendly, key = { index, _ -> "friendly:$index" }) { index, (label, value) ->
                InfoRow(label, value, index, friendly.size)
            }
            if (raw.isNotEmpty()) {
                item(key = "gap") { Spacer(Modifier.height(16.dp)) }
                item(key = "all-fields") {
                    AllFieldsToggle(count = raw.size, expanded = showAll, onToggle = { showAll = !showAll })
                }
                if (showAll) {
                    itemsIndexed(raw, key = { index, _ -> "raw:$index" }) { index, (label, value) ->
                        // The toggle row is the first segment of this group.
                        InfoRow(label, value, index + 1, raw.size + 1)
                    }
                }
            }
        }
    }
}

/**
 * The ViewModel lists friendly rows ("Software version") first, then every other device-info field under its
 * raw key ("time-zone-name"). Raw keys are lowercase and never contain spaces; friendly labels are capitalized.
 */
internal fun splitRows(rows: List<Pair<String, String>>): Pair<List<Pair<String, String>>, List<Pair<String, String>>> {
    val firstRaw = rows.indexOfFirst { (label, _) -> label.firstOrNull()?.isLowerCase() == true && ' ' !in label }
    return if (firstRaw < 0) rows to emptyList() else rows.take(firstRaw) to rows.drop(firstRaw)
}

/**
 * One card per group, rows split by hairlines: a spec sheet reads better as a block than as separate tiles.
 * Each row is its own lazy item, so the card is assembled from per-row shapes.
 */
private fun cardShape(index: Int, count: Int): Shape {
    val top = if (index == 0) CardCorner else 0.dp
    val bottom = if (index == count - 1) CardCorner else 0.dp
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

private val CardCorner = 24.dp

private fun Modifier.cardRow(index: Int, count: Int, color: Color): Modifier =
    fillMaxWidth()
        .clip(cardShape(index, count))
        .background(color)

@Composable
private fun RowDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 20.dp))
}

@Composable
private fun InfoRow(label: String, value: String, index: Int, count: Int) {
    Column(Modifier.cardRow(index, count, MaterialTheme.colorScheme.surfaceContainer)) {
        if (index > 0) RowDivider()
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .semantics(mergeDescendants = true) {}
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SelectionContainer {
                Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun AllFieldsToggle(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    val stateText = stringResource(if (expanded) R.string.screens_expanded else R.string.screens_collapsed)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .cardRow(0, if (expanded) count + 1 else 1, MaterialTheme.colorScheme.surfaceContainer)
            .clickable(
                onClickLabel = stringResource(if (expanded) R.string.info_hide_all else R.string.info_show_all),
                role = Role.Button,
                onClick = onToggle,
            )
            .semantics { stateDescription = stateText }
            .heightIn(min = 72.dp)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Icon(Icons.Rounded.DataObject, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.info_all_fields), style = MaterialTheme.typography.titleMedium)
            Text(
                text = pluralStringResource(R.plurals.info_all_fields_count, count, count),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.Rounded.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.rotate(rotation),
        )
    }
}

internal val SampleInfo = DeviceInfoUiState(
    name = "Living Room TV",
    rows = listOf(
        "Name" to "Living Room TV",
        "Model" to "Roku TV",
        "Model number" to "C148X",
        "Made by" to "Acme",
        "Software version" to "15.3.4 build 2402",
        "Serial number" to "X01900ABCDEF",
        "Device ID" to "S0A1B2C3D4E5",
        "IP address" to "192.168.8.198",
        "Connection" to "Wi-Fi",
        "Wi-Fi MAC" to "a8:b5:7c:12:34:56",
        "Power" to "On",
        "Control by mobile apps" to "Enabled",
        "Uptime" to "52:17:09",
        "udn" to "29a40003-6c08-1070-80c3-a8b57c123456",
        "screen-size" to "55",
        "panel-id" to "7",
        "ui-resolution" to "1080p",
        "time-zone-name" to "United States/Pacific",
        "language" to "en",
        "country" to "US",
        "supports-wake-on-wlan" to "true",
        "has-wifi-5G-support" to "true",
    ),
)

@PreviewLightDark
@Composable
private fun DeviceInfoScreenPreview() {
    ClickerTheme {
        DeviceInfoScreen(SampleInfo, onBack = {})
    }
}
