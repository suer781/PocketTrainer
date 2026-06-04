# PocketTrainer 审计报告

> 审计时间：2026-06-05 01:37
> 更新时间：2026-06-05 02:01
> 审计范围：全项目源码（C++ / Kotlin / Gradle / Manifest）
> CI 状态：编译通过

---

## 🔴 P0 — 必须修（编译错误 / 运行崩溃）

### 1. TrainScreen 调用了不存在的方法

**文件**: `ui/screens/TrainScreen.kt`
**问题**: `viewModel.exportLora(context)` — 但 `TrainingViewModel` 没有 `exportLora` 方法。
`exportLora` 定义在 `LoraManagerViewModel` 里，且签名不同（接受 `String` 不是 `Context`）。

**CI 为何没报错**: 待确认。可能是 build cache 或者 CI 没有完整编译 Kotlin。

**修复方案**:
- 方案 A: 在 `TrainingViewModel` 里加 `exportLora(context)` 方法，内部调用 `exportModel()` + 复制到外部存储
- 方案 B: 改 TrainScreen 直接调用 `viewModel.exportModel()`，不传 context

```kotlin
// TrainingViewModel.kt 新增
fun exportLora(context: Context) {
    val src = File(context.filesDir, "lora_weights.safetensors")
    if (!src.exists()) { _error.value = "没有可导出的 LoRA 权重"; return }
    val dest = File(context.getExternalFilesDir(null), "export/lora_weights.safetensors")
    dest.parentFile?.mkdirs()
    src.copyTo(dest, overwrite = true)
    _exportedPath.value = dest.absolutePath
}
```

---

### 2. Settings 不持久化

**文件**: `ui/screens/SettingsScreen.kt`
**问题**: 所有设置项都用 `remember { mutableFloatStateOf(4f) }`，app 重启后全部丢失。

**修复方案**: 用 DataStore Preferences 或 SharedPreferences 持久化。

```kotlin
// 方案：SharedPreferences（简单够用）
val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
var epochs by remember { mutableIntStateOf(prefs.getInt("epochs", 4)) }

LaunchedEffect(epochs) {
    prefs.edit().putInt("epochs", epochs).apply()
}
```

需要持久化的字段：
- epochs, learningRate, batchSize, seqLen, loraRank, loraAlpha, loraDropout
- useQLora, useMixedPrecision
- accelerateType (int + autoConvert)

---

### 3. text_dataset.h load() 不做 tokenize

**文件**: `app/src/main/cpp/text_dataset.h`
**问题**: `load()` 读取了文本行到 `texts_`，但从未调用 tokenizer 转换为 token ID。
训练时 `token_ids_` 为空，前向传播会 crash。

**修复方案**: 在 `load()` 末尾加 tokenize 步骤：

```cpp
void load(const std::string& path, Tokenizer& tok) {
    // ... 现有读取逻辑 ...
    token_ids_.clear();
    for (const auto& text : texts_) {
        auto ids = tok.encode(text);
        token_ids_.insert(token_ids_.end(), ids.begin(), ids.end());
    }
}
```

或者在 `get_batch()` 里 lazy tokenize（避免 OOM）。

---

### 4. AndroidManifest 缺少 FOREGROUND_SERVICE_TYPE

**文件**: `app/src/main/AndroidManifest.xml`
**问题**: Android 14+ 要求 `<service>` 声明 `foregroundServiceType`，否则 `startForeground()` 会抛异常。

**修复方案**:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />

<service
    android:name=".training.TrainingForegroundService"
    android:foregroundServiceType="specialUse"
    android:exported="false">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="Local model training" />
