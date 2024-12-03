import android.graphics.Bitmap
import android.util.Log
import android.graphics.Canvas as AndroidCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.graphics.Paint as AndroidPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import com.example.myapplication.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DrawingScreen() {
    // State to track all drawn paths
    val paths = remember { mutableStateListOf<Path>() }

    // State to track the current path being drawn
    val currentPath = remember { mutableStateOf<Path?>(null) }

    // State to track Gemini's guess
    var geminiGuess by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val coroutineScope = rememberCoroutineScope()

    // Gemini API setup
    val generativeModel = remember {
        GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = BuildConfig.apiKey
        )
    }

    // Function to send bitmap to Gemini
    fun sendBitmapToGemini(bitmap: Bitmap) {
        isLoading = true
        coroutineScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    generativeModel.generateContent(
                        content {
                            image(bitmap)
                            text("What is being drawn in this image? Guess the object or scene.")
                        }
                    )
                }
                geminiGuess = response.text ?: "No guess available"
            } catch (e: Exception) {
                geminiGuess = "Error: ${e.localizedMessage}"
            } finally {
                isLoading = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xfff4edde))
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Drawing Canvas
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                Log.d("GestureDebug", "Drag started at: $offset")
                                val path = Path()
                                path.moveTo(offset.x, offset.y)
                                currentPath.value = path
                            },
                            onDrag = { change, _ ->
                                currentPath.value?.lineTo(change.position.x, change.position.y)
                                currentPath.value = currentPath.value
                            },
                            onDragEnd = {
                                currentPath.value?.let { newPath ->
                                    paths.add(newPath)

                                    // Create bitmap for Gemini
                                    val bitmap = Bitmap.createBitmap(
                                        (configuration.screenWidthDp * context.resources.displayMetrics.density).toInt(),
                                        (configuration.screenHeightDp * context.resources.displayMetrics.density).toInt(),
                                        Bitmap.Config.ARGB_8888
                                    )
                                    val canvas = AndroidCanvas(bitmap)

                                    // Fill with white background
                                    canvas.drawColor(android.graphics.Color.WHITE)

                                    // Prepare paint for drawing
                                    val paint = AndroidPaint().apply {
                                        color = android.graphics.Color.BLACK
                                        style = AndroidPaint.Style.STROKE
                                        strokeWidth = 5f
                                    }

                                    // Draw all paths onto the bitmap
                                    paths.forEach { path ->
                                        canvas.drawPath(path.asAndroidPath(), paint)
                                    }

                                    // Send bitmap to Gemini
                                    sendBitmapToGemini(bitmap)
                                }

                                currentPath.value = null
                            }
                        )
                    }
            ) {
                // Draw completed paths
                paths.forEach { path ->
                    drawPath(
                        path = path,
                        color = Color.Black,
                        style = Stroke(width = 5f)
                    )
                }

                // Draw current path
                currentPath.value?.let { path ->
                    drawPath(
                        path = path,
                        color = Color.Black,
                        style = Stroke(width = 5f)
                    )
                }
            }

            // Display Gemini's guess
            Text(
                text = if (isLoading) "Loading..." else "Gemini's Guess: $geminiGuess",
                color = Color.Black,
                modifier = Modifier.padding(16.dp)
            )

            Button(
                onClick = {
                    paths.clear()
                    currentPath.value = null
                    geminiGuess = ""
                }
            ) {
                Text("Reset Canvas")
            }
        }
    }
}