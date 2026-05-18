package com.caseforge.scanner.agent

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay

/**
 * Accessibility service that the AI agent uses to read and operate the X431 app.
 *
 * This service exposes a small set of operations (read_screen / tap / type / scroll / back /
 * wait_for) that the agent loop in [AgentRunner] calls as tools. It also detects VINs on
 * screen and notifies listeners so we can auto-start a diagnostic session.
 */
class ScannerAccessibilityService : AccessibilityService() {

    companion object {
        const val TAG = "X431Agent.A11y"

        // The X431 family ships under several package names depending on model/region.
        val X431_PACKAGES = setOf(
            "com.cnlaunch.x431padv",
            "com.cnlaunch.x431padv2",
            "com.cnlaunch.diagnose.x431pro",
            "com.cnlaunch.diagnosemodule",
            "com.cnlaunch.x431pro",
            "com.x431.diagnose",
        )

        private val VIN_REGEX = Regex("\\b[A-HJ-NPR-Z0-9]{17}\\b")

        @Volatile private var INSTANCE: ScannerAccessibilityService? = null
        fun instance(): ScannerAccessibilityService? = INSTANCE

        // Pluggable callbacks
        @Volatile var onVinDetected: ((String) -> Unit)? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        INSTANCE = this
        Log.i(TAG, "ScannerAccessibilityService connected")
    }

    override fun onDestroy() {
        if (INSTANCE === this) INSTANCE = null
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.packageName?.toString() !in X431_PACKAGES) return

        // Cheap VIN detection on any text change.
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> scanForVin()
        }
    }

    private fun scanForVin() {
        val root = rootInActiveWindow ?: return
        val visibleText = StringBuilder()
        collectText(root, visibleText, depthLimit = 12)
        VIN_REGEX.find(visibleText.toString())?.value?.let { vin ->
            onVinDetected?.invoke(vin)
        }
    }

    private fun collectText(node: AccessibilityNodeInfo?, out: StringBuilder, depthLimit: Int) {
        node ?: return
        if (depthLimit <= 0) return
        node.text?.toString()?.let { if (it.isNotBlank()) out.append(it).append('\n') }
        node.contentDescription?.toString()?.let { if (it.isNotBlank()) out.append(it).append('\n') }
        for (i in 0 until node.childCount) {
            collectText(node.getChild(i), out, depthLimit - 1)
        }
    }

    // -------- Tool implementations called by AgentRunner --------

    /** Read the current X431 screen as a serializable tree the LLM can reason about. */
    fun readScreen(): ScreenSnapshot {
        val root = rootInActiveWindow
            ?: return ScreenSnapshot(pkg = null, activity = null, nodes = emptyList(), text = "")
        val nodes = mutableListOf<UiNode>()
        val textBuf = StringBuilder()
        walk(root, nodes, textBuf)
        return ScreenSnapshot(
            pkg = root.packageName?.toString(),
            activity = null,
            nodes = nodes,
            text = textBuf.toString().trim(),
        )
    }

    private fun walk(
        node: AccessibilityNodeInfo?,
        out: MutableList<UiNode>,
        textBuf: StringBuilder,
        depth: Int = 0,
    ) {
        node ?: return
        if (depth > 24) return
        val rect = Rect().also { node.getBoundsInScreen(it) }
        val text = node.text?.toString().orEmpty()
        val desc = node.contentDescription?.toString().orEmpty()
        val display = text.ifBlank { desc }
        if (display.isNotBlank()) textBuf.append(display).append('\n')

        if (display.isNotBlank() || node.isClickable || node.isEditable) {
            out.add(
                UiNode(
                    id = out.size,
                    text = display.take(120),
                    className = node.className?.toString()?.substringAfterLast('.'),
                    clickable = node.isClickable,
                    editable = node.isEditable,
                    scrollable = node.isScrollable,
                    bounds = listOf(rect.left, rect.top, rect.right, rect.bottom),
                )
            )
        }
        for (i in 0 until node.childCount) walk(node.getChild(i), out, textBuf, depth + 1)
    }

    /** Click whatever clickable element matches [predicate]. Returns true if a tap was dispatched. */
    fun tapByText(query: String, exact: Boolean = false): Boolean {
        val root = rootInActiveWindow ?: return false
        val match = findFirst(root) { n ->
            val t = (n.text?.toString() ?: n.contentDescription?.toString() ?: "")
            n.isClickable && (if (exact) t.equals(query, true) else t.contains(query, true))
        } ?: run {
            // Try non-clickable that has a clickable ancestor.
            findFirst(root) { n ->
                val t = (n.text?.toString() ?: n.contentDescription?.toString() ?: "")
                if (exact) t.equals(query, true) else t.contains(query, true)
            }?.let { climbToClickable(it) }
        } ?: return false
        return match.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    fun tapAt(x: Int, y: Int): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val done = CompletableDeferred<Boolean>()
        val ok = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) { done.complete(true) }
            override fun onCancelled(g: GestureDescription?) { done.complete(false) }
        }, Handler(Looper.getMainLooper()))
        return ok
    }

    fun typeInto(targetText: String?, value: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val target = if (targetText == null) {
            findFirst(root) { it.isEditable && it.isFocused } ?: findFirst(root) { it.isEditable }
        } else {
            findFirst(root) { it.isEditable && (it.text?.toString()?.contains(targetText, true) == true) }
                ?: findFirst(root) { it.isEditable }
        } ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun scroll(direction: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val scrollable = findFirst(root) { it.isScrollable } ?: return false
        val action = when (direction.lowercase()) {
            "down", "forward" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            "up", "backward" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            else -> return false
        }
        return scrollable.performAction(action)
    }

    fun back(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    suspend fun waitFor(textContains: String, timeoutMs: Long = 8000L): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            val root = rootInActiveWindow
            if (root != null) {
                val found = findFirst(root) { n ->
                    val t = (n.text?.toString() ?: n.contentDescription?.toString() ?: "")
                    t.contains(textContains, true)
                }
                if (found != null) return true
            }
            delay(250)
        }
        return false
    }

    // --- helpers ---

    private fun findFirst(
        root: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            val n = stack.removeLast()
            if (predicate(n)) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let(stack::addLast)
        }
        return null
    }

    private fun climbToClickable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var cur: AccessibilityNodeInfo? = node
        repeat(6) {
            cur ?: return null
            if (cur!!.isClickable) return cur
            cur = cur!!.parent
        }
        return null
    }
}

/** A serializable snapshot of the X431 UI for the agent to reason over. */
@kotlinx.serialization.Serializable
data class ScreenSnapshot(
    val pkg: String?,
    val activity: String?,
    val nodes: List<UiNode>,
    val text: String,
)

@kotlinx.serialization.Serializable
data class UiNode(
    val id: Int,
    val text: String,
    val className: String?,
    val clickable: Boolean,
    val editable: Boolean,
    val scrollable: Boolean,
    val bounds: List<Int>, // [left, top, right, bottom]
)
