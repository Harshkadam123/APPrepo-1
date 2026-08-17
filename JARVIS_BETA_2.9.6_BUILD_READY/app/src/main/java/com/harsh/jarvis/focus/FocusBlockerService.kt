package com.harsh.jarvis.focus

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.content.Intent
import com.harsh.jarvis.MainActivity

/**
 * Optional local app blocker. It reads the foreground package and, for browser/content
 * protection, may inspect the active accessibility node tree. It does not persist
 * keystrokes, message bodies, screenshots, or extracted page text.
 */
class FocusBlockerService : AccessibilityService() {
    private lateinit var adultProtection: AdultProtectionManager
    private lateinit var distractionGuard: DistractionContentGuard

    override fun onServiceConnected() {
        super.onServiceConnected()
        adultProtection = AdultProtectionManager(this)
        distractionGuard = DistractionContentGuard(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return

        // Adult protection is always active once this Accessibility Service is enabled.
        // There is intentionally no in-app switch or stop action for this protection.
        if (::adultProtection.isInitialized && AdultProtectionManager.isBrowser(packageName)) {
            if (adultProtection.inspectBrowser(packageName, rootInActiveWindow)) return
        }
        if (::distractionGuard.isInitialized && distractionGuard.shouldBlock(packageName, rootInActiveWindow)) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            return
        }

        val manager = FocusManager(this)
        val state = manager.state()
        val limits = DigitalLimits(this)
        if (state == null) {
            val limit = limits.get(packageName)
            if (limit > 0) {
                val used = UsageStatsReader(this).today().firstOrNull { it.packageName == packageName }?.minutes ?: 0L
                if (used >= limit) performGlobalAction(GLOBAL_ACTION_HOME)
            }
            return
        }
        if (state.remaining() <= 0) { manager.stop(completed = true); return }
        if (state.pausedAt != 0L && !state.strict) return
        if (packageName in manager.blockList() && packageName != this.packageName) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("blocked_package", packageName)
            startActivity(i)
        }
    }
    override fun onInterrupt() = Unit
}
