package com.example.appsapp

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import java.util.concurrent.LinkedBlockingQueue

class FreezeAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "FreezeService"
        private val packageQueue = LinkedBlockingQueue<String>()
        private var isProcessing = false
        private var isMenuFreeze = false
        private var currentPackage: String? = null
        private var mainButtonClicked = false
        private var isWaitingForNext = false
        private var lastActionTime = 0L
        private val mainHandler = Handler(Looper.getMainLooper())
        private var instance: FreezeAccessibilityService? = null

        fun startFreezing(
            context: Context,
            packageNames: List<String>,
            isMenuFreeze1: Boolean = false
        ) {
            val filtered = packageNames.filter { it != context.packageName }
            packageQueue.clear()
            packageQueue.addAll(filtered)
            isProcessing = true
            mainButtonClicked = false
            Log.d(TAG, "")
            Log.d(TAG, "")
            Log.d(TAG, "CALL1: $isProcessing")
            isMenuFreeze = isMenuFreeze1
            processNext(context)
        }

        private fun processNext(context: Context) {
            currentPackage = packageQueue.poll()
            mainButtonClicked = false
            isWaitingForNext = false
            Log.d(TAG, "POLL: $currentPackage CTX ${context.packageName}")

            if (currentPackage != null) {
                if (currentPackage == context.packageName) {
                    Log.d(TAG, "")
                    Log.d(TAG, "SKIP: $currentPackage")
                    processNext(context)
                    return
                }
                Log.d(TAG, "")
                Log.d(TAG, "")
                Log.d(TAG, "Starting to freeze: $currentPackage")
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$currentPackage")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                    addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                }
                context.startActivity(intent)
//                intent.let { context.startActivity(it) }
            } else {
                Log.d(TAG, "Queue empty, finished.")

                val sharedPref = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                val turnOffScreen = sharedPref.getBoolean("turn_off_screen", false)

                if (turnOffScreen && !isMenuFreeze) {
                    Toast.makeText(
                        context,
                        R.string.status_closing_apps_turning_off,
                        Toast.LENGTH_SHORT
                    ).show()
                    lockScreen()
                }
                stopAutomation()
                returnToMainApp(context)
            }
        }

        private fun returnToMainApp(context: Context) {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            intent?.let { context.startActivity(it) }
        }

        fun stopAutomation() {
            Log.d(TAG, "Automation stopped.")
            packageQueue.clear()
            isProcessing = false
            isMenuFreeze = false
            currentPackage = null
            mainButtonClicked = false
            isWaitingForNext = false
            mainHandler.removeCallbacksAndMessages("retry_all")
        }

        fun lockScreen() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!isProcessing || isWaitingForNext) return

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val nodeId = event.source?.viewIdResourceName
            if (nodeId == "android:id/button2") {
                Log.d(TAG, "User clicked Cancel manually. Stopping queue.")
                stopAutomation()
                return
            }
        }

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        val sharedPref = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val lastActionDelay = sharedPref.getInt("last_action_time", 500).toLong()

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastActionTime < lastActionDelay) return

        val allWindows = windows
        for (window in allWindows) {
            val root = window.root ?: continue

            if (handleDialog(root)) {
                lastActionTime = System.currentTimeMillis()
                return
            }

            if (handleAppInfo(root)) {
                lastActionTime = System.currentTimeMillis()
                return
            }
        }

        mainHandler.removeCallbacksAndMessages("retry_all")
        mainHandler.postDelayedCompat({
            if (isProcessing) {
                for (w in windows) {
                    val r = w.root ?: continue
                    if (handleDialog(r) || handleAppInfo(r)) {
                        lastActionTime = System.currentTimeMillis()
                        break
                    }
                }
            }
        }, 600, "retry_all")
    }

    private fun handleDialog(root: AccessibilityNodeInfo): Boolean {
        // Pixel 6a specific: button is likely "OK" or "Force stop" (NO question mark)
        val dialogTexts = listOf("OK", "ОК", "Force stop", "Остановить")

        // 2. Search for clickable nodes matching text
        val clickableNode = recursiveSearchClickable(root, dialogTexts)
        if (clickableNode != null) {
            Log.d(TAG, "Found clickable dialog button via text: ${clickableNode.windowId}")
            Log.d(TAG, "Found clickable dialog button via text: ${clickableNode.text}")
            Log.d(TAG, "TRY CLICK")
            val nodeText = clickableNode.text?.toString()?.uppercase() ?: ""

            if (performAggressiveClick(clickableNode)) {
                Log.d(TAG, "Confirmed dialog via text match for $currentPackage")
                currentPackage?.let { FreezeStatusObserver.notifyPackageFrozen(it) }
                isWaitingForNext = true

                Log.d(TAG, "Found clickable dialog button via is: ${nodeText == "OK"}")
                if (nodeText == "OK") {
                    // close current intent
                    goBackAndContinue()
                }
                return true
            } else {
                Log.d(TAG, "TRY CLICK FALSE")
                return false
            }
        }

        // If we found "Force stop?" (the title) but no button yet, dump the window to help debug
        val titleMatch = root.findAccessibilityNodeInfosByText("Force stop?")
        if (!titleMatch.isNullOrEmpty()) {
            Log.d(
                TAG,
                "Dialog title 'Force stop?' detected, but button not found yet. Dumping window..."
            )
            dumpNodes(root, 0)
        }

        return false
    }

    private fun handleAppInfo(root: AccessibilityNodeInfo): Boolean {
        if (mainButtonClicked) return false

        val ids = listOf(
            "com.android.settings:id/force_stop_button",
            "com.android.settings:id/right_button",
            "com.android.settings:id/action_button"
        )
        val texts = listOf("Force stop", "Остановить")

        for (id in ids) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            for (node in nodes) {
                if (node.isVisibleToUser) {
                    if (node.isEnabled) {
                        if (performAggressiveClick(node)) {
                            Log.d(TAG, "Clicked main Force Stop (ID: $id) for $currentPackage")
                            mainButtonClicked = true
                            return true
                        }
                    } else {
                        Log.d(TAG, "Main button disabled, skipping $currentPackage")
                        currentPackage?.let { FreezeStatusObserver.notifyPackageFrozen(it) }
                        isWaitingForNext = true
                        goBackAndContinue()
                        return true
                    }
                }
            }
        }

        val nodeByText = recursiveSearchClickable(root, texts)
        if (nodeByText != null) {
            // Ensure it's not the dialog button by checking ID
            if (nodeByText.viewIdResourceName != "android:id/button1") {
                if (nodeByText.isEnabled) {
                    if (performAggressiveClick(nodeByText)) {
                        Log.d(TAG, "Clicked main Force Stop (Text) for $currentPackage")
                        mainButtonClicked = true
                        return true
                    }
                } else {
                    Log.d(TAG, "Main button (Text) disabled, skipping $currentPackage")
                    currentPackage?.let { FreezeStatusObserver.notifyPackageFrozen(it) }
                    isWaitingForNext = true
                    goBackAndContinue()
                    return true
                }
            }
        }
        return false
    }

    private fun performAggressiveClick(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            Log.d(TAG, "CLICK FIRST CLICKABLE")
            return true
        }
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                Log.d(TAG, "CLICK WHILE TRUE")
                return true
            }
            parent = parent.parent
        }

        if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            Log.d(TAG, "CLICK NODE !!! TRUE")
            return true
        }
        Log.d(TAG, "CLICK NODE LAST")
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun recursiveSearchClickable(
        node: AccessibilityNodeInfo,
        texts: List<String>
    ): AccessibilityNodeInfo? {
        val nodeText = node.text?.toString()?.uppercase() ?: ""
        val contentDesc = node.contentDescription?.toString()?.uppercase() ?: ""

        for (text in texts) {
            val target = text.uppercase()
            // Strict match for text to avoid matching titles like "Force stop?"
            val matches =
                nodeText == target || nodeText.startsWith("$target ") || contentDesc == target

            if (matches) {
                // Check if this node or any parent is clickable
                if (node.isClickable || isAnyParentClickable(node)) {
                    return node
                }
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = recursiveSearchClickable(child, texts)
            if (result != null) return result
        }
        return null
    }

    private fun isAnyParentClickable(node: AccessibilityNodeInfo): Boolean {
        var p = node.parent
        while (p != null) {
            if (p.isClickable) return true
            p = p.parent
        }
        return false
    }

    private fun dumpNodes(node: AccessibilityNodeInfo, depth: Int) {
        if (depth > 20) return // Safety
        val indent = "  ".repeat(depth)
        Log.d(
            TAG,
            "${indent}Text: ${node.text} | ID: ${node.viewIdResourceName} | Clickable: ${node.isClickable} | Visible: ${node.isVisibleToUser}"
        )
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            dumpNodes(child, depth + 1)
        }
    }

    private fun goBackAndContinue() {
        val sharedPref = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val returnTime = sharedPref.getInt("return_time", 300).toLong()
        val nextTime = sharedPref.getInt("next_time", 400).toLong()

        mainHandler.postDelayedCompat({
            performGlobalAction(GLOBAL_ACTION_BACK)
            returnToMainApp(this)
            mainHandler.postDelayedCompat({
                processNext(this)
            }, nextTime)
        }, returnTime)
    }

    override fun onInterrupt() {}
}

fun Handler.postDelayedCompat(runnable: Runnable, delayMillis: Long, token: Any? = "") {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        postDelayed(runnable, token, delayMillis)
    } else {
        val message = Message.obtain(this, runnable)
        message.obj = token
        sendMessageDelayed(message, delayMillis)
    }
}
