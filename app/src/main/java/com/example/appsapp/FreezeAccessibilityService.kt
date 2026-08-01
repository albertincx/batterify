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
        
        // Listen for user manual clicks on Cancel button
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val nodeId = event.source?.viewIdResourceName
            if (nodeId == "android:id/button2") {
                Log.d(TAG, "User clicked Cancel manually. Stopping queue.")
                stopAutomation()
                return
            }
        }

        // Listen for Window changes
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && 
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastActionTime < 300) return

        // Search in all interactive windows (dialogs are often separate windows)
        val allWindows = windows
        for (window in allWindows) {
            val root = window.root ?: continue
            
            // Step 1: Handle the "OK" / confirmation dialog
            if (handleDialog(root)) {
                lastActionTime = System.currentTimeMillis()
                return
            }

            // Step 2: Handle the main "Force Stop" button in App Info
            if (handleAppInfo(root)) {
                lastActionTime = System.currentTimeMillis()
                return
            }
        }

        // Retry mechanism for cases where the window is just loading
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
        }, "retry_all", 500)
    }

    private fun handleDialog(root: AccessibilityNodeInfo): Boolean {
        val dialogTexts = listOf("OK", "ОК", "Force stop", "Остановить", "Force stop?", "Остановить?")
        
        // 1. Search by system ID button1
        val dialogButtons = root.findAccessibilityNodeInfosByViewId("android:id/button1")
        if (!dialogButtons.isNullOrEmpty()) {
            for (node in dialogButtons) {
                if (node.isVisibleToUser && node.isEnabled) {
                    if (performAggressiveClick(node)) {
                        Log.d(TAG, "Clicked Dialog button1 for $currentPackage")
                        goBackAndContinue()
                        return true
                    }
                }
            }
        }

        // 2. Search by text (recursive)
        val nodeByText = recursiveSearch(root, dialogTexts, true)
        if (nodeByText != null && nodeByText.isVisibleToUser && nodeByText.isEnabled) {
            if (performAggressiveClick(nodeByText)) {
                Log.d(TAG, "Clicked Dialog button by text for $currentPackage")
                goBackAndContinue()
                return true
            }
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
        
        // Try IDs
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
                        Log.d(TAG, "Main button already disabled for $currentPackage, skipping")
                        goBackAndContinue()
                        return true
                    }
                }
            }
        }

        // Try Text (recursive)
        val nodeByText = recursiveSearch(root, texts, false)
        if (nodeByText != null && nodeByText.isVisibleToUser) {
            if (nodeByText.isEnabled) {
                if (performAggressiveClick(nodeByText)) {
                    Log.d(TAG, "Clicked main Force Stop (Text) for $currentPackage")
                    mainButtonClicked = true
                    return true
                }
            } else {
                Log.d(TAG, "Main button (Text) already disabled for $currentPackage, skipping")
                goBackAndContinue()
                return true
            }
        }
        return false
    }

    private fun performAggressiveClick(node: AccessibilityNodeInfo): Boolean {
        // Try direct click
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        
        // Try clicking parents
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            parent = parent.parent
        }
        
        // Try clicking by coordinates if nothing else works (last resort)
        // Note: performAction(ACTION_CLICK) on a non-clickable node sometimes works if it has clickable descendants or vice-versa
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun recursiveSearch(node: AccessibilityNodeInfo, texts: List<String>, isDialogSearch: Boolean): AccessibilityNodeInfo? {
        val nodeText = node.text?.toString()?.uppercase()
        val contentDesc = node.contentDescription?.toString()?.uppercase()
        
        for (text in texts) {
            val target = text.uppercase()
            if ((nodeText != null && nodeText.contains(target)) || (contentDesc != null && contentDesc.contains(target))) {
                if (isDialogSearch) {
                    // Dialogs: prefer button1 or node with "button" in class
                    if (node.viewIdResourceName == "android:id/button1" || node.className?.contains("Button") == true) return node
                } else {
                    // Main info: avoid dialog button1
                    if (node.viewIdResourceName != "android:id/button1") return node
                }
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = recursiveSearch(child, texts, isDialogSearch)
            if (result != null) return result
        }
        return null
    }

    private fun goBackAndContinue() {
        mainHandler.postDelayed({
            performGlobalAction(GLOBAL_ACTION_BACK)
            mainHandler.postDelayed({
                processNext(this)
            }, 800)
        }, 500)
    }

    override fun onInterrupt() {}
}
