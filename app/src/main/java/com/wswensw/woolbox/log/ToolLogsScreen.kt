package com.wswensw.woolbox.log

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wswensw.woolbox.R
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolLogsScreen(tool: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val exportedMessage = stringResource(R.string.logs_exported)
    val events by remember(tool) { ToolLogs.recent(context, tool) }.collectAsStateWithLifecycle(emptyList())
    val storageError by ToolLogs.error.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<ToolLogEvent?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-ndjson")) { uri ->
        if (uri != null) scope.launch {
            exporting = true
            try { ToolLogs.export(context, tool, uri); status = exportedMessage }
            catch (e: Exception) { status = e.message }
            finally { exporting = false }
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.tool_logs)) },
        navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.navigation_back)) } },
        actions = { TextButton(enabled = !exporting, onClick = { export.launch("woolbox-${tool.ifEmpty { "all" }}-${System.currentTimeMillis()}.jsonl") }) { Text(stringResource(R.string.logs_export)) } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item { Text(stringResource(R.string.logs_recent), Modifier.padding(16.dp)) }
            (storageError ?: status)?.let { message -> item { Text(message, Modifier.padding(16.dp)) } }
            items(events, key = { it.id }) { event ->
                TextButton(onClick = { selected = event }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(8.dp)) {
                        Text("${event.tool} · ${event.action}")
                        Text(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(event.time)))
                        Text(event.details.take(160), maxLines = 2)
                    }
                }
            }
        }
    }
    selected?.let { event ->
        AlertDialog(onDismissRequest = { selected = null }, title = { Text(event.action) },
            text = { androidx.compose.foundation.lazy.LazyColumn { item { SelectionContainer { Text(event.json()) } } } },
            confirmButton = { TextButton(onClick = { selected = null }) { Text(stringResource(R.string.action_close)) } })
    }
}
