@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.sceneview.demo.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.ModelTraining
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import io.github.sceneview.demo.R
import io.github.sceneview.demo.storage.CaptureProject
import io.github.sceneview.demo.storage.CaptureStorageManager
import io.github.sceneview.demo.storage.TrainedSplat
import io.github.sceneview.demo.storage.TrainedSplatStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LibraryTab {
    Scans,
    TrainedSplats
}

@Composable
fun LibraryScreen(
    onTrainProject: (CaptureProject) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState { LibraryTab.entries.size }

    // Scans state
    var captures by remember { mutableStateOf(CaptureStorageManager.getCaptureProjects(context)) }
    var folderDisplay by remember { mutableStateOf(CaptureStorageManager.getCaptureFolderDisplay(context)) }

    // Trained splats state
    var trainedSplats by remember { mutableStateOf(TrainedSplatStorage.loadTrainedSplats(context)) }

    fun refreshData() {
        captures = CaptureStorageManager.getCaptureProjects(context)
        folderDisplay = CaptureStorageManager.getCaptureFolderDisplay(context)
        trainedSplats = TrainedSplatStorage.loadTrainedSplats(context)
    }

    LaunchedEffect(Unit) {
        refreshData()
    }

    // SAF folder picker launcher
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            CaptureStorageManager.setCustomFolder(context, uri)
            refreshData()
            Toast.makeText(context, "Captures folder updated", Toast.LENGTH_SHORT).show()
        }
    }

    // Export trained splat launcher
    var splatToExport by remember { mutableStateOf<TrainedSplat?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        val splat = splatToExport
        if (uri != null && splat != null) {
            scope.launch(Dispatchers.IO) {
                val file = TrainedSplatStorage.getSplatFile(context, splat.exportName)
                if (file.exists()) {
                    try {
                        context.contentResolver.openOutputStream(uri)?.use { outStream ->
                            file.inputStream().use { inStream ->
                                inStream.copyTo(outStream)
                            }
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Exported successfully!", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    // Rename dialog state
    var projectToRename by remember { mutableStateOf<CaptureProject?>(null) }
    var newProjectName by remember { mutableStateOf("") }

    // Delete dialog state
    var projectToDelete by remember { mutableStateOf<CaptureProject?>(null) }
    var splatToDelete by remember { mutableStateOf<TrainedSplat?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.library_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { refreshData() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Library"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = pagerState.currentPage == LibraryTab.Scans.ordinal,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(LibraryTab.Scans.ordinal)
                        }
                    },
                    text = { Text(stringResource(R.string.library_tab_scans)) },
                    icon = { Icon(Icons.Default.CameraAlt, contentDescription = null) }
                )
                Tab(
                    selected = pagerState.currentPage == LibraryTab.TrainedSplats.ordinal,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(LibraryTab.TrainedSplats.ordinal)
                        }
                    },
                    text = { Text(stringResource(R.string.library_tab_splats)) },
                    icon = { Icon(Icons.Default.ModelTraining, contentDescription = null) }
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                when (LibraryTab.entries[page]) {
                    LibraryTab.Scans -> {
                        ScansListTab(
                            captures = captures,
                            folderDisplay = folderDisplay,
                            hasCustomFolder = CaptureStorageManager.hasCustomFolder(context),
                            onChangeFolder = { folderPickerLauncher.launch(null) },
                            onResetFolder = {
                                CaptureStorageManager.resetToDefaultFolder(context)
                                refreshData()
                            },
                            onTrain = onTrainProject,
                            onRename = { project ->
                                projectToRename = project
                                newProjectName = project.name
                            },
                            onDelete = { project ->
                                projectToDelete = project
                            },
                            onShare = { project ->
                                shareCaptureProject(context, project)
                            }
                        )
                    }
                    LibraryTab.TrainedSplats -> {
                        TrainedSplatsTab(
                            splats = trainedSplats,
                            onExport = { splat ->
                                splatToExport = splat
                                exportLauncher.launch(splat.exportName)
                            },
                            onShare = { splat ->
                                shareTrainedSplat(context, splat)
                            },
                            onDelete = { splat ->
                                splatToDelete = splat
                            }
                        )
                    }
                }
            }
        }
    }

    // Rename Dialog
    projectToRename?.let { project ->
        AlertDialog(
            onDismissRequest = { projectToRename = null },
            title = { Text(stringResource(R.string.library_rename_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newProjectName,
                        onValueChange = { newProjectName = it },
                        label = { Text(stringResource(R.string.library_rename_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            CaptureStorageManager.renameCapture(context, project, newProjectName)
                            refreshData()
                            projectToRename = null
                        }
                    },
                    enabled = newProjectName.isNotBlank()
                ) {
                    Text(stringResource(R.string.library_action_rename))
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToRename = null }) {
                    Text(stringResource(R.string.library_action_cancel))
                }
            }
        )
    }

    // Delete Scan Confirmation Dialog
    projectToDelete?.let { project ->
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            title = { Text(stringResource(R.string.library_delete_confirm_title)) },
            text = {
                Text(stringResource(R.string.library_delete_confirm_msg, project.name))
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            CaptureStorageManager.deleteCapture(context, project)
                            refreshData()
                            projectToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.library_action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToDelete = null }) {
                    Text(stringResource(R.string.library_action_cancel))
                }
            }
        )
    }

    // Delete Trained Splat Confirmation Dialog
    splatToDelete?.let { splat ->
        AlertDialog(
            onDismissRequest = { splatToDelete = null },
            title = { Text(stringResource(R.string.library_delete_splat_confirm_title)) },
            text = {
                Text(stringResource(R.string.library_delete_splat_confirm_msg, splat.datasetName))
            },
            confirmButton = {
                Button(
                    onClick = {
                        TrainedSplatStorage.deleteTrainedSplat(context, splat)
                        refreshData()
                        splatToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.library_action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { splatToDelete = null }) {
                    Text(stringResource(R.string.library_action_cancel))
                }
            }
        )
    }
}

