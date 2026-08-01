package com.example.appsapp

import android.Manifest
import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat

class PackageKiller {
    companion object {
        fun killPackage(context: Context, packageName: String): Boolean {
            // Check if package is installed
            if (!isPackageInstalled(context, packageName)) {
                return false
            }

            // First check if we have the usage stats permission
            if (!hasUsageStatsPermission(context)) {
                Toast.makeText(context, "Требуется разрешение на доступ к статистике использования.", Toast.LENGTH_LONG).show()
                
                // Redirect to usage stats permission page
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return false
            }

            try {
                // If device is rooted, we can use am force-stop which is the most effective way
                // to achieve what the user wants (stop everything including background)
                try {
                    Runtime.getRuntime().exec(arrayOf("su", "-c", "am force-stop $packageName"))
                } catch (e: Exception) {
                    // Not rooted or su failed, continue with standard methods
                }

                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

                // Try to kill background processes
                activityManager.killBackgroundProcesses(packageName)

                Toast.makeText(context, "Попытка остановки $packageName", Toast.LENGTH_SHORT).show()
                return true
            } catch (e: SecurityException) {
                // If we can't kill it, offer to open app info
                openAppInfo(context, packageName)
                return false
            } catch (e: Exception) {
                return false
            }
        }

        fun isPackageInstalled(context: Context, packageName: String): Boolean {
            return try {
                context.packageManager.getPackageInfo(packageName, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
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
        @RequiresPermission(Manifest.permission.KILL_BACKGROUND_PROCESSES)
        fun forceStopPackage(context: Context, packageName: String): Boolean {
            // This requires special system-level permissions or root access
            // Only available on rooted devices or system apps
            if (isSystemApp(context)) {
                try {
                    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                    activityManager.killBackgroundProcesses(packageName)
                    Toast.makeText(context, "Force stopped (background) $packageName", Toast.LENGTH_SHORT).show()
                    return true
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to force stop: ${e.message}", Toast.LENGTH_LONG).show()
                    return false
                }
            } else {
                Toast.makeText(context, "Force stop requires system-level permissions", Toast.LENGTH_LONG).show()
                return false
            }
        }

        private fun isSystemApp(context: Context): Boolean {
            return try {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                val applicationInfo = packageInfo.applicationInfo
                applicationInfo != null && ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0)
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }
        fun openAppInfo(context: Context, packageName: String) {
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
        
        fun forceStopPackage1(context: Context, packageName: String): Boolean {
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