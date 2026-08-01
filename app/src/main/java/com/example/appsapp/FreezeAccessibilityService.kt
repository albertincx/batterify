package com.example.appsapp

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.LinkedBlockingQueue

class FreezeAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "FreezeService"
        private val packageQueue = LinkedBlockingQueue<String>()
        private var isProcessing = false
        private var currentPackage: String? = null
        private var mainButtonClicked = false
        private var lastActionTime = 0L
        private val mainHandler = Handler(Looper.getMainLooper())

        fun startFreezing(context: Context, packageNames: List<String>) {
            packageQueue.clear()
            packageQueue.addAll(packageNames)
            isProcessing = true
            mainButtonClicked = false
            processNext(context)
        }

        private fun processNext(context: Context) {
            currentPackage = packageQueue.poll()
            mainButtonClicked = false
            
            if (currentPackage != null) {
                Log.d(TAG, "Starting to freeze: $currentPackage")
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$currentPackage")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } else {
                isProcessing = false
                Log.d(TAG, "Queue empty, finished.")
            }
        }
        
        fun stopAutomation() {
            Log.d(TAG, "Automation stopped by user or interruption.")
            packageQueue.clear()
            isProcessing = false
            currentPackage = null
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!isProcessing) return
        
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val nodeId = event.source?.viewIdResourceName
            if (nodeId == "android:id/button2") {
                Log.d(TAG, "User clicked Cancel manually. Stopping queue.")
                stopAutomation()
                return
            }
        }

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && 
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastActionTime < 200) return

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
        mainHandler.postDelayed({
            if (isProcessing) {
                for (w in windows) {
                    val r = w.root ?: continue
                    if (handleDialog(r) || handleAppInfo(r)) {
                        lastActionTime = System.currentTimeMillis()
                        break
                    }
                }
            }
        }, "retry_all", 400)
    }

    private fun handleDialog(root: AccessibilityNodeInfo): Boolean {
        // Pixel 6a specific: button is likely "OK" or "Force stop" (NO question mark)
        val dialogTexts = listOf("OK", "ОК", "Force stop", "Остановить")
        
        // 1. Prioritize android:id/button1 (The "Positive" button in standard dialogs)
        val buttons = root.findAccessibilityNodeInfosByViewId("android:id/button1")
        if (!buttons.isNullOrEmpty()) {
            for (node in buttons) {
                if (node.isVisibleToUser && node.isEnabled) {
                    if (performAggressiveClick(node)) {
                        Log.d(TAG, "Confirmed dialog via button1 for $currentPackage")
                        currentPackage?.let { FreezeStatusObserver.notifyPackageFrozen(it) }
                        goBackAndContinue()
                        return true
                    }
                }
            }
        }

        // 2. Search for clickable nodes matching text
        val clickableNode = recursiveSearchClickable(root, dialogTexts)
        if (clickableNode != null) {
            Log.d(TAG, "Found clickable dialog button via text: ${clickableNode.text}")
            if (performAggressiveClick(clickableNode)) {
                Log.d(TAG, "Confirmed dialog via text match for $currentPackage")
                currentPackage?.let { FreezeStatusObserver.notifyPackageFrozen(it) }
                goBackAndContinue()
                return true
            }
        }
        
        // If we found "Force stop?" (the title) but no button yet, dump the window to help debug
        val titleMatch = root.findAccessibilityNodeInfosByText("Force stop?")
        if (!titleMatch.isNullOrEmpty()) {
            Log.d(TAG, "Dialog title 'Force stop?' detected, but button not found yet. Dumping window...")
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
                    goBackAndContinue()
                    return true
                }
            }
        }
        return false
    }

    private fun performAggressiveClick(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            parent = parent.parent
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun recursiveSearchClickable(node: AccessibilityNodeInfo, texts: List<String>): AccessibilityNodeInfo? {
        val nodeText = node.text?.toString()?.uppercase() ?: ""
        val contentDesc = node.contentDescription?.toString()?.uppercase() ?: ""
        
        for (text in texts) {
            val target = text.uppercase()
            // Strict match for text to avoid matching titles like "Force stop?"
            val matches = nodeText == target || nodeText.startsWith(target + " ") || contentDesc == target
            
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
        Log.d(TAG, "${indent}Text: ${node.text} | ID: ${node.viewIdResourceName} | Clickable: ${node.isClickable} | Visible: ${node.isVisibleToUser}")
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            dumpNodes(child, depth + 1)
        }
    }

    private fun goBackAndContinue() {
        mainHandler.postDelayed({
            performGlobalAction(GLOBAL_ACTION_BACK)
            mainHandler.postDelayed({
                processNext(this)
            }, 700)
        }, 300)
    }

    override fun onInterrupt() {}
}
