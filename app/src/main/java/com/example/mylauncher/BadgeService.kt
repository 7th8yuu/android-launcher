package com.example.mylauncher

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Đếm thông báo theo ứng dụng để hiện chấm đỏ trên icon. Không lưu hay gửi nội dung thông báo. */
class BadgeService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = true
        refresh()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        connected = false
        counts = emptyMap()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    private fun refresh() {
        try {
            val list = activeNotifications ?: return
            val m = HashMap<String, Int>()
            for (n in list) {
                if (n.isOngoing) continue
                if ((n.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) continue
                m[n.packageName] = (m[n.packageName] ?: 0) + 1
            }
            counts = m
        } catch (_: Exception) {
        }
    }

    companion object {
        var counts by mutableStateOf<Map<String, Int>>(emptyMap())
            private set
        var connected by mutableStateOf(false)
            private set
    }
}
