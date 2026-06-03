# PocketTrainer Java/Kotlin 源码

## 包结构

```
com.pockettrainer/
├── training/          # 训练核心
│   ├── NativeTraining.kt      # JNI 桥接
│   └── TrainingViewModel.kt   # ViewModel
├── ui/                # 界面
│   ├── screens/
│   │   ├── TrainingScreen.kt
│   │   ├── DatasetScreen.kt
│   │   └── ClusterScreen.kt
│   └── components/    # 通用组件
├── data/              # 数据层
│   ├── model/         # 数据模型
│   └── repository/    # 数据仓库
└── util/              # 工具类
```