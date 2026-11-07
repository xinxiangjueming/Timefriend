package com.example.timefriend.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.example.timefriend.ui.component.BlurredDialogScaffold
import com.example.timefriend.ui.component.DialogButtonRow
import com.example.timefriend.ui.component.DialogCancelButton
import com.example.timefriend.ui.component.DialogConfirmButton
import com.example.timefriend.ui.component.DialogOverlay
import com.example.timefriend.ui.component.NumberStepper
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 睡眠输入弹窗（版本自适应玻璃卡）。
 *
 * 经 [DialogOverlay] 注册到宿主 slot（MainScreen Layer 6），与采样源同窗口兄弟：
 * - 高版本（API 33+）：官方 DialogContent 液态玻璃卡（colorControls → blur →
 *   lens(depthEffect) + Highlight.Plain + containerColor），外部压暗画进采样源；
 * - 低版本：外部 Haze 20dp 模糊 + 25% 压暗 + 不透明实心 tint 卡
 *   （miuixBlur=false，浅色 White / 深色 #3A3A3A，无 miuix 模糊）。
 * - 宽度由 BlurredDialogScaffold 内部 dialogWidthAdaptive 统一；
 *   点击外部关闭、返回键关闭由 scaffold / BackHandler 承担。
 */
@Composable
fun SleepInputDialog(
    onDismiss: () -> Unit,
    onSave: (minutes: Int) -> Unit
) {
    var hours by remember { mutableIntStateOf(8) }
    var minutes by remember { mutableIntStateOf(0) }

    DialogOverlay {
        BackHandler { onDismiss() }
        BlurredDialogScaffold(onDismissRequest = onDismiss, miuixBlur = false) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.sleep_dialog_title),
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
                        value = hours,
                        range = 0..23,
                        onValueChange = { hours = it }
                    )

                    Text(
                        text = " : ",
                        fontSize = 28.sp,
                        color = MiuixTheme.colorScheme.onSurface
                    )

                    NumberStepper(
                        value = minutes,
                        range = 0..59,
                        onValueChange = { minutes = it }
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
                            text = stringResource(R.string.action_save),
                            onClick = {
                                onSave(hours * 60 + minutes)
                                onDismiss()
                            }
                        )
                    }
                )
            }
        }
    }
}
