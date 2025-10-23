package com.example.appsapp

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat

class PackageKiller {
    companion object {
        fun killPackage(context: Context, packageName: String): Boolean {
            // First check if we have the usage stats permission
            if (!hasUsageStatsPermission(context)) {
                Toast.makeText(context, "Usage stats permission required. Please grant permission.", Toast.LENGTH_LONG).show()
                
                // Redirect to usage stats permission page
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return false
            }
            
            // On modern Android (6.0+), the KILL_BACKGROUND_PROCESSES permission is only available to system apps
            // For regular apps, this will throw a SecurityException
            // So we'll just provide feedback to the user about what's happening
            if (packageName == context.packageName) {
                // If trying to kill the current app, we can use finishAndRemoveTask or similar
                Toast.makeText(context, "Cannot kill current app from within itself", Toast.LENGTH_LONG).show()
                return false
            } else {
                // For other packages, inform the user about the restriction
                Toast.makeText(
                    context, 
                    "Modern Android doesn't allow apps to kill other apps' processes for security reasons. " +
                    "Package '$packageName' may still be running.", 
                    Toast.LENGTH_LONG
                ).show()
                
                // Provide option to open app info instead
                openAppInfo(context, packageName)
                return false
            }
        }
        
        private fun hasUsageStatsPermission(context: Context): Boolean {
            return try {
                val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                val currentTime = System.currentTimeMillis()
                
                // Check if there's usage stats data available (meaning permission is granted)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    usageStatsManager.queryUsageStats(
                        UsageStatsManager.INTERVAL_DAILY,
                        currentTime - 1000 * 1000, // Last 1000 seconds
                        currentTime
                    ).isNotEmpty()
                } else {
                    // For older versions, try to query and see if it fails
                    usageStatsManager.queryUsageStats(
                        UsageStatsManager.INTERVAL_DAILY,
                        currentTime - 1000 * 1000, // Last 1000 seconds
                        currentTime
                    ).isNotEmpty()
                }
            } catch (e: SecurityException) {
                false
            }
        }
        
        private fun openAppInfo(context: Context, packageName: String) {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:$packageName")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Could not open app info", Toast.LENGTH_SHORT).show()
            }
        }
        
        fun forceStopPackage(context: Context, packageName: String): Boolean {
            // Modern Android doesn't allow regular apps to force stop other apps
            // Only system apps or apps with special permissions can do this
            Toast.makeText(
                context, 
                "Force stopping other apps is not allowed on modern Android for security reasons. " +
                "Only system apps can force stop other packages.",
                Toast.LENGTH_LONG
            ).show()
            
            openAppInfo(context, packageName)
            return false
        }
    }
}