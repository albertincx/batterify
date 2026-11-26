package com.example.appsapp

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build

data class RunningPackage(
    val packageName: String,
    val processName: String,
    val processId: Int,
    val appName: String,
    var isVisible: Boolean = true,
    var isSelected: Boolean = false,
    var isFrozen: Boolean = false,
    var frozenTimestamp: Long = 0L,
    var isHidden: Boolean = false,
    var lastTimeUsed: Long = 0L
)

class RunningPackageHelper {
    companion object {
        private const val PREFS_NAME = "batterify_prefs"
        private const val HIDDEN_PACKAGES_KEY = "hidden_packages"
        private const val HIDDEN_MARKED_PACKAGES_KEY = "hidden_marked_packages"

        fun getRunningPackages(context: Context): List<RunningPackage> {
            val hiddenPackages = getHiddenPackages(context)
            val hiddenMarkedPackages = getHiddenMarkedPackages(context)
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
                        if (!isSystemPackage(usageStat.packageName, context.packageName)) {
                            val isFrozen = isPackageStopped(context, usageStat.packageName)
                            val isHidden = hiddenPackages.contains(usageStat.packageName)
                            runningPackages.add(
                                RunningPackage(
                                    packageName = usageStat.packageName,
                                    processName = usageStat.packageName,
                                    processId = -1, // UsageStats doesn't provide PID
                                    appName = getApplicationLabel(context, usageStat.packageName),
                                    isVisible = true,
                                    isSelected = !isFrozen && isHidden && hiddenMarkedPackages.contains(usageStat.packageName),
                                    isFrozen = isFrozen,
                                    frozenTimestamp = if (isFrozen) 1L else 0L, // Placeholder for existing frozen apps
                                    isHidden = isHidden,
                                    lastTimeUsed = usageStat.lastTimeUsed
                                )
                            )
                        }
                    }
                }
            } else {
                // Fallback: just add the current app
                val isHidden = hiddenPackages.contains(context.packageName)
                runningPackages.add(
                    RunningPackage(
                        packageName = context.packageName,
                        processName = context.packageName,
                        processId = android.os.Process.myPid(),
                        appName = getApplicationLabel(context, context.packageName),
                        isVisible = true,
                        isSelected = isHidden && hiddenMarkedPackages.contains(context.packageName),
                        isFrozen = false, // Current app is obviously running
                        isHidden = isHidden,
                        lastTimeUsed = System.currentTimeMillis()
                    )
                )
            }
            
            // Also try to get information from ActivityManager if available
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val appProcesses = activityManager.runningAppProcesses
            appProcesses?.forEach { processInfo ->
                val packageName = processInfo.processName
                // Filter out system processes
                if (!isSystemPackage(packageName, context.packageName)) {
                    // Only add if not already in the list from usage stats
                    if (!runningPackages.any { it.packageName == packageName }) {
                        val isFrozen = isPackageStopped(context, packageName)
                        val isHidden = hiddenPackages.contains(packageName)
                        runningPackages.add(
                            RunningPackage(
                                packageName = packageName,
                                processName = packageName,
                                processId = processInfo.pid,
                                appName = getApplicationLabel(context, packageName),
                                isVisible = true,
                                isSelected = !isFrozen && isHidden && hiddenMarkedPackages.contains(packageName),
                                isFrozen = isFrozen,
                                frozenTimestamp = if (isFrozen) 1L else 0L,
                                isHidden = isHidden
                            )
                        )
                    }
                }
            }
            
            return runningPackages.distinctBy { it.packageName }
                .sortedWith(Comparator { p1, p2 ->
                    if (p1.isFrozen != p2.isFrozen) {
                        p1.isFrozen.compareTo(p2.isFrozen) // Running (false/0) first
                    } else if (p1.isFrozen) {
                        p2.frozenTimestamp.compareTo(p1.frozenTimestamp) // Recently frozen first
                    } else {
                        val timeCompare = p2.lastTimeUsed.compareTo(p1.lastTimeUsed) // Newest running first
                        if (timeCompare != 0) timeCompare else p1.appName.compareTo(p2.appName, ignoreCase = true)
                    }
                })
        }

        private fun isSystemPackage(packageName: String, myPackageName: String): Boolean {
            if (packageName.startsWith("com.android.chrome")) {
                return false
            }
            return packageName.startsWith("com11.android")
                    || packageName.startsWith("com11.google.android")
                    || packageName.startsWith("android123")
                    || packageName == myPackageName
                    || packageName.startsWith("system:")
                    || packageName.startsWith("com.miui")
        }
        
        fun isPackageStopped(context: Context, packageName: String): Boolean {
            return try {
                val ai = context.packageManager.getApplicationInfo(packageName, 0)
                (ai.flags and android.content.pm.ApplicationInfo.FLAG_STOPPED) != 0
            } catch (e: Exception) {
                false
            }
        }
        
        fun hasUsageStatsPermission(context: Context): Boolean {
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
        
        fun getApplicationLabel(context: Context, packageName: String): String {
            return try {
                val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
                val appInfo = packageInfo.applicationInfo
                appInfo?.loadLabel(context.packageManager)?.toString() ?: packageName
            } catch (e: Exception) {
                packageName // Return package name if we can't get the label
            }
        }

        fun hidePackage(context: Context, packageName: String) {
            val hidden = getHiddenPackages(context).toMutableSet()
            hidden.add(packageName)
            saveHiddenPackages(context, hidden)
        }

        fun unhidePackage(context: Context, packageName: String) {
            val hidden = getHiddenPackages(context).toMutableSet()
            hidden.remove(packageName)
            saveHiddenPackages(context, hidden)
            setPackageHiddenMarked(context, packageName, false)
        }

        private fun getHiddenPackages(context: Context): Set<String> {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getStringSet(HIDDEN_PACKAGES_KEY, emptySet())?.toSet() ?: emptySet()
        }

        private fun saveHiddenPackages(context: Context, packages: Set<String>) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putStringSet(HIDDEN_PACKAGES_KEY, packages.toSet()).apply()
        }

        fun getHiddenMarkedPackages(context: Context): Set<String> {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getStringSet(HIDDEN_MARKED_PACKAGES_KEY, emptySet())?.toSet() ?: emptySet()
        }

        fun setPackageHiddenMarked(context: Context, packageName: String, isMarked: Boolean) {
            val marked = getHiddenMarkedPackages(context).toMutableSet()
            if (isMarked) {
                marked.add(packageName)
            } else {
                marked.remove(packageName)
            }
            saveHiddenMarkedPackages(context, marked)
        }

        fun setPackagesHiddenMarked(context: Context, packageNames: Collection<String>, isMarked: Boolean) {
            val marked = getHiddenMarkedPackages(context).toMutableSet()
            if (isMarked) {
                marked.addAll(packageNames)
            } else {
                marked.removeAll(packageNames.toSet())
            }
            saveHiddenMarkedPackages(context, marked)
        }

        private fun saveHiddenMarkedPackages(context: Context, packages: Set<String>) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putStringSet(HIDDEN_MARKED_PACKAGES_KEY, packages.toSet()).apply()
        }

        fun getAllUserPackages(context: Context): List<RunningPackage> {
            val packageManager = context.packageManager
            val installedPackages = packageManager.getInstalledPackages(0)
            val userPackages = mutableListOf<RunningPackage>()
            val hiddenPackages = getHiddenPackages(context)
            val hiddenMarkedPackages = getHiddenMarkedPackages(context)

            installedPackages.forEach { packageInfo ->
                val ai = packageInfo.applicationInfo ?: return@forEach
                // Filter out system apps that haven't been updated
                val isSystem = (ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                val isUpdatedSystem = (ai.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

                if ((!isSystem || isUpdatedSystem) && packageInfo.packageName != context.packageName) {
                    val isFrozen = isPackageStopped(context, packageInfo.packageName)
                    val isHidden = hiddenPackages.contains(packageInfo.packageName)
                    userPackages.add(
                        RunningPackage(
                            packageName = packageInfo.packageName,
                            processName = packageInfo.packageName,
                            processId = -1,
                            appName = ai.loadLabel(packageManager).toString(),
                            isVisible = true,
                            isSelected = !isFrozen && isHidden && hiddenMarkedPackages.contains(packageInfo.packageName),
                            isFrozen = isFrozen,
                            frozenTimestamp = if (isFrozen) 1L else 0L,
                            isHidden = isHidden
                        )
                    )
                }
            }

            return userPackages.sortedWith(Comparator { p1, p2 ->
                if (p1.isFrozen != p2.isFrozen) {
                    p1.isFrozen.compareTo(p2.isFrozen) // Running (false/0) first
                } else {
                    p1.appName.compareTo(p2.appName, ignoreCase = true)
                }
            })
        }
    }
}