package com.pockettrainer.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

data class LoraFile(
    val name: String,
    val path: String,
    val sizeMb: Double,
    val lastModified: Long
)

data class LoraManagerState(
    val loraFiles: List<LoraFile> = emptyList(),
    val isProcessing: Boolean = false,
    val statusMessage: String? = null,
    val statusIsError: Boolean = false,
    val selectedLora: LoraFile? = null
)

class LoraManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(LoraManagerState())
    val state: StateFlow<LoraManagerState> = _state.asStateFlow()

    private val context get() = getApplication<Application>()
    private val loraDir get() = File(context.getExternalFilesDir(null), "lora_output").apply { mkdirs() }
    private val exportDir get() = File(context.getExternalFilesDir(null), "export").apply { mkdirs() }
    private val importDir get() = File(context.getExternalFilesDir(null), "import").apply { mkdirs() }

    init { refreshList() }

    fun refreshList() {
        val files = (loraDir.listFiles()?.toList() ?: emptyList()) +
                    (exportDir.listFiles()?.toList() ?: emptyList())
        val loraFiles = files
            .filter { it.extension == "safetensors" }
            .map { f ->
                LoraFile(
                    name = f.name,
                    path = f.absolutePath,
                    sizeMb = f.length() / (1024.0 * 1024.0),
                    lastModified = f.lastModified()
                )
            }
            .sortedByDescending { it.lastModified }

        _state.update { it.copy(loraFiles = loraFiles) }
    }

    fun selectLora(file: LoraFile) {
        _state.update { it.copy(selectedLora = if (it.selectedLora == file) null else file) }
    }

    /** 导出当前训练好的 LoRA */
    fun exportLora(fileName: String = "lora_${System.currentTimeMillis()}.safetensors") {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isProcessing = true) }
            val outFile = File(exportDir, fileName)
            val ok = NativeTraining.nativeExportModel(outFile.absolutePath)
            _state.update {
                it.copy(
                    isProcessing = false,
                    statusMessage = if (ok) "已导出: ${outFile.name}" else "导出失败",
                    statusIsError = !ok
                )
            }
            if (ok) refreshList()
        }
    }

    /** 导入 LoRA 文件（从用户选择的 URI 复制到 import 目录） */
    fun importLoraFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isProcessing = true) }
            try {
                val fileName = "imported_${System.currentTimeMillis()}.safetensors"
                val outFile = File(importDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    outFile.outputStream().use { output -> input.copyTo(output) }
                } ?: throw Exception("无法读取文件")

                // 尝试加载到当前模型
                val loaded = NativeTraining.nativeImportLora(outFile.absolutePath)
                _state.update {
                    it.copy(
                        isProcessing = false,
                        statusMessage = if (loaded) "导入成功: $fileName" else "文件已保存但加载失败（需先加载基座模型）",
                        statusIsError = !loaded && false  // 文件保存了就不算错误
                    )
                }
                refreshList()
            } catch (e: Exception) {
                _state.update {
                    it.copy(isProcessing = false, statusMessage = "导入失败: ${e.message}", statusIsError = true)
                }
            }
        }
    }

    /** 合并 LoRA 到基座模型，导出完整模型 */
    fun mergeAndExport(fileName: String = "merged_${System.currentTimeMillis()}.safetensors") {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isProcessing = true) }
            val outFile = File(exportDir, fileName)
            val ok = NativeTraining.nativeMergeAndSave(outFile.absolutePath)
            _state.update {
                it.copy(
                    isProcessing = false,
                    statusMessage = if (ok) "合并导出: ${outFile.name}" else "合并失败（需先加载模型+LoRA）",
                    statusIsError = !ok
                )
            }
            if (ok) refreshList()
        }
    }

    /** 分享文件（生成 content URI） */
    fun shareFile(file: LoraFile): Intent {
        val f = File(file.path)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** 删除文件 */
    fun deleteFile(file: LoraFile) {
        viewModelScope.launch(Dispatchers.IO) {
            val deleted = File(file.path).delete()
            _state.update {
                it.copy(
                    statusMessage = if (deleted) "已删除: ${file.name}" else "删除失败",
                    statusIsError = !deleted,
                    selectedLora = if (it.selectedLora == file) null else it.selectedLora
                )
            }
            refreshList()
        }
    }

    fun clearStatus() {
        _state.update { it.copy(statusMessage = null) }
    }
}
