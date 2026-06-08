package com.example.timefriend.ui.screen

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timefriend.R
import com.example.timefriend.data.TimeRecord
import com.example.timefriend.ui.component.DayPieChart
import com.example.timefriend.ui.component.PieChartLegend
import com.example.timefriend.ui.theme.GlobalCornerRadius
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun HistoryScreen(
    records: List<TimeRecord>,
    weekRecords: List<TimeRecord>,
    monthRecords: List<TimeRecord>,
    selectedDate: String,
    recordDates: Set<String>,
    onSelectDate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var pieMode by remember { mutableStateOf(0) } // 0 = week, 1 = month
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val calendarContent = @Composable {
        CalendarView(
            selectedDate = selectedDate,
            recordDates = recordDates,
            onSelectDate = onSelectDate
        )
    }

    val pieRecords = if (pieMode == 0) weekRecords else monthRecords
    val pieLabel = if (pieMode == 0) stringResource(R.string.history_week_total)
                   else stringResource(R.string.history_month_total)

    val pieContent = @Composable {
        Card(
            cornerRadius = GlobalCornerRadius,
            colors = CardDefaults.defaultColors()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Week/Month toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PieModeChip(
                        text = stringResource(R.string.calendar_week_mode),
                        selected = pieMode == 0,
                        onClick = { pieMode = 0 }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    PieModeChip(
                        text = stringResource(R.string.calendar_month_mode),
                        selected = pieMode == 1,
                        onClick = { pieMode = 1 }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (pieRecords.isNotEmpty()) {
                    DayPieChart(records = pieRecords, size = 180.dp, strokeWidth = 22.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    PieChartLegend(records = pieRecords)
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (pieMode == 0) stringResource(R.string.history_no_records_week)
                                   else stringResource(R.string.history_no_records_month),
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }

    if (isLandscape) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Card(
                cornerRadius = GlobalCornerRadius,
                colors = CardDefaults.defaultColors(),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(end = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    calendarContent()
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                pieContent()
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                cornerRadius = GlobalCornerRadius,
                colors = CardDefaults.defaultColors()
            ) {
                Box(modifier = Modifier.padding(12.dp)) {
                    calendarContent()
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            pieContent()
        }
    }
}

@Composable
private fun CalendarView(
    selectedDate: String,
    recordDates: Set<String>,
    onSelectDate: (String) -> Unit
) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val monthFormat = SimpleDateFormat(
        stringResource(R.string.calendar_month_format), Locale.CHINESE
    )

    var baseDate by remember { mutableStateOf(Calendar.getInstance()) }

    val cal = baseDate.clone() as Calendar
    cal.set(Calendar.DAY_OF_MONTH, 1)
    val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

    val today = dateFormat.format(java.util.Date())
    val weekdays = stringArrayResource(R.array.weekday_headers)

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.calendar_prev_month),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable {
                        val prev = baseDate.clone() as Calendar
                        prev.add(Calendar.MONTH, -1)
                        baseDate = prev
                    }
                    .padding(8.dp),
                color = MiuixTheme.colorScheme.onSurface
            )
            Text(
                text = monthFormat.format(baseDate.time),
                fontSize = 18.sp,
                color = MiuixTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.calendar_next_month),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable {
                        val next = baseDate.clone() as Calendar
                        next.add(Calendar.MONTH, 1)
                        baseDate = next
                    }
                    .padding(8.dp),
                color = MiuixTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            weekdays.forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        val totalCells = firstDayOfWeek - 1 + daysInMonth
        val rows = (totalCells + 6) / 7

        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0..6) {
                    val index = row * 7 + col
                    val dayNum = index - (firstDayOfWeek - 1) + 1

                    if (dayNum in 1..daysInMonth) {
                        cal.set(Calendar.DAY_OF_MONTH, dayNum)
                        val dateStr = dateFormat.format(cal.time)

                        DayCell(
                            day = dayNum,
                            isToday = dateStr == today,
                            isSelected = dateStr == selectedDate,
                            hasData = dateStr in recordDates,
                            onClick = { onSelectDate(dateStr) },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    isToday: Boolean,
    isSelected: Boolean,
    hasData: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = when {
        isSelected -> MiuixTheme.colorScheme.primary
        isToday -> MiuixTheme.colorScheme.primary.copy(alpha = 0.15f)
        else -> MiuixTheme.colorScheme.surface.copy(alpha = 0f)
    }
    val textColor = when {
        isSelected -> MiuixTheme.colorScheme.onPrimary
        isToday -> MiuixTheme.colorScheme.primary
        else -> MiuixTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .background(bgColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$day",
                fontSize = 14.sp,
                color = textColor
            )
            if (hasData) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MiuixTheme.colorScheme.onPrimary
                            else MiuixTheme.colorScheme.primary
                        )
                )
            }
        }
    }
}

@Composable
private fun PieModeChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceContainerHigh
    val textColor = if (selected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(GlobalCornerRadius))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = textColor
        )
    }
}
