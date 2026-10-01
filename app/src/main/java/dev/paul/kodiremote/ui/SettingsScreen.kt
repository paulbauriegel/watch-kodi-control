package dev.paul.kodiremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import dev.paul.kodiremote.data.KodiConfig
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: RemoteViewModel, onDone: () -> Unit) {
    val saved = vm.config.value
    var host by remember { mutableStateOf(saved.host) }
    var port by remember { mutableStateOf(saved.port.toString()) }
    var username by remember { mutableStateOf(saved.username) }
    var password by remember { mutableStateOf(saved.password) }
    var result by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { ListHeader { Text("Kodi") } }
            item { SettingsField("Host / IP", host, { host = it }, KeyboardType.Uri) }
            item { SettingsField("Port", port, { port = it.filter(Char::isDigit).take(5) }, KeyboardType.Number) }
            item { SettingsField("Username", username, { username = it }) }
            item { SettingsField("Password", password, { password = it }, KeyboardType.Password, secret = true) }
            item {
                Button(
                    onClick = {
                        val config = KodiConfig(host.trim(), port.toIntOrNull() ?: 8080, username, password)
                        vm.saveConfig(config)
                        result = "Testing…"
                        scope.launch {
                            val outcome = vm.testConnection(config)
                            result = outcome
                            if (outcome == Status.Connected.label) onDone()
                        }
                    },
                    enabled = host.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save & test", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
            }
            result?.let { text ->
                item {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                Text(
                    text = "Enable Kodi › Settings › Services › Control › Allow remote control via HTTP. " +
                        "Watch needs Wi-Fi on the same network.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    secret: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(start = 14.dp, bottom = 2.dp),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceContainer, RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}
