package com.example.mylauncher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Dịch vụ trợ năng chỉ dùng để khoá màn hình (chạm 2 lần). Không đọc nội dung màn hình. */
class LockService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        connected = true
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        connected = false
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    companion object {
        @Volatile private var instance: LockService? = null
        var connected by mutableStateOf(false)
            private set

        fun lock(): Boolean {
            val s = instance ?: return false
            return if (Build.VERSION.SDK_INT >= 28) s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) else false
        }
    }
}
