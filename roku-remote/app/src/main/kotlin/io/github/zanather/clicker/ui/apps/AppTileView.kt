package io.github.zanather.clicker.ui.apps

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.zanather.clicker.R
import io.github.zanather.clicker.ui.model.AppTile
import io.github.zanather.clicker.ui.theme.LocalHapticsEnabled

private val TileShape = RoundedCornerShape(22.dp)
private val IconShape = RoundedCornerShape(16.dp)

/** Room around every icon for the "now playing" ring, so active and idle icons stay the same size. */
private val RingInset = 5.dp

/** Roku channel art is landscape (336 × 210 on current firmware). */
private const val IconAspect = 16f / 10f

/**
 * One channel: icon, name, and badges for pinned and now playing. Long-press is mirrored as a TalkBack custom
 * action, since screen-reader users can't discover a hidden gesture.
 */
@Composable
internal fun AppTileView(
    app: AppTile,
    isActive: Boolean,
    onLaunch: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pinLabel = stringResource(if (app.isFavorite) R.string.apps_unpin else R.string.apps_pin)
    val states = listOfNotNull(
        stringResource(R.string.apps_now_playing).takeIf { isActive },
        stringResource(R.string.apps_pinned).takeIf { app.isFavorite },
    ).joinToString()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(TileShape)
            .combinedClickable(
                onClickLabel = stringResource(R.string.apps_open),
                role = Role.Button,
                onLongClickLabel = pinLabel,
                onLongClick = { onToggleFavorite(app.id) },
                hapticFeedbackEnabled = LocalHapticsEnabled.current,
                onClick = { onLaunch(app.id) },
            )
            .semantics {
                if (states.isNotEmpty()) stateDescription = states
                customActions = listOf(
                    CustomAccessibilityAction(pinLabel) {
                        onToggleFavorite(app.id)
                        true
                    },
                )
            }
            .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 10.dp),
    ) {
        Box {
            ChannelIcon(app, isActive)
            if (app.isFavorite) {
                PinnedBadge(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-2).dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = app.name,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        if (isActive) {
            Spacer(Modifier.height(6.dp))
            // The tile's state description already says it; don't read it twice.
            NowPlayingPill(Modifier.clearAndSetSemantics {})
        }
    }
}

@Composable
private fun ChannelIcon(app: AppTile, isActive: Boolean) {
    val ring = if (isActive) {
        Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp + RingInset))
    } else {
        Modifier
    }
    Box(
        Modifier
            .fillMaxWidth()
            .then(ring)
            .padding(RingInset)
            .aspectRatio(IconAspect)
            .clip(IconShape),
    ) {
        // The monogram stays until the real icon arrives, and for good when the TV refuses icons (Limited mode).
        var loaded by remember(app.iconUrl) { mutableStateOf(false) }
        if (!loaded) Monogram(app)
        if (app.iconUrl != null) {
            AsyncImage(
                model = app.iconUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onSuccess = { loaded = true },
                onError = { loaded = false },
                modifier = Modifier
                    .matchParentSize()
                    .background(if (loaded) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent),
            )
        }
    }
}

/** A letter on one of the theme's tonal containers, picked by id so each channel keeps its color. */
@Composable
private fun Monogram(app: AppTile) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (app.id.hashCode().mod(4)) {
        0 -> scheme.primaryContainer to scheme.onPrimaryContainer
        1 -> scheme.secondaryContainer to scheme.onSecondaryContainer
        2 -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        else -> scheme.surfaceContainerHighest to scheme.onSurface
    }
    val letter = app.name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "•"
    // Sized in dp, not sp: it's decoration inside a fixed box and must not overflow at large font scales.
    val letterSize = with(LocalDensity.current) { 30.dp.toSp() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(IconAspect)
            .background(container),
    ) {
        Text(
            text = letter,
            color = content,
            fontSize = letterSize,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}

@Composable
private fun PinnedBadge(modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface),
        modifier = modifier.size(26.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Star, contentDescription = null, modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
private fun NowPlayingPill(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        // Text only, so it can wrap cleanly in narrow columns and at large font sizes.
        Text(
            text = stringResource(R.string.apps_now_playing),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}
