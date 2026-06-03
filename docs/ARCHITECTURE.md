# PocketTrainer 架构设计

## 训练流程

```
1. 加载 GGUF 模型 (llama.cpp)
2. 注入 LoRA 适配器 (A: d_in×r, B: r×d_out)
3. 训练循环:
   for epoch in epochs:
     for batch in dataset:
       a. 前向传播: y = W_base·x + (B·A)·x · (α/r)
       b. 计算损失: cross_entropy(logits, labels)
       c. 反向传播: ∂L/∂B, ∂L/∂A
       d. AdamW 更新: 只更新 LoRA 参数
       e. 梯度累积: 多步合并
4. 保存 LoRA 权重
5. 可选: 合并导出完整模型
```

## 内存策略

| 组件 | 内存占用 | 优化方式 |
|------|----------|----------|
| 基础模型 | 4-bit 量化 | GGUF NF4 |
| LoRA 参数 | fp32 | rank 4-16 |
| 优化器状态 | 2× LoRA | AdamW |
| 激活值 | 检查点 | 只保留关键层 |
| 梯度 | 累积 | 多步合并 |

## 分布式训练

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│  协调器      │     │  Worker 1   │     │  Worker 2   │
│  层 0-11    │────▶│  层 12-23   │────▶│  层 24-35   │
│             │◀────│             │◀────│             │
└─────────────┘     └─────────────┘     └─────────────┘
      AllReduce 梯度同步 (Ring AllReduce)
```

## 关键公式

### LoRA 前向
```
y = W·x + (B·A)·x · (α/r)
```

### LoRA 梯度
```
∂L/∂B = ∂L/∂y · (A·x)^T · (α/r)
∂L/∂A = B^T · ∂L/∂y · x^T · (α/r)
```

### AdamW 更新
```
m_t = β₁·m_{t-1} + (1-β₁)·g
v_t = β₂·v_{t-1} + (1-β₂)·g²
θ_t = θ_{t-1} - lr · m̂_t / (√v̂_t + ε)
```