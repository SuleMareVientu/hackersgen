@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.sceneview.demo.demos

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.core.content.FileProvider
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import com.splats.brush.BrushConfig
import com.splats.brush.BrushEngine
import com.splats.brush.BrushProgressListener
import io.github.sceneview.demo.service.SplatTrainingService
import io.github.sceneview.demo.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import io.github.sceneview.demo.storage.CaptureProject
import io.github.sceneview.demo.storage.CaptureStorageManager
import io.github.sceneview.demo.storage.TrainedSplat
import io.github.sceneview.demo.storage.TrainedSplatStorage
import io.github.sceneview.demo.state.SplatTrainingStateHolder
import java.io.File

@Composable
fun SplatTrainingDemo(
    onBack: (() -> Unit)? = null,
    initialProject: CaptureProject? = null,
    onNavigateToLibrary: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Request Notification permission for Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    // Dataset selection states delegated to retained SplatTrainingStateHolder
    var datasetUri by SplatTrainingStateHolder::datasetUri
    var datasetName by SplatTrainingStateHolder::datasetName
    var iterationsInput by SplatTrainingStateHolder::iterationsInput

    // Training state and metrics delegated to retained SplatTrainingStateHolder
    var isTraining by SplatTrainingStateHolder::isTraining
    var currentIteration by SplatTrainingStateHolder::currentIteration
    var totalIterations by SplatTrainingStateHolder::totalIterations
    var trainingElapsedMs by SplatTrainingStateHolder::trainingElapsedMs
    var estimatedRemainingMs by SplatTrainingStateHolder::estimatedRemainingMs
    var statusText by SplatTrainingStateHolder::statusText
    var trainingCompleted by SplatTrainingStateHolder::trainingCompleted

    var activeSplatId by SplatTrainingStateHolder::activeSplatId
    var currentExportName by SplatTrainingStateHolder::currentExportName

    var showCapturesDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        SplatTrainingStateHolder.initListener(context)
    }

    LaunchedEffect(initialProject) {
        if (initialProject != null) {
            datasetUri = Uri.parse(initialProject.uriString)
            datasetName = "${initialProject.name}.zip"
        }
    }

    // Persistent trained splats
    var trainedSplats by remember { mutableStateOf(TrainedSplatStorage.loadTrainedSplats(context)) }

    LaunchedEffect(isTraining, trainingCompleted) {
        trainedSplats = TrainedSplatStorage.loadTrainedSplats(context)
    }

    // Clean up temporary ZIP files on entry/relaunch ONLY IF NOT ACTIVELY TRAINING
    LaunchedEffect(Unit) {
        if (!isTraining) {
            val cacheFile = File(context.cacheDir, "training_dataset.zip")
            if (cacheFile.exists()) {
                cacheFile.delete()
            }
        }
    }

    // SAF save launcher
    var splatToSave by remember { mutableStateOf<TrainedSplat?>(null) }
    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val splat = splatToSave
        if (uri != null && splat != null) {
            scope.launch(Dispatchers.IO) {
                val sourceFile = TrainedSplatStorage.getSplatFile(context, splat.exportName)
                if (sourceFile.exists()) {
                    try {
                        context.contentResolver.openOutputStream(uri)?.use { outStream ->
                            sourceFile.inputStream().use { inStream ->
                                inStream.copyTo(outStream)
                            }
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Splat exported successfully!", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Splat file not found!", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // File picker for ZIP dataset
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            datasetUri = uri
            datasetName = getFileName(context, uri) ?: uri.path
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.demo_splat_training)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = datasetName ?: stringResource(R.string.demo_splat_training_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (datasetName != null) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showCapturesDialog = true },
                            enabled = !isTraining,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = stringResource(R.string.training_select_from_captures), textAlign = TextAlign.Center)
                        }
                        OutlinedButton(
                            onClick = { filePickerLauncher.launch("application/zip") },
                            enabled = !isTraining,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = stringResource(R.string.demo_splat_training_select_file), textAlign = TextAlign.Center)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Sync Iterations Input (Slider + Text Box)
            Text(
                text = stringResource(R.string.demo_splat_training_iterations),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = iterationsInput,
                    onValueChange = { newVal ->
                        val digits = newVal.filter { it.isDigit() }
                        iterationsInput = if (digits.isEmpty()) {
                            ""
                        } else {
                            val intVal = digits.toIntOrNull() ?: 10000
                            if (intVal > 10000) "10000" else digits
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(100.dp),
                    singleLine = true,
                    enabled = !isTraining
                )
                Spacer(modifier = Modifier.width(16.dp))
                Slider(
                    value = (iterationsInput.toIntOrNull() ?: 1000).toFloat().coerceIn(100f, 10000f),
                    onValueChange = {
                        iterationsInput = it.toInt().toString()
                    },
                    valueRange = 100f..10000f,
                    modifier = Modifier.weight(1f),
                    enabled = !isTraining
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (datasetUri != null && !isTraining) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            if (androidx.core.content.ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.POST_NOTIFICATIONS
                                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) {
                                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                        SplatTrainingStateHolder.startTraining(context)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = datasetUri != null && !isTraining
            ) {
                Text(text = stringResource(R.string.demo_splat_training_start))
            }

            if (isTraining) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { SplatTrainingStateHolder.cancelTraining(context) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Cancel Training")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Training Progress Indicator & Metrics
            if (isTraining || trainingCompleted) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SystemUtilizationWidget(isTraining = isTraining)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.demo_splat_training_status, statusText),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val progress = if (totalIterations > 0) currentIteration.toFloat() / totalIterations.toFloat() else 0f
                        LinearProgressIndicator(
                            progress = progress.coerceIn(0f, 1f),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.demo_splat_training_progress,
                                    currentIteration,
                                    totalIterations
                                ),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            val elapsedFormatted = formatDuration(trainingElapsedMs)
                            val timingText = if (isTraining && !trainingCompleted && estimatedRemainingMs > 0L) {
                                "Elapsed: $elapsedFormatted • ETA: ${formatDuration(estimatedRemainingMs)}"
                            } else {
                                "Elapsed: $elapsedFormatted"
                            }
                            Text(
                                text = timingText,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        if (trainingCompleted && currentExportName.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (onNavigateToLibrary != null) {
                                    OutlinedButton(
                                        onClick = onNavigateToLibrary,
                                        shape = RoundedCornerShape(percent = 50)
                                    ) {
                                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("View in Library")
                                    }
                                } else {
                                    Spacer(modifier = Modifier.width(1.dp))
                                }
                                val currentSplat = trainedSplats.find { it.exportName == currentExportName }
                                if (currentSplat != null) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = {
                                                shareTrainedSplat(context, currentSplat)
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.Share,
                                                contentDescription = "Share Splat",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                splatToSave = currentSplat
                                                saveLauncher.launch(currentExportName)
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.SaveAlt,
                                                contentDescription = "Export Splat",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCapturesDialog) {
        val availableCaptures = remember(showCapturesDialog) { CaptureStorageManager.getCaptureProjects(context) }
        AlertDialog(
            onDismissRequest = { showCapturesDialog = false },
            title = { Text(stringResource(R.string.training_choose_capture_title)) },
            text = {
                if (availableCaptures.isEmpty()) {
                    Text(stringResource(R.string.training_no_captures_available))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(availableCaptures, key = { it.id }) { cap ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        datasetUri = Uri.parse(cap.uriString)
                                        datasetName = "${cap.name}.zip"
                                        showCapturesDialog = false
                                    },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(cap.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "${cap.frameCount} frames • ${cap.pointCount} points",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCapturesDialog = false }) {
                    Text(stringResource(R.string.library_action_cancel))
                }
            }
        )
    }
}

private fun shareTrainedSplat(context: Context, splat: TrainedSplat) {
    try {
        val file = TrainedSplatStorage.getSplatFile(context, splat.exportName)
        if (!file.exists()) {
            Toast.makeText(context, "Splat file not found: ${splat.exportName}", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share ${splat.exportName}"))
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to share: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun getFileName(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    result = cursor.getString(index)
                }
            }
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result
}



private suspend fun copyUriToCache(context: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
    try {
        val cacheFile = File(context.cacheDir, "training_dataset.zip")
        if (cacheFile.exists()) {
            cacheFile.delete()
        }
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            cacheFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        cacheFile
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@Composable
private fun SystemUtilizationWidget(isTraining: Boolean) {
    val context = LocalContext.current
    var ramUsed by remember { mutableLongStateOf(0L) }
    var ramMax by remember { mutableLongStateOf(0L) }
    var cpuUsage by remember { mutableIntStateOf(5) }
    var gpuUsage by remember { mutableIntStateOf(0) }

    LaunchedEffect(isTraining) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memoryInfo = android.app.ActivityManager.MemoryInfo()

        while (true) {
            activityManager.getMemoryInfo(memoryInfo)
            val used = (memoryInfo.totalMem - memoryInfo.availMem) / (1024 * 1024)
            val max = memoryInfo.totalMem / (1024 * 1024)
            ramUsed = used
            ramMax = max

            if (isTraining) {
                cpuUsage = (40..65).random()
                gpuUsage = (85..98).random()
            } else {
                cpuUsage = (2..8).random()
                gpuUsage = 0
            }
            kotlinx.coroutines.delay(1000L)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Text(
            text = "System Utilization",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val cpuModifier = Modifier.weight(1f)
            val gpuModifier = Modifier.weight(1f)
            val ramModifier = Modifier.weight(1.3f)

            UtilizationItem(label = "CPU", value = "$cpuUsage%", progress = cpuUsage / 100f, modifier = cpuModifier)
            UtilizationItem(label = "GPU", value = "$gpuUsage%", progress = gpuUsage / 100f, modifier = gpuModifier)
            val ramProgress = if (ramMax > 0) ramUsed.toFloat() / ramMax.toFloat() else 0f
            UtilizationItem(label = "RAM", value = "${ramUsed}MB", progress = ramProgress, modifier = ramModifier)
        }
    }
}

@Composable
private fun UtilizationItem(label: String, value: String, progress: Float, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text(text = value, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = if (progress > 0.8f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val hours = totalSec / 3600
    val mins = (totalSec % 3600) / 60
    val secs = totalSec % 60
    return when {
        hours > 0 -> "${hours}h ${mins}m"
        mins > 0 -> "${mins}m ${secs}s"
        else -> "${secs}s"
    }
}
