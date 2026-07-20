package com.wswensw.template.record.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.wswensw.template.record.data.RecordRepository
import com.wswensw.template.ui.TemplateRouteScaffold
import com.wswensw.template.ui.TemplateSection

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

    TemplateRouteScaffold(title = "Records connection", onBack = onBack) {
        TemplateSection(title = "Server") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(RecordRepository.BASE_URL)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it)
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
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
                    },
                    enabled = !saving && username.isNotBlank() && password.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (saving) "Connecting" else "Save and connect")
                }
            }
        }
    }
}
