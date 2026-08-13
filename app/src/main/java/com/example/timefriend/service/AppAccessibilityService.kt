package com.example.timefriend.service

import android.accessibilityservice.AccessibilityService

/**
 * 空实现的无障碍服务，仅用于提升进程优先级，防止后台被杀。
 */
class AppAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
