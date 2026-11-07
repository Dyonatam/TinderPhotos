package com.example.swipeclean

import android.os.Bundle
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
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
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

@Composable
fun App() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var photos by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var index by remember { mutableIntStateOf(0) }
    var started by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                photos = withContext(Dispatchers.IO) {
                    DocumentFile.fromTreeUri(ctx, uri)?.listFiles()
                        ?.filter { it.type?.startsWith("image/") == true }
                        ?.sortedBy { it.name } ?: emptyList()
                }
                index = 0
                started = true
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val photo = photos.getOrNull(index)
        if (photo == null) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (started) {
                    Text("Fim!", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(16.dp))
                }
                Button(onClick = { picker.launch(null) }) { Text("Selecionar pasta") }
            }
        } else {
            val offsetX = remember(photo.uri) { Animatable(0f) }
            val x = offsetX.value
            val tint = if (x > 0) Color.Red else Color.Green
            val alpha = (abs(x) / 800f).coerceIn(0f, 0.5f)

            Box(
                Modifier
                    .fillMaxSize()
                    .background(tint.copy(alpha = alpha))
                    .pointerInput(photo.uri) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                scope.launch {
                                    when {
                                        offsetX.value > 300f -> {
                                            offsetX.animateTo(2000f)
                                            withContext(Dispatchers.IO) { photo.delete() }
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
                    model = photo.uri,
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
        }
    }
}
