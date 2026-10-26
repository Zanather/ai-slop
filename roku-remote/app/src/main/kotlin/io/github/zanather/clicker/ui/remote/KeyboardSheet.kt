package io.github.zanather.clicker.ui.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.zanather.clicker.R
import io.github.zanather.clicker.ecp.RemoteKey
import io.github.zanather.clicker.ecp.keysForEdit
import io.github.zanather.clicker.ui.model.RemoteActions
import kotlinx.coroutines.delay

/** Local mirror of the session's Backspace repeat (400 ms, then about every 90 ms plus a round trip). */
private const val BACKSPACE_REPEAT_DELAY_MS = 400L
private const val BACKSPACE_REPEAT_INTERVAL_MS = 110L

private fun String.dropLastCodePoint(): String =
    if (isEmpty()) this else substring(0, offsetByCodePoints(length, -1))

/**
 * Live typing into the TV's on-screen keyboard. The field mirrors what the TV has: every edit is
 * turned into the Backspaces and characters that make the TV's text match.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KeyboardSheet(actions: RemoteActions, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // What the TV has been sent; the field's text once every key has landed.
    var sent by rememberSaveable { mutableStateOf("") }
    var text by rememberSaveable { mutableStateOf("") }
    var backspaceHeld by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    fun trimOne() {
        sent = sent.dropLastCodePoint()
        text = sent
    }

    LaunchedEffect(backspaceHeld) {
        if (!backspaceHeld) return@LaunchedEffect
        delay(BACKSPACE_REPEAT_DELAY_MS)
        while (sent.isNotEmpty()) {
            trimOne()
            delay(BACKSPACE_REPEAT_INTERVAL_MS)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        // In here, not above: the sheet is its own window, with its own field to focus and its own IME.
        val keyboard = LocalSoftwareKeyboardController.current
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.remote_keyboard_title), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = text,
                onValueChange = { new ->
                    val keys = keysForEdit(sent, new)
                    if (keys.isNotEmpty()) actions.sendKeys(keys)
                    sent = new
                    text = new
                },
                label = { Text(stringResource(R.string.remote_keyboard_field)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { actions.press(RemoteKey.Enter) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                KeyButton(
                    key = RemoteKey.Backspace,
                    label = stringResource(R.string.remote_keyboard_backspace),
                    actions = actions,
                    shape = RoundedCornerShape(16.dp),
                    colors = KeyDefaults.secondary(),
                    onActivate = ::trimOne,
                    onPressedChange = { backspaceHeld = it },
                    modifier = Modifier.size(width = 72.dp, height = 52.dp),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = null)
                }
                FilledTonalButton(
                    onClick = {
                        val count = sent.codePointCount(0, sent.length)
                        if (count > 0) actions.sendKeys(List(count) { RemoteKey.Backspace })
                        sent = ""
                        text = ""
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(52.dp),
                ) {
                    Text(stringResource(R.string.remote_keyboard_clear))
                }
                Spacer(Modifier.weight(1f))
                KeyButton(
                    key = RemoteKey.Enter,
                    label = stringResource(R.string.remote_keyboard_enter),
                    actions = actions,
                    shape = RoundedCornerShape(16.dp),
                    colors = KeyDefaults.accent(),
                    modifier = Modifier.height(52.dp),
                ) {
                    Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardReturn, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.remote_keyboard_enter), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Row {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.remote_keyboard_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
