package com.example.timefriend.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
fun DayPieChart(
    records: List<TimeRecord>,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
    strokeWidth: Dp = 28.dp
) {
    val slices = TimeCategory.entries.map { cat ->
        val totalMin = records
            .filter { it.category == cat }
            .sumOf { it.durationMinutes.toLong() }
            .toFloat()
        PieSlice(cat, totalMin, categoryColor(cat))
    }.filter { it.minutes > 0f }

    val totalMinutes = slices.sumOf { it.minutes.toLong() }.coerceAtLeast(1L).toFloat()
    val totalHours = totalMinutes / 60f

    var animPlayed by remember { mutableStateOf(false) }
    val animProgress by animateFloatAsState(
        targetValue = if (animPlayed) 1f else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "pie_anim"
    )
    LaunchedEffect(records) { animPlayed = false; animPlayed = true }

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val sweepWidth = strokeWidth.toPx()
            val arcSize = this.size.width - sweepWidth
            val topLeft = sweepWidth / 2f
            var startAngle = -90f

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

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (slices.isNotEmpty()) {
                val h = totalMinutes.toLong() / 60
                val m = totalMinutes.toLong() % 60
                Text(
                    text = "${h}h${m}min",
                    fontSize = 28.sp,
                    color = MiuixTheme.colorScheme.onBackground
                )
            } else {
                Text(
                    text = "0h0min",
                    fontSize = 28.sp,
                    color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                )
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
            .sumOf { it.durationMinutes.toLong() }
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
                value = "${h}h${m}min"
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
