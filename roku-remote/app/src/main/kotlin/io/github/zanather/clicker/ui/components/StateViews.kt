package io.github.zanather.clicker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** A spinner with a caption, centered in the space left by [contentPadding]. */
@Composable
fun LoadingState(label: String, contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            CircularProgressIndicator(strokeWidth = 5.dp, modifier = Modifier.size(52.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * A whole-screen explanation: [MessagePanel] vertically centered, but scrollable so long setup instructions at
 * large font sizes are never clipped. [carded] puts it on a tonal card, for problems the user has to act on;
 * empty states sit directly on the background.
 */
@Composable
fun MessageState(
    icon: ImageVector,
    title: String,
    body: String,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    carded: Boolean = false,
    action: (@Composable () -> Unit)? = null,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                // Inside the scroll so the column fills the viewport and Arrangement.Center has room to work.
                .heightIn(min = maxHeight)
                .padding(contentPadding)
                .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            if (carded) {
                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .widthIn(max = 520.dp)
                        .fillMaxWidth(),
                ) {
                    MessagePanel(icon, title, body, action = action)
                }
            } else {
                MessagePanel(icon, title, body, Modifier.widthIn(max = 520.dp), action)
            }
        }
    }
}

/** Icon in a tonal circle, title, body and an optional action, all centered. Usable inside lists. */
@Composable
fun MessagePanel(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .padding(horizontal = 24.dp, vertical = 28.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(80.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(40.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(24.dp))
            action()
        }
    }
}

/** A tonal note inside a list, for messages that may run to several lines. */
@Composable
fun InlineMessage(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Text(text = text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
    }
}
