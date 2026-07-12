package com.ibanimation.converter

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
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
            MaterialTheme {
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

// Translates Android's content:// URIs into standard Linux paths for Rust
fun getPathFromUri(uri: Uri): String {
    val decodedPath = Uri.decode(uri.toString())
    if (decodedPath.contains("primary:")) {
        val subPath = decodedPath.substringAfter("primary:")
        return "/storage/emulated/0/$subPath"
    }
    return "/storage/emulated/0/Download" // Fallback default
}

@Composable
fun ConverterScreen() {
    val scope = rememberCoroutineScope()
    var inputPath by remember { mutableStateOf(Environment.getExternalStorageDirectory().path + "/Download") }
    var outputPath by remember { mutableStateOf(Environment.getExternalStorageDirectory().path + "/Download") }
    var statusText by remember { mutableStateOf("Ready to process files bro!") }
    var isProcessing by remember { mutableStateOf(false) }

    // Folder Picker Launchers
    val inputLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { inputPath = getPathFromUri(it) }
    }

    val outputLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { outputPath = getPathFromUri(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "PDF <-> CBZ Engine", style = MaterialTheme.typography.headlineLarge)
        Text(text = "Rust + M3 Powered", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        
        Spacer(modifier = Modifier.height(32.dp))

        // Input Path Row with Picker Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputPath,
                onValueChange = { inputPath = it },
                label = { Text("Input Folder") },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { inputLauncher.launch(null) },
                modifier = Modifier.padding(top = 6.dp)
            ) {
                Text("📁")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Output Path Row with Picker Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = outputPath,
                onValueChange = { outputPath = it },
                label = { Text("Output Folder") },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { outputLauncher.launch(null) },
                modifier = Modifier.padding(top = 6.dp)
            ) {
                Text("📁")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (isProcessing) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = {
                    scope.launch {
                        isProcessing = true
                        statusText = "Converting PDF to CBZ..."
                        val count = processPdfToCbz(inputPath, outputPath)
                        statusText = "Done! Processed $count PDF files."
                        isProcessing = false
                    }
                },
                enabled = !isProcessing
            ) {
                Text("PDF ➔ CBZ")
            }

            Button(
                onClick = {
                    scope.launch {
                        isProcessing = true
                        statusText = "Converting CBZ to PDF..."
                        val count = processCbzToPdf(inputPath, outputPath)
                        statusText = "Done! Processed $count CBZ files."
                        isProcessing = false
                    }
                },
                enabled = !isProcessing
            ) {
                Text("CBZ ➔ PDF")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Text(
                text = statusText,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

suspend fun processPdfToCbz(inputDir: String, outputDir: String): Int = withContext(Dispatchers.IO) {
    val inDir = File(inputDir)
    val outDir = File(outputDir)
    if (!inDir.exists()) return@withContext 0
    outDir.mkdirs()

    var count = 0
    val pdfFiles = inDir.listFiles { _, name -> name.endsWith(".pdf", ignoreCase = true) } ?: return@withContext 0

    for (pdf in pdfFiles) {
        val tempDir = File(outDir, ".temp_${pdf.nameWithoutExtension}")
        tempDir.mkdirs()

        try {
            val fileDescriptor = android.os.ParcelFileDescriptor.open(pdf, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fileDescriptor)

            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                
                val imgFile = File(tempDir, String.format("page-%04d.jpg", i + 1))
                val outStream = FileOutputStream(imgFile)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outStream)
                outStream.close()
                
                bitmap.recycle()
                page.close()
            }
            renderer.close()
            fileDescriptor.close()

            val cbzFile = File(outDir, "${pdf.nameWithoutExtension}.cbz")
            NativeEngine.packToCbz(tempDir.absolutePath, cbzFile.absolutePath)
            count++
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            tempDir.deleteRecursively()
        }
    }
    return@withContext count
}

suspend fun processCbzToPdf(inputDir: String, outputDir: String): Int = withContext(Dispatchers.IO) {
    val inDir = File(inputDir)
    val outDir = File(outputDir)
    if (!inDir.exists()) return@withContext 0
    outDir.mkdirs()

    var count = 0
    val cbzFiles = inDir.listFiles { _, name -> name.endsWith(".cbz", ignoreCase = true) } ?: return@withContext 0

    for (cbz in cbzFiles) {
        val tempDir = File(outDir, ".temp_${cbz.nameWithoutExtension}")
        tempDir.mkdirs()

        try {
            NativeEngine.extractCbz(cbz.absolutePath, tempDir.absolutePath)

            val imageFiles = tempDir.walkTopDown()
                .filter { it.isFile && (it.extension.equals("jpg", true) || it.extension.equals("png", true)) }
                .sortedBy { it.name }
                .toList()

            if (imageFiles.isNotEmpty()) {
                val pdfDocument = PdfDocument()
                for ((index, imgFile) in imageFiles.withIndex()) {
                    val bitmap = BitmapFactory.decodeFile(imgFile.absolutePath)
                    val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    pdfDocument.finishPage(page)
                    bitmap.recycle()
                }
                val pdfFile = File(outDir, "${cbz.nameWithoutExtension}.pdf")
                val outStream = FileOutputStream(pdfFile)
                pdfDocument.writeTo(outStream)
                pdfDocument.close()
                outStream.close()
                count++
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            tempDir.deleteRecursively()
        }
    }
    return@withContext count
}
