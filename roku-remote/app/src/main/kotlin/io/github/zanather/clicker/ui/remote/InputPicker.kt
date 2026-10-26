package io.github.zanather.clicker.ui.remote

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Input
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.SettingsInputComposite
import androidx.compose.material.icons.rounded.SettingsInputHdmi
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.zanather.clicker.R
import io.github.zanather.clicker.ecp.RemoteKey
import io.github.zanather.clicker.ui.model.AppTile
import io.github.zanather.clicker.ui.model.RemoteActions

/** Shown when the TV hasn't listed its inputs (yet): the keys every Roku TV accepts. */
private val FallbackInputs = listOf(
    RemoteKey.InputTuner,
    RemoteKey.InputHdmi1,
    RemoteKey.InputHdmi2,
    RemoteKey.InputHdmi3,
    RemoteKey.InputHdmi4,
    RemoteKey.InputAv1,
)

/** Roku input ids look like `tvinput.hdmi2`, `tvinput.dtv` (antenna) and `tvinput.cvbs` (AV). */
private fun iconForInput(id: String): ImageVector = when {
    "hdmi" in id -> Icons.Rounded.SettingsInputHdmi
    "dtv" in id || "tuner" in id -> Icons.Rounded.LiveTv
    "cvbs" in id || "av" in id -> Icons.Rounded.SettingsInputComposite
    else -> Icons.AutoMirrored.Rounded.Input
}

@Composable
private fun fallbackLabel(key: RemoteKey): String = when (key) {
    RemoteKey.InputTuner -> stringResource(R.string.remote_input_tuner)
    RemoteKey.InputAv1 -> stringResource(R.string.remote_input_av)
    // InputHDMI1..InputHDMI4
    else -> stringResource(R.string.remote_input_hdmi, key.ecpName.last().digitToInt())
}

/** An "Input" button with a menu of the TV's inputs, named as the user named them on the TV. */
@Composable
internal fun InputButton(inputs: List<AppTile>, activeAppId: String?, actions: RemoteActions, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        FilledTonalButton(onClick = { expanded = true }, contentPadding = PaddingValues(start = 14.dp, end = 10.dp)) {
            Icon(Icons.AutoMirrored.Rounded.Input, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.remote_input))
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Rounded.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            if (inputs.isNotEmpty()) {
                for (input in inputs) {
                    InputItem(input.name, iconForInput(input.id), isActive = input.id == activeAppId) {
                        expanded = false
                        actions.launch(input.id)
                    }
                }
            } else {
                for (key in FallbackInputs) {
                    InputItem(fallbackLabel(key), iconForInput(key.ecpName.lowercase()), isActive = false) {
                        expanded = false
                        actions.press(key)
                    }
                }
            }
        }
    }
}

@Composable
private fun InputItem(name: String, icon: ImageVector, isActive: Boolean, onClick: () -> Unit) {
    val active = stringResource(R.string.remote_input_active)
    DropdownMenuItem(
        text = { Text(name, style = MaterialTheme.typography.bodyLarge) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        trailingIcon = if (isActive) {
            { Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        } else {
            null
        },
        onClick = onClick,
        modifier = if (isActive) Modifier.semantics { stateDescription = active } else Modifier,
    )
}
