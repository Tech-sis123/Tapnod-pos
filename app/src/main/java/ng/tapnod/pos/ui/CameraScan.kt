package ng.tapnod.pos.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import ng.tapnod.pos.PosViewModel
import ng.tapnod.pos.TapMode
import ng.tapnod.pos.UiState
import ng.tapnod.pos.util.CardScan
import ng.tapnod.pos.util.Money
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Card scanning with the back camera, for phones (or cards) where NFC doesn't work.
 * ML Kit reads the text on the card on the phone itself: nothing is uploaded.
 */
@Composable
fun CameraScanScreen(state: UiState, vm: PosViewModel, mode: TapMode) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        asked = true
    }
    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
    }

    var torch by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var hint by remember { mutableStateOf("Hold the front of the card inside the frame") }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(GAP)) {
        Tile(
            Modifier
                .fillMaxWidth()
                .weight(1f), color = TP.Ink, padding = 0.dp
        ) {
            if (granted) {
                CameraPreview(
                    onCamera = { camera = it },
                    onHit = { hit ->
                        when (hit) {
                            is CardScan.Hit.Number -> vm.onCameraCard(hit.pan, mode)
                            CardScan.Hit.CardSeen -> vm.onCameraCard(null, mode)
                        }
                    },
                    onHint = { hint = it }
                )
                // Card-shaped guide frame
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width * 0.86f
                    val h = w / 1.586f
                    val left = (size.width - w) / 2
                    val top = (size.height - h) / 2
                    drawRoundRect(
                        color = TP.Lime,
                        topLeft = Offset(left, top),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(28f, 28f),
                        style = Stroke(width = 6f)
                    )
                }
            } else {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (asked) "Camera is blocked" else "Allow the camera",
                        color = TP.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tapnod uses the camera only to see the card. Nothing is saved or uploaded.",
                        color = TP.InkMute, fontSize = 14.sp
                    )
                }
            }
            Column(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .background(TP.Ink.copy(alpha = 0.55f))
                    .padding(22.dp)
            ) {
                Eyebrow(
                    if (mode == TapMode.CHARGE) "Step 2 of 3 · Scan card" else "Setup · Scan to link",
                    color = TP.Lime
                )
                Spacer(Modifier.height(6.dp))
                Text(hint, color = TP.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(132.dp),
            horizontalArrangement = Arrangement.spacedBy(GAP)
        ) {
            Tile(
                Modifier
                    .weight(2f)
                    .fillMaxHeight(), color = TP.Lime
            ) {
                Eyebrow(if (mode == TapMode.CHARGE) "Amount" else "Linking to", Modifier.align(Alignment.TopStart), color = TP.Ink)
                Text(
                    if (mode == TapMode.CHARGE) Money.format(state.amountNaira) else (state.linkCustomer?.name ?: "Customer"),
                    Modifier.align(Alignment.BottomStart),
                    fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(GAP)
            ) {
                Tile(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    color = if (torch) TP.Lime else TP.Soft,
                    onClick = {
                        torch = !torch
                        camera?.cameraControl?.enableTorch(torch)
                    },
                    padding = 14.dp
                ) {
                    Text(
                        if (torch) "Light on" else "Light", Modifier.align(Alignment.BottomStart),
                        fontSize = 15.sp, fontWeight = FontWeight.Bold
                    )
                }
                Tile(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    color = TP.Line,
                    onClick = { vm.cancelTap() },
                    padding = 14.dp
                ) {
                    Text("Cancel", Modifier.align(Alignment.BottomStart), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraPreview(
    onCamera: (Camera) -> Unit,
    onHit: (CardScan.Hit) -> Unit,
    onHint: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val done = remember { AtomicBoolean(false) }

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        // "Card seen" without a number must show up in a few frames in a row before it counts.
        var seenStreak = 0

        providerFuture.addListener({
            val p = providerFuture.get()
            provider = p
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { proxy ->
                val media = proxy.image
                if (media == null || done.get()) {
                    proxy.close()
                    return@setAnalyzer
                }
                val input = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                recognizer.process(input)
                    .addOnSuccessListener { result ->
                        when (val hit = CardScan.find(result.text)) {
                            is CardScan.Hit.Number -> if (done.compareAndSet(false, true)) onHit(hit)
                            CardScan.Hit.CardSeen -> {
                                seenStreak++
                                onHint("Card found. Hold still…")
                                if (seenStreak >= 3 && done.compareAndSet(false, true)) onHit(hit)
                            }
                            null -> {
                                seenStreak = 0
                                onHint("Hold the front of the card inside the frame")
                            }
                        }
                    }
                    .addOnCompleteListener { proxy.close() }
            }
            runCatching {
                p.unbindAll()
                val cam = p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                onCamera(cam)
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            provider?.unbindAll()
            recognizer.close()
            executor.shutdown()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(22.dp))
    )
}
