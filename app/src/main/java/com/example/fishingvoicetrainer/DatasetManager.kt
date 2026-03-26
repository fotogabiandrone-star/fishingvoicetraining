package com.example.fishingvoicetrainer

import android.content.Context
import java.io.File

// =======================================
//  MAPAREA COMENZILOR PE FOLDERE NUMEROTATE
// =======================================
val commandFolders = mapOf(
    "start_l1" to "01_start_l1",
    "start_l2" to "02_start_l2",
    "stop_l1"  to "03_stop_l1",
    "stop_l2"  to "04_stop_l2",
    "linebait_l1" to "05_linebait_l1",
    "linebait_l2" to "06_linebait_l2",
    "peste_l1" to "07_peste_l1",
    "peste_l2" to "08_peste_l2",
    "captura_l1" to "09_captura_l1",
    "captura_l2" to "10_captura_l2",
    "obs_l1" to "11_obs_l1",
    "obs_l2" to "12_obs_l2",
    "obs_general" to "13_obs_general",
    "da" to "14_da",
    "nu" to "15_nu",
    "confirm" to "16_confirm",
    "nu_confirm" to "17_nu_confirm",
    "repeta" to "18_repeta"
)

// =======================================
//  GENERARE NUME INCREMENTAL (001, 002…)
// =======================================
private fun nextIndexFor(dir: File, prefix: String): String {
    val existing = dir.listFiles { f -> f.extension == "pcm" } ?: return "001"

    val maxIndex = existing
        .mapNotNull { file ->
            val num = file.nameWithoutExtension.removePrefix(prefix + "_")
            num.toIntOrNull()
        }
        .maxOrNull() ?: 0

    return String.format("%03d", maxIndex + 1)
}

// =======================================
//  FUNCTIA PRINCIPALA: getOutputFile()
// =======================================
fun getOutputFile(context: Context, command: String): File {

    val folderName = commandFolders[command]
        ?: throw IllegalArgumentException("Comanda necunoscută: $command")

    val datasetRoot = File(context.getExternalFilesDir(null), "dataset")
    if (!datasetRoot.exists()) datasetRoot.mkdirs()

    val commandDir = File(datasetRoot, folderName)
    if (!commandDir.exists()) commandDir.mkdirs()

    val index = nextIndexFor(commandDir, command)
    val fileName = "${command}_${index}.pcm"

    return File(commandDir, fileName)
}
