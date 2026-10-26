package io.github.zanather.clicker.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import io.github.zanather.clicker.R

/**
 * The frame every secondary screen shares: a back arrow, a large collapsing title on tall windows and a
 * single-row bar on short ones (landscape phones), where the large title would eat a third of the height.
 *
 * [content] gets padding that already covers the top bar and the system bars, so lists can pass it straight
 * to `contentPadding` and draw edge to edge behind the navigation bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClickerScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val shortWindow = LocalWindowInfo.current.containerDpSize.height < 560.dp
    val scrollBehavior = if (shortWindow) {
        TopAppBarDefaults.pinnedScrollBehavior()
    } else {
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    }
    val titleContent: @Composable () -> Unit = {
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    val backButton: @Composable () -> Unit = {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.screens_back))
        }
    }
    val colors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.surface,
        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    )
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            if (shortWindow) {
                TopAppBar(
                    title = titleContent,
                    navigationIcon = backButton,
                    actions = actions,
                    colors = colors,
                    scrollBehavior = scrollBehavior,
                )
            } else {
                LargeTopAppBar(
                    title = titleContent,
                    navigationIcon = backButton,
                    actions = actions,
                    colors = colors,
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        content = content,
    )
}

/** Widest a column of text and rows gets before it's centered; past this, lines get too long to scan. */
val ReadableWidth = 640.dp

/**
 * Turns the scaffold's [padding] into list content padding that keeps rows at most [ReadableWidth] wide and
 * centered (landscape, tablets) while the whole window stays scrollable, plus [bottom] room after the last row.
 */
@Composable
fun ReadableColumnPadding(
    padding: PaddingValues,
    bottom: Dp = 32.dp,
    content: @Composable (PaddingValues) -> Unit,
) {
    BoxWithConstraints {
        val direction = LocalLayoutDirection.current
        val start = padding.calculateStartPadding(direction)
        val end = padding.calculateEndPadding(direction)
        val side = max(Grouped.Margin, (maxWidth - start - end - ReadableWidth) / 2)
        content(
            PaddingValues(
                start = start + side,
                top = padding.calculateTopPadding(),
                end = end + side,
                bottom = padding.calculateBottomPadding() + bottom,
            ),
        )
    }
}
