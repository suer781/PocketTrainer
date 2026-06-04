package com.pockettrainer.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DatasetRepository(private val context: Context) {
    private val datasetsDir: File by lazy { File(context.filesDir, "datasets").also { it.mkdirs() } }
    private val gson = Gson()

    fun detectFormat(file: File): DatasetFormat {
        if (file.extension == "csv") return DatasetFormat.CSV
        val firstLine = file.bufferedReader().use { it.readLine() } ?: return DatasetFormat.PLAIN_TEXT
        return try {
            val json = gson.fromJson(firstLine, JsonObject::class.java)
            when {
                json.has("messages") -> DatasetFormat.CHAT_JSONL
                json.has("instruction") -> DatasetFormat.ALPACA_JSONL
                json.has("conversations") -> DatasetFormat.SHAREGPT_JSONL
                else -> DatasetFormat.PLAIN_TEXT
            }
        } catch (_: Exception) { DatasetFormat.PLAIN_TEXT }
    }

    suspend fun convertToTrainingFormat(inputFile: File, format: DatasetFormat, outputDir: File = datasetsDir): Result<DatasetInfo> = withContext(Dispatchers.IO) {
        try {
            val name = inputFile.nameWithoutExtension
            val trainFile = File(outputDir, "${name}_train.txt")
            val validFile = File(outputDir, "${name}_valid.txt")
            val samples = parseDataset(inputFile, format)
            val split = (samples.size * 0.9).toInt()
            trainFile.writeText(samples.take(split).joinToString("\n\n") { it })
            validFile.writeText(samples.drop(split).joinToString("\n\n") { it })
            Result.success(DatasetInfo(name, outputDir.absolutePath, format, samples.size, trainFile.length() + validFile.length()))
        } catch (e: Exception) { Result.failure(e) }
    }

    private fun parseDataset(file: File, format: DatasetFormat): List<String> = when (format) {
        DatasetFormat.CHAT_JSONL -> file.readLines().mapNotNull { l -> try { val j = gson.fromJson(l, JsonObject::class.java); j.getAsJsonArray("messages").map { m -> val r = m.asJsonObject; "${if (r.get("role").asString=="user") "User" else "Assistant"}: ${r.get("content").asString}" }.joinToString("\n") } catch (_: Exception) { null } }
        DatasetFormat.ALPACA_JSONL -> file.readLines().mapNotNull { l -> try { val j = gson.fromJson(l, JsonObject::class.java); "User: ${j.get("instruction")?.asString ?: ""}${j.get("input")?.asString?.let { if (it.isNotEmpty()) "\n$it" else "" } ?: ""}\nAssistant: ${j.get("output")?.asString ?: ""}" } catch (_: Exception) { null } }
        DatasetFormat.SHAREGPT_JSONL -> file.readLines().mapNotNull { l -> try { val j = gson.fromJson(l, JsonObject::class.java); j.getAsJsonArray("conversations").map { c -> val v = c.asJsonObject; "${if (v.get("from").asString=="human") "User" else "Assistant"}: ${v.get("value").asString}" }.joinToString("\n") } catch (_: Exception) { null } }
        DatasetFormat.PLAIN_TEXT -> file.readLines().filter { it.isNotBlank() }
        DatasetFormat.CSV -> file.readLines().drop(1).mapNotNull { l -> val p = l.split(",", limit = 2); if (p.size >= 2) "User: ${p[0].trim()}\nAssistant: ${p[1].trim()}" else null }
    }
}