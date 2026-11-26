package com.example.appsapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlin.math.abs

class FloatingButtonService : Service(), AccessibilityManager.AccessibilityStateChangeListener {

    companion object {
        const val CHANNEL_ID = "floating_button_channel"
        const val NOTIFICATION_ID = 1001

        fun startService(context: Context) {
            val intent = Intent(context, FloatingButtonService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, FloatingButtonService::class.java)
            context.stopService(intent)
        }

        fun isOverlayPermissionGranted(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }
        }

        fun isRunning(context: Context): Boolean {
            return instance != null
        }

        private var instance: FloatingButtonService? = null

        fun updateFloatingButtonState() {
            instance?.updateButtonUI()
        }
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var accessibilityManager: AccessibilityManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        accessibilityManager = getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        accessibilityManager?.addAccessibilityStateChangeListener(this)

        createNotificationChannel()
        startForegroundServiceNotification()

        if (isOverlayPermissionGranted(this)) {
            initFloatingWindow()
        } else {
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundServiceNotification()
        updateButtonUI()
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Batterify Floating Button",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows the floating overlay button for Batterify state"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun startForegroundServiceNotification() {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Floating overlay button active")
            .setSmallIcon(R.drawable.ic_battery_full)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } catch (e: Exception) {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun initFloatingWindow() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_button, null)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        setupTouchAndClickListener(floatingView, params)
        windowManager?.addView(floatingView, params)
        updateButtonUI()
    }

    private fun setupTouchAndClickListener(view: View?, params: WindowManager.LayoutParams) {
        val handler = Handler(Looper.getMainLooper())
        val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

        view?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var longPressTriggered = false

            private val longPressRunnable = Runnable {
                longPressTriggered = true
                freezeHiddenMarkedPackages()
            }

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        longPressTriggered = false
                        handler.postDelayed(longPressRunnable, longPressTimeout)
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val diffX = abs(event.rawX - initialTouchX)
                        val diffY = abs(event.rawY - initialTouchY)
                        if (diffX >= 10 || diffY >= 10) {
                            handler.removeCallbacks(longPressRunnable)
                        }
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(floatingView, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        handler.removeCallbacks(longPressRunnable)
                        val diffX = abs(event.rawX - initialTouchX)
                        val diffY = abs(event.rawY - initialTouchY)
                        if (!longPressTriggered && diffX < 10 && diffY < 10) {
                            openAccessibilitySettings()
                        }
                        return true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        handler.removeCallbacks(longPressRunnable)
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun freezeHiddenMarkedPackages() {
        if (!FreezeAccessibilityService.isEnabled(this)) {
            Toast.makeText(this, "Please enable Accessibility Service first", Toast.LENGTH_LONG).show()
            openAccessibilitySettings()
            return
        }

        val markedPackages = RunningPackageHelper.getHiddenMarkedPackages(this)
        val activeMarked = markedPackages.filter { !RunningPackageHelper.isPackageStopped(this, it) }
        if (activeMarked.isEmpty()) {
            Toast.makeText(this, "No active hidden marked packages", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Stopping ${activeMarked.size} hidden apps...", Toast.LENGTH_SHORT).show()
        FreezeAccessibilityService.startFreezing(this, activeMarked)
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    fun updateButtonUI() {
        val isEnabled = FreezeAccessibilityService.isEnabled(this)
        val container = floatingView?.findViewById<View>(R.id.floating_button_container)
        val statusText = floatingView?.findViewById<TextView>(R.id.floating_button_status)

        if (isEnabled) {
            container?.setBackgroundResource(R.drawable.bg_floating_button_green)
            statusText?.text = "ON"
        } else {
            container?.setBackgroundResource(R.drawable.bg_floating_button_red)
            statusText?.text = "OFF"
        }
    }

    override fun onAccessibilityStateChanged(enabled: Boolean) {
        updateButtonUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        accessibilityManager?.removeAccessibilityStateChangeListener(this)
        if (floatingView != null) {
            windowManager?.removeView(floatingView)
            floatingView = null
        }
        instance = null
    }
}
