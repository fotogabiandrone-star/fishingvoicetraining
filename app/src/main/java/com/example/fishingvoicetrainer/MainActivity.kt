package com.example.fishingvoicetrainer

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioRecord
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.fishingvoicetrainer.ui.theme.FishingVoiceTrainerTheme
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// ---------------------------------------------------------
//  SCREEN STATES
// ---------------------------------------------------------
enum class ScreenState {
    MAIN,
    RECORD,
    SAMPLES,
    SETTINGS   // 🔵 NOU
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FishingVoiceTrainerTheme {
                AppContent()
            }
        }
    }
}

// ---------------------------------------------------------
//  APP CONTENT + NAVIGARE
// ---------------------------------------------------------
@Composable
fun AppContent() {
    var screen by remember { mutableStateOf(ScreenState.MAIN) }
    var selectedCommand by remember { mutableStateOf("") }

    when (screen) {

        ScreenState.MAIN -> MainScreen(
            onCommandSelected = { cmd ->
                selectedCommand = cmd
                screen = ScreenState.RECORD
            },
            onSamples = { screen = ScreenState.SAMPLES },
            onSettings = { screen = ScreenState.SETTINGS }   // 🔵 NOU
        )

        ScreenState.RECORD -> RecordScreen(
            command = selectedCommand,
            onBack = { screen = ScreenState.MAIN }
        )

        ScreenState.SAMPLES -> SamplesScreen(
            onBack = { screen = ScreenState.MAIN }
        )

        ScreenState.SETTINGS -> SettingsScreen(   // 🔵 NOU
            onBack = { screen = ScreenState.MAIN }
        )
    }
}

