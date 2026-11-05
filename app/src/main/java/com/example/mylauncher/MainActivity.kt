package com.example.mylauncher

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    lateinit var state: LauncherState
    lateinit var widgetHost: AppWidgetHost
    lateinit var widgetManager: AppWidgetManager

    private var pendingIconKey: String? = null
    private var pendingWidgetId = -1
    private var pendingPage = 0
    private var listening = false

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        val key = pendingIconKey
        if (uri != null && key != null) {
            lifecycleScope.launch {
                val ok = withContext(Dispatchers.IO) { state.icons.saveCustom(key, uri) }
                if (ok) state.store.iconVersion++ else state.toast("Không đọc được ảnh")
            }
        }
    }

    private val bindWidget = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) afterBind(pendingWidgetId)
        else widgetHost.deleteAppWidgetId(pendingWidgetId)
    }

    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            state.reloadApps()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        widgetManager = AppWidgetManager.getInstance(this)
        widgetHost = AppWidgetHost(this, 1024)
        state = LauncherState(this)

        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, pkgReceiver, f, ContextCompat.RECEIVER_EXPORTED)

        setContent { LauncherRoot(state) }
        state.reloadApps()
    }

    override fun onStart() {
        super.onStart()
        try { widgetHost.startListening(); listening = true } catch (_: Exception) {}
    }

    override fun onStop() {
        super.onStop()
        if (listening) {
            try { widgetHost.stopListening() } catch (_: Exception) {}
            listening = false
        }
    }

    override fun onResume() {
        super.onResume()
        state.launching = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) state.onHomePressed()
    }

    override fun onDestroy() {
        try { unregisterReceiver(pkgReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }

    // ---------- Hành động ----------
    fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    fun lockScreen() {
        if (!LockService.lock()) {
            toast("Cần bật dịch vụ trợ năng để khoá màn hình")
            state.showSettings = true
        }
    }

    fun pickIconFor(app: AppEntry) {
        pendingIconKey = app.key
        try { pickImage.launch("image/*") } catch (_: Exception) { toast("Không mở được bộ chọn ảnh") }
    }

    fun openAppInfo(app: AppEntry) {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun uninstall(app: AppEntry) {
        startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.pkg}")))
    }

    fun openWallpaperPicker() {
        try {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Chọn hình nền"))
        } catch (_: Exception) { toast("Không mở được trình chọn hình nền") }
    }

    fun openDefaultLauncherSettings() {
        try { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    // ---------- Widget ----------
    fun startAddWidget(info: AppWidgetProviderInfo) {
        val id = widgetHost.allocateAppWidgetId()
        pendingWidgetId = id
        pendingPage = state.currentPage
        if (widgetManager.bindAppWidgetIdIfAllowed(id, info.provider)) {
            afterBind(id)
        } else {
            val i = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            try { bindWidget.launch(i) } catch (_: Exception) {
                widgetHost.deleteAppWidgetId(id)
                toast("Không thể thêm widget này")
            }
        }
    }

    private fun afterBind(id: Int) {
        val info = widgetManager.getAppWidgetInfo(id)
        if (info?.configure != null) {
            try {
                widgetHost.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIG, null)
            } catch (_: Exception) {
                state.addWidget(pendingPage, id)
            }
        } else {
            state.addWidget(pendingPage, id)
        }
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_CONFIG) {
            val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId) ?: pendingWidgetId
            if (resultCode == Activity.RESULT_OK) state.addWidget(pendingPage, id)
            else widgetHost.deleteAppWidgetId(id)
        }
    }

    companion object {
        private const val REQ_CONFIG = 4242
    }
}
