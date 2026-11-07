package com.example.swipeclean

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) { App() }
        }
    }
}

class Scan(val uris: List<Uri>, val bytes: Long)

// Lê só os metadados (nome, tipo, tamanho) em UMA consulta: não abre nenhuma foto
fun scanFolder(ctx: Context, tree: Uri): Scan {
    val docId = DocumentsContract.getTreeDocumentId(tree)
    val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
    val items = ArrayList<Pair<String, Uri>>()
    var bytes = 0L
    ctx.contentResolver.query(
        children,
        arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        ),
        null, null, null
    )?.use { c ->
        while (c.moveToNext()) {
            if (c.getString(2)?.startsWith("image/") == true) {
                items.add(c.getString(1) to DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)))
                if (!c.isNull(3)) bytes += c.getLong(3)
            }
        }
    }
    return Scan(items.sortedBy { it.first }.map { it.second }, bytes)
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var photos by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var index by remember { mutableIntStateOf(0) }
    var started by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Scan?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                loading = true
                error = null
                try {
                    pending = withContext(Dispatchers.IO) { scanFolder(ctx, uri) }
                } catch (e: Exception) {
                    pending = null
                    error = e.message ?: "Erro ao ler a pasta"
                }
                loading = false
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val photo = photos.getOrNull(index)
        val scan = pending
        if (loading) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text("Contando fotos...")
            }
        } else if (photo != null) {
            val offsetX = remember(photo) { Animatable(0f) }
            val x = offsetX.value
            val tint = if (x > 0) Color.Red else Color.Green
            val alpha = (abs(x) / 800f).coerceIn(0f, 0.5f)

            Box(
                Modifier
                    .fillMaxSize()
                    .background(tint.copy(alpha = alpha))
                    .pointerInput(photo) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                scope.launch {
                                    when {
                                        offsetX.value > 300f -> {
                                            offsetX.animateTo(2000f)
                                            withContext(Dispatchers.IO) {
                                                try {
                                                    DocumentsContract.deleteDocument(ctx.contentResolver, photo)
                                                } catch (e: Exception) { }
                                            }
                                            photos = photos.toMutableList().also { it.removeAt(index) }
                                        }
                                        offsetX.value < -300f -> {
                                            offsetX.animateTo(-2000f)
                                            index++
                                        }
                                        else -> offsetX.animateTo(0f)
                                    }
                                }
                            },
                            onDragCancel = { scope.launch { offsetX.animateTo(0f) } },
                            onHorizontalDrag = { change, dx ->
                                change.consume()
                                scope.launch { offsetX.snapTo(offsetX.value + dx) }
                            }
                        )
                    }
            ) {
                AsyncImage(
                    model = photo,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = offsetX.value
                            rotationZ = offsetX.value / 60f
                        }
                )
                Text(
                    "${index + 1}/${photos.size}",
                    modifier = Modifier.align(Alignment.TopCenter).padding(16.dp)
                )
            }
        } else if (scan != null) {
            // Tela de confirmação: mostra quantas fotos tem antes de começar
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (scan.uris.isEmpty()) {
                    Text("Nenhuma foto nesta pasta", style = MaterialTheme.typography.headlineSmall)
                } else {
                    Text(
                        "${scan.uris.size} fotos",
                        style = MaterialTheme.typography.headlineLarge
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(Formatter.formatFileSize(ctx, scan.bytes))
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Direita apaga (permanente)\nEsquerda mantém",
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = {
                        photos = scan.uris
                        index = 0
                        started = true
                        pending = null
                    }) { Text("Começar") }
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedButton(onClick = { picker.launch(null) }) { Text("Escolher outra pasta") }
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val msg = error ?: if (started) "Fim!" else null
                if (msg != null) {
                    Text(msg, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(16.dp))
                }
                Button(onClick = { picker.launch(null) }) { Text("Selecionar pasta") }
            }
        }
    }
}