@Composable
private fun ScansListTab(
    captures: List<CaptureProject>,
    folderDisplay: String,
    hasCustomFolder: Boolean,
    onChangeFolder: () -> Unit,
    onResetFolder: () -> Unit,
    onTrain: (CaptureProject) -> Unit,
    onRename: (CaptureProject) -> Unit,
    onDelete: (CaptureProject) -> Unit,
    onShare: (CaptureProject) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Folder Configuration Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        stringResource(R.string.library_storage_folder, folderDisplay),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onChangeFolder,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.library_change_folder), style = MaterialTheme.typography.labelMedium)
                    }
                    if (hasCustomFolder) {
                        TextButton(onClick = onResetFolder) {
                            Text(stringResource(R.string.library_reset_folder), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (captures.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        text = stringResource(R.string.library_no_scans),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(captures, key = { it.id }) { project ->
                    ScanProjectCard(
                        project = project,
                        onTrain = { onTrain(project) },
                        onRename = { onRename(project) },
                        onDelete = { onDelete(project) },
                        onShare = { onShare(project) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanProjectCard(
    project: CaptureProject,
    onTrain: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    val dateStr = remember(project.timestamp) {
        SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(Date(project.timestamp))
    }
    val sizeMbStr = remember(project.sizeBytes) {
        if (project.sizeBytes > 0) {
            String.format(Locale.US, "%.1f MB", project.sizeBytes.toDouble() / (1024.0 * 1024.0))
        } else ""
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = dateStr + if (sizeMbStr.isNotEmpty()) " • $sizeMbStr" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row {
                    IconButton(onClick = onShare) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.library_scan_share), modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onRename) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.library_scan_rename), modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.library_scan_delete),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${stringResource(R.string.library_scan_frames, project.frameCount)} • ${stringResource(R.string.library_scan_points, project.pointCount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = onTrain,
                    shape = RoundedCornerShape(percent = 50),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.library_scan_train), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun TrainedSplatsTab(
    splats: List<TrainedSplat>,
    onExport: (TrainedSplat) -> Unit,
    onShare: (TrainedSplat) -> Unit,
    onDelete: (TrainedSplat) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        if (splats.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ModelTraining,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        text = stringResource(R.string.library_no_splats),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(splats, key = { it.id }) { splat ->
                    TrainedSplatCard(
                        splat = splat,
                        onExport = { onExport(splat) },
                        onShare = { onShare(splat) },
                        onDelete = { onDelete(splat) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TrainedSplatCard(
    splat: TrainedSplat,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = remember(splat.timestamp) {
        SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(Date(splat.timestamp))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = splat.datasetName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row {
                    IconButton(onClick = onShare) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Share splat",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.library_scan_delete),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.library_splat_iterations, splat.iterations),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedButton(
                    onClick = onExport,
                    shape = RoundedCornerShape(percent = 50)
                ) {
                    Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.library_splat_export), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
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

private fun shareCaptureProject(context: Context, project: CaptureProject) {
    try {
        val uri = Uri.parse(project.uriString)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share ${project.name}"))
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to share: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
