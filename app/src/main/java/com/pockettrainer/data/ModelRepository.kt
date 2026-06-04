package com.pockettrainer.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ModelRepository(private val context: Context) {
    private val modelsDir: File by lazy { File(context.filesDir, "models").also { it.mkdirs() } }

    fun getDownloadedModels(): List<File> {
        return modelsDir.listFiles()?.filter { it.isDirectory }?.filter { dir ->
            dir.listFiles()?.any { it.name.endsWith(".safetensors") || it.name.endsWith(".gguf") } == true
        } ?: emptyList()
    }
}