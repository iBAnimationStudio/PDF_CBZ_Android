package com.ibanimation.converter

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestStoragePermission()
        setContent {
            ConverterTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ConverterScreen()
                }
            }
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConverterScreen() {
    val scope = rememberCoroutineScope()
    var inputPath by remember { mutableStateOf(Environment.getExternalStorageDirectory().path + "/Download") }
    var outputPath by remember { mutableStateOf(Environment.getExternalStorageDirectory().path + "/Download") }

    // Engine State
    var verboseLog by remember { mutableStateOf("") }
    var progressFraction by remember { mutableFloatStateOf(0f) }
    var isProcessing by remember { mutableStateOf(false) }

    // Export Formats & Engine Configuration State
    var selectedFormat by remember { mutableStateOf("JPG") }
    var jpgQuality by remember { mutableFloatStateOf(85f) }
    
    // M3 Settings Dropdown State
    var expandedEngineDropdown by remember { mutableStateOf(false) }
    val pdfEngineOptions = listOf("Standard (Fast)", "High Quality (Render 2x)", "Low Quality (Compressed)")
    var selectedPdfEngine by remember { mutableStateOf(pdfEngineOptions[0]) }

    // Picker & Popup State
    var showPicker by remember { mutableStateOf(false) }
    var activeTarget by remember { mutableStateOf("input") }
    var showConfirmationSheet by remember { mutableStateOf(false) }
    var selectedAction by remember { mutableStateOf("") }
    var scannedFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    val selectedFiles = remember { mutableStateListOf<File>() }

    // Aesthetic Success Dialog State
    var showSuccessDialog by remember { mutableStateOf(false) }
    var successMessage by remember { mutableStateOf("") }

    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        animationSpec = tween(durationMillis = 300),
        label = "ProgressAnimation"
    )

    if (showPicker) {
        FolderPickerDialog(
            initialPath = if (activeTarget == "input") inputPath else outputPath,
            onFolderSelected = { selectedPath ->
                if (activeTarget == "input") inputPath = selectedPath else outputPath = selectedPath
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }

    // Aesthetic Task Finished Dialog
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = { Text("Task Finished", style = MaterialTheme.typography.headlineSmall) },
            text = { Text(successMessage, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { showSuccessDialog = false }) {
                    Text("Done")
                }
            },
            shape = RoundedCornerShape(28.dp)
        )
    }

    // Confirmation Sheet
    if (showConfirmationSheet) {
        ModalBottomSheet(
            onDismissRequest = { showConfirmationSheet = false },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
            ) {
                Text(
                    text = if (selectedAction == "PDF_TO_CBZ") "Action: PDF ➔ CBZ" else "Action: CBZ ➔ PDF",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Found ${scannedFiles.size} target files",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (selectedFiles.size == scannedFiles.size) {
                                selectedFiles.clear()
                            } else {
                                selectedFiles.clear()
                                selectedFiles.addAll(scannedFiles)
                            }
                        }
                ) {
                    Checkbox(
                        checked = selectedFiles.size == scannedFiles.size && scannedFiles.isNotEmpty(),
                        onCheckedChange = { checked ->
                            selectedFiles.clear()
                            if (checked) selectedFiles.addAll(scannedFiles)
                        }
                    )
                    Text("Select All", style = MaterialTheme.typography.bodyMedium)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                LazyColumn(modifier = Modifier.height(180.dp)) {
                    items(scannedFiles) { file ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (selectedFiles.contains(file)) selectedFiles.remove(file) else selectedFiles.add(file)
                                }
                        ) {
                            Checkbox(
                                checked = selectedFiles.contains(file),
                                onCheckedChange = { checked ->
                                    if (checked) selectedFiles.add(file) else selectedFiles.remove(file)
                                }
                            )
                            Text(file.name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        showConfirmationSheet = false
                        isProcessing = true
                        scope.launch {
                            val targets = selectedFiles.toList()
                            val count = if (selectedAction == "PDF_TO_CBZ") {
                                processPdfToCbz(targets, outputPath, selectedFormat, jpgQuality.toInt()) { log, progress ->
                                    verboseLog = log
                                    progressFraction = progress
                                }
                            } else {
                                processCbzToPdf(targets, outputPath) { log, progress ->
                                    verboseLog = log
                                    progressFraction = progress
                                }
                            }
                            
                            isProcessing = false
                            progressFraction = 0f
                            verboseLog = ""

                            successMessage = if (selectedAction == "PDF_TO_CBZ") {
                                "Successfully converted $count PDF files to CBZ."
                            } else {
                                "Successfully converted $count CBZ files to PDF."
                            }
                            showSuccessDialog = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedFiles.isNotEmpty(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Start Conversion")
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .animateContentSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.SwapHoriz,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )
        Text(text = "PDF ⇌ CBZ Converter", style = MaterialTheme.typography.headlineLarge)
        Text(text = "Rust backend", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

        Spacer(modifier = Modifier.height(28.dp))

        // Input Path
        OutlinedTextField(
            value = inputPath,
            onValueChange = { inputPath = it },
            label = { Text("Input Folder") },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(onClick = { activeTarget = "input"; showPicker = true }) {
                    Icon(Icons.Rounded.Folder, contentDescription = "Select Input Folder")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Output Path
        OutlinedTextField(
            value = outputPath,
            onValueChange = { outputPath = it },
            label = { Text("Output Folder") },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(onClick = { activeTarget = "output"; showPicker = true }) {
                    Icon(Icons.Rounded.Folder, contentDescription = "Select Output Folder")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Dynamic Wavy Expressive Snake Progress Panel
        AnimatedVisibility(
            visible = isProcessing,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                LinearWavyProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = verboseLog,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        // CBZ Export Settings Card
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("CBZ Export Settings", style = MaterialTheme.typography.titleMedium)
                
                Spacer(modifier = Modifier.height(12.dp))

                // M3 Render Engine Settings Dropdown
                ExposedDropdownMenuBox(
                    expanded = expandedEngineDropdown,
                    onExpandedChange = { expandedEngineDropdown = !expandedEngineDropdown }
                ) {
                    OutlinedTextField(
                        value = selectedPdfEngine,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Render Preset") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedEngineDropdown) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    ExposedDropdownMenu(
                        expanded = expandedEngineDropdown,
                        onDismissRequest = { expandedEngineDropdown = false }
                    ) {
                        pdfEngineOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    selectedPdfEngine = option
                                    expandedEngineDropdown = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Image Format Selection
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedFormat == "JPG",
                        onClick = { selectedFormat = "JPG" },
                        label = { Text("JPG (Smaller)") }
                    )
                    FilterChip(
                        selected = selectedFormat == "PNG",
                        onClick = { selectedFormat = "PNG" },
                        label = { Text("PNG (Lossless)") }
                    )
                }

                AnimatedVisibility(
                    visible = selectedFormat == "JPG",
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("JPG Quality: ${jpgQuality.toInt()}%", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = jpgQuality,
                            onValueChange = { jpgQuality = it },
                            valueRange = 10f..100f,
                            steps = 17
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Conversion Trigger Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    val files = File(inputPath).listFiles { _, name -> name.endsWith(".pdf", ignoreCase = true) }?.toList() ?: emptyList()
                    scannedFiles = files
                    selectedFiles.clear()
                    selectedFiles.addAll(files)
                    selectedAction = "PDF_TO_CBZ"
                    showConfirmationSheet = true
                },
                enabled = !isProcessing,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp)
            ) { Text("PDF ➔ CBZ") }

            Button(
                onClick = {
                    val files = File(inputPath).listFiles { _, name -> name.endsWith(".cbz", ignoreCase = true) }?.toList() ?: emptyList()
                    scannedFiles = files
                    selectedFiles.clear()
                    selectedFiles.addAll(files)
                    selectedAction = "CBZ_TO_PDF"
                    showConfirmationSheet = true
                },
                enabled = !isProcessing,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp)
            ) { Text("CBZ ➔ PDF") }
        }
    }
}

// Native Folder Picker Dialog
@Composable
fun FolderPickerDialog(initialPath: String, onFolderSelected: (String) -> Unit, onDismiss: () -> Unit) {
    var currentPath by remember { mutableStateOf(initialPath) }
    val currentFile = File(currentPath)
    val folders = remember(currentPath) {
        currentFile.listFiles { file -> file.isDirectory && !file.isHidden }
            ?.sortedBy { it.name } ?: emptyList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Select Directory", style = MaterialTheme.typography.titleMedium)
                Text(text = currentPath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        },
        text = {
            Column(modifier = Modifier.height(350.dp)) {
                if (currentFile.parentFile != null && currentPath != Environment.getExternalStorageDirectory().path) {
                    ListItem(
                        headlineContent = { Text(".. (Parent Folder)") },
                        leadingContent = { Text("⬅️") },
                        modifier = Modifier.clickable { currentFile.parentFile?.let { currentPath = it.absolutePath } }
                    )
                    HorizontalDivider()
                }
                LazyColumn {
                    items(folders) { folder ->
                        ListItem(
                            headlineContent = { Text(folder.name) },
                            leadingContent = { Text("📁") },
                            modifier = Modifier.clickable { currentPath = folder.absolutePath }
                        )
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onFolderSelected(currentPath) }) { Text("Select This Folder") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

suspend fun processPdfToCbz(
    files: List<File>,
    outputDir: String,
    format: String = "JPG",
    quality: Int = 85,
    onUpdate: (String, Float) -> Unit
): Int = withContext(Dispatchers.IO) {
    val outDir = File(outputDir)
    outDir.mkdirs()
    var count = 0

    for ((index, pdfFile) in files.withIndex()) {
        val totalProgress = index.toFloat() / files.size
        onUpdate("Opening ${pdfFile.name}...", totalProgress)

        val tempDir = File(outDir, ".temp_${pdfFile.nameWithoutExtension}")
        tempDir.mkdirs()

        try {
            val fileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val pdfRenderer = PdfRenderer(fileDescriptor)
            val pageCount = pdfRenderer.pageCount

            val isPng = format.equals("PNG", ignoreCase = true)
            val ext = if (isPng) "png" else "jpg"
            val compressFormat = if (isPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG

            for (i in 0 until pageCount) {
                val pageProgress = i.toFloat() / pageCount
                onUpdate("Rendering page ${i + 1}/$pageCount ($format)...", totalProgress + (pageProgress * 0.8f / files.size))

                val page = pdfRenderer.openPage(i)
                val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val imgFile = File(tempDir, String.format("page_%04d.%s", i + 1, ext))
                val outStream = FileOutputStream(imgFile)
                bitmap.compress(compressFormat, quality, outStream)
                outStream.flush()
                outStream.close()
                bitmap.recycle()
            }
            pdfRenderer.close()
            fileDescriptor.close()

            onUpdate("⚡ Rust Core: Packing ${pdfFile.nameWithoutExtension}.cbz...", totalProgress + (0.85f / files.size))
            val cbzFile = File(outDir, "${pdfFile.nameWithoutExtension}.cbz")
            val success = NativeEngine.packToCbz(tempDir.absolutePath, cbzFile.absolutePath)
            if (success) count++

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            tempDir.deleteRecursively()
        }
    }
    return@withContext count
}

suspend fun processCbzToPdf(files: List<File>, outputDir: String, onUpdate: (String, Float) -> Unit): Int = withContext(Dispatchers.IO) {
    val outDir = File(outputDir)
    outDir.mkdirs()
    var count = 0

    for ((index, cbz) in files.withIndex()) {
        val totalProgress = (index.toFloat() / files.size)
        onUpdate("Processing ${cbz.name}...", totalProgress)

        val tempDir = File(outDir, ".temp_${cbz.nameWithoutExtension}")
        tempDir.mkdirs()

        try {
            NativeEngine.extractCbz(cbz.absolutePath, tempDir.absolutePath)

            onUpdate("⚡ Rust Core: Packaging ${cbz.nameWithoutExtension}.pdf...", totalProgress + (0.5f / files.size))
            val pdfFile = File(outDir, "${cbz.nameWithoutExtension}.pdf")
            
            NativeEngine.onProgressUpdate = { msg, prog ->
                val dynamicProgress = totalProgress + (0.5f / files.size) + (prog * 0.5f / files.size)
                onUpdate("⚡ Rust Core: $msg", dynamicProgress)
            }
            
            val success = NativeEngine.imagesToPdf(tempDir.absolutePath, pdfFile.absolutePath)
            if (success) count++
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            tempDir.deleteRecursively()
        }
    }
    return@withContext count
}

@Composable
fun ConverterTheme(
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = androidx.compose.ui.platform.LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}