<div align="center">

# 🧠 口袋训练 PocketTrainer

**手机端本地大模型微调工具**

*在你的手机上训练 AI，无需云端，无需付费，数据不出设备。*

![Android](https://img.shields.io/badge/Platform-Android%2012%2B-green)
![C++](https://img.shields.io/badge/Core-C%2B%2B-blue)
![License](https://img.shields.io/badge/License-Apache%202.0-orange)
![Status](https://img.shields.io/badge/Status-Active%20Development-yellow)

</div>

---

## 📖 这是什么

**口袋训练** 是一款 Android 应用，让你直接在手机上对大语言模型进行 **LoRA 微调训练**。

不需要 GPU 服务器，不需要花钱租云算力，不需要把数据上传到任何地方。插上电、放一晚上，你就能得到一个专属于你的个性化 AI。

### 核心理念

- 🔒 **隐私优先** — 所有数据和训练都在本地完成，零上传
- 📱 **移动原生** — 专为手机硬件优化，不是桌面软件的缩水版
- 🧩 **模块化** — 训练引擎、UI、数据管理各模块独立，方便替换和升级
- 🌐 **分布式** — 多台手机组网训练，用数量弥补单机算力

---

## ✨ 功能特性

### 🎯 模型训练
- **QLoRA 4-bit 量化微调** — 4GB 内存即可训练 1.5B 参数模型
- **LoRA / DoRA / 全参数微调** — 多种训练策略可选
- **自动配置** — 根据手机性能自动选择最优参数
- **训练进度实时监控** — Loss 曲线、速度、内存使用一目了然
- **暂停 / 恢复 / 断点续训** — 随时中断，不怕白跑

### 📊 数据管理
- **多格式支持** — JSONL、Alpaca、ShareGPT、CSV
- **数据预览** — 训练前检查数据质量
- **本地导入** — 从手机存储直接加载

### 🌐 分布式集群
- **局域网组网** — 多台手机一起训练
- **协调器-Worker 模式** — 自动分配模型层和计算任务
- **Ring AllReduce** — 高效梯度同步

### 🤖 模型推理
- **GGUF 格式** — 兼容 llama.cpp 生态
- **LoRA 热加载** — 训练完立即测试效果
- **模型导出** — 合并 LoRA 权重导出完整模型

---

## 🏗️ 技术架构

```
┌─────────────────────────────────────────────┐
│                   UI 层                      │
│    Jetpack Compose + Material 3              │
│  ┌──────────┬──────────┬──────────┐         │
│  │ Training │ Dataset  │ Cluster  │         │
│  │ Screen   │ Screen   │ Screen   │         │
│  └────┬─────┴────┬─────┴────┬─────┘         │
│       │          │          │                │
│  ┌────▼──────────▼──────────▼─────┐         │
│  │       TrainingViewModel         │         │
│  │    (StateFlow + Coroutines)     │         │
│  └────────────┬────────────────────┘         │
├───────────────┼─────────────────────────────┤
│               │  JNI Bridge                  │
│  ┌────────────▼────────────────────┐         │
│  │        NativeTraining.kt        │         │
│  └────────────┬────────────────────┘         │
├───────────────┼─────────────────────────────┤
│               │  C++ Core                    │
│  ┌────────────▼────────────────────┐         │
│  │    MobileFineTuner Engine        │         │
│  │  ┌─────────┬────────┬────────┐  │         │
│  │  │ LoRA    │AdamW   │Memory  │  │         │
│  │  │ Trainer │8-bit   │Manager │  │         │
│  │  └─────────┴────────┴────────┘  │         │
│  ├──────────────────────────────────┤         │
│  │       llama.cpp (推理引擎)       │         │
│  └──────────────────────────────────┘         │
└─────────────────────────────────────────────┘
```

### 技术栈

| 层级 | 技术 |
|------|------|
| **UI** | Kotlin + Jetpack Compose + Material 3 |
| **架构** | MVVM + StateFlow + Coroutines |
| **训练引擎** | C++ (MobileFineTuner) + llama.cpp |
| **桥接** | JNI (Java Native Interface) |
| **构建** | Gradle + CMake + NDK |
| **数据** | JSONL / Alpaca / ShareGPT |
| **通信** | TCP/WebSocket + Protocol Buffers (集群) |

---

## 🚀 快速开始

### 环境要求

- Android Studio Ladybug 或更高版本
- Android SDK 34+
- Android NDK 27+
- CMake 3.22.1+

### 构建

```bash
git clone https://github.com/suer781/PocketTrainer.git
cd PocketTrainer
```

用 Android Studio 打开项目，连接手机或启动模拟器，点击 Run。

### 使用

1. **下载模型** — 从 HuggingFace 下载 GGUF 格式的模型（推荐 Qwen2 1.5B Q4_K_M）
2. **准备数据** — 准备 JSONL 格式的训练数据（每行 `{"text": "..."})`
3. **配置训练** — 选择 LoRA rank、学习率、epoch 数
4. **开始训练** — 点击"开始训练"，插上充电器，去睡觉
5. **测试效果** — 训练完成后直接在对话界面测试

---

## 📋 支持的模型

| 模型 | 参数量 | 最低内存 | 推荐配置 |
|------|--------|----------|----------|
| Qwen2 0.5B | 500M | 2GB | rank=4, lr=2e-4 |
| Qwen2 1.5B | 1.5B | 4GB | rank=8, lr=2e-4 |
| Gemma 2B | 2B | 5GB | rank=8, lr=1e-4 |
| Llama 3.2 1B | 1B | 3GB | rank=8, lr=2e-4 |
| Phi-3 Mini | 3.8B | 8GB | rank=16, lr=1e-4 |

---

## 📁 项目结构

```
PocketTrainer/
├── app/src/main/
│   ├── cpp/                        # C++ 训练引擎
│   │   ├── qlora_trainer.h/cpp     # QLoRA 训练器
│   │   ├── qlora_backward.h/cpp    # 反向传播引擎
│   │   ├── ggml_hooks.h/cpp        # 中间层张量提取
│   │   ├── training_jni.cpp        # JNI 桥接
│   │   └── CMakeLists.txt          # 构建配置
│   ├── java/com/localllm/
│   │   ├── training/
│   │   │   ├── NativeTraining.kt   # JNI 封装
│   │   │   └── TrainingViewModel.kt # UI 状态管理
│   │   └── ui/screens/
│   │       ├── TrainingScreen.kt   # 训练界面
│   │       ├── DatasetScreen.kt    # 数据管理界面
│   │       └── ClusterScreen.kt    # 分布式集群界面
│   └── res/                        # 资源文件
├── docs/                           # 文档
└── README.md
```

---

## 🗺️ 开发路线

### Phase 1 — 基础训练 ✅ / 🔄
- [x] 项目骨架搭建
- [x] C++ QLoRA 训练引擎框架
- [x] JNI 桥接层
- [x] 训练 UI 界面
- [x] 数据集管理界面
- [ ] 集成 MobileFineTuner 训练核心
- [ ] 编译验证 + 端到端测试

### Phase 2 — 模型生态
- [ ] 更多模型支持（Llama、Phi、Mistral）
- [ ] 模型下载管理器
- [ ] LoRA 权重市场（分享和下载）
- [ ] 训练模板库（角色扮演、代码生成、翻译等）

### Phase 3 — 分布式训练
- [ ] 局域网设备发现
- [ ] 模型层分配调度
- [ ] Ring AllReduce 梯度同步
- [ ] 多设备训练可视化

### Phase 4 — 高级功能
- [ ] 语音输入训练数据
- [ ] 图片标注 → 多模态训练
- [ ] 训练效果对比评估
- [ ] 导出到 Ollama / vLLM / llama.cpp

---

## 🤝 贡献

欢迎贡献！无论是 bug 修复、新功能、文档改进还是 UI 优化。

1. Fork 本仓库
2. 创建功能分支 (`git checkout -b feature/amazing-feature`)
3. 提交更改 (`git commit -m 'Add amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 创建 Pull Request

---

## 📄 许可证

本项目基于 [Apache License 2.0](LICENSE) 开源。

---

## 🙏 致谢

- [llama.cpp](https://github.com/ggerganov/llama.cpp) — 高效的 LLM 推理引擎
- [MobileFineTuner](https://github.com/Edge-Intelligence-Lab/MobileFineTuner) — 移动端训练框架
- [PocketPal AI](https://github.com/a-ghorbani/pocketpal-ai) — 移动端 AI 助手应用

---

<div align="center">

**如果这个项目对你有帮助，请给个 ⭐ Star 支持一下！**

*在手机上训练 AI，从此不再是梦。*

</div>