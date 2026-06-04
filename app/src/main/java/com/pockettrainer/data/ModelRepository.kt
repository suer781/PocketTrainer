package com.pockettrainer.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class ModelRepository(private val context: Context) {
    private val modelsDir: File by lazy { File(context.filesDir, "models").also { it.mkdirs() } }

    fun getDownloadedModels(): List<File> {
        return modelsDir.listFiles()?.filter { it.isDirectory }?.filter { dir ->
            dir.listFiles()?.any { it.name.endsWith(".safetensors") || it.name.endsWith(".gguf") } == true
        } ?: emptyList()
    }

    suspend fun downloadModel(repoId: String, filename: String = "model.safetensors", onProgress: (Float) -> Unit = {}): Result<File> = withContext(Dispatchers.IO) {
        try {
            val modelName = repoId.split("/").last()
            val modelDir = File(modelsDir, modelName).also { it.mkdirs() }
            val outputFile = File(modelDir, filename)
            if (outputFile.exists()) return@withContext Result.success(modelDir)

            val url = URL("https://huggingface.co/$repoId/resolve/main/$filename")
            val conn = url.openConnection() as HttpURLConnection
            conn.connect()
            val totalSize = conn.contentLength.toLong()
            val input = conn.inputStream
            val output = FileOutputStream(outputFile)
            val buffer = ByteArray(8192)
            var bytesRead: Long = 0
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
                bytesRead += read
                if (totalSize > 0) onProgress(bytesRead.toFloat() / totalSize)
            }
            output.close(); input.close()
            Result.success(modelDir)
        } catch (e: Exception) { Result.failure(e) }
    }
}