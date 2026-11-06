package eu.darken.sdmse.appcleaner.ui.settings

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.common.adb.local.DirectAdbOptions
import eu.darken.sdmse.common.adb.local.DirectAdbRecovery
import javax.inject.Inject

/**
 * Optional ADB pairing-code reader, scoped to Android Settings only.
 * Never performs a click, controls other apps or participates in cache deletion.
 */
@AndroidEntryPoint
class DirectAdbPairingAccessibilityService : AccessibilityService() {
    @Inject lateinit var recovery: DirectAdbRecovery
    private val handler = Handler(Looper.getMainLooper())
    private var previousSignature: Int? = null
    private val poll = object : Runnable {
        var remaining = 0
        override fun run() {
            if (readCode()) return
            if (remaining-- > 0) handler.postDelayed(this, 400L)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null ||
            !DirectAdbOptions.get(this, DirectAdbOptions.Option.AUTO_PAIR) ||
            recovery.state.value.pairingPort == null ||
            !isAndroidSettings(event.packageName?.toString())) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return
        handler.removeCallbacks(poll)
        poll.remaining = 12
        poll.run()
    }

    private fun readCode(): Boolean {
        if (!DirectAdbOptions.get(this, DirectAdbOptions.Option.AUTO_PAIR) ||
            recovery.state.value.pairingPort == null) return true
        val root = rootInActiveWindow ?: return false
        if (!isAndroidSettings(root.packageName?.toString())) return false
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        val labels = mutableListOf<String>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited++ < 100) {
            val node = queue.removeFirst()
            node.text?.toString()?.let(labels::add)
            node.contentDescription?.toString()?.let(labels::add)
            repeat(node.childCount.coerceAtMost(20)) { index ->
                node.getChild(index)?.let(queue::add)
            }
        }
        if (labels.none { it.contains("pairing code", ignoreCase = true) ||
                          it.contains("pair device with", ignoreCase = true) ||
                          it.contains("pair with device", ignoreCase = true) }) return false
        val code = labels.asSequence().map(String::trim)
            .firstOrNull { it.matches(Regex("[0-9]{6}")) }
            ?: labels.asSequence().filter { it.contains("pairing code", true) }
                .mapNotNull { Regex("(?<![0-9])[0-9]{6}(?![0-9])").find(it)?.value }
                .firstOrNull()
            ?: return false
        // No code, source window text, or paired device identity is saved or logged.
        val signature = code.hashCode()
        if (signature != previousSignature) {
            previousSignature = signature
            recovery.submitPairingCode(code)
        }
        return true
    }

    private fun isAndroidSettings(pkg: String?) = pkg in setOf(
        "com.android.settings", "com.google.android.settings",
        "com.samsung.android.settings", "com.android.tv.settings"
    )

    override fun onInterrupt() { handler.removeCallbacks(poll) }
    override fun onDestroy() {
        handler.removeCallbacks(poll)
        super.onDestroy()
    }
}
