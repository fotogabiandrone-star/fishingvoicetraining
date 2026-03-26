package com.example.fishingvoicetrainer

import android.media.AudioRecord
import android.media.MediaPlayer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fishingvoicetrainer.ui.theme.FishingVoiceTrainerTheme
import java.io.File

enum class ScreenState {
    MAIN,
    RECORD
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
            }
        )

        ScreenState.RECORD -> RecordScreen(
            command = selectedCommand,
            onBack = { screen = ScreenState.MAIN }
        )
    }
}

@Composable
fun MainScreen(onCommandSelected: (String) -> Unit) {

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Selectează comanda",
            fontSize = 22.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        commands.forEach { cmd ->
            Button(
                onClick = { onCommandSelected(cmd) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(cmd)
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

    // MEDIA PLAYER
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    val context = LocalContext.current

    // Eliberăm playerul când ieșim din ecran
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
        }
    }

    if (!permissionGranted) {
        RequestAudioPermission {
            permissionGranted = true
        }
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

                    lastRecordedFile?.let {
                        convertPcmToWav(it)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Stop Recording")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // PLAY BUTTON
        if (!isRecording && lastRecordedFile != null) {
            Button(
                onClick = {
                    val wavPath = lastRecordedFile!!.absolutePath.replace(".pcm", ".wav")
                    mediaPlayer?.release()
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(wavPath)
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
        if (granted) {
            onGranted()
        }
    }

    LaunchedEffect(Unit) {
        launcher.launch(android.Manifest.permission.RECORD_AUDIO)
    }
}
