package com.eazz.h2hdr

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Transform
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eazz.h2hdr.core.HdrConverter
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        window.colorMode = ActivityInfo.COLOR_MODE_HDR

        setContent {
            H2HdrTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        H2HdrTopBar()
                    }
                ) { innerPadding ->
                    H2HdrMainScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun H2HdrTopBar() {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = "H2HDR",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "HLG HEIF 转 ISO Gainmap HDR",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
fun H2HdrMainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val converter = remember { HdrConverter(context) }
    val scope = rememberCoroutineScope()

    var sourceImageUri by remember { mutableStateOf<Uri?>(null) }
    var loadedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var convertedResult by remember { mutableStateOf<HdrConverter.ConversionResult?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            sourceImageUri = it
            convertedResult = null
            isProcessing = true
            scope.launch {
                try {
                    loadedBitmap = converter.loadHlgHeif(it)
                } catch (e: Exception) {
                    Toast.makeText(context, "加载 HEIF 失败: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isProcessing = false
                }
            }
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/jpeg")
    ) { destUri ->
        destUri?.let { uri ->
            convertedResult?.ultraHdrBitmap?.let { bmp ->
                scope.launch {
                    try {
                        context.contentResolver.openOutputStream(uri)?.let { stream ->
                            converter.saveUltraHdr(bmp, stream)
                            Toast.makeText(context, "已成功另存为 Ultra HDR 图像", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val currentDisplayBmp = convertedResult?.ultraHdrBitmap ?: loadedBitmap

                    if (currentDisplayBmp != null) {
                        ZoomableImage(bitmap = currentDisplayBmp)
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AddPhotoAlternate,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "选择 HLG HEIF 照片开始处理",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = isProcessing,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            AnimatedVisibility(visible = loadedBitmap != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text("格式: HEIF/HLG") },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                    if (convertedResult != null) {
                        SuggestionChip(
                            onClick = {},
                            label = { Text("ISO 21496-1 Gainmap 就绪") },
                            icon = {
                                Icon(
                                    Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        pickerLauncher.launch(arrayOf("image/heic", "image/heif", "image/*"))
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("打开图片")
                }

                Button(
                    onClick = {
                        loadedBitmap?.let { bmp ->
                            isProcessing = true
                            scope.launch {
                                convertedResult = converter.convertToGainmapHdr(bmp)
                                isProcessing = false
                            }
                        }
                    },
                    enabled = loadedBitmap != null && convertedResult == null && !isProcessing,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Rounded.Transform, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("一键转换")
                }
            }
        }

        AnimatedVisibility(
            visible = convertedResult != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(animationSpec = spring()),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
        ) {
            ExtendedFloatingActionButton(
                onClick = {
                    saveLauncher.launch("H2HDR_${System.currentTimeMillis()}.jpg")
                },
                icon = { Icon(Icons.Rounded.SaveAlt, contentDescription = null) },
                text = { Text("另存为 Ultra HDR") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
fun ZoomableImage(bitmap: Bitmap) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offset = if (scale > 1f) offset + pan else androidx.compose.ui.geometry.Offset.Zero
                }
            }
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "HDR 图像预览",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        )
    }
}

@Composable
fun H2HdrTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colorScheme = dynamicDarkColorScheme(context)
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
