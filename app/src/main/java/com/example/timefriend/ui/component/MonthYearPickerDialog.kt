package com.example.timefriend.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timefriend.R
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 年月选择弹窗（复刻 SportLink ui/MonthYearPickerDialog.kt，适配本项目 DialogBlur 基础设施）。
 *
 * 结构对齐 SportLink：标题 + 年/月两个 NumberStepper（年 2020–2035 / 月 1–12）+
 * DialogButtonRow（取消/确定）。视觉走 [BlurredDialogScaffold] 统一壳：
 * 高版本为官方 DialogContent 液态玻璃卡（外部压暗画进采样源），低版本为
 * 外部 Haze scrim（20dp 模糊 + 25% 压暗）+ textureBlur 10f 玻璃卡，
 * 经 [DialogOverlay] 注册到宿主 slot 同窗口渲染。
 * 全部可见文本走 stringResource（标题/取消/确定五套语言同步）。
 *
 * @param initialYear  初始年（如 2026）
 * @param initialMonth 初始月（1–12）
 * @param onConfirm    确认回调（year、month 均为自然值，month 1–12）
 */
@Composable
fun MonthYearPickerDialog(
    initialYear: Int,
    initialMonth: Int,
    onDismiss: () -> Unit,
    onConfirm: (year: Int, month: Int) -> Unit,
) {
    var year by remember { mutableIntStateOf(initialYear) }
    var month by remember { mutableIntStateOf(initialMonth) }

    DialogOverlay {
        BackHandler { onDismiss() }
        // 宽度由 BlurredDialogScaffold 内部 dialogWidthAdaptive 统一（对齐 SportLink）
        BlurredDialogScaffold(onDismissRequest = onDismiss) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.month_year_picker_title),
                    fontSize = 20.sp,
                    color = MiuixTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NumberStepper(
                        value = year,
                        range = 2020..2035,
                        onValueChange = { year = it }
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    NumberStepper(
                        value = month,
                        range = 1..12,
                        onValueChange = { month = it }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                DialogButtonRow(
                    cancel = {
                        DialogCancelButton(
                            text = stringResource(R.string.action_cancel),
                            onClick = onDismiss
                        )
                    },
                    confirm = {
                        DialogConfirmButton(
                            text = stringResource(R.string.action_confirm),
                            onClick = {
                                onConfirm(year, month)
                                onDismiss()
                            }
                        )
                    }
                )
            }
        }
    }
}

/**
 * ▲/▼ 步进选择器（从 SportLink MonthYearPickerDialog 复刻，internal 同包共享：
 * 年月弹窗与睡眠输入弹窗的时/分 stepper 共用）。
 */
@Composable
internal fun NumberStepper(
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TextButton(
            text = "▲",
            onClick = { if (value < range.last) onValueChange(value + 1) },
            colors = ButtonDefaults.textButtonColorsPrimary()
        )

        Text(
            text = String.format("%02d", value),
            fontSize = 36.sp,
            color = MiuixTheme.colorScheme.onSurface
        )

        TextButton(
            text = "▼",
            onClick = { if (value > range.first) onValueChange(value - 1) },
            colors = ButtonDefaults.textButtonColorsPrimary()
        )
    }
}
