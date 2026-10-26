package io.github.zanather.clicker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Segmented lists: a group of rows reads as one rounded card, yet each row is its own tappable shape with a
 * hairline gap, so ripples and selection highlights never bleed into a neighbour.
 */
object Grouped {
    /** Space between rows of a group; give lists `Arrangement.spacedBy(Gap)`. */
    val Gap = 2.dp

    /** Side margin between a group and the window edge. */
    val Margin = 16.dp

    private val outer = 24.dp
    private val inner = 6.dp

    fun shape(index: Int, count: Int): Shape {
        val top = if (index == 0) outer else inner
        val bottom = if (index == count - 1) outer else inner
        return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
    }
}

/** Clips and fills a row as item [index] of a [count]-row group. Chain `clickable` after it so ripples fit the shape. */
fun Modifier.groupedItem(index: Int, count: Int, color: Color): Modifier =
    fillMaxWidth().clip(Grouped.shape(index, count)).background(color)

/** A small accent-colored title above a group, with an optional [action] (a text button) at the end. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            // Lines the title up with the rows' leading icons; a button brings its own vertical padding.
            .padding(start = 16.dp, top = if (action == null) 20.dp else 8.dp, bottom = if (action == null) 8.dp else 0.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
                .semantics { heading() },
        )
        action?.invoke()
    }
}
