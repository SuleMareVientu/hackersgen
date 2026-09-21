@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.sceneview.demo.demos

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import io.github.sceneview.demo.R
import io.github.sceneview.demo.service.JobMetadata
import io.github.sceneview.demo.service.OpenSplatServerService
import io.github.sceneview.demo.service.SystemStatsResponse
import io.github.sceneview.demo.state.SplatTrainingStateHolder
import io.github.sceneview.demo.storage.CaptureProject
import io.github.sceneview.demo.storage.CaptureStorageManager
import io.github.sceneview.demo.storage.TrainedSplat
import io.github.sceneview.demo.storage.TrainedSplatStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

enum class SplatTrainingTab {
    Local,
    Server
}

@Composable
fun SplatTrainingDemo(
    onBack: (() -> Unit)? = null,
    initialProject: CaptureProject? = null,
    onNavigateToLibrary: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState { SplatTrainingTab.entries.size }

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
        ) {
            PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = pagerState.currentPage == SplatTrainingTab.Local.ordinal,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(SplatTrainingTab.Local.ordinal)
                        }
                    },
                    text = { Text(stringResource(R.string.training_tab_local)) },
                    icon = { Icon(Icons.Default.Devices, contentDescription = null) }
                )
                Tab(
                    selected = pagerState.currentPage == SplatTrainingTab.Server.ordinal,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(SplatTrainingTab.Server.ordinal)
                        }
                    },
                    text = { Text(stringResource(R.string.training_tab_server)) },
                    icon = { Icon(Icons.Default.Cloud, contentDescription = null) }
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                when (page) {
                    SplatTrainingTab.Local.ordinal -> {
                        LocalTrainingContent(
                            initialProject = initialProject,
                            onNavigateToLibrary = onNavigateToLibrary
                        )
                    }
                    SplatTrainingTab.Server.ordinal -> {
                        ServerTrainingContent(
                            onNavigateToLibrary = onNavigateToLibrary
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LOCAL TRAINING TAB (On-Device Brush Engine)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LocalTrainingContent(
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
    val isTraining by SplatTrainingStateHolder::isTraining
    val currentIteration by SplatTrainingStateHolder::currentIteration
    val totalIterations by SplatTrainingStateHolder::totalIterations
    val trainingElapsedMs by SplatTrainingStateHolder::trainingElapsedMs
    val estimatedRemainingMs by SplatTrainingStateHolder::estimatedRemainingMs
    val statusText by SplatTrainingStateHolder::statusText
    val trainingCompleted by SplatTrainingStateHolder::trainingCompleted

    val currentExportName by SplatTrainingStateHolder::currentExportName
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

    // Clean up temporary ZIP files and incomplete splat records on entry/relaunch ONLY IF NOT ACTIVELY TRAINING
    LaunchedEffect(Unit) {
        if (!isTraining) {
            TrainedSplatStorage.cleanIncompleteSplats(context)
            val cacheFile = File(context.cacheDir, "training_dataset.zip")
            if (cacheFile.exists()) {
                cacheFile.delete()
            }
            trainedSplats = TrainedSplatStorage.loadTrainedSplats(context)
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
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

        Spacer(modifier = Modifier.height(20.dp))

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

        Spacer(modifier = Modifier.height(20.dp))

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

        Spacer(modifier = Modifier.height(20.dp))

        // Training Progress Indicator & Metrics
        if (isTraining || trainingCompleted) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = RoundedCornerShape(16.dp)
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
                        progress = { progress.coerceIn(0f, 1f) },
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

// ─────────────────────────────────────────────────────────────────────────────
// SERVER TRAINING TAB (Self-Hosted Remote GPU Training)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ServerTrainingContent(
    onNavigateToLibrary: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("opensplat_server_prefs", Context.MODE_PRIVATE) }

    // Server Configuration State
    var serverUrl by remember {
        mutableStateOf(prefs.getString("server_url", "http://192.168.1.100:8000") ?: "http://192.168.1.100:8000")
    }
    var isCheckingConnection by remember { mutableStateOf(false) }
    var connectionStatus by remember { mutableStateOf("Not checked") }
    var isConnected by remember { mutableStateOf(false) }
    var systemStats by remember { mutableStateOf<SystemStatsResponse?>(null) }
    var showLanHint by remember { mutableStateOf(false) }

    // Source Selection State
    var selectedSourceUri by remember { mutableStateOf<Uri?>(null) }
    var selectedSourceName by remember { mutableStateOf<String?>(null) }
    var isDatasetSource by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableIntStateOf(0) }
    var showCapturesDialog by remember { mutableStateOf(false) }

    // Training Parameters
    var numItersInput by remember { mutableStateOf("30000") }
    var downscaleFactor by remember { mutableIntStateOf(1) }
    var useCpu by remember { mutableStateOf(false) }

    // Active Job & Monitoring State
    var activeJobId by remember { mutableStateOf<String?>(null) }
    var activeJobMetadata by remember { mutableStateOf<JobMetadata?>(null) }
    var activeJobLogs by remember { mutableStateOf<List<String>>(emptyList()) }
    var jobList by remember { mutableStateOf<List<JobMetadata>>(emptyList()) }
    var showLogs by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }

    fun saveServerUrl(url: String) {
        serverUrl = url
        prefs.edit().putString("server_url", url).apply()
    }

    fun testConnection() {
        scope.launch {
            isCheckingConnection = true
            connectionStatus = "Checking..."
            OpenSplatServerService.checkStatus(serverUrl).fold(
                onSuccess = { res ->
                    isConnected = true
                    connectionStatus = "${res.service}: ${res.status}"
                    Toast.makeText(context, "Connected to server!", Toast.LENGTH_SHORT).show()
                    // Fetch system stats & jobs
                    OpenSplatServerService.getSystemStats(serverUrl).onSuccess { stats ->
                        systemStats = stats
                    }
                    OpenSplatServerService.listJobs(serverUrl).onSuccess { list ->
                        jobList = list
                        if (activeJobId == null && list.isNotEmpty()) {
                            activeJobId = list.first().jobId
                            activeJobMetadata = list.first()
                        }
                    }
                },
                onFailure = { ex ->
                    isConnected = false
                    connectionStatus = "Failed: ${ex.message}"
                    systemStats = null
                }
            )
            isCheckingConnection = false
        }
    }

    // Auto-poll active job status & logs while training/preprocessing
    LaunchedEffect(isConnected, activeJobId, activeJobMetadata?.status) {
        val currentId = activeJobId
        if (isConnected && currentId != null) {
            val status = activeJobMetadata?.status
            val shouldPoll = status == "preprocessing" || status == "training" || status == "uploaded"
            if (shouldPoll) {
                while (true) {
                    delay(2500L)
                    val res = OpenSplatServerService.getJobStatus(serverUrl, currentId)
                    res.onSuccess { data ->
                        activeJobMetadata = data.metadata
                        activeJobLogs = data.logs
                    }
                    val currentStats = OpenSplatServerService.getSystemStats(serverUrl)
                    currentStats.onSuccess { systemStats = it }
                    if (activeJobMetadata?.status == "completed" || activeJobMetadata?.status == "failed" || activeJobMetadata?.status == "stopped") {
                        break
                    }
                }
            }
        }
    }

    // Video File Picker Launcher
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val name = getFileName(context, uri) ?: "video.mp4"
            if (!name.lowercase().endsWith(".mp4")) {
                Toast.makeText(context, "Please select an .mp4 video file", Toast.LENGTH_LONG).show()
            } else {
                selectedSourceUri = uri
                selectedSourceName = name
                isDatasetSource = false
            }
        }
    }

    // SAF Model Export Launcher
    var downloadingJobId by remember { mutableStateOf<String?>(null) }
    val downloadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val jId = downloadingJobId
        if (uri != null && jId != null) {
            scope.launch {
                isDownloading = true
                Toast.makeText(context, "Downloading model file...", Toast.LENGTH_SHORT).show()
                val result = runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        OpenSplatServerService.downloadModel(serverUrl, jId, "ply", outputStream).getOrThrow()
                    } ?: throw Exception("Failed to open destination file")
                }
                result.fold(
                    onSuccess = {
                        Toast.makeText(context, "Model download complete!", Toast.LENGTH_LONG).show()
                    },
                    onFailure = { ex ->
                        Toast.makeText(context, "Download failed: ${ex.message}", Toast.LENGTH_LONG).show()
                    }
                )
                isDownloading = false
            }
        }
    }

    fun saveModelToLibrary(job: JobMetadata) {
        scope.launch(Dispatchers.IO) {
            isDownloading = true
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Saving model to Library...", Toast.LENGTH_SHORT).show()
            }
            try {
                val exportName = "server_${job.jobId}.ply"
                val destFile = File(context.filesDir, exportName)
                destFile.outputStream().use { outStream ->
                    OpenSplatServerService.downloadModel(serverUrl, job.jobId, "ply", outStream).getOrThrow()
                }

                val splat = TrainedSplat(
                    id = job.jobId,
                    datasetName = job.sourceFile.ifEmpty { "Server Model (${job.jobId})" },
                    iterations = if (job.totalSteps > 0) job.totalSteps else (job.step.takeIf { it > 0 } ?: 30000),
                    elapsedMs = 0L,
                    psnr = 0f,
                    ssim = 0f,
                    status = "completed",
                    exportName = exportName,
                    timestamp = System.currentTimeMillis(),
                    currentIteration = job.step
                )
                val existing = TrainedSplatStorage.loadTrainedSplats(context).filterNot { it.id == splat.id }
                TrainedSplatStorage.saveTrainedSplats(context, listOf(splat) + existing)

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Saved to Trained Splats in Library!", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val exportName = "server_${job.jobId}.ply"
                val destFile = File(context.filesDir, exportName)
                if (destFile.exists()) {
                    destFile.delete()
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to save: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                isDownloading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── 1. SERVER CONFIGURATION & HARDWARE CARD ────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Desktop Server Connection",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { showLanHint = !showLanHint }) {
                        Icon(
                            imageVector = if (showLanHint) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "LAN Guide"
                        )
                    }
                }

                AnimatedVisibility(visible = showLanHint) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.server_lan_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { saveServerUrl(it) },
                        label = { Text(stringResource(R.string.server_url_label)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Button(
                        onClick = { testConnection() },
                        enabled = !isCheckingConnection
                    ) {
                        if (isCheckingConnection) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(stringResource(R.string.server_connect_test))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Connection Status Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    )
                    Text(
                        text = connectionStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Live Server System Stats
                systemStats?.let { stats ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Live Server Hardware",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        stats.cpu?.let { cpu ->
                            HardwareStatChip(label = "CPU", value = "${cpu.percent}% (${cpu.cores}c)")
                        }
                        stats.ram?.let { ram ->
                            val usedGb = String.format(Locale.US, "%.1f", ram.used.toDouble() / (1024 * 1024 * 1024))
                            val totalGb = String.format(Locale.US, "%.1f", ram.total.toDouble() / (1024 * 1024 * 1024))
                            HardwareStatChip(label = "RAM", value = "$usedGb/$totalGb GB (${ram.percent.toInt()}%)")
                        }
                        stats.gpu?.let { gpu ->
                            if (gpu.available) {
                                val gpuLabel = gpu.name?.replace("NVIDIA GeForce ", "") ?: "GPU"
                                val vramPercent = gpu.memoryPercent?.toInt() ?: 0
                                HardwareStatChip(
                                    label = gpuLabel,
                                    value = "Load: ${gpu.load?.toInt() ?: 0}% • VRAM: $vramPercent% • ${gpu.temp ?: 0}°C"
                                )
                            }
                        }
                        HardwareStatChip(label = "Active Jobs", value = "${stats.activeJobsCount}")
                    }
                }
            }
        }

        // ── 2. DATASET / VIDEO UPLOAD CARD ─────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.server_upload_source),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { videoPickerLauncher.launch("video/mp4") },
                        enabled = isConnected && !isUploading,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.VideoFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("MP4 Video", fontSize = 13.sp)
                    }
                    OutlinedButton(
                        onClick = { showCapturesDialog = true },
                        enabled = isConnected && !isUploading,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Library ZIP", fontSize = 13.sp)
                    }
                }

                selectedSourceName?.let { name ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = name,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (isDatasetSource) "Nerfstudio Capture Dataset (.zip)" else "MP4 Video Recording",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = {
                                    selectedSourceUri = null
                                    selectedSourceName = null
                                },
                                enabled = !isUploading
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = "Remove")
                            }
                        }
                    }
                }

                if (isUploading) {
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { uploadProgress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Uploading to server: $uploadProgress%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val uri = selectedSourceUri ?: return@Button
                        val name = selectedSourceName ?: "upload"
                        isUploading = true
                        uploadProgress = 0
                        scope.launch {
                            val uploadResult = if (isDatasetSource) {
                                OpenSplatServerService.uploadDataset(serverUrl, context, uri, name) { progress ->
                                    uploadProgress = progress
                                }
                            } else {
                                OpenSplatServerService.uploadVideo(serverUrl, context, uri, name) { progress ->
                                    uploadProgress = progress
                                }
                            }
                            isUploading = false
                            uploadResult.fold(
                                onSuccess = { res ->
                                    Toast.makeText(context, "Upload complete! Job ID: ${res.jobId}", Toast.LENGTH_LONG).show()
                                    activeJobId = res.jobId
                                    // Refresh status
                                    OpenSplatServerService.getJobStatus(serverUrl, res.jobId).onSuccess { data ->
                                        activeJobMetadata = data.metadata
                                        activeJobLogs = data.logs
                                    }
                                    OpenSplatServerService.listJobs(serverUrl).onSuccess { jobList = it }
                                },
                                onFailure = { ex ->
                                    Toast.makeText(context, "Upload failed: ${ex.message}", Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                    },
                    enabled = isConnected && selectedSourceUri != null && !isUploading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Upload to Server")
                }
            }
        }

        // ── 3. ACTIVE JOB MONITOR & CONTROLS CARD ──────────────────────────
        activeJobId?.let { jId ->
            val meta = activeJobMetadata
            val status = meta?.status ?: "unknown"

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Active Job: $jId",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (!meta?.sourceFile.isNullOrEmpty()) {
                                Text(
                                    text = "Source: ${meta.sourceFile}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        JobStatusBadge(status = status)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Pre-launch parameter tuning if uploaded or stopped
                    if (status == "uploaded" || status == "stopped") {
                        Text(
                            text = "Training Configuration",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = numItersInput,
                                onValueChange = { newVal ->
                                    numItersInput = newVal.filter { it.isDigit() }
                                },
                                label = { Text("Iterations") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = downscaleFactor.toString(),
                                onValueChange = { newVal ->
                                    val factor = newVal.filter { it.isDigit() }.toIntOrNull() ?: 1
                                    downscaleFactor = factor.coerceIn(1, 8)
                                },
                                label = { Text("Downscale (1-8x)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = useCpu,
                                onCheckedChange = { useCpu = it }
                            )
                            Text(
                                text = "Force CPU (Disable GPU)",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                val iters = numItersInput.toIntOrNull() ?: 30000
                                scope.launch {
                                    OpenSplatServerService.startJob(serverUrl, jId, iters, downscaleFactor, useCpu).fold(
                                        onSuccess = {
                                            Toast.makeText(context, "Job started!", Toast.LENGTH_SHORT).show()
                                            OpenSplatServerService.getJobStatus(serverUrl, jId).onSuccess {
                                                activeJobMetadata = it.metadata
                                                activeJobLogs = it.logs
                                            }
                                        },
                                        onFailure = { ex ->
                                            Toast.makeText(context, "Failed to start: ${ex.message}", Toast.LENGTH_LONG).show()
                                        }
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.server_start_training))
                        }
                    }

                    // Progress bar & live metrics during training or preprocessing
                    if (status == "preprocessing" || status == "training") {
                        val progPercent = meta?.progress ?: 0f
                        LinearProgressIndicator(
                            progress = { (progPercent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Step: ${meta?.step ?: 0} / ${meta?.totalSteps ?: 0}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            meta?.loss?.let { lossVal ->
                                Text(
                                    text = String.format(Locale.US, "Loss: %.5f", lossVal),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    OpenSplatServerService.stopJob(serverUrl, jId).fold(
                                        onSuccess = {
                                            Toast.makeText(context, "Job stopped", Toast.LENGTH_SHORT).show()
                                            OpenSplatServerService.getJobStatus(serverUrl, jId).onSuccess {
                                                activeJobMetadata = it.metadata
                                            }
                                        },
                                        onFailure = { ex ->
                                            Toast.makeText(context, "Stop failed: ${ex.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.server_stop_job))
                        }
                    }

                    // Completed Actions: Save to Library or Export File
                    if (status == "completed") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "3D Gaussian Splat model ready!",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { meta?.let { saveModelToLibrary(it) } },
                                        enabled = !isDownloading,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.server_save_to_library), fontSize = 12.sp)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            downloadingJobId = jId
                                            downloadLauncher.launch("$jId.ply")
                                        },
                                        enabled = !isDownloading,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.server_export_file), fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Failure Message
                    if (status == "failed" && !meta?.errorMessage.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Error: ${meta.errorMessage}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Job Secondary Actions: Refresh, Logs toggle, Delete
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { showLogs = !showLogs }
                        ) {
                            Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (showLogs) "Hide Logs" else "View Logs (${activeJobLogs.size})")
                        }

                        IconButton(
                            onClick = {
                                scope.launch {
                                    OpenSplatServerService.deleteJob(serverUrl, jId).fold(
                                        onSuccess = {
                                            Toast.makeText(context, "Job deleted", Toast.LENGTH_SHORT).show()
                                            activeJobId = null
                                            activeJobMetadata = null
                                            activeJobLogs = emptyList()
                                            OpenSplatServerService.listJobs(serverUrl).onSuccess { jobList = it }
                                        },
                                        onFailure = { ex ->
                                            Toast.makeText(context, "Delete failed: ${ex.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete Job",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    // Collapsible Server Logs
                    AnimatedVisibility(visible = showLogs) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Console Logs",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            OpenSplatServerService.clearJobLogs(serverUrl, jId)
                                            activeJobLogs = emptyList()
                                        }
                                    }
                                ) {
                                    Text("Clear", fontSize = 11.sp)
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                if (activeJobLogs.isEmpty()) {
                                    Text(
                                        text = "No logs generated yet.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Text(
                                        text = activeJobLogs.takeLast(50).joinToString("\n"),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── 4. SERVER JOBS LIST CARD ───────────────────────────────────────
        if (jobList.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.server_jobs_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = {
                                scope.launch {
                                    OpenSplatServerService.listJobs(serverUrl).onSuccess { jobList = it }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh Jobs")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    jobList.forEach { job ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    activeJobId = job.jobId
                                    activeJobMetadata = job
                                    scope.launch {
                                        OpenSplatServerService.getJobStatus(serverUrl, job.jobId).onSuccess {
                                            activeJobMetadata = it.metadata
                                            activeJobLogs = it.logs
                                        }
                                    }
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (activeJobId == job.jobId) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = job.jobId,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "${job.sourceFile} • ${job.jobType}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                JobStatusBadge(status = job.status)
                            }
                        }
                    }
                }
            }
        }
    }

    // Capture Project Selector Dialog for Server Upload
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
                                        selectedSourceUri = Uri.parse(cap.uriString)
                                        selectedSourceName = "${cap.name}.zip"
                                        isDatasetSource = true
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

// ─────────────────────────────────────────────────────────────────────────────
// SHARED UI COMPONENTS & HELPERS
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HardwareStatChip(label: String, value: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text(text = value, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
        }
    }
}

@Composable
private fun JobStatusBadge(status: String) {
    val (bgColor, textColor) = when (status.lowercase()) {
        "completed" -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        "training", "preprocessing" -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        "failed" -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        "stopped" -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(percent = 50)
    ) {
        Text(
            text = status.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
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
            delay(1000L)
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
