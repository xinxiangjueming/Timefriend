package com.example.timefriend.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.example.timefriend.ui.theme.GlobalCornerRadius
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 弹窗底部双按钮栏：取消居中于左半区、确定居中于右半区。
 *
 * 用两个各占 50% 宽度的 [Box] 各自居中，按钮位置不随文字宽度变化，
 * 始终落在弹窗宽度的 1/4 与 3/4 处。
 *
 * - 双按钮：左右两半区各居中一个。
 * - 单按钮（如仅取消）：整行居中，兼容单按钮场景。
 * - 按钮背景宽度 = 所在半区（或整行）的 [buttonWidthFraction]，由内层
 *   [Box] 提供宽度约束，按钮组件（[DialogCancelButton]/[DialogConfirmButton]）
 *   默认 fillMaxWidth 撑满该约束。
 *
 * @param cancel  取消（左半区）按钮内容，可空。
 * @param confirm 确定（右半区）按钮内容，可空。
 * @param buttonWidthFraction 按钮背景占所在半区（单按钮为整行）的宽度比例，默认 0.8。
 */
@Composable
fun DialogButtonRow(
    modifier: Modifier = Modifier,
    cancel: (@Composable () -> Unit)? = null,
    confirm: (@Composable () -> Unit)? = null,
    buttonWidthFraction: Float = 0.8f,
) {
    when {
        // 双按钮：两个 Box 各占 50%。必须用 weight(1f)（默认 fill=true）而非
        // fillMaxWidth(0.5f)——Row 测量固定子项时 maxWidth = 「剩余宽」（逐个子项累减），
        // 第二个 0.5f Box 实际只有 0.25W，fillMaxWidth 的按钮被撑进窄盒 → 文字竖排/省略号。
        cancel != null && confirm != null -> Row(modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxWidth(buttonWidthFraction), contentAlignment = Alignment.Center) { cancel() }
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxWidth(buttonWidthFraction), contentAlignment = Alignment.Center) { confirm() }
            }
        }
        cancel != null -> Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth(buttonWidthFraction), contentAlignment = Alignment.Center) { cancel() }
        }
        confirm != null -> Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth(buttonWidthFraction), contentAlignment = Alignment.Center) { confirm() }
        }
    }
}

/**
 * miuix 风格弹窗取消按钮（灰底灰字，规格同 sportlink DialogButtonRow.kt）。
 * 统一弹窗按钮为 MIUI 风格：取消 = 次要灰底，确认 = 主题蓝实心底。
 * 圆角跟随项目圆角体系（GlobalCornerRadius = 28dp）。
 *
 * 默认 [Modifier.fillMaxWidth] 撑满父容器宽度约束（配合 [DialogButtonRow]
 * 的 [buttonWidthFraction] 容器使用，使按钮背景占半区 80% 宽）。
 */
@Composable
fun DialogCancelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    MiuixTextButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        cornerRadius = GlobalCornerRadius,
        colors = MiuixButtonDefaults.textButtonColors(
            // 背景：surfaceContainerHigh（浅 #E8E8E8 / 深 #242424）。勿用默认 secondaryVariant
            // （浅 #F0F0F0 / 深 #434343）——与弹窗底几乎同色，灰底"包裹"实机不可见。
            color = MiuixTheme.colorScheme.surfaceContainerHigh,
            // 文字：onSecondaryVariant（浅 #303030 / 深 #D9D9D9）。勿用 onSurfaceContainerVariant
            // ——与灰底对比度仅 ~2.5:1（WCAG AA 要求 4.5:1），字色≈底色。
            textColor = MiuixTheme.colorScheme.onSecondaryVariant,
        ),
    )
}

/**
 * miuix 风格弹窗确认按钮：主题蓝底白字，圆角跟随项目圆角体系
 * （GlobalCornerRadius = 28dp）。
 *
 * 常规确认键统一为蓝色实心；破坏性操作（删除等不可逆确认）将来如需红色
 * DialogDangerConfirmButton，参照 sportlink 同名组件（#D32F2F 底白字）。
 *
 * 默认 [Modifier.fillMaxWidth] 撑满父容器宽度约束（配合 [DialogButtonRow]
 * 的 [buttonWidthFraction] 容器使用，使按钮背景占半区 80% 宽）。
 */
@Composable
fun DialogConfirmButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
) {
    MiuixButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        cornerRadius = GlobalCornerRadius,
        colors = MiuixButtonDefaults.buttonColorsPrimary(),
    ) {
        MiuixText(
            text = text,
            style = MiuixTheme.textStyles.button,
            // 保险：任何宽度不足场景下文字不得竖排换行（两字按钮宁可省略号也不竖排）
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
