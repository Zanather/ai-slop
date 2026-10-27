package io.github.zanather.clicker.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.zanather.clicker.R
import io.github.zanather.clicker.ui.components.groupedItem

/** A setting whose whole row flips the switch, announced to TalkBack as one switch. */
@Composable
internal fun SwitchRow(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onToggle: () -> Unit,
    index: Int,
    count: Int,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .groupedItem(index, count, MaterialTheme.colorScheme.surfaceContainer)
            .toggleable(value = checked, role = Role.Switch, onValueChange = { onToggle() })
            .heightIn(min = 76.dp)
            .padding(start = 16.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
    ) {
        SettingIcon(icon)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked,
            // The row handles the toggle; a second clickable here would be a duplicate TalkBack stop.
            onCheckedChange = null,
            thumbContent = if (checked) {
                { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
            } else {
                null
            },
        )
    }
}

@Composable
private fun SettingIcon(icon: ImageVector) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
    }
}

/** "Remote not working?" with the two fixes that live on the TV, folded away until asked for. */
@Composable
internal fun HelpCard(expanded: Boolean, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    val stateText = stringResource(if (expanded) R.string.screens_expanded else R.string.screens_collapsed)
    Column(Modifier.groupedItem(0, 1, MaterialTheme.colorScheme.surfaceContainer)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(
                    onClickLabel = stringResource(if (expanded) R.string.settings_hide_help else R.string.settings_show_help),
                    role = Role.Button,
                    onClick = onToggle,
                )
                .semantics { stateDescription = stateText }
                .heightIn(min = 76.dp)
                .padding(start = 16.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
        ) {
            SettingIcon(Icons.AutoMirrored.Rounded.HelpOutline)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.settings_help_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.settings_help_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(16.dp))
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(rotation),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(start = 16.dp, end = 20.dp, bottom = 20.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 56.dp, bottom = 16.dp))
                val target = stringResource(R.string.settings_help_step_access_target)
                val path = stringResource(R.string.settings_help_step_access_path, target)
                HelpStep(
                    number = 1,
                    title = stringResource(R.string.settings_help_step_access_title),
                    body = buildAnnotatedString {
                        append(path)
                        val start = path.lastIndexOf(target)
                        if (start >= 0) addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, start + target.length)
                    },
                )
                Spacer(Modifier.size(16.dp))
                HelpStep(
                    number = 2,
                    title = stringResource(R.string.settings_help_step_power_title),
                    body = buildAnnotatedString { append(stringResource(R.string.settings_help_step_power_body)) },
                )
            }
        }
    }
}

@Composable
private fun HelpStep(number: Int, title: String, body: AnnotatedString) {
    Row(Modifier.semantics(mergeDescendants = true) {}) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.width(40.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(28.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            ) {
                Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun AboutRow(version: String?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .groupedItem(0, 1, MaterialTheme.colorScheme.surfaceContainer)
            .semantics(mergeDescendants = true) {}
            .heightIn(min = 76.dp)
            .padding(start = 16.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
    ) {
        SettingIcon(Icons.Rounded.Info)
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
            Text(
                text = version?.let { stringResource(R.string.settings_version, it) } ?: stringResource(R.string.settings_about_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
