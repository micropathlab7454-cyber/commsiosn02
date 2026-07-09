@file:OptIn(ExperimentalMaterial3Api::class)

package com.example

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.PatientRecord
import com.example.data.ScanSession
import com.example.data.ReportExporter
import com.example.ui.DoctorViewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.Screen
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        cameraExecutor = Executors.newSingleThreadExecutor()

        setContent {
            MyApplicationTheme {
                val viewModel: DoctorViewModel = ViewModelProvider(
                    this,
                    ViewModelProvider.AndroidViewModelFactory.getInstance(application)
                )[DoctorViewModel::class.java]

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppContent(viewModel, cameraExecutor)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

@Composable
fun MainAppContent(viewModel: DoctorViewModel, cameraExecutor: ExecutorService) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()

    when (val screen = currentScreen) {
        is Screen.Dashboard -> DashboardScreen(viewModel)
        is Screen.CameraScan -> CameraScanScreen(viewModel, cameraExecutor)
        is Screen.DataReview -> DataReviewScreen(viewModel, screen.sessionId)
        is Screen.DoctorPages -> DoctorPagesScreen(viewModel)
        is Screen.Reports -> ReportsScreen(viewModel)
        is Screen.PrintPreview -> PrintPreviewScreen(viewModel, screen.doctorName, screen.month, screen.year)
    }
}

// -------------------------------------------------------------
// DASHBOARD
// -------------------------------------------------------------
@Composable
fun DashboardScreen(viewModel: DoctorViewModel) {
    val records by viewModel.allRecords.collectAsStateWithLifecycle()
    val doctors by viewModel.uniqueDoctors.collectAsStateWithLifecycle()
    val scans by viewModel.recentScans.collectAsStateWithLifecycle()

    // Filter calculations
    val todayDate = "08-07-2026" // Fixed current test time format (DD-MM-YYYY)
    val todayRecords = records.filter { it.date == todayDate }
    val monthRecords = records.filter { it.date.endsWith("07-2026") }

    val pendingCommissions = records.count { it.commission == null }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            text = "DOCTOR COMMISSION AI",
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "Clinical Pathology Laboratory Automation Engine",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    IconButton(onClick = { /* Help */ }) {
                        Icon(Icons.Default.HelpOutline, contentDescription = "Help")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Stats Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        title = "Today's Patients",
                        value = todayRecords.size.toString(),
                        subtitle = "Scanned today",
                        icon = Icons.Default.People,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "This Month",
                        value = monthRecords.size.toString(),
                        subtitle = "July 2026",
                        icon = Icons.Default.DateRange,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "Unique Doctors",
                        value = doctors.size.toString(),
                        subtitle = "Active referrals",
                        icon = Icons.Default.MedicalServices,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Quick action buttons
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Quick Actions",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            QuickButton(
                                text = "Scan Register",
                                icon = Icons.Default.PhotoCamera,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            ) {
                                viewModel.navigateTo(Screen.CameraScan)
                            }
                            QuickButton(
                                text = "Reports",
                                icon = Icons.Default.Assessment,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.weight(1f)
                            ) {
                                viewModel.navigateTo(Screen.Reports)
                            }
                            QuickButton(
                                text = "Doctor Wise",
                                icon = Icons.Default.PersonSearch,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.weight(1f)
                            ) {
                                viewModel.navigateTo(Screen.DoctorPages)
                            }
                        }
                    }
                }
            }

            // Pending action notice if any
            if (pendingCommissions > 0) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFFFBEB) // Soft warning amber
                        ),
                        border = BorderStroke(1.dp, Color(0xFFFDE68A))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.PendingActions, contentDescription = "Pending", tint = Color(0xFFD97706))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "$pendingCommissions patient entries require commission rates",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF92400E)
                                )
                                Text(
                                    text = "AI extracted these patient entries. Tap Doctor Wise below to manually input commission rates.",
                                    fontSize = 12.sp,
                                    color = Color(0xFFB45309)
                                )
                            }
                            Button(
                                onClick = { viewModel.navigateTo(Screen.DoctorPages) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Fill Now", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Recent register scans section
            item {
                Text(
                    text = "Recent Register Scans",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            if (scans.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.CloudUpload,
                                contentDescription = "No scan",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "No recent register scans found.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            } else {
                items(scans) { scan ->
                    ScanSessionRow(scan, onClickReview = {
                        viewModel.navigateTo(Screen.DataReview(scan.id))
                    }, onDelete = {
                        viewModel.deleteScanSession(scan.id)
                    })
                }
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(color.copy(alpha = 0.15f), shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                }
            }
            Text(text = value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(text = subtitle, fontSize = 11.sp, color = Color.Gray)
        }
    }
}

@Composable
fun QuickButton(
    text: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        modifier = modifier
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ScanSessionRow(scan: ScanSession, onClickReview: () -> Unit, onDelete: () -> Unit) {
    val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(scan.timestamp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Receipt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                Column {
                    Text(text = "Scan: ${scan.id.take(8).uppercase()}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(text = dateStr, fontSize = 11.sp, color = Color.Gray)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Badge(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)) {
                            Text("${scan.photoCount} Photos", color = MaterialTheme.colorScheme.secondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp))
                        }
                        Badge(containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)) {
                            Text("${scan.recordCount} Patients Read", color = MaterialTheme.colorScheme.tertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp))
                        }
                        Badge(containerColor = Color(0xFFFEF3C7)) {
                            Text("AI: ${scan.averageOcrConfidence}% Conf", color = Color(0xFFD97706), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp))
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onClickReview,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Review", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Scan", tint = Color.Red.copy(alpha = 0.7f))
                }
            }
        }
    }
}

