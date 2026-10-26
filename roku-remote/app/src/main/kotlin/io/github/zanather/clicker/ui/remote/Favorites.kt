package io.github.zanather.clicker.ui.remote

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.zanather.clicker.R
import io.github.zanather.clicker.ui.model.AppTile

// Roku channel art is 4:3.
private val TileWidth = 84.dp
private val TileHeight = 63.dp
private val TileShape = RoundedCornerShape(16.dp)

/** One-tap channel launch. Ends with a shortcut to the full channel list. */
@Composable
internal fun FavoritesRow(
    favorites: List<AppTile>,
    activeAppId: String?,
    onLaunch: (appId: String) -> Unit,
    onOpenApps: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    LazyRow(
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(favorites, key = { it.id }) { tile ->
            FavoriteTile(tile, isActive = tile.id == activeAppId, onClick = { onLaunch(tile.id) })
        }
        item(key = "all") { AllChannelsTile(onOpenApps) }
    }
}

@Composable
private fun FavoriteTile(tile: AppTile, isActive: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val playing = stringResource(R.string.remote_favorite_playing)
    TileColumn(
        onClick = onClick,
        onClickLabel = stringResource(R.string.remote_favorite_open),
        modifier = if (isActive) Modifier.semantics { stateDescription = playing } else Modifier,
        label = tile.name,
        labelColor = if (isActive) scheme.primary else scheme.onSurfaceVariant,
        labelWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
    ) {
        Box(
            modifier = Modifier
                .size(TileWidth, TileHeight)
                .then(if (isActive) Modifier.border(BorderStroke(2.5.dp, scheme.primary), TileShape) else Modifier)
                .padding(if (isActive) 4.dp else 0.dp)
                .clip(if (isActive) RoundedCornerShape(12.dp) else TileShape),
        ) {
            ChannelArt(tile)
        }
    }
}

/** The TV's own channel art, over a monogram that shows while it loads or when the TV won't share it. */
@Composable
private fun ChannelArt(tile: AppTile) {
    var loaded by remember(tile.iconUrl) { mutableStateOf(false) }
    if (!loaded) Monogram(tile.name)
    if (tile.iconUrl != null) {
        AsyncImage(
            model = tile.iconUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onSuccess = { loaded = true },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun Monogram(name: String) {
    val scheme = MaterialTheme.colorScheme
    // A stable tint per channel, from the theme's tonal containers, so neighbours are easy to tell apart.
    val (container, content) = when (Math.floorMod(name.hashCode(), 3)) {
        0 -> scheme.primaryContainer to scheme.onPrimaryContainer
        1 -> scheme.secondaryContainer to scheme.onSecondaryContainer
        else -> scheme.tertiaryContainer to scheme.onTertiaryContainer
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(container),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name.trim().take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = content,
            )
        }
    }
}

@Composable
private fun AllChannelsTile(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    TileColumn(
        onClick = onClick,
        onClickLabel = null,
        label = stringResource(R.string.remote_favorites_all),
        labelColor = scheme.onSurfaceVariant,
        labelWeight = FontWeight.Normal,
        description = stringResource(R.string.remote_favorites_all_description),
    ) {
        Box(
            modifier = Modifier
                .size(TileWidth, TileHeight)
                .clip(TileShape)
                .border(BorderStroke(1.dp, scheme.outlineVariant), TileShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Apps, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun TileColumn(
    onClick: () -> Unit,
    onClickLabel: String?,
    label: String,
    labelColor: Color,
    labelWeight: FontWeight,
    modifier: Modifier = Modifier,
    /** Replaces the visible label for TalkBack when the label alone is too terse. */
    description: String? = null,
    art: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .width(TileWidth)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        art()
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = labelWeight,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = if (description != null) Modifier.clearAndSetSemantics {} else Modifier,
        )
    }
}