// ---------------------------------------------------------
//  MAIN SCREEN
// ---------------------------------------------------------
@Composable
fun MainScreen(
    onCommandSelected: (String) -> Unit,
    onSamples: () -> Unit,
    onSettings: () -> Unit   // 🔵 NOU
) {

    val context = LocalContext.current
    val datasetRoot = File(context.getExternalFilesDir(null), "dataset")

    val commands = listOf(
        "start_l1", "start_l2",
        "stop_l1", "stop_l2",
        "linebait_l1", "linebait_l2",
        "peste_l1", "peste_l2",
        "captura_l1", "captura_l2",
        "obs_l1", "obs_l2",
        "obs_general",
        "da", "nu",
        "confirm", "nu_confirm",
        "repeta"
    )

    val target = 50

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .padding(WindowInsets.navigationBars.asPaddingValues()),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {

        item {
            Text("Selectează comanda", fontSize = 20.sp)
            Spacer(modifier = Modifier.height(4.dp))
        }

        items(commands) { cmd ->

            val foldersForCommand = datasetRoot
                .listFiles()
                ?.filter { it.isDirectory && it.name.endsWith(cmd) }
                ?: emptyList()

            val count = foldersForCommand.sumOf { folder ->
                folder.listFiles()
                    ?.count { it.extension.lowercase() == "wav" }
                    ?: 0
            }

            val color = when {
                count == 0 -> Color(0xFFFF4444)
                count < 10 -> Color(0xFFFFBB33)
                else -> Color(0xFF99CC00)
            }

            Button(
                onClick = { onCommandSelected(cmd) },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(color),
                contentPadding = PaddingValues(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(cmd, fontSize = 16.sp, color = Color.Black)
                    Text("$count/$target", fontSize = 14.sp, color = Color.Black)
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onSamples,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(8.dp)
            ) {
                Text("Gestionare mostre", fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { exportDataset(context) },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(8.dp)
            ) {
                Text("Exportă dataset ZIP", fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onSettings,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(8.dp)
            ) {
                Text("Setări", fontSize = 16.sp)
            }
        }
    }
}

// ---------------------------------------------------------
//  EXPORT DATASET
// ---------------------------------------------------------
fun exportDataset(context: android.content.Context) {
    val datasetRoot = File(context.getExternalFilesDir(null), "dataset")
    val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    val zipFile = File(downloads, "dataset_export.zip")

    ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
        datasetRoot.walkTopDown().forEach { file ->
            if (file.isFile) {
                val entry = ZipEntry(file.relativeTo(datasetRoot).path)
                zip.putNextEntry(entry)
                zip.write(file.readBytes())
                zip.closeEntry()
            }
        }
    }
}

// ---------------------------------------------------------
//  SETTINGS SCREEN (SLIDER + EXPLICAȚII + RESET S20)
// ---------------------------------------------------------
@Composable
fun SettingsScreen(onBack: () -> Unit) {

    val context = LocalContext.current
    val prefs = context.getSharedPreferences("settings", 0)

    var minTh by remember { mutableStateOf(prefs.getInt("minThreshold", 1800)) }
    var maxTh by remember { mutableStateOf(prefs.getInt("maxThreshold", 3800)) }
    var win by remember { mutableStateOf(prefs.getInt("windowSize", 80)) }
    var minMs by remember { mutableStateOf(prefs.getInt("minVoiceMs", 200)) }

    var preRollMs by remember { mutableStateOf(prefs.getInt("preRollMs", 60)) }
    var postRollMs by remember { mutableStateOf(prefs.getInt("postRollMs", 180)) }

    fun save() {
        prefs.edit()
            .putInt("minThreshold", minTh)
            .putInt("maxThreshold", maxTh)
            .putInt("windowSize", win)
            .putInt("minVoiceMs", minMs)
            .putInt("preRollMs", preRollMs)
            .putInt("postRollMs", postRollMs)
            .apply()
    }

    var saved by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .padding(WindowInsets.navigationBars.asPaddingValues()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {

        item {
            Text("Setări procesare audio", fontSize = 22.sp)
        }

        // ---------------------------
        // Prag minim
        item {
            Text("Prag minim (minThreshold): $minTh")
            Slider(
                value = minTh.toFloat(),
                onValueChange = { minTh = it.toInt() },
                valueRange = 1200f..3000f
            )
        }

        // ---------------------------
        // Prag maxim
        item {
            Text("Prag maxim (maxThreshold): $maxTh")
            Slider(
                value = maxTh.toFloat(),
                onValueChange = { maxTh = it.toInt() },
                valueRange = 2500f..6000f
            )
        }

        // ---------------------------
        // Window size
        item {
            Text("Dimensiune fereastră (windowSize): $win")
            Slider(
                value = win.toFloat(),
                onValueChange = { win = it.toInt() },
                valueRange = 40f..240f
            )
            Text("Recomandat: 80 (5 ms)", fontSize = 12.sp)
        }

        // ---------------------------
        // Durată minimă voce
        item {
            Text("Durată minimă voce (minVoiceMs): $minMs ms")
            Slider(
                value = minMs.toFloat(),
                onValueChange = { minMs = it.toInt() },
                valueRange = 100f..400f
            )
        }

        // ---------------------------
        // PRE-ROLL
        item {
            Text("Pre-roll (ms): $preRollMs")
            Slider(
                value = preRollMs.toFloat(),
                onValueChange = { preRollMs = it.toInt() },
                valueRange = 0f..150f
            )
            Text("Câte ms păstrezi înainte de voce. Recomandat: 60 ms.", fontSize = 12.sp)
        }

        // ---------------------------
        // POST-ROLL
        item {
            Text("Post-roll (ms): $postRollMs")
            Slider(
                value = postRollMs.toFloat(),
                onValueChange = { postRollMs = it.toInt() },
                valueRange = 50f..250f
            )
            Text("Câte ms păstrezi după voce. Recomandat: 180 ms.", fontSize = 12.sp)
        }

        // ---------------------------
        // Reset
        item {
            Button(
                onClick = {
                    minTh = 1800
                    maxTh = 3800
                    win = 80
                    minMs = 200
                    preRollMs = 60
                    postRollMs = 180
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Reset la valori recomandate pentru Samsung S20")
            }
        }

        // ---------------------------
        // Salvare
        item {
            Button(
                onClick = {
                    save()
                    saved = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Salvează")
            }

            if (saved) {
                Text(
                    "Setările au fost salvate",
                    fontSize = 12.sp,
                    color = Color(0xFF2E7D32)
                )
            }
        }

        // ---------------------------
        // Înapoi
        item {
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Înapoi")
            }
        }
    }
}




// ---------------------------------------------------------
//  RECORD SCREEN (nemodificat)
// ---------------------------------------------------------
@Composable
fun RecordScreen(command: String, onBack: () -> Unit) {

    var lastRecordedFile by remember { mutableStateOf<File?>(null) }

    var permissionGranted by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<AudioRecord?>(null) }

    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    val context = LocalContext.current

    DisposableEffect(Unit) {
        onDispose { mediaPlayer?.release() }
    }

    if (!permissionGranted) {
        RequestAudioPermission { permissionGranted = true }
    }

    if (!permissionGranted) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Aștept permisiunea pentru microfon...")
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text("Recorder pentru: $command", fontSize = 22.sp)

        Spacer(modifier = Modifier.height(20.dp))

        if (!isRecording) {
            Button(
                onClick = {

                    val hasPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED

                    if (!hasPermission) return@Button

                    val file = getOutputFile(context, command)
                    lastRecordedFile = file
                    recorder = startRecording(file)
                    isRecording = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start Recording")
            }
        } else {
            Button(
                onClick = {
                    stopRecording(recorder)
                    isRecording = false

                    lastRecordedFile?.let { pcmFile ->
                        val trimmed = trimSilence(context, pcmFile)
                        val wavFile = convertPcmToWav(trimmed)
                        lastRecordedFile = wavFile
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Stop Recording")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (!isRecording && lastRecordedFile != null) {
            Button(
                onClick = {
                    mediaPlayer?.release()
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(lastRecordedFile!!.absolutePath)
                        prepare()
                        start()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Redă înregistrarea")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(onClick = onBack) {
            Text("Înapoi")
        }
    }
}

// ---------------------------------------------------------
@Composable
fun RequestAudioPermission(onGranted: () -> Unit) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onGranted()
    }

    LaunchedEffect(Unit) {
        launcher.launch(Manifest.permission.RECORD_AUDIO)
    }
}
