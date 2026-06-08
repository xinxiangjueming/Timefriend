package com.example.timefriend.ui.screen

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
import com.example.timefriend.ui.theme.GlobalCornerRadius
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SleepInputDialog(
    onDismiss: () -> Unit,
    onSave: (minutes: Int) -> Unit
) {
    var hours by remember { mutableIntStateOf(8) }
    var minutes by remember { mutableIntStateOf(0) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            cornerRadius = GlobalCornerRadius,
            colors = CardDefaults.defaultColors()
        ) {
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(
                            textColor = MiuixTheme.colorScheme.onSurfaceContainerVariant
                        )
                    )
                    TextButton(
                        text = stringResource(R.string.action_save),
                        onClick = {
                            onSave(hours * 60 + minutes)
                            onDismiss()
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        }
    }
}

@Composable
private fun NumberStepper(
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
