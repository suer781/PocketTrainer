package com.pockettrainer.data

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class DatasetFormat {
    PLAIN_TEXT,
    CSV,
    CHAT_JSONL,
    ALPACA_JSONL,
    SHAREGPT_JSONL
}

data class DatasetInfo(
    val name: String,
    val path: String,
    val format: DatasetFormat,
    val sampleCount: Int,
    val size: Long
)

class DatasetRepository(private val context: Context) {
    private val datasetsDir: File by lazy {
        File(context.getExternalFilesDir(null), "datasets").also { it.mkdirs() }
    }
    private val gson = Gson()

    fun getDatasets(): List&lt;DatasetInfo&gt; {
        return datasetsDir.listFiles()?.mapNotNull { file -&gt;
            if (file.isFile) {
                val format = detectFormat(file)
                val sampleCount = countSamples(file, format)
                DatasetInfo(
                    name = file.name,
                    path = file.absolutePath,
                    format = format,
                    sampleCount = sampleCount,
                    size = file.length()
                )
            } else {
                null
            }
        }?.sortedByDescending { File(it.path).lastModified() } ?: emptyList()
    }

    suspend fun importDataset(uri: Uri): Result&lt;DatasetInfo&gt; = withContext(Dispatchers.IO) {
        try {
            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "dataset_${System.currentTimeMillis()}"
            val safeFileName = fileName.replace(Regex("[\\\\/:*?\"&lt;&gt;|]"), "_")
                .take(128)
                .ifEmpty { "dataset_${System.currentTimeMillis()}" }
            val targetFile = File(datasetsDir, safeFileName)

            context.contentResolver.openInputStream(uri)?.use { input -&gt;
                targetFile.outputStream().use { output -&gt; input.copyTo(output) }
            } ?: throw Exception("无法打开文件")

            val format = detectFormat(targetFile)
            val sampleCount = countSamples(targetFile, format)

            Result.success(DatasetInfo(
                name = safeFileName,
                path = targetFile.absolutePath,
                format = format,
                sampleCount = sampleCount,
                size = targetFile.length()
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteDataset(dataset: DatasetInfo): Result&lt;Unit&gt; = withContext(Dispatchers.IO) {
        try {
            val file = File(dataset.path)
            if (file.exists()) {
                file.delete()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun detectFormat(file: File): DatasetFormat {
        if (file.extension.lowercase() == "csv") return DatasetFormat.CSV
        val firstLine = file.bufferedReader().use { it.readLine() } ?: return DatasetFormat.PLAIN_TEXT
        return try {
            val json = gson.fromJson(firstLine, JsonObject::class.java)
            when {
                json.has("messages") -&gt; DatasetFormat.CHAT_JSONL
                json.has("instruction") -&gt; DatasetFormat.ALPACA_JSONL
                json.has("conversations") -&gt; DatasetFormat.SHAREGPT_JSONL
                else -&gt; DatasetFormat.PLAIN_TEXT
            }
        } catch (_: Exception) { DatasetFormat.PLAIN_TEXT }
    }

    private fun countSamples(file: File, format: DatasetFormat): Int {
        return when (format) {
            DatasetFormat.PLAIN_TEXT -&gt; file.readLines().filter { it.isNotBlank() }.size
            DatasetFormat.CSV -&gt; file.readLines().size - 1
            DatasetFormat.CHAT_JSONL,
            DatasetFormat.ALPACA_JSONL,
            DatasetFormat.SHAREGPT_JSONL -&gt; file.readLines().size
        }
    }

    suspend fun convertToTrainingFormat(inputFile: File, format: DatasetFormat, outputDir: File = datasetsDir): Result&lt;DatasetInfo&gt; = withContext(Dispatchers.IO) {
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

    fun parseDataset(file: File, format: DatasetFormat): List&lt;String&gt; = when (format) {
        DatasetFormat.CHAT_JSONL -&gt; file.readLines().mapNotNull { l -&gt;
            try {
                val j = gson.fromJson(l, JsonObject::class.java)
                j.getAsJsonArray("messages").map { m -&gt;
                    val r = m.asJsonObject
                    "${if (r.get("role").asString.lowercase() == "user") "User" else "Assistant"}: ${r.get("content").asString}"
                }.joinToString("\n")
            } catch (_: Exception) { null }
        }
        DatasetFormat.ALPACA_JSONL -&gt; file.readLines().mapNotNull { l -&gt;
            try {
                val j = gson.fromJson(l, JsonObject::class.java)
                "User: ${j.get("instruction")?.asString ?: ""}${j.get("input")?.asString?.let { if (it.isNotEmpty()) "\n$it" else "" } ?: ""}\nAssistant: ${j.get("output")?.asString ?: ""}"
            } catch (_: Exception) { null }
        }
        DatasetFormat.SHAREGPT_JSONL -&gt; file.readLines().mapNotNull { l -&gt;
            try {
                val j = gson.fromJson(l, JsonObject::class.java)
                j.getAsJsonArray("conversations").map { c -&gt;
                    val v = c.asJsonObject
                    "${if (v.get("from").asString.lowercase() == "human") "User" else "Assistant"}: ${v.get("value").asString}"
                }.joinToString("\n")
            } catch (_: Exception) { null }
        }
        DatasetFormat.PLAIN_TEXT -&gt; file.readLines().filter { it.isNotBlank() }
        DatasetFormat.CSV -&gt; file.readLines().drop(1).mapNotNull { l -&gt;
            val p = l.split(",", limit = 2)
            if (p.size &gt;= 2) "User: ${p[0].trim()}\nAssistant: ${p[1].trim()}" else null
        }
    }
}
