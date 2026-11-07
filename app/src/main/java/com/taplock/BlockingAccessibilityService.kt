package com.taplock

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class BlockingAccessibilityService : AccessibilityService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var currentState = LockState()
    private var lastBlockedPackage: String? = null
    private var lastBlockedAt: Long = 0L
    private var blockingOverlay: FrameLayout? = null
    private var pendingHomeAction: Runnable? = null

    override fun onServiceConnected() {
        serviceScope.launch {
            LockStore(applicationContext).state.collect {
                currentState = it
                if (!it.isLocked) removeBlockingOverlay()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName == this.packageName) return

        val state = currentState
        if (!state.isLocked || packageName !in state.selected) {
            removeBlockingOverlay()
            return
        }
        val now = System.currentTimeMillis()
        if (packageName == lastBlockedPackage && now - lastBlockedAt < 1200) return
        lastBlockedPackage = packageName
        lastBlockedAt = now
        showBlockingOverlay(packageName)
        if (pendingHomeAction == null) {
            pendingHomeAction = Runnable {
                pendingHomeAction = null
                performGlobalAction(GLOBAL_ACTION_HOME)
            }.also { handler.postDelayed(it, 350L) }
        }
    }

    private fun showBlockingOverlay(blockedPackage: String) {
        if (blockingOverlay != null) return
        val label = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(blockedPackage, 0)).toString()
        }.getOrDefault("This app")

        val panel = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(232, 234, 233))
            isClickable = true
            isFocusable = true
            setOnClickListener { performGlobalAction(GLOBAL_ACTION_HOME) }
        }
        val message = TextView(this).apply {
            text = "$label is locked\n\nYour TapLock focus session is active."
            textSize = 23f
            setTextColor(Color.rgb(42, 48, 45))
            gravity = Gravity.CENTER
            setPadding(36, 36, 36, 36)
        }
        panel.addView(message, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        val layout = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }

        try {
            (getSystemService(WINDOW_SERVICE) as WindowManager).addView(panel, layout)
            blockingOverlay = panel
        } catch (_: Exception) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    private fun removeBlockingOverlay() {
        pendingHomeAction?.let(handler::removeCallbacks)
        pendingHomeAction = null
        val overlay = blockingOverlay ?: return
        blockingOverlay = null
        runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(overlay) }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        removeBlockingOverlay()
        serviceScope.cancel()
        super.onDestroy()
    }
}
