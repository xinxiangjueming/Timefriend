package com.example.timefriend.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timefriend.R
import com.example.timefriend.data.TimeCategory
import com.example.timefriend.data.TimeRecord
import com.example.timefriend.ui.theme.CategoryFixed
import com.example.timefriend.ui.theme.CategoryInvestment
import com.example.timefriend.ui.theme.CategorySleep
import com.example.timefriend.ui.theme.CategoryWaste
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

fun categoryColor(category: TimeCategory): Color = when (category) {
    TimeCategory.SLEEP -> CategorySleep
    TimeCategory.WASTE -> CategoryWaste
    TimeCategory.FIXED -> CategoryFixed
    TimeCategory.INVESTMENT -> CategoryInvestment
}

data class PieSlice(
    val category: TimeCategory,
    val minutes: Float,
    val color: Color
)

@Composable
/**
 * 日占比图。两种形态：
 * - [filled] = false（默认）：圆环图（历史版本行为，历史页底部卡片在用）；
 * - [filled] = true：实心饼图，扇形直接以分类色填充（无描边、无中心空洞）。
 * 两种尺寸模式：
 * - [size] 指定（默认调用方：主页/历史页旧路径）：固定正方形，行为与历史版本一致；
 * - [size] = [Dp.Unspecified]：尺寸完全由调用方 [modifier] 的约束决定（如
 *   `Modifier.weight(1f).aspectRatio(1f)` 的弹性正方形），用于等高卡内自适应——
 *   替代"固定尺寸 + 内部 verticalScroll"的滚动兜底（内部滚动会抢走外层页面的
 *   纵向滑动手势，对齐 sportlink 各页 weight 弹性自适应、无内部滚动的设计）。
 * 中心文字字号按实际内径等比缩放（内径 ≈ 14.4%），两种模式观感一致；
 * [showCenterText] = false 时不绘制中心文字（实心饼图中央被色块占据，无可读空间）。
 */
fun DayPieChart(
    records: List<TimeRecord>,
    modifier: Modifier = Modifier,
    size: Dp = Dp.Unspecified,
    strokeWidth: Dp = 28.dp,
    filled: Boolean = false,
    showCenterText: Boolean = true
) {
    val slices = TimeCategory.entries.map { cat ->
        val totalMin = records
            .filter { it.category == cat }
            .sumOf { it.durationMinutes }
            .toFloat()
        PieSlice(cat, totalMin, categoryColor(cat))
    }.filter { it.minutes > 0f }

    val totalMinutes = slices.sumOf { it.minutes.toLong() }.coerceAtLeast(1L).toFloat()
    val totalHours = totalMinutes / 60f

    // 中心文字字号随圆环内径等比缩放：内径 = 尺寸 - strokeWidth。
    // 固定模式用 size 直算；自适应模式用 onSizeChanged 实测尺寸换算（首帧 0 → 占位 14sp，
    // 实测后立即重算，视觉上一帧内完成无感）。
    var measuredSizePx by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val centerTextSize = if (size != Dp.Unspecified) {
        ((size - strokeWidth).value * 0.144f).sp
    } else if (measuredSizePx > 0f) {
        with(density) { ((measuredSizePx - strokeWidth.toPx()).toDp().value * 0.144f).sp }
    } else {
        14.sp
    }

    var animPlayed by remember { mutableStateOf(false) }
    val animProgress by animateFloatAsState(
        targetValue = if (animPlayed) 1f else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "pie_anim"
    )
    LaunchedEffect(records) { animPlayed = false; animPlayed = true }

    Box(
        modifier = modifier
            .onSizeChanged { measuredSizePx = it.height.toFloat() }
            .then(if (size != Dp.Unspecified) Modifier.size(size) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = if (size != Dp.Unspecified) Modifier.size(size)
                       else Modifier.fillMaxSize()
        ) {
            var startAngle = -90f

            if (filled) {
                // 实心饼图：扇形直接以分类色填充（useCenter = true，无描边）。
                // 无数据时不绘制任何内容（原灰色占位圆盘视觉上呈"圆形阴影"，已按需求移除）——
                // Canvas/Box 仍占位，布局尺寸不变，仅画面留空。
                slices.forEach { slice ->
                    val sweep = (slice.minutes / totalMinutes) * 360f * animProgress
                    drawArc(
                        color = slice.color,
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = true
                    )
                    startAngle += sweep
                }
            } else {
                val sweepWidth = strokeWidth.toPx()
                val arcSize = this.size.width - sweepWidth
                val topLeft = sweepWidth / 2f

                if (slices.isEmpty()) {
                    drawArc(
                        color = Color.Gray.copy(alpha = 0.2f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(topLeft, topLeft),
                        size = Size(arcSize, arcSize),
                        style = Stroke(width = sweepWidth, cap = StrokeCap.Round)
                    )
                } else {
                    slices.forEach { slice ->
                        val sweep = (slice.minutes / totalMinutes) * 360f * animProgress
                        drawArc(
                            color = slice.color,
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = Offset(topLeft, topLeft),
                            size = Size(arcSize, arcSize),
                            style = Stroke(width = sweepWidth, cap = StrokeCap.Round)
                        )
                        startAngle += sweep
                    }
                }
            }
        }

        if (showCenterText) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (slices.isNotEmpty()) {
                    val h = totalMinutes.toLong() / 60
                    val m = totalMinutes.toLong() % 60
                    Text(
                        text = stringResource(R.string.pie_duration_format, h, m),
                        fontSize = centerTextSize,
                        color = MiuixTheme.colorScheme.onBackground
                    )
                } else {
                    Text(
                        text = stringResource(R.string.pie_duration_empty),
                        fontSize = centerTextSize,
                        color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}

@Composable
fun PieChartLegend(
    records: List<TimeRecord>,
    modifier: Modifier = Modifier
) {
    val slices = TimeCategory.entries.map { cat ->
        val totalMin = records
            .filter { it.category == cat }
            .sumOf { it.durationMinutes }
            .toFloat()
        PieSlice(cat, totalMin, categoryColor(cat))
    }

    Column(modifier = modifier) {
        slices.forEach { slice ->
            val h = slice.minutes.toLong() / 60
            val m = slice.minutes.toLong() % 60
            LegendItem(
                color = slice.color,
                label = stringResource(slice.category.displayNameRes),
                value = stringResource(R.string.pie_duration_format, h, m)
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(modifier = Modifier.size(12.dp)) {
            drawCircle(color = color)
        }
        Spacer(modifier = Modifier.size(6.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
    }
}
