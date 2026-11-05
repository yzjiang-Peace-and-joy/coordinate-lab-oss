package app.coordinate.lab

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization

/** Callers return true only after persistence succeeds; failed saves keep the user's input. */
@Composable
internal fun RouteNameDialog(
    title: String,
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Boolean
) {
    var value by remember(title, initialName) { mutableStateOf(initialName) }
    var saveError by remember(title, initialName) { mutableStateOf<String?>(null) }
    val trimmed = value.trim()
    val valid = trimmed.isNotBlank() && trimmed.length <= 40
    val requester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val submit: () -> Unit = {
        if (valid) {
            if (onSave(trimmed)) onDismiss() else saveError = "未能保存，请重试"
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it; saveError = null },
                modifier = Modifier.fillMaxWidth().focusRequester(requester),
                singleLine = true,
                label = { Text("名称") },
                isError = trimmed.length > 40 || saveError != null,
                supportingText = {
                    Text(saveError ?: if (trimmed.length > 40) "名称最多 40 字" else "${trimmed.length}/40")
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() })
            )
            LaunchedEffect(Unit) {
                requester.requestFocus()
                keyboard?.show()
            }
        },
        confirmButton = { TextButton(onClick = submit, enabled = valid) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