</service>
```

---

### 5. MobileFineTuner 大量算子未实现

**文件**: `third_party/MobileFineTuner/operator/finetune_ops/` 下多个文件
**问题**: 以下算子只有声明没有实现，训练时调用到会直接 crash：

| 算子 | 文件 | 影响 |
|------|------|------|
| `gather` | tensor.h / tensor.cpp | embedding lookup 不工作 |
| `scatter` | tensor.h | 反向传播 scatter 不工作 |
| `argmax` | functional_ops.h | 推理采样不工作 |
| `max/min` | functional_ops.h | 梯度裁剪不工作 |
| `where` | functional_ops.h | masked attention 不工作 |
| `normal` | functional_ops.h | dropout 随机初始化不工作 |
| `to_float/to_int` | data_ops.h | 类型转换不工作 |
| attention backward | memory_efficient_attention.cpp:168 | 训练反向传播不完整 |

**修复方案**: 这是最大的工程，需要逐个实现 C++ kernel。建议按训练链路优先级：
1. `gather` + `to_float`（embedding 前向）
2. `where` + `normal`（attention + dropout）
3. `max/min/argmax`（推理）
4. attention backward（训练反向）
5. `scatter`（完整反向）

每个算子大约 50-200 行 C++ 代码。

---

## 🟡 P1 — 应该修（功能缺失）

### 6. 导入模型无格式校验

**文件**: `data/ModelRepository.kt`
**问题**: `importFromUri()` 和 `importFromUrl()` 不校验文件内容。
传入一个 .txt 文件也能导入，后续加载时 crash。

**修复方案**: 下载/复制完成后检查文件头：
- GGUF: 前 4 字节 `0x46475547` ("GGUF")
- SafeTensors: 前 8 字节是 header length，检查是否合理

```kotlin
private fun validateModelFile(file: File): Boolean {
    if (!file.exists() || file.length() < 8) return false
    val header = file.readBytes().take(4)
    // GGUF magic
    if (header == listOf(0x47, 0x55, 0x46, 0x47)) return true
    // SafeTensors: first 8 bytes = header length (LE uint64)
    val hlen = file.readBytes().take(8).foldIndexed(0L) { i, acc, b ->
        acc or ((b.toLong() and 0xFF) shl (i * 8))
    }
    return hlen in 1..1_000_000_000
}
```

---

### 7. DatasetScreen 导入按钮未接文件选择器

**文件**: `ui/screens/DatasetScreen.kt`
**问题**: "导入数据集"按钮可能只是占位，没有真正打开 SAF 文件选择器。

**修复方案**: 用 `ActivityResultContracts.OpenDocument()` 打开文件选择器，
选择后调用 `DatasetRepository.importDataset(uri)` 复制到 app 私有目录。

---

### 8. 无 ProGuard/R8 规则

**文件**: 缺少 `app/proguard-rules.pro`
**问题**: Release 构建时 R8 可能 strip JNI native 方法，导致 `UnsatisfiedLinkError`。

**修复方案**: 创建 proguard-rules.pro：

```proguard
# Keep JNI methods
-keepclasseswithmembernames class com.pockettrainer.training.NativeTraining {
    native <methods>;
}
-keep class com.pockettrainer.training.TrainingCallback { *; }
```

---

## 🟢 P2 — 可以后做

### 9. EXO 集群集成不完整
- ClusterScreen UI 完整
- 底层 EXO 分布式训练可能没有真正接通
- 需要验证跨设备通信和任务分发

### 10. 无单元测试
- C++ 层：至少测 tokenize → forward → backward 链路
- Kotlin 层：至少测 ViewModel 状态机

### 11. 训练进度通知
- TrainingForegroundService 有了，但 notification channel 和更新逻辑可能不完整

---

## 修复记录

| 时间 | # | 修复内容 | CI |
|------|---|----------|----|
| 02:01 | 1 | TrainingViewModel 加 exportLora(context) wrapper | ✅ |
| 02:01 | 2 | SettingsScreen 用 SharedPreferences 持久化 | ✅ |
| 02:01 | 3 | text_dataset.h 误报，已有字符级 tokenizer | — |
| 02:01 | 4 | AndroidManifest 加 FOREGROUND_SERVICE_DATA_SYNC 权限 | ✅ |

## 修复顺序建议

```
Week 1: P0 编译/崩溃问题
  ├── Day 1: #1 exportLora + #4 FOREGROUND_SERVICE_TYPE  (30min)
  ├── Day 2: #2 Settings 持久化                          (1h)
  ├── Day 3: #3 text_dataset.h tokenize                  (2h)
  └── Day 4-7: #5 MFT 算子补全（gather/to_float/where）   (持续)

Week 2: P1 功能完善
  ├── Day 1: #6 模型格式校验                              (30min)
  ├── Day 2: #7 DatasetScreen 文件选择器                  (1h)
  └── Day 3: #8 ProGuard 规则                            (30min)

Week 3+: P2 长期
  └── EXO 集成 / 单元测试 / 通知完善
```

---

## 当前项目状态总结

| 模块 | 完成度 | 说明 |
|------|--------|------|
| 项目骨架 | ✅ 100% | 18+ 文件，结构完整 |
| C++ 训练引擎 | ⚠️ 40% | 接口完整，但 MFT 算子大量未实现 |
| JNI 桥接 | ✅ 90% | 训练/推理/LoRA 都有 JNI，差 exportLora |
| Kotlin ViewModel | ⚠️ 70% | TrainingVM 和 LoraVM 功能齐全，Settings 不持久化 |
| UI 界面 | ✅ 85% | 6 个页面都有，导出按钮有编译错误 |
| 数据管理 | ⚠️ 60% | 模型导入 OK，数据集导入可能不完整 |
| 分布式训练 | ⚠️ 30% | UI 有了，底层 EXO 接入待验证 |
| 构建/CI | ✅ 95% | CI 全绿，差 ProGuard 规则 |
| i18n 汉化 | ✅ 97.6% | 411/421 key |
| 单元测试 | ❌ 0% | 无 |
