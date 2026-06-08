package com.example.timefriend.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timefriend.ui.theme.GlobalCornerRadius
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.textureBlurEffect
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun BlurToast(
    message: String,
    backdrop: LayerBackdrop,
    onDismiss: () -> Unit
) {
    var visible by remember { mutableStateOf(true) }

    LaunchedEffect(message) {
        visible = true
        delay(2000)
        visible = false
        delay(300)
        onDismiss()
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        val shape = RoundedCornerShape(GlobalCornerRadius)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    // 1. Shadow
                    .dropShadow(
                        shape = shape,
                        shadow = Shadow(
                            radius = 10.dp,
                            color = Color.Black,
                            alpha = 0.2f
                        )
                    )
                    // 2. Divider border
                    .squircleBackground(
                        color = MiuixTheme.colorScheme.dividerLine,
                        cornerRadius = GlobalCornerRadius
                    )
                    .padding(0.75.dp)
                    // 3. Blur backdrop (drawn behind content)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { shape },
                        effects = {
                            textureBlurEffect(
                                blurRadiusX = BlurDefaults.BlurRadius,
                                blurRadiusY = BlurDefaults.BlurRadius
                            )
                        }
                    )
                    // 4. Semi-transparent tint on top of blur
                    .squircleBackground(
                        color = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.3f),
                        cornerRadius = GlobalCornerRadius
                    )
            ) {
                Text(
                    text = message,
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceContainer,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp)
                )
            }
        }
    }
}
