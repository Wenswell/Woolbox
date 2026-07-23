package com.wswensw.woolbox.record.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.wswensw.woolbox.record.data.RecordRepository
import com.wswensw.woolbox.ui.AppRouteScaffold
import com.wswensw.woolbox.ui.AppSection
import com.wswensw.woolbox.ui.AppStyle

@Composable
fun RecordsConnectionScreen(
    state: RecordsUiState,
    onBack: () -> Unit,
    onSave: (username: String, password: String, onResult: (String?) -> Unit) -> Unit,
) {
    var username by rememberSaveable(state.connection.username) {
        mutableStateOf(state.connection.username)
    }
    var password by rememberSaveable { mutableStateOf("") }
    var saving by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current

    fun submit() {
        if (saving || username.isBlank() || password.isEmpty()) return
        saving = true
        error = null
        onSave(username, password) { message ->
            saving = false
            error = message
            if (message == null) {
                password = ""
                onBack()
            }
        }
    }

    AppRouteScaffold(title = "Records connection", onBack = onBack) {
        AppSection(title = "Server") {
            Text(
                text = RecordRepository.BASE_URL,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    horizontal = AppStyle.spacing.pageHorizontal,
                    vertical = 8.dp,
                ),
            )
        }

        AppSection(title = "Credentials") {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                enabled = !saving,
                label = { Text("Username") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                ),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = AppStyle.spacing.pageHorizontal,
                        vertical = 8.dp,
                    ),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                enabled = !saving,
                label = { Text("Password") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (!saving && username.isNotBlank() && password.isNotEmpty()) {
                            submit()
                        }
                    },
                ),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = AppStyle.spacing.pageHorizontal,
                        vertical = 8.dp,
                    ),
            )
            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(
                        horizontal = AppStyle.spacing.pageHorizontal,
                        vertical = 4.dp,
                    ),
                )
            }
            Button(
                onClick = ::submit,
                enabled = !saving && username.isNotBlank() && password.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = AppStyle.spacing.pageHorizontal,
                        vertical = 12.dp,
                    )
                    .heightIn(min = 52.dp),
            ) {
                Text(if (saving) "Connecting" else "Save and connect")
            }
        }
    }
}
