# PocketTrainer 技术债务清单

> 最后更新: 2026-06-05

---

## ~~P0: Tokenizer 与 GPT-2 BPE 不匹配~~ ✅ 已修复

**现状**: `text_dataset.h` 使用字符级 tokenizer——UTF-8 码点直接映射为 token ID。GPT-2 原生使用 BPE (Byte-Pair Encoding)，词表 50257 个 token。

**影响**:
- 模型权重的 embedding 层是为 BPE token 训练的，喂入字符级 token 会导致语义不一致
- LoRA 微调需要从头学习 token 映射，效率极低
- 中文场景下，一个汉字 = 1 token（UTF-8 码点），而 GPT-2 BPE 会将中文拆成多个字节 token

**修复方案**:
- 方案 A: 集成 [sentencepiece](https://github.com/google/sentencepiece) C++ 库，使用 GPT-2 的 vocab 文件
- 方案 B: 集成 [tiktoken](https://github.com/openai/tiktoken) (Rust/C)，更轻量
- 方案 C: 自实现 BPE 解码器 + 读取 safetensors 中的 merges.txt

**工作量**: 大（需新增 native 依赖、修改 TextDataset 构造函数、JNI 层传递 vocab 路径）
**优先级**: 高——不修这个，模型训练效果根本不对

---

## ~~P1: Native JSON 解析脆弱~~ ✅ 已修复

**现状**: `training_jni.cpp` 的 `nativeStartTraining` 用字符串搜索 (`find` + `substr`) 解析 Kotlin 传入的 JSON 配置。

**影响**:
- 字段顺序变化或新增字段时容易解析失败
- 嵌套 JSON（如 preprocessing rules）解析不可靠
- 没有错误处理——格式不对直接 crash 或用默认值

**修复方案**:
- 方案 A: 引入 [nlohmann/json](https://github.com/nlohmann/json)（header-only，零依赖，最流行）
- 方案 B: 引入 [cJSON](https://github.com/DaveGamble/cJSON)（极轻量，单文件 .c/.h）
- 方案 C: 改为 JNI 直接读取 Java 对象字段（不传 JSON，改传各参数）

**工作量**: 中（方案 C 最干净，但需改 JNI 接口）
**优先级**: 中——当前能跑，但维护成本高

---

## 🟢 P2: 训练进度回调频率过高

**现状**: `nativeGetProgress` 每次 JNI 调用返回当前状态，Kotlin 端用 `while(active)` 循环轮询。

**影响**:
- 高频 JNI 调用有开销
- UI 刷新过快可能卡顿

**修复方案**: 在 native 端用 `std::atomic` 存状态，Kotlin 端用固定间隔（如 500ms）轮询，或改用 JNI callback push 模式

**工作量**: 小
**优先级**: 低

---

## 🟢 P3: EncryptedSharedPreferences

**现状**: `SettingsScreen` 使用普通 SharedPreferences 存储 nThreads/useGPU/useBLAS。这些是非敏感配置，**不需要加密**。

**结论**: 当前不存在敏感数据持久化问题。模型路径和数据集路径只在 ViewModel 内存中，不落盘。此项 **无需修复**。

---

## 📋 已修复清单

| 日期 | 问题 | 提交 |
|------|------|------|
| 2026-06-05 | stopTraining 设 COMPLETED 而非 STOPPED | becd336 |
| 2026-06-05 | onCleared 不停止训练 → native 线程泄漏 | becd336 |
| 2026-06-05 | exportModel 固定文件名覆盖 | becd336 |
| 2026-06-05 | importModelFromUri 路径穿越 | becd336 |
| 2026-06-05 | TrainingScreen 缺 STOPPED 状态 → UI 卡死 | becd336 |
| 2026-06-05 | run_evaluation 用训练集做验证 → 数据泄漏 | 4e18694 |
| 2026-06-05 | nativeCleanup 不等训练线程 → use-after-free | 4e18694 |
| 2026-06-05 | n_head=12 硬编码 | 714d6e2 |
| 2026-06-05 | tokenizer 逐字节切分（改为 UTF-8 码点） | 714d6e2 |
| 2026-06-05 | AdvancedOptionsPanel 无边界校验 | 6d77754 |
| 2026-06-05 | DatasetScreen 文件名未清理 | 6d77754 |
| 2026-06-05 | ModelRepository 重复 val 声明 | 6d77754 |