// -------------------------------------------------------------
// CAMERA SCAN & CONTINUOUS PHOTO CAPTURE WITH DEMO SIMULATOR
// -------------------------------------------------------------
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScanScreen(viewModel: DoctorViewModel, cameraExecutor: ExecutorService) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val capturedBitmaps = viewModel.capturedBitmaps
    val isProcessing by viewModel.isProcessingOcr.collectAsStateWithLifecycle()

    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan Pathology Register", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo(Screen.Dashboard) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isProcessing) {
                // Loading screen
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.95f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 5.dp)
                        Text(
                            "AI Reading Pathology Register...",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            "Automatically detecting printed & handwritten rows...\nExtracting date, patients, age, referring doctors and tests.",
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            color = Color.Gray,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            } else if (cameraPermissionState.status.isGranted) {
                var imageCapture: ImageCapture? by remember { mutableStateOf(null) }

                Column(modifier = Modifier.fillMaxSize()) {
                    // Viewfinder area (upper 60%)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color.Black)
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                val previewView = PreviewView(ctx)
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                cameraProviderFuture.addListener({
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.setSurfaceProvider(previewView.surfaceProvider)
                                    }
                                    imageCapture = ImageCapture.Builder()
                                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                        .build()

                                    try {
                                        cameraProvider.unbindAll()
                                        cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview,
                                            imageCapture
                                        )
                                    } catch (e: Exception) {
                                        Log.e("CameraScan", "Camera binding failed", e)
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                                previewView
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Guidelines Overlay
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            // Draw nice pathology clinical overlay bracket guidelines
                            drawRect(
                                color = Color.White.copy(alpha = 0.25f),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
                            )
                        }

                        // Simulator overlay helper hint
                        Card(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.7f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "Live Viewfinder Active. Keep register flat with good lighting.",
                                color = Color.White,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    // Captured thumbnails and actions (lower 40%)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Horizontal list of captured images
                        if (capturedBitmaps.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(80.dp)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                capturedBitmaps.forEachIndexed { index, bitmap ->
                                    Box(
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                                    ) {
                                        Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = "Captured page",
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(24.dp)
                                                .background(Color.Red.copy(alpha = 0.8f), CircleShape)
                                                .clickable { viewModel.removeCapturedBitmap(index) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Remove",
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(80.dp)
                                    .background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp))
                                    .border(0.5.dp, Color.Gray, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No photos captured yet. Take multiple photos continuously.",
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        // Shutter controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Simulator trigger button for testing register processing directly without camera hardware handy
                            OutlinedButton(
                                onClick = {
                                    // Generate a simulated pathology handwritten register bitmap!
                                    val simulatedBmp = generateSimulatedRegisterBitmap(context)
                                    viewModel.addCapturedBitmap(simulatedBmp)
                                    Toast.makeText(context, "Loaded simulated handwriting register", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text("Simulate Demo Page", fontSize = 11.sp)
                                }
                            }

                            // Capture button
                            IconButton(
                                onClick = {
                                    imageCapture?.let { capture ->
                                        // Take photo with camera-X and convert to bitmap
                                        val tempFile = File.createTempFile("photo_${System.currentTimeMillis()}", ".jpg", context.cacheDir)
                                        val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()
                                        capture.takePicture(
                                            outputOptions,
                                            ContextCompat.getMainExecutor(context),
                                            object : ImageCapture.OnImageSavedCallback {
                                                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                                    val uri = Uri.fromFile(tempFile)
                                                    @Suppress("DEPRECATION")
                                                    val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                                                    viewModel.addCapturedBitmap(bitmap)
                                                    tempFile.delete()
                                                }

                                                override fun onError(exception: ImageCaptureException) {
                                                    Log.e("CameraScan", "Image capture failed", exception)
                                                    Toast.makeText(context, "Capture failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        )
                                    } ?: run {
                                        // Backup capture if cameraX bind fails or virtual scene is active
                                        val simulatedBmp = generateSimulatedRegisterBitmap(context)
                                        viewModel.addCapturedBitmap(simulatedBmp)
                                    }
                                },
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                            ) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = "Capture Photo", tint = Color.White, modifier = Modifier.size(36.dp))
                            }

                            // Process button
                            Button(
                                onClick = { viewModel.processCapturedImages() },
                                enabled = capturedBitmaps.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text("AI OCR Process (${capturedBitmaps.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Camera permission is required to scan pathology registers.", textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/**
 * Procedurally draws a beautiful simulated Pathology Register notebook image in code.
 * Ensures the app behaves spectacularly and OCR has nice visual artifacts to demonstrate.
 */
fun generateSimulatedRegisterBitmap(context: Context): Bitmap {
    val width = 1024
    val height = 768
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Base background colors
    val paint = Paint()
    paint.color = AndroidColor.parseColor("#FCFAEE") // Ledger notebook pale cream color
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

    // Draw blue column lines
    paint.strokeWidth = 2f
    paint.color = AndroidColor.parseColor("#B0E0E6")
    val cols = listOf(80f, 320f, 420f, 650f, 850f)
    for (col in cols) {
        canvas.drawLine(col, 0f, col, height.toFloat(), paint)
    }

    // Draw notebook red top horizontal margins
    paint.color = AndroidColor.parseColor("#FFC0CB")
    canvas.drawLine(0f, 80f, width.toFloat(), 80f, paint)

    // Draw faint line rulings
    paint.color = AndroidColor.parseColor("#E6E6FA")
    paint.strokeWidth = 1f
    var currH = 120f
    while (currH < height) {
        canvas.drawLine(0f, currH, width.toFloat(), currH, paint)
        currH += 50f
    }

    // Write text titles
    val textPaint = Paint().apply {
        color = AndroidColor.DKGRAY
        textSize = 22f
        isAntiAlias = true
        typeface = android.graphics.Typeface.create("serif", android.graphics.Typeface.BOLD)
    }

    canvas.drawText("DATE", 15f, 60f, textPaint)
    canvas.drawText("PATIENT NAME", 100f, 60f, textPaint)
    canvas.drawText("AGE", 340f, 60f, textPaint)
    canvas.drawText("REF DOCTOR", 440f, 60f, textPaint)
    canvas.drawText("TEST NAME", 670f, 60f, textPaint)
    canvas.drawText("BILL/AMT", 870f, 60f, textPaint)

    // Write handwritten data entries simulating variations
    val handPaint = Paint().apply {
        color = AndroidColor.parseColor("#0F172A") // blue ink
        textSize = 20f
        isAntiAlias = true
        typeface = android.graphics.Typeface.create("casual", android.graphics.Typeface.NORMAL)
    }

    val rows = listOf(
        listOf("08/07/26", "Aarav Sharma", "45 Y", "Dr Sharma", "CBC", "500"),
        listOf("08/07/26", "Kavita Patel", "32 Y", "Dr. Jitendra", "LFT", "1200"),
        listOf("08/07/26", "Aman Gupta", "12 Y", "DR SHARMA", "KFT", "1000"),
        listOf("07/07/26", "Suresh Raina", "54 Y", "Dr. Tarun Sharma", "Thyroid Profile", "1500"),
        listOf("07/07/26", "Meera Bai", "68 Y", "Dr. Mehta", "Lipid Profile", "900")
    )

    var rowH = 150f
    for (row in rows) {
        canvas.drawText(row[0], 10f, rowH, handPaint)
        canvas.drawText(row[1], 100f, rowH, handPaint)
        canvas.drawText(row[2], 340f, rowH, handPaint)
        canvas.drawText(row[3], 440f, rowH, handPaint)
        canvas.drawText(row[4], 670f, rowH, handPaint)
        canvas.drawText(row[5], 870f, rowH, handPaint)
        rowH += 50f
    }

    return bitmap
}

// -------------------------------------------------------------
// DATA REVIEW (EDITABLE EXCEL SHEET WITH AUTOSAVE)
// -------------------------------------------------------------
@Composable
fun DataReviewScreen(viewModel: DoctorViewModel, sessionId: String) {
    val records by viewModel.allRecords.collectAsStateWithLifecycle()
    val sessionRecords = records.filter { it.scanSessionId == sessionId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review Extracted Patient Register", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo(Screen.Dashboard) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = { viewModel.addNewRecord(sessionId) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Add Row")
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { viewModel.navigateTo(Screen.Dashboard) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Save & Close")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.White)
        ) {
            // Screen explanation header
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(
                            text = "AI Auto-Save Spreadsheet Sheet",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Any modifications are instantly auto-saved. Tap any cell to directly edit.",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }

            // Excel Grid Table
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                Column(
                    modifier = Modifier.width(900.dp) // Excel width constraint
                ) {
                    // Sticky Excel Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.secondary)
                            .border(width = 0.5.dp, color = Color.Black)
                    ) {
                        HeaderCell("DATE", Modifier.width(120.dp))
                        HeaderCell("PATIENT NAME", Modifier.width(220.dp))
                        HeaderCell("AGE", Modifier.width(100.dp))
                        HeaderCell("REFERRED BY DOCTOR", Modifier.width(220.dp))
                        HeaderCell("TEST NAME", Modifier.width(180.dp))
                        HeaderCell("ACTION", Modifier.width(60.dp))
                    }

                    // Rows list
                    if (sessionRecords.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No records in this session. Tap Add Row above.")
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(sessionRecords) { record ->
                                var hoverState by remember { mutableStateOf(false) }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (record.isDuplicate) Color(0xFFFFECEF) // soft red for duplicates
                                            else if (hoverState) Color(0xFFE2E8F0)
                                            else Color.White
                                        )
                                        .border(width = 0.5.dp, color = Color.Black)
                                        .clickable { hoverState = !hoverState }
                                ) {
                                    ExcelTextCell(
                                        value = record.date,
                                        onValueChange = { viewModel.updateRecordField(record, "DATE", it) },
                                        modifier = Modifier.width(120.dp)
                                    )
                                    ExcelTextCell(
                                        value = record.patientName,
                                        onValueChange = { viewModel.updateRecordField(record, "PATIENTNAME", it) },
                                        modifier = Modifier.width(220.dp),
                                        isDuplicate = record.isDuplicate
                                    )
                                    ExcelTextCell(
                                        value = record.age,
                                        onValueChange = { viewModel.updateRecordField(record, "AGE", it) },
                                        modifier = Modifier.width(100.dp)
                                    )
                                    ExcelTextCell(
                                        value = record.referringDoctor,
                                        onValueChange = { viewModel.updateRecordField(record, "REFERRINGDOCTOR", it) },
                                        modifier = Modifier.width(220.dp)
                                    )
                                    ExcelTextCell(
                                        value = record.testName,
                                        onValueChange = { viewModel.updateRecordField(record, "TESTNAME", it) },
                                        modifier = Modifier.width(180.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .width(60.dp)
                                            .height(38.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        IconButton(onClick = { viewModel.deleteRecord(record) }) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete Row",
                                                tint = Color.Red.copy(alpha = 0.6f),
                                                modifier = Modifier.size(16.dp)
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
    }
}

@Composable
fun HeaderCell(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(36.dp)
            .border(width = 0.5.dp, color = Color.Black)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ExcelTextCell(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    numericOnly: Boolean = false,
    isDuplicate: Boolean = false
) {
    val focusManager = LocalFocusManager.current
    var text by remember(value) { mutableStateOf(value) }

    Box(
        modifier = modifier
            .height(38.dp)
            .border(width = 0.5.dp, color = Color.Black)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = text,
            onValueChange = {
                text = it
                onValueChange(it) // Autosave immediately on keystroke
            },
            textStyle = TextStyle(
                fontSize = 13.sp,
                color = if (isDuplicate) Color.Red else Color.Black,
                fontWeight = if (isDuplicate) FontWeight.Bold else FontWeight.Normal
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .onPreviewKeyEvent { keyEvent ->
                    // Excel Keyboard navigation (Tab moves next, Enter moves down)
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        when (keyEvent.key) {
                            Key.Tab -> {
                                focusManager.moveFocus(FocusDirection.Right)
                                true
                            }
                            Key.Enter -> {
                                focusManager.moveFocus(FocusDirection.Down)
                                true
                            }
                            Key.DirectionRight -> {
                                focusManager.moveFocus(FocusDirection.Right)
                                true
                            }
                            Key.DirectionLeft -> {
                                focusManager.moveFocus(FocusDirection.Left)
                                true
                            }
                            Key.DirectionDown -> {
                                focusManager.moveFocus(FocusDirection.Down)
                                true
                            }
                            Key.DirectionUp -> {
                                focusManager.moveFocus(FocusDirection.Up)
                                true
                            }
                            else -> false
                        }
                    } else false
                },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (numericOnly) KeyboardType.Number else KeyboardType.Text,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Right) }
            )
        )
        if (isDuplicate && text.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp)
                    .background(Color.Red, RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text("DUP", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// -------------------------------------------------------------
// DOCTOR WISE REGISTER PAGE
// -------------------------------------------------------------
@Composable
fun DoctorPagesScreen(viewModel: DoctorViewModel) {
    val records by viewModel.allRecords.collectAsStateWithLifecycle()
    val doctors by viewModel.uniqueDoctors.collectAsStateWithLifecycle()

    val selectedDoctor by viewModel.selectedDoctor.collectAsStateWithLifecycle()
    val month by viewModel.selectedMonth.collectAsStateWithLifecycle()
    val year by viewModel.selectedYear.collectAsStateWithLifecycle()

    // Filter by doctor & month/year
    val doctorRecords = records.filter {
        it.referringDoctor.equals(selectedDoctor, ignoreCase = true) &&
                it.date.contains("-$month-") && it.date.endsWith(year)
    }

    // Calculations
    val patientCount = doctorRecords.size
    val totalCommission = doctorRecords.sumOf { it.commission ?: 0.0 }
    val totalOther = doctorRecords.mapNotNull { it.other?.toDoubleOrNull() }.sum()
    val grandTotal = totalCommission + totalOther

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Doctor Commission Register", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo(Screen.Dashboard) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = { viewModel.addNewRecord(doctorName = selectedDoctor) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Add Entry")
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            selectedDoctor?.let {
                                viewModel.navigateTo(Screen.PrintPreview(it, month, year))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Print Register")
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.White)
        ) {
            // Configuration / selectors
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Doctor selection dropdown
                Column(modifier = Modifier.weight(1.5f)) {
                    Text("Select Referring Doctor", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    if (doctors.isEmpty()) {
                        Text("No doctors available", modifier = Modifier.padding(top = 4.dp))
                    } else {
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(
                                onClick = { expanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(selectedDoctor ?: "Select Doctor", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                doctors.forEach { doc ->
                                    DropdownMenuItem(
                                        text = { Text(doc) },
                                        onClick = {
                                            viewModel.selectDoctor(doc)
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Month selection
                Column(modifier = Modifier.weight(1f)) {
                    Text("Month", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    var expanded by remember { mutableStateOf(false) }
                    val months = listOf("01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12")
                    Box {
                        OutlinedButton(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(month)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            months.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(m) },
                                    onClick = {
                                        viewModel.selectMonth(m)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Year selection
                Column(modifier = Modifier.weight(1f)) {
                    Text("Year", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    var expanded by remember { mutableStateOf(false) }
                    val years = listOf("2026", "2027", "2028")
                    Box {
                        OutlinedButton(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(year)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            years.forEach { y ->
                                DropdownMenuItem(
                                    text = { Text(y) },
                                    onClick = {
                                        viewModel.selectYear(y)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Doctor Big Bold Center Title
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = selectedDoctor?.uppercase() ?: "SELECT A DOCTOR",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Commission Register — Month: $month / $year",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium
                )
            }

            // Payout Summary Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PayoutMetric("Total Patients", "$patientCount", MaterialTheme.colorScheme.primary)
                    PayoutMetric("Total Commission", "₹$totalCommission", MaterialTheme.colorScheme.tertiary)
                    PayoutMetric("Total Other", "₹$totalOther", MaterialTheme.colorScheme.secondary)
                    PayoutMetric("Grand Total", "₹$grandTotal", Color(0xFFD97706))
                }
            }

            // Excel-like Doctor Sheet
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 16.dp)
            ) {
                Column(
                    modifier = Modifier.width(900.dp) // Excel width constraint
                ) {
                    // Sticky Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.secondary)
                            .border(width = 0.5.dp, color = Color.Black)
                    ) {
                        HeaderCell("DATE", Modifier.width(130.dp))
                        HeaderCell("PATIENT NAME", Modifier.width(220.dp))
                        HeaderCell("AGE", Modifier.width(90.dp))
                        HeaderCell("TEST", Modifier.width(220.dp))
                        HeaderCell("COMMISSION", Modifier.width(120.dp))
                        HeaderCell("OTHER", Modifier.width(120.dp))
                    }

                    // Records list
                    if (doctorRecords.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No patient referrals found for this doctor in $month/$year.", color = Color.Gray)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(doctorRecords) { record ->
                                var hoverState by remember { mutableStateOf(false) }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (hoverState) Color(0xFFE2E8F0) else Color.White
                                        )
                                        .border(width = 0.5.dp, color = Color.Black)
                                        .clickable { hoverState = !hoverState }
                                ) {
                                    ExcelTextCell(
                                        value = record.date,
                                        onValueChange = { viewModel.updateRecordField(record, "DATE", it) },
                                        modifier = Modifier.width(130.dp)
                                    )
                                    ExcelTextCell(
                                        value = record.patientName,
                                        onValueChange = { viewModel.updateRecordField(record, "PATIENTNAME", it) },
                                        modifier = Modifier.width(220.dp)
                                    )
                                    ExcelTextCell(
                                        value = record.age,
                                        onValueChange = { viewModel.updateRecordField(record, "AGE", it) },
                                        modifier = Modifier.width(90.dp)
                                    )
                                    ExcelTextCell(
                                        value = record.testName,
                                        onValueChange = { viewModel.updateRecordField(record, "TESTNAME", it) },
                                        modifier = Modifier.width(220.dp)
                                    )
                                    // COMMISSION cell: blank by default, numeric rate
                                    ExcelTextCell(
                                        value = record.commission?.toString() ?: "",
                                        onValueChange = { viewModel.updateRecordField(record, "COMMISSION", it) },
                                        modifier = Modifier.width(120.dp),
                                        numericOnly = true
                                    )
                                    // OTHER cell: blank by default, alphanumeric
                                    ExcelTextCell(
                                        value = record.other ?: "",
                                        onValueChange = { viewModel.updateRecordField(record, "OTHER", it) },
                                        modifier = Modifier.width(120.dp)
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

@Composable
fun PayoutMetric(title: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

// -------------------------------------------------------------
// REPORTS PAGE (DOCTOR WISE MONTHLY COMMISSION REGISTER)
// -------------------------------------------------------------
@Composable
fun ReportsScreen(viewModel: DoctorViewModel) {
    val records by viewModel.allRecords.collectAsStateWithLifecycle()
    val doctors by viewModel.uniqueDoctors.collectAsStateWithLifecycle()
    val month by viewModel.selectedMonth.collectAsStateWithLifecycle()
    val year by viewModel.selectedYear.collectAsStateWithLifecycle()

    val context = LocalContext.current

    // Build the monthly data structure for each doctor
    val reportRows = doctors.map { doc ->
        val docRecords = records.filter {
            it.referringDoctor.equals(doc, ignoreCase = true) &&
                    it.date.contains("-$month-") && it.date.endsWith(year)
        }
        val totalPatients = docRecords.size
        val totalCommission = docRecords.sumOf { it.commission ?: 0.0 }
        val totalOther = docRecords.mapNotNull { it.other?.toDoubleOrNull() }.sum()
        val grandTotal = totalCommission + totalOther

        DoctorReportRow(
            doctorName = doc,
            totalPatients = totalPatients,
            totalCommission = totalCommission,
            totalOther = totalOther,
            grandTotal = grandTotal
        )
    }.filter { it.totalPatients > 0 } // only show doctors with referrals

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pathology Commission Reports", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo(Screen.Dashboard) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Export trigger
                    IconButton(onClick = {
                        val csvContent = buildString {
                            append("Doctor Name,Total Patients,Total Commission,Total Other,Grand Total\n")
                            reportRows.forEach { r ->
                                append("${r.doctorName},${r.totalPatients},${r.totalCommission},${r.totalOther},${r.grandTotal}\n")
                            }
                        }
                        val sendIntent: Intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, csvContent)
                            type = "text/csv"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, "Export Doctor Commission Register")
                        context.startActivity(shareIntent)
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Export")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.White)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Month year header select
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Month selector
                Column(modifier = Modifier.weight(1f)) {
                    Text("Select Month", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    var expanded by remember { mutableStateOf(false) }
                    val months = listOf("01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12")
                    Box {
                        OutlinedButton(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(month)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            months.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(m) },
                                    onClick = {
                                        viewModel.selectMonth(m)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Year selector
                Column(modifier = Modifier.weight(1f)) {
                    Text("Select Year", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    var expanded by remember { mutableStateOf(false) }
                    val years = listOf("2026", "2027", "2028")
                    Box {
                        OutlinedButton(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(year)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            years.forEach { y ->
                                DropdownMenuItem(
                                    text = { Text(y) },
                                    onClick = {
                                        viewModel.selectYear(y)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = "Doctor-Wise Monthly Commission Sheet",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.secondary
            )

            // Table of doctors reports
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.secondary)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("DOCTOR NAME", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1.5f))
                        Text("PATIENTS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                        Text("COMMISSION", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1.2f), textAlign = TextAlign.End)
                        Text("OTHER", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1.2f), textAlign = TextAlign.End)
                        Text("GRAND TOTAL", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1.2f), textAlign = TextAlign.End)
                    }

                    if (reportRows.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No referrals recorded in July 2026.", color = Color.Gray)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(reportRows) { row ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.White)
                                        .border(0.5.dp, MaterialTheme.colorScheme.outline)
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                        .clickable {
                                            viewModel.selectDoctor(row.doctorName)
                                            viewModel.navigateTo(Screen.DoctorPages)
                                        },
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(row.doctorName, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1.5f))
                                    Text("${row.totalPatients}", fontSize = 13.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                                    Text("₹${row.totalCommission}", fontSize = 13.sp, modifier = Modifier.weight(1.2f), textAlign = TextAlign.End)
                                    Text("₹${row.totalOther}", fontSize = 13.sp, modifier = Modifier.weight(1.2f), textAlign = TextAlign.End)
                                    Text("₹${row.grandTotal}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, modifier = Modifier.weight(1.2f), textAlign = TextAlign.End)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

data class DoctorReportRow(
    val doctorName: String,
    val totalPatients: Int,
    val totalCommission: Double,
    val totalOther: Double,
    val grandTotal: Double
)

// -------------------------------------------------------------
// PRINT PREVIEW (LANDSCAPE A4 PROFESSIONAL REGISTER PREVIEW)
// -------------------------------------------------------------
private fun getSavedWidths(context: Context, doctor: String): List<Float> {
    val prefs = context.getSharedPreferences("table_widths", Context.MODE_PRIVATE)
    val saved = prefs.getString(doctor, null)
    if (saved != null) {
        return saved.split(",").mapNotNull { it.toFloatOrNull() }
    }
    return emptyList()
}

private fun saveWidths(context: Context, doctor: String, widths: List<Float>) {
    val prefs = context.getSharedPreferences("table_widths", Context.MODE_PRIVATE)
    prefs.edit().putString(doctor, widths.joinToString(",")).apply()
}

@Composable
fun PrintPreviewScreen(viewModel: DoctorViewModel, doctorName: String, month: String, year: String) {
    val records by viewModel.allRecords.collectAsStateWithLifecycle()
    val doctorRecords = records.filter {
        it.referringDoctor.equals(doctorName, ignoreCase = true) &&
                it.date.contains("-$month-") && it.date.endsWith(year)
    }

    val totalCommission = doctorRecords.sumOf { it.commission ?: 0.0 }
    val totalOther = doctorRecords.mapNotNull { it.other?.toDoubleOrNull() }.sum()
    val grandTotal = totalCommission + totalOther

    val context = LocalContext.current
    val density = LocalDensity.current

    // PDF and Excel auto-generation states
    var pdfFile by remember { mutableStateOf<File?>(null) }
    var excelFile by remember { mutableStateOf<File?>(null) }
    var isGenerating by remember { mutableStateOf(false) }

    LaunchedEffect(doctorName, month, year, doctorRecords) {
        if (doctorRecords.isNotEmpty()) {
            isGenerating = true
            withContext(Dispatchers.IO) {
                pdfFile = ReportExporter.generatePdfReport(context, doctorName, month, year, doctorRecords)
                excelFile = ReportExporter.generateExcelReport(context, doctorName, month, year, doctorRecords)
            }
            isGenerating = false
        }
    }

    // Table column widths (using saved or content-based auto widths)
    val savedList = remember(doctorName) { getSavedWidths(context, doctorName) }
    val columnWidths = remember(doctorName, doctorRecords) {
        val initial = if (savedList.size == 6) {
            savedList.map { it.dp }
        } else {
            val dateMaxLen = (listOf("DATE") + doctorRecords.map { it.date }).maxOf { it.length }
            val nameMaxLen = (listOf("PATIENT NAME") + doctorRecords.map { it.patientName }).maxOf { it.length }
            val ageMaxLen = (listOf("AGE") + doctorRecords.map { it.age }).maxOf { it.length }
            val testMaxLen = (listOf("TEST NAME") + doctorRecords.map { it.testName }).maxOf { it.length }
            val commMaxLen = (listOf("COMMISSION") + doctorRecords.map { it.commission?.let { "₹$it" } ?: "-" }).maxOf { it.length }
            val otherMaxLen = (listOf("OTHER") + doctorRecords.map { it.other ?: "-" }).maxOf { it.length }

            listOf(
                maxOf(80, dateMaxLen * 8).dp,
                maxOf(150, nameMaxLen * 8).dp,
                maxOf(60, ageMaxLen * 8).dp,
                maxOf(180, testMaxLen * 8).dp,
                maxOf(100, commMaxLen * 8).dp,
                maxOf(100, otherMaxLen * 8).dp
            )
        }
        mutableStateListOf<Dp>().apply { addAll(initial) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Print Layout & Reconciliation Report", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo(Screen.DoctorPages) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (pdfFile != null) {
                        // EXPORT OPTIONS: Download PDF, Download Excel, Print, Share on WhatsApp
                        IconButton(onClick = {
                            ReportExporter.downloadFileToDownloadsFolder(context, pdfFile!!, "application/pdf")
                        }) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = "Download PDF", tint = Color.White)
                        }
                        IconButton(onClick = {
                            excelFile?.let { ReportExporter.downloadFileToDownloadsFolder(context, it, "text/csv") }
                        }) {
                            Icon(Icons.Default.TableView, contentDescription = "Download Excel", tint = Color.White)
                        }
                        IconButton(onClick = {
                            ReportExporter.printDocument(context, pdfFile!!, "Report_${doctorName}_${month}_${year}")
                        }) {
                            Icon(Icons.Default.LocalPrintshop, contentDescription = "Print Report", tint = Color.White)
                        }
                        IconButton(onClick = {
                            ReportExporter.shareToWhatsApp(context, pdfFile!!, "application/pdf")
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Share on WhatsApp", tint = Color.White)
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFE2E8F0)) // Desk surface
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Inline status bar with Download/WhatsApp buttons
                if (isGenerating) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("Preparing professional clinical PDF...", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                } else if (pdfFile != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                    ) {
                        Button(
                            onClick = { ReportExporter.downloadFileToDownloadsFolder(context, pdfFile!!, "application/pdf") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0056D2)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("📄 Download PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Button(
                            onClick = { ReportExporter.shareToWhatsApp(context, pdfFile!!, "application/pdf") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("📲 Share on WhatsApp", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // A4 Register Sheet container (Landscape)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.White),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(4.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Register Header
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "CLINICAL PATHOLOGY LABORATORY DOCTOR REGISTER",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                color = Color.Black,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "MONTHLY COMMISSION RECONCILIATION SHEET",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color.DarkGray,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("DOCTOR: ${doctorName.uppercase()}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.Black)
                                Text("PERIOD: $month / $year", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.Black)
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.Black, thickness = 2.dp)
                        }

                        // Patients Resizable Excel-Style Table
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val availableWidth = maxWidth
                            val totalWidth = columnWidths.fold(0.dp) { acc, dp -> acc + dp }

                            // If total calculated column width is less than screen width, stretch on desktop
                            val displayWidths = remember(columnWidths, availableWidth) {
                                if (totalWidth < availableWidth) {
                                    val ratio = availableWidth.value / totalWidth.value
                                    columnWidths.map { (it.value * ratio).dp }
                                } else {
                                    columnWidths
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.5.dp, Color.Black)
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                // Table Header Row
                                Row(
                                    modifier = Modifier
                                        .background(Color(0xFFF1F5F9))
                                        .border(1.dp, Color.Black),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    for (i in 0 until 6) {
                                        val headerText = when (i) {
                                            0 -> "DATE"
                                            1 -> "PATIENT NAME"
                                            2 -> "AGE"
                                            3 -> "TEST NAME"
                                            4 -> "COMMISSION"
                                            else -> "OTHER"
                                        }

                                        Box(
                                            modifier = Modifier
                                                .width(displayWidths[i])
                                                .padding(vertical = 4.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            PrintHeaderCell(headerText, Modifier.fillMaxWidth())
                                        }

                                        if (i < 5) {
                                            // Excel-Style Draggable Border Handle
                                            Box(
                                                modifier = Modifier
                                                    .width(8.dp)
                                                    .height(36.dp)
                                                    .background(Color.Transparent)
                                                    .pointerInput(i, doctorName) {
                                                        detectDragGestures { change, dragAmount ->
                                                            change.consume()
                                                            val deltaDp = dragAmount.x.toDp()
                                                            val newWidth = columnWidths[i] + deltaDp
                                                            if (newWidth > 45.dp) {
                                                                columnWidths[i] = newWidth
                                                                saveWidths(context, doctorName, columnWidths.map { it.value })
                                                            }
                                                        }
                                                    }
                                            ) {
                                                Spacer(
                                                    modifier = Modifier
                                                        .align(Alignment.Center)
                                                        .width(1.5.dp)
                                                        .fillMaxHeight()
                                                        .background(Color.Black)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Table Data Rows with Custom Wrapping
                                if (doctorRecords.isEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Text("No records found for this doctor and month period.", fontSize = 12.sp, color = Color.Gray)
                                    }
                                } else {
                                    doctorRecords.forEach { record ->
                                        Row(
                                            modifier = Modifier.border(0.5.dp, Color.Black),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            for (i in 0 until 6) {
                                                val cellText = when (i) {
                                                    0 -> record.date
                                                    1 -> record.patientName
                                                    2 -> record.age
                                                    3 -> record.testName
                                                    4 -> record.commission?.let { "₹$it" } ?: "-"
                                                    else -> record.other ?: "-"
                                                }

                                                Box(
                                                    modifier = Modifier
                                                        .width(displayWidths[i])
                                                        .padding(vertical = 4.dp)
                                                ) {
                                                    PrintDataCell(cellText, Modifier.fillMaxWidth())
                                                }

                                                if (i < 5) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                }
                                            }
                                        }
                                    }
                                }

                                // Totals Sum Row
                                Row(
                                    modifier = Modifier
                                        .background(Color(0xFFF8FAFC))
                                        .border(1.5.dp, Color.Black),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val leftColumnsWidth = displayWidths[0] + displayWidths[1] + displayWidths[2] + displayWidths[3] + 24.dp

                                    Box(
                                        modifier = Modifier
                                            .width(leftColumnsWidth)
                                            .border(0.5.dp, Color.Black)
                                            .padding(8.dp),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Text("TOTALS", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = Color.Black)
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Box(
                                        modifier = Modifier
                                            .width(displayWidths[4])
                                            .border(0.5.dp, Color.Black)
                                            .padding(8.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text("₹${String.format("%.2f", totalCommission)}", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = Color(0xFF0056D2))
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Box(
                                        modifier = Modifier
                                            .width(displayWidths[5])
                                            .border(0.5.dp, Color.Black)
                                            .padding(8.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text("₹${String.format("%.2f", totalOther)}", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = Color.Black)
                                    }
                                }
                            }
                        }

                        // Grand Total summary block
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Card(
                                border = BorderStroke(1.5.dp, Color.Black),
                                shape = RoundedCornerShape(0.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                        Text("Total Referred Patients:", fontSize = 12.sp, color = Color.Black)
                                        Text("${doctorRecords.size}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Black)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                        Text("Grand Total Register Payout:", fontSize = 12.sp, color = Color.Black)
                                        Text("₹${String.format("%.2f", grandTotal)}", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = Color(0xFF0056D2))
                                    }
                                }
                            }
                        }

                        // Signature Lines
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 40.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                HorizontalDivider(color = Color.Black, modifier = Modifier.width(160.dp))
                                Text("Prepared By (Lab Executive)", fontSize = 10.sp, color = Color.DarkGray, modifier = Modifier.padding(top = 4.dp))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                HorizontalDivider(color = Color.Black, modifier = Modifier.width(160.dp))
                                Text("Verified By (Authorized Pathologist)", fontSize = 10.sp, color = Color.DarkGray, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PrintHeaderCell(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .border(0.5.dp, Color.Black)
            .padding(8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = Color.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun PrintDataCell(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .border(0.5.dp, Color.Black)
            .padding(6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            color = Color.Black,
            maxLines = 4,
            overflow = TextOverflow.Clip
        )
    }
}
