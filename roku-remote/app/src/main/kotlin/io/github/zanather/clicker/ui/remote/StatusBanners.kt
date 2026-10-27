package io.github.zanather.clicker.ui.remote

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PhonelinkLock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.TvOff
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.zanather.clicker.R
import io.github.zanather.clicker.ecp.RemoteKey
import io.github.zanather.clicker.ui.model.ConnectionStatus
import io.github.zanather.clicker.ui.model.RemoteActions

private val BannerShape = RoundedCornerShape(20.dp)
private val CardShape = RoundedCornerShape(28.dp)

// A little tighter than Material's defaults so a card's three actions share one line on a typical phone.
private val IconButtonPadding = PaddingValues(start = 16.dp, top = 10.dp, end = 20.dp, bottom = 10.dp)
private val TextButtonPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)

/**
 * What's wrong and how to fix it, for every status that needs the user. Never disables the remote: a
 * stale status must not block a press that would have worked.
 */
@Composable
internal fun StatusBanner(
    status: ConnectionStatus,
    deviceName: String,
    actions: RemoteActions,
    onOpenDevices: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (status) {
        ConnectionStatus.Searching -> ProgressBanner(
            title = stringResource(R.string.remote_searching_title, deviceName),
            body = stringResource(R.string.remote_searching_body),
            modifier = modifier,
        )
        ConnectionStatus.Waking -> ProgressBanner(
            title = stringResource(R.string.remote_waking_title, deviceName),
            body = stringResource(R.string.remote_waking_body),
            modifier = modifier,
        )
        ConnectionStatus.TvOff -> TvOffBanner(deviceName, onTurnOn = { actions.press(RemoteKey.Power) }, modifier)
        is ConnectionStatus.Unreachable -> UnreachableCard(deviceName, status.canWake, actions, onOpenDevices, modifier)
        is ConnectionStatus.Blocked ->
            if (status.limitedMode) LimitedModeCard(onCheckAgain = actions::retry, modifier) else RefusedCard(onCheckAgain = actions::retry, modifier)
        ConnectionStatus.NoDevice, ConnectionStatus.Connecting, ConnectionStatus.Connected -> Unit
    }
}

@Composable
private fun ProgressBanner(title: String, body: String, modifier: Modifier) {
    Surface(
        shape = BannerShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.5.dp,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TvOffBanner(deviceName: String, onTurnOn: () -> Unit, modifier: Modifier) {
    Surface(
        shape = BannerShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(Modifier.padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.TvOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(14.dp))
            Text(
                text = stringResource(R.string.remote_tv_off_title, deviceName),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = onTurnOn) { Text(stringResource(R.string.remote_turn_on)) }
        }
    }
}

@Composable
private fun UnreachableCard(
    deviceName: String,
    canWake: Boolean,
    actions: RemoteActions,
    onOpenDevices: () -> Unit,
    modifier: Modifier,
) {
    StatusCard(
        icon = Icons.Rounded.WifiOff,
        title = stringResource(R.string.remote_unreachable_title, deviceName),
        modifier = modifier,
    ) {
        Tip(stringResource(R.string.remote_unreachable_tip_wifi))
        Tip(stringResource(R.string.remote_unreachable_tip_fast_start))
        CardButtons {
            if (canWake) {
                Button(onClick = actions::wake, contentPadding = IconButtonPadding) {
                    ButtonIcon(Icons.Rounded.Bolt)
                    Text(stringResource(R.string.remote_wake_tv))
                }
                FilledTonalButton(onClick = actions::retry, contentPadding = TextButtonPadding) { Text(stringResource(R.string.remote_try_again)) }
            } else {
                Button(onClick = actions::retry, contentPadding = IconButtonPadding) {
                    ButtonIcon(Icons.Rounded.Refresh)
                    Text(stringResource(R.string.remote_try_again))
                }
            }
            TextButton(onClick = onOpenDevices) { Text(stringResource(R.string.remote_choose_tv)) }
        }
    }
}

/** The fix for Roku OS 14.1+'s default "Limited" mobile-app control, the most common setup problem. */
@Composable
private fun LimitedModeCard(onCheckAgain: () -> Unit, modifier: Modifier) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    StatusCard(
        icon = Icons.Rounded.PhonelinkLock,
        title = stringResource(R.string.remote_blocked_limited_title),
        modifier = modifier.animateContentSize(),
        trailing = {
            IconButton(onClick = { expanded = !expanded }) {
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = stringResource(if (expanded) R.string.remote_hide_steps else R.string.remote_show_steps),
                )
            }
        },
    ) {
        if (expanded) {
            Text(stringResource(R.string.remote_blocked_limited_body), style = MaterialTheme.typography.bodyMedium)
            SettingsPath(stringArrayResource(R.array.remote_blocked_steps).toList())
            Tip(stringResource(R.string.remote_blocked_enabled_note))
            Tip(stringResource(R.string.remote_blocked_official_app_note))
        }
        CardButtons { CheckAgainButton(onCheckAgain) }
    }
}

@Composable
private fun RefusedCard(onCheckAgain: () -> Unit, modifier: Modifier) {
    StatusCard(
        icon = Icons.Rounded.PhonelinkLock,
        title = stringResource(R.string.remote_blocked_refused_title),
        modifier = modifier,
    ) {
        Text(stringResource(R.string.remote_blocked_refused_body), style = MaterialTheme.typography.bodyMedium)
        CardButtons { CheckAgainButton(onCheckAgain) }
    }
}

@Composable
private fun CheckAgainButton(onClick: () -> Unit) {
    Button(onClick = onClick, contentPadding = IconButtonPadding) {
        ButtonIcon(Icons.Rounded.Refresh)
        Text(stringResource(R.string.remote_check_again))
    }
}

/** The TV menu path as numbered steps, read out as one sentence by TalkBack. */
@Composable
private fun SettingsPath(steps: List<String>) {
    val scheme = MaterialTheme.colorScheme
    val description = stringResource(R.string.remote_blocked_steps_description)
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        steps.forEachIndexed { index, step ->
            val last = index == steps.lastIndex
            Row(
                modifier = Modifier
                    .heightIn(min = 32.dp)
                    .clip(CircleShape)
                    .background(if (last) scheme.primary else scheme.surface)
                    .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (last) scheme.onPrimary else scheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (last) scheme.primary else scheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = step,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (last) FontWeight.Bold else FontWeight.Medium,
                    color = if (last) scheme.onPrimary else scheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun StatusCard(
    icon: ImageVector,
    title: String,
    modifier: Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Assertive },
    ) {
        Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                        .semantics { heading() },
                )
                trailing?.invoke()
            }
            Column(Modifier.padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

@Composable
private fun Tip(text: String) {
    Row {
        Icon(
            imageVector = Icons.Rounded.TipsAndUpdates,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CardButtons(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) { content() }
}

@Composable
private fun ButtonIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
}

/** Hero for a first launch: nothing to control yet, so the whole body explains how to get started. */
@Composable
internal fun FindTvHero(onOpenDevices: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(184.dp)
                    .clip(CircleShape)
                    .background(scheme.primaryContainer.copy(alpha = 0.35f)),
            )
            Box(
                Modifier
                    .size(136.dp)
                    .clip(CircleShape)
                    .background(scheme.primaryContainer.copy(alpha = 0.7f)),
            )
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(CircleShape)
                    .background(scheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Tv, contentDescription = null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(48.dp))
            }
        }
        Spacer(Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.remote_find_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.remote_find_body),
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onOpenDevices,
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 16.dp),
        ) {
            Text(stringResource(R.string.remote_find_action), style = MaterialTheme.typography.titleMedium)
        }
    }
}
