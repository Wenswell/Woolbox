package com.wswensw.woolbox.qr

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.wswensw.woolbox.R
import com.wswensw.woolbox.log.ToolLogs

internal fun newScanner() = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())

@Composable
fun ScanScreen(onLogs: () -> Unit) {
    val context = LocalContext.current
    val permissionMessage = stringResource(R.string.scan_permission)
    val emptyMessage = stringResource(R.string.scan_empty)
    val noHandlerMessage = stringResource(R.string.scan_no_handler)
    var session by rememberSaveable { mutableStateOf(ToolLogs.session()) }
    var result by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var camera by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var alive by remember { mutableStateOf(true) }
    DisposableEffect(Unit) { onDispose { alive = false } }
    fun log(action: String, details: String = "") = ToolLogs.record(context, "scan", session, action, details)
    fun failure(message: String) { if (alive) { error = message; log("error", message); camera = false } }
    fun found(text: String, source: String) { if (alive) { result = text; camera = false; log("result", "$source\n$text") } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        log("camera_permission", granted.toString())
        if (granted) camera = true else error = permissionMessage
    }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            busy = true
            val scanner = newScanner()
            try {
                scanner.process(InputImage.fromFilePath(context, uri))
                    .addOnSuccessListener { codes ->
                        val text = codes.firstNotNullOfOrNull { it.rawValue }
                        if (text == null) failure(emptyMessage) else found(text, "gallery")
                    }.addOnFailureListener { failure(it.message ?: it.javaClass.simpleName) }
                    .addOnCompleteListener { scanner.close(); busy = false }
            } catch (e: Exception) { scanner.close(); busy = false; failure(e.message ?: e.javaClass.simpleName) }
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.scan_title), style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = onLogs) { Text(stringResource(R.string.tool_logs)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !busy, onClick = {
                session = ToolLogs.session(); result = null; error = null; log("start", "camera")
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) camera = true
                else permission.launch(Manifest.permission.CAMERA)
            }) { Text(stringResource(R.string.scan_camera)) }
            OutlinedButton(enabled = !busy, onClick = {
                session = ToolLogs.session(); camera = false; result = null; error = null; log("start", "gallery"); photo.launch("image/*")
            }) { Text(stringResource(R.string.scan_gallery)) }
        }
        if (busy) CircularProgressIndicator()
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (camera) CameraPreview(Modifier.weight(1f), { found(it, "camera") }, ::failure)
        result?.let { text ->
            SelectionContainer(Modifier.weight(1f).verticalScroll(rememberScrollState())) { Text(text) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("QR", text)); log("copy", text)
                    android.widget.Toast.makeText(context, R.string.copied_to_clipboard, android.widget.Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.action_copy)) }
                val uri = Uri.parse(text)
                if (uri.scheme in setOf("https", "http") && !uri.host.isNullOrBlank()) {
                    OutlinedButton(onClick = {
                        try { context.startActivity(Intent(Intent.ACTION_VIEW, uri)); log("open", text) }
                        catch (e: ActivityNotFoundException) { failure(noHandlerMessage) }
                    }) { Text(stringResource(R.string.scan_open)) }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraPreview(modifier: Modifier, onResult: (String) -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val resultCallback by rememberUpdatedState(onResult)
    val errorCallback by rememberUpdatedState(onError)
    AndroidView(factory = { previewView }, modifier = modifier.fillMaxWidth())
    DisposableEffect(owner) {
        var disposed = false
        var delivered = false
        val scanner = newScanner()
        val executor = ContextCompat.getMainExecutor(context)
        val future = ProcessCameraProvider.getInstance(context)
        val preview = Preview.Builder().build().apply { surfaceProvider = previewView.surfaceProvider }
        val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        var provider: ProcessCameraProvider? = null
        analysis.setAnalyzer(executor) { proxy ->
            val image = proxy.image
            if (disposed || delivered || image == null) proxy.close()
            else scanner.process(InputImage.fromMediaImage(image, proxy.imageInfo.rotationDegrees))
                .addOnSuccessListener { codes ->
                    if (!disposed && !delivered) codes.firstNotNullOfOrNull { it.rawValue }?.let { delivered = true; resultCallback(it) }
                }.addOnFailureListener { if (!disposed && !delivered) { delivered = true; errorCallback(it.message ?: it.javaClass.simpleName) } }
                .addOnCompleteListener { proxy.close() }
        }
        future.addListener({
            if (!disposed) try {
                provider = future.get()
                provider!!.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (e: Exception) { errorCallback(e.message ?: e.javaClass.simpleName) }
        }, executor)
        onDispose { disposed = true; analysis.clearAnalyzer(); provider?.unbind(preview, analysis); scanner.close() }
    }
}
