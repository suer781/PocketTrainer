package com.pockettrainer.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class ModelRepository(private val context: Context) {
    // 使用与 TrainingViewModel 一致的目录结构（外部存储）
    private val modelsDir: File by lazy {
        File(context.getExternalFilesDir(null), "models").also { it.mkdirs() }
    }

    private fun sanitizeFilename(name: String): String =
        name.replace(Regex("[^a-zA-Z0-9._\\x20-]"), "_").replace("..", "_")

    fun getDownloadedModels(): List<File> {
        // 直接返回模型文件，不使用子目录
        return modelsDir.listFiles()?.filter {
            it.isFile && (it.name.endsWith(".safetensors") || it.name.endsWith(".gguf"))
        } ?: emptyList()
    }

    suspend fun importFromUrl(
        urlStr: String,
        onProgress: (Float) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = URL(urlStr)
            val filename = sanitizeFilename(url.path.substringAfterLast("/")).ifBlank { "model.safetensors" }
            val outputFile = File(modelsDir, filename)

            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.connect()
            if (conn.responseCode != 200) throw Exception("HTTP ${conn.responseCode}")

            val totalSize = conn.contentLength.toLong()
            conn.inputStream.use { input ->
                outputFile.outputStream.use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesRead += read
                        if (totalSize > 0) onProgress(bytesRead.toFloat() / totalSize)
                    }
                }
            }
            if (!validateModelFile(outputFile)) {
                outputFile.delete()
                throw Exception("Invalid model file format (expected GGUF or SafeTensors)")
            }
            Result.success(outputFile.absolutePath)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun importFromUri(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val fileName = sanitizeFilename(uri.lastPathSegment?.substringAfterLast("/") ?: "").ifBlank { "imported_model_${System.currentTimeMillis()}" }
            val outputFile = File(modelsDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                outputFile.outputStream.use { output -> input.copyTo(output) }
            } ?: throw Exception("Cannot open URI")
            if (!validateModelFile(outputFile)) {
                outputFile.delete()
                throw Exception("Invalid model file format (expected GGUF or SafeTensors)")
            }
            Result.success(outputFile.absolutePath)
        } catch (e: Exception) { Result.failure(e) }
    }

    private fun validateModelFile(file: File): Boolean {
        if (!file.exists() || file.length() < 8) return false
        val header = file.inputStream.use { it.readNBytes(8) }
        // GGUF magic: 0x46475547 ("GGUF" in LE)
        if (header[0] == 0x47.toByte() && header[1] == 0x55.toByte() &&
            header[2] == 0x46.toByte() && header[3] == 0x47.toByte()) return true
        // SafeTensors: first 8 bytes = header length (LE uint64)
        val hlen = header.foldIndexed(0L) { i, acc, b -> acc or ((b.toLong() and 0xFF) shl (i * 8)) }
        if (hlen < 2L || hlen > file.length() - 8) return false
        // Header JSON must start with '{'
        val firstJsonByte = file.inputStream.use { it.skip(8); it.read() }
        return firstJsonByte == '{'.code
    }
}
