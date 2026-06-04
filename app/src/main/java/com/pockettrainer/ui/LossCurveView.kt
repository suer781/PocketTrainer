package com.pockettrainer.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min

@Composable
fun LossCurveView(
    lossHistory: List<Float>,
    currentLoss: Float,
    modifier: Modifier = Modifier
) {
    if (lossHistory.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            Text("等待训练数据...", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val textMeasurer = rememberTextMeasurer()
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant

    // 平滑后的曲线
    val smoothedLoss = remember(lossHistory) { smoothCurve(lossHistory, windowSize = 5) }
    val rawLoss = lossHistory

    // Y 轴范围
    val minLoss = (rawLoss.min() * 0.9f)
    val maxLoss = (rawLoss.max() * 1.1f)
    val lossRange = max(maxLoss - minLoss, 0.001f)

    // 动画进度
    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(lossHistory.size) {
        animProgress.snapTo(0f)
        animProgress.animateTo(1f, animationSpec = tween(300, easing = EaseOutCubic))
    }

    Column(modifier = modifier) {
        // 标题行
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Loss 曲线",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "当前: %.4f".format(currentLoss),
                style = MaterialTheme.typography.bodySmall,
                color = lineColor
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(surfaceColor, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            val w = size.width
            val h = size.height
            val padding = 40f

            // 网格线
            val gridLines = 5
            for (i in 0..gridLines) {
                val y = h - padding - (h - 2 * padding) * i / gridLines
                drawLine(gridColor, Offset(padding, y), Offset(w - 10f, y), strokeWidth = 0.5f)

                val lossVal = minLoss + lossRange * i / gridLines
                val textResult = textMeasurer.measure(
                    AnnotatedString("%.2f".format(lossVal)),
                    style = TextStyle(fontSize = 8.sp, color = textColor)
                )
                drawText(textResult, topLeft = Offset(0f, y - textResult.size.height / 2))
            }

            // X 轴标签
            if (rawLoss.size > 1) {
                val xLabels = listOf(0, rawLoss.size / 4, rawLoss.size / 2, rawLoss.size * 3 / 4, rawLoss.size - 1)
                for (idx in xLabels) {
                    val x = padding + (w - padding - 10f) * idx / max(rawLoss.size - 1, 1)
                    val textResult = textMeasurer.measure(
                        AnnotatedString("$idx"),
                        style = TextStyle(fontSize = 8.sp, color = textColor)
                    )
                    drawText(textResult, topLeft = Offset(x - textResult.size.width / 2, h - 10f))
                }
            }

            // 绘制原始数据（淡色）
            drawLossLine(rawLoss, gridColor.copy(alpha = 0.3f), minLoss, lossRange, w, h, padding, animProgress.value)

            // 绘制平滑曲线（主色）
            drawLossLine(smoothedLoss, lineColor, minLoss, lossRange, w, h, padding, animProgress.value)

            // 最新点高亮
            if (rawLoss.isNotEmpty()) {
                val lastIdx = rawLoss.size - 1
                val x = padding + (w - padding - 10f) * lastIdx / max(rawLoss.size - 1, 1)
                val y = h - padding - (h - 2 * padding) * ((rawLoss[lastIdx] - minLoss) / lossRange) * animProgress.value
                drawCircle(lineColor, radius = 5f, center = Offset(x, y))
                drawCircle(Color.White, radius = 3f, center = Offset(x, y))
            }
        }
    }
}

private fun DrawScope.drawLossLine(
    data: List<Float>,
    color: Color,
    minLoss: Float,
    lossRange: Float,
    w: Float,
    h: Float,
    padding: Float,
    animProgress: Float
) {
    if (data.size < 2) return
    val path = Path()
    val count = (data.size * animProgress).toInt().coerceAtLeast(2)

    for (i in 0 until count) {
        val x = padding + (w - padding - 10f) * i / max(data.size - 1, 1)
        val y = h - padding - (h - 2 * padding) * ((data[i] - minLoss) / lossRange)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }

    drawPath(path, color, style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** 简单移动平均平滑 */
private fun smoothCurve(data: List<Float>, windowSize: Int = 5): List<Float> {
    if (data.size <= windowSize) return data
    return data.windowed(windowSize, 1, partialWindows = true) { window ->
        window.average().toFloat()
    }
}
