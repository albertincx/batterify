package com.example.appsapp

import android.app.ActivityManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

data class RunningPackage(
    val packageName: String,
    val processName: String,
    val processId: Int,
    val appName: String,
    var isVisible: Boolean = true,
    var isSelected: Boolean = false
)

class RunningPackageHelper {
    companion object {
        fun getRunningPackages(context: Context): List<RunningPackage> {
            val runningPackages = mutableListOf<RunningPackage>()
            
            // Check if we have usage stats permission
            if (hasUsageStatsPermission(context)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                    val endTime = System.currentTimeMillis()
                    val startTime = endTime - 24 * 60 * 60 * 1000 // Last 24 hours
                    val usageStatsList = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
                    
                    // Get the most recently used packages
                    val mostRecentStats = usageStatsList
                        .groupBy { it.packageName }
                        .map { entry -> 
                            entry.value.maxByOrNull { it.lastTimeUsed } 
                        }
                        .filterNotNull()
                        .sortedByDescending { it.lastTimeUsed }
                        .take(50) // Take the 50 most recently used apps
                    
                    mostRecentStats.forEach { usageStat ->
                        if (
                            !usageStat.packageName.startsWith("com.android")
                            && !usageStat.packageName.startsWith("com.google.android")
                        ) {
                            runningPackages.add(
                                RunningPackage(
                                    packageName = usageStat.packageName,
                                    processName = usageStat.packageName,
                                    processId = -1, // UsageStats doesn't provide PID
                                    appName = getApplicationLabel(context, usageStat.packageName),
                                    isVisible = true
                                )
                            )
                        }
                    }
                }
            } else {
                // Fallback: just add the current app
                runningPackages.add(
                    RunningPackage(
                        packageName = context.packageName,
                        processName = context.packageName,
                        processId = android.os.Process.myPid(),
                        appName = getApplicationLabel(context, context.packageName),
                        isVisible = true
                    )
                )
            }
            
            // Also try to get information from ActivityManager if available
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val appProcesses = activityManager.runningAppProcesses
            appProcesses?.forEach { processInfo ->
                val packageName = processInfo.processName
                // Filter out system processes
                if (!packageName.startsWith("com.android") 
                    && !packageName.startsWith("com.google.android")
                    && !packageName.startsWith("system:")
                    && !packageName.startsWith("com.miui")) { // Additional filter for MIUI system apps
                    // Only add if not already in the list from usage stats
                    if (!runningPackages.any { it.packageName == packageName }) {
                        runningPackages.add(
                            RunningPackage(
                                packageName = packageName,
                                processName = packageName,
                                processId = processInfo.pid,
                                appName = getApplicationLabel(context, packageName),
                                isVisible = true
                            )
                        )
                    }
                }
            }
            
            return runningPackages.distinctBy { it.packageName } // Remove duplicates
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
        
        private fun getApplicationLabel(context: Context, packageName: String): String {
            return try {
                val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
                val appInfo = packageInfo.applicationInfo
                appInfo?.loadLabel(context.packageManager)?.toString() ?: packageName
            } catch (e: Exception) {
                packageName // Return package name if we can't get the label
            }
        }
    }
}