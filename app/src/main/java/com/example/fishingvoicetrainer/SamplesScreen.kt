package com.example.fishingvoicetrainer

import android.media.MediaPlayer
import androidx.compose.foundation.clickable
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
import java.io.File

@Composable
fun SamplesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val datasetRoot = File(context.getExternalFilesDir(null), "dataset")

    var selectedFolder by remember { mutableStateOf<File?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose { mediaPlayer?.release() }
    }

    // === LISTĂ FOLDERE ===
    if (selectedFolder == null) {
        val folders = datasetRoot.listFiles()?.filter { it.isDirectory } ?: emptyList()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {

            Text("Foldere mostre", fontSize = 20.sp)

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(folders) { folder ->

                    val wavCount = folder.listFiles()
                        ?.count { it.extension.lowercase() == "wav" }
                        ?: 0

                    FolderItemCompact(
                        folder = folder,
                        count = wavCount,
                        onOpen = { selectedFolder = folder },
                        onDelete = { folder.deleteRecursively() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Înapoi")
            }
        }

        return
    }

    // === LISTĂ FIȘIERE WAV ===
    val wavFiles = selectedFolder!!
        .listFiles()
        ?.filter { it.extension.lowercase() == "wav" }
        ?.sortedBy { it.name }
        ?: emptyList()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Text("Fișiere în: ${selectedFolder!!.name}", fontSize = 18.sp)

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(wavFiles) { file ->

                FileItemCompact(
                    file = file,
                    onPlay = {
                        mediaPlayer?.release()
                        mediaPlayer = MediaPlayer().apply {
                            setDataSource(file.absolutePath)
                            prepare()
                            start()
                        }
                    },
                    onDelete = { file.delete() }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = { selectedFolder = null },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Înapoi la foldere")
        }
    }
}

@Composable
fun FolderItemCompact(folder: File, count: Int, onOpen: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Column {
                Text(folder.name, fontSize = 16.sp)
                Text("$count mostre", fontSize = 12.sp)
            }

            Button(
                onClick = onDelete,
                contentPadding = PaddingValues(6.dp)
            ) {
                Text("Șterge", fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun FileItemCompact(file: File, onPlay: () -> Unit, onDelete: () -> Unit) {

    // Nume simplificat: Mostră 001
    val simpleName = file.nameWithoutExtension
        .replace(Regex(".*_(\\d+)"), "Mostră $1")

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Text(simpleName, fontSize = 14.sp, modifier = Modifier.weight(1f))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                Button(
                    onClick = onPlay,
                    contentPadding = PaddingValues(6.dp)
                ) {
                    Text("Play", fontSize = 12.sp)
                }

                Button(
                    onClick = onDelete,
                    contentPadding = PaddingValues(6.dp)
                ) {
                    Text("Șterge", fontSize = 12.sp)
                }
            }
        }
    }
}
