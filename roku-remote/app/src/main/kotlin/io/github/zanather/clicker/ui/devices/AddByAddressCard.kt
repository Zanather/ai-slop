package io.github.zanather.clicker.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.zanather.clicker.R
import io.github.zanather.clicker.ui.components.groupedItem

/**
 * Manual entry for networks where discovery can't see the TV (client isolation, VPNs). Only digits and dots
 * are kept, so a pasted address with stray spaces still works; the ViewModel validates the rest.
 */
@Composable
internal fun AddByAddressCard(onAddByAddress: (String) -> Unit, modifier: Modifier = Modifier) {
    var address by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val submit = {
        if (address.isNotEmpty()) {
            focusManager.clearFocus()
            onAddByAddress(address)
        }
    }
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .groupedItem(0, 1, MaterialTheme.colorScheme.surfaceContainer)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
    ) {
        OutlinedTextField(
            value = address,
            onValueChange = { typed -> address = typed.filter { it.isDigit() || it == '.' } },
            label = { Text(stringResource(R.string.devices_ip_label)) },
            placeholder = { Text(stringResource(R.string.devices_ip_placeholder)) },
            leadingIcon = { Icon(Icons.Rounded.Lan, contentDescription = null) },
            supportingText = { Text(stringResource(R.string.devices_ip_help)) },
            singleLine = true,
            // Uri rather than Decimal: some number pads only allow a single dot.
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            ),
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = submit,
            enabled = address.isNotEmpty(),
            shape = RoundedCornerShape(16.dp),
            // Lines the button up with the field's outline, below its floating label.
            modifier = Modifier
                .padding(top = 8.dp)
                .heightIn(min = 56.dp),
        ) {
            Text(stringResource(R.string.devices_add))
        }
    }
}
