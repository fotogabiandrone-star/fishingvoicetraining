package com.example.fishingvoicetrainer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ListeningScreen(onBack: () -> Unit) {

    val context = LocalContext.current
    val prefs = context.getSharedPreferences("settings", 0)

    var permissionGranted by remember { mutableStateOf(false) }

    if (!permissionGranted) {
        RequestAudioPermission { permissionGranted = true }
    }

    if (!permissionGranted) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Aștept permisiunea pentru microfon…")
        }
        return
    }

    // ---------------------------------------------------------
    // STATE
    // ---------------------------------------------------------
    var isListening by remember { mutableStateOf(false) }
    var detected by remember { mutableStateOf("—") }

    // NEW DEFAULTS
    val defaultSensitivity = 2000f
    val defaultThreshold = 250000f
    val defaultMinMargin = 10000f

    var sensitivity by remember { mutableStateOf(defaultSensitivity) }
    var matchThreshold by remember { mutableStateOf(defaultThreshold) }
    var minMargin by remember { mutableStateOf(defaultMinMargin) }

    var debugMode by remember { mutableStateOf(true) }

    // debug info from engine
    var lastBestScore by remember { mutableStateOf(0f) }
    var lastBestLabel by remember { mutableStateOf("—") }

    val scroll = rememberScrollState()

    // ---------------------------------------------------------
    // UI
    // ---------------------------------------------------------
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(WindowInsets.systemBars.asPaddingValues())
            .padding(20.dp)
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // ---------------------------------------------------------
        // TOP BUTTONS
        // ---------------------------------------------------------
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    if (!isListening) {
                        ListeningEngine.startListening(
                            context,
                            sensitivity.toInt(),
                            matchThreshold
                        ) { cmd ->
                            detected = cmd
                        }
                    } else {
                        ListeningEngine.stopListening()
                    }
                    isListening = !isListening
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isListening) "Oprește" else "Pornește")
            }

            Button(
                onClick = onBack,
                modifier = Modifier.weight(1f)
            ) {
                Text("Înapoi")
            }

            Button(
                onClick = {
                    sensitivity = defaultSensitivity
                    matchThreshold = defaultThreshold
                    minMargin = defaultMinMargin

                    prefs.edit().putInt("minMargin", defaultMinMargin.toInt()).apply()

                    ListeningEngine.updateSensitivity(defaultSensitivity.toInt())
                    ListeningEngine.updateThreshold(defaultThreshold)
                    ListeningEngine.updateMinMargin(defaultMinMargin)
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Reset")
            }
        }

        // ---------------------------------------------------------
        // DETECTED TEXT
        // ---------------------------------------------------------
        Text(
            "Detectat: $detected",
            fontSize = 22.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        // ---------------------------------------------------------
        // SLIDERS SECTION
        // ---------------------------------------------------------

        // SENSITIVITY
        Text("Sensibilitate microfon: ${sensitivity.toInt()}")
        Slider(
            value = sensitivity,
            onValueChange = {
                sensitivity = it
                ListeningEngine.updateSensitivity(it.toInt())
            },
            valueRange = 200f..5000f,
            modifier = Modifier.fillMaxWidth()
        )

        // THRESHOLD
        Text("Prag matching (DTW threshold): ${matchThreshold.toInt()}")
        Slider(
            value = matchThreshold,
            onValueChange = {
                matchThreshold = it
                ListeningEngine.updateThreshold(it)
            },
            valueRange = 100000f..300000f,
            modifier = Modifier.fillMaxWidth()
        )

        // MIN MARGIN
        Text("Diferență minimă între comenzi (minMargin): ${minMargin.toInt()}")
        Slider(
            value = minMargin,
            onValueChange = {
                minMargin = it
                prefs.edit().putInt("minMargin", it.toInt()).apply()
                ListeningEngine.updateMinMargin(it)
            },
            valueRange = 1000f..30000f,
            modifier = Modifier.fillMaxWidth()
        )

        // ---------------------------------------------------------
        // DEBUG MODE
        // ---------------------------------------------------------
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = debugMode,
                onCheckedChange = { debugMode = it }
            )
            Text("Mod debug vizual")
        }

        if (debugMode) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("DEBUG:", fontSize = 16.sp)
                Text("• Listening: $isListening")
                Text("• Sensitivity: ${sensitivity.toInt()}")
                Text("• Threshold: ${matchThreshold.toInt()}")
                Text("• minMargin: ${minMargin.toInt()}")
                Text("• Ultimul scor DTW: ${lastBestScore.toInt()}")
                Text("• Cea mai apropiată comandă: $lastBestLabel")
                Text("• Loguri DTW: vezi Logcat (tag = ListeningEngine)")
            }
        }
    }
}
