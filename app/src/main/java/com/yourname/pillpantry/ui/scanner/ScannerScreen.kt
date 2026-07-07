package com.yourname.pillpantry.ui.scanner

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.yourname.pillpantry.data.FirebaseRepository
import com.yourname.pillpantry.data.OpenFoodFactsRepository
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

private enum class ScanMode { GROCERY, VITAMIN }

@Composable
fun ScannerScreen(
    userId: String,
    repository: FirebaseRepository,
    offRepository: OpenFoodFactsRepository
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    var mode by remember { mutableStateOf(ScanMode.GROCERY) }
    var scanned by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    var pendingBarcode by remember { mutableStateOf<String?>(null) }
    var itemName by remember { mutableStateOf("") }
    var suggestedBrand by remember { mutableStateOf<String?>(null) }
    var lookupLoading by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    var dosageInput by remember { mutableStateOf("1") }
    var thresholdInput by remember { mutableStateOf("10") }

    fun resetScanState() {
        scanned = false
    }

    fun showSuccess(text: String) {
        message = text
        scope.launch {
            kotlinx.coroutines.delay(1500)
            message = null
            resetScanState()
        }
    }

    fun handleBarcode(barcode: String) {
        if (scanned) return
        scanned = true
        scope.launch {
            try {
                if (mode == ScanMode.GROCERY) {
                    val existing = repository.findGroceryByBarcode(userId, barcode)
                    if (existing != null) {
                        repository.incrementGroceryQuantity(userId, existing.id, 1)
                        showSuccess("Added 1 to \"${existing.name}\"")
                        return@launch
                    }
                } else {
                    val existingVitamin = repository.findVitaminByBarcode(userId, barcode)
                    if (existingVitamin != null) {
                        // This app tracks vitamin stock via "Take Dose" in the
                        // Pantry tab rather than by re-scanning, since pill
                        // bottles don't reliably re-scan to a fixed restock
                        // amount the way grocery items do.
                        showSuccess("\"${existingVitamin.name}\" already tracked — use Pantry tab to adjust pills")
                        return@launch
                    }
                }

                // New item — open the naming dialog, and for groceries try an
                // Open Food Facts lookup to pre-fill the name.
                pendingBarcode = barcode
                itemName = ""
                suggestedBrand = null
                dosageInput = "1"
                thresholdInput = "10"
                showDialog = true

                if (mode == ScanMode.GROCERY) {
                    lookupLoading = true
                    val result = offRepository.lookup(barcode)
                    lookupLoading = false
                    if (result != null) {
                        itemName = result.name
                        suggestedBrand = result.brand
                    }
                }
            } catch (e: Exception) {
                message = "Error: ${e.message}"
                resetScanState()
            }
        }
    }

    fun cancelDialog() {
        showDialog = false
        pendingBarcode = null
        itemName = ""
        suggestedBrand = null
        lookupLoading = false
        dosageInput = "1"
        thresholdInput = "10"
        resetScanState()
    }

    fun saveNewItem() {
        val barcode = pendingBarcode ?: return
        val name = itemName.trim()
        if (name.isEmpty()) return

        val dosage = dosageInput.toLongOrNull()
        val threshold = thresholdInput.toLongOrNull()
        if (mode == ScanMode.VITAMIN && (dosage == null || dosage <= 0 || threshold == null || threshold < 0)) {
            message = "Enter a valid dose amount and refill threshold."
            return
        }

        scope.launch {
            try {
                if (mode == ScanMode.GROCERY) {
                    repository.addGrocery(userId, name, barcode)
                } else {
                    repository.addVitamin(userId, name, barcode, dosage!!, threshold!!)
                }
                showDialog = false
                pendingBarcode = null
                itemName = ""
                suggestedBrand = null
                dosageInput = "1"
                thresholdInput = "10"
                showSuccess("Saved \"$name\"")
            } catch (e: Exception) {
                message = "Error: ${e.message}"
                resetScanState()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            CameraPreview(
                onBarcodeDetected = ::handleBarcode,
                isPaused = scanned
            )

            // Scan-area overlay
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(width = 260.dp, height = 160.dp)
                    .border(2.dp, Color(0xFF00E676), RoundedCornerShape(12.dp))
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("We need camera access to scan barcodes.")
                Spacer(Modifier.height(16.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Grant Permission")
                }
            }
        }

        // Mode toggle row
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ModeButton(
                label = "Scan as Grocery",
                selected = mode == ScanMode.GROCERY,
                modifier = Modifier.weight(1f)
            ) { mode = ScanMode.GROCERY }

            ModeButton(
                label = "Scan as Vitamin",
                selected = mode == ScanMode.VITAMIN,
                modifier = Modifier.weight(1f)
            ) { mode = ScanMode.VITAMIN }
        }

        message?.let {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(20.dp)
                    .fillMaxWidth(),
                color = Color(0xFF00796B),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    it,
                    color = Color.White,
                    modifier = Modifier.padding(16.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { cancelDialog() },
            title = { Text("New Item") },
            text = {
                Column {
                    Text(
                        "Barcode: ${pendingBarcode ?: ""}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))

                    if (lookupLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Looking up product…", style = MaterialTheme.typography.bodySmall)
                        }
                    } else if (suggestedBrand != null) {
                        Text(
                            "Found on Open Food Facts · $suggestedBrand",
                            color = Color(0xFF00796B),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = itemName,
                        onValueChange = { itemName = it },
                        label = { Text(if (mode == ScanMode.GROCERY) "e.g. Almond Milk" else "e.g. Vitamin D3") },
                        enabled = !lookupLoading,
                        singleLine = true
                    )

                    if (mode == ScanMode.VITAMIN) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                             OutlinedTextField(
                                value = pillsInput,
                                onValueChange = { pillsInput = it },
                                label = { Text("Pills per bottle") },
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = dosageInput,
                                onValueChange = { dosageInput = it },
                                label = { Text("Pills per dose") },
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = thresholdInput,
                                onValueChange = { thresholdInput = it },
                                label = { Text("Refill at") },
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { saveNewItem() }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { cancelDialog() }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ModeButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier,
        color = if (selected) Color(0xFF00796B) else Color.White.copy(alpha = 0.25f),
        shape = RoundedCornerShape(8.dp),
        onClick = onClick
    ) {
        Text(
            label,
            color = Color.White,
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun CameraPreview(
    onBarcodeDetected: (String) -> Unit,
    isPaused: Boolean
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(executor, BarcodeAnalyzer { barcode ->
                            if (!isPaused) onBarcodeDetected(barcode)
                        })
                    }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                } catch (e: Exception) {
                    // Camera binding can fail if the lifecycle is already
                    // destroyed; safe to ignore for this simple use case.
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        }
    )
}
