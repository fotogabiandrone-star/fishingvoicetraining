package com.example.fishingvoicetrainer

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioRecord
import android.media.MediaPlayer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.fishingvoicetrainer.ui.theme.FishingVoiceTrainerTheme
import java.io.File

enum class ScreenState {
    MAIN,
    RECORD,
    SAMPLES
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
            onSamples = { screen = ScreenState.SAMPLES }
        )

        ScreenState.RECORD -> RecordScreen(
            command = selectedCommand,
            onBack = { screen = ScreenState.MAIN }
        )

        ScreenState.SAMPLES -> SamplesScreen(
            onBack = { screen = ScreenState.MAIN }
        )
    }
}

@Composable
fun MainScreen(
    onCommandSelected: (String) -> Unit,
    onSamples: () -> Unit
) {

    val context = LocalContext.current
    val datasetRoot = File(context.getExternalFilesDir(null), "dataset")

    val commands = listOf(
        "start_l1",
        "start_l2",
        "stop_l1",
        "stop_l2",
        "linebait_l1",
        "linebait_l2",
        "peste_l1",
        "peste_l2",
        "captura_l1",
        "captura_l2",
        "obs_l1",
        "obs_l2",
        "obs_general",
        "da",
        "nu",
        "confirm",
        "nu_confirm",
        "repeta"
    )

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

            // toate folderele de tip 01_start_l1, 02_start_l1 etc.
            val foldersForCommand = datasetRoot
                .listFiles()
                ?.filter { it.isDirectory && it.name.endsWith(cmd) }
                ?: emptyList()

            // numărăm toate WAV-urile din toate folderele pentru comanda asta
            val count = foldersForCommand.sumOf { folder ->
                folder.listFiles()
                    ?.count { it.extension.lowercase() == "wav" }
                    ?: 0
            }

            Button(
                onClick = { onCommandSelected(cmd) },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(cmd, fontSize = 16.sp)
                    Text("$count", fontSize = 14.sp)
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
        }
    }
}


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
                        val trimmed = trimSilence(pcmFile)
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
