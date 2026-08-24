package com.example.appsapp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class HistoryItem(val appName: String, val packageName: String, val timestamp: Long)

object HistoryHelper {
    private const val PREFS_NAME = "history_prefs"
    private const val KEY_HISTORY = "history_list"
    private const val MAX_HISTORY = 50

    fun addHistory(context: Context, appName: String, packageName: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val history = getHistory(context).toMutableList()
        
        // Remove existing if same package
        history.removeAll { it.packageName == packageName }
        
        history.add(0, HistoryItem(appName, packageName, System.currentTimeMillis()))
        
        if (history.size > MAX_HISTORY) {
            history.removeAt(history.size - 1)
        }
        
        val jsonArray = JSONArray()
        history.forEach {
            val jsonObject = JSONObject()
            jsonObject.put("appName", it.appName)
            jsonObject.put("packageName", it.packageName)
            jsonObject.put("timestamp", it.timestamp)
            jsonArray.put(jsonObject)
        }
        
        prefs.edit().putString(KEY_HISTORY, jsonArray.toString()).apply()
    }

    fun getHistory(context: Context): List<HistoryItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_HISTORY, "[]")
        val jsonArray = JSONArray(jsonString)
        val history = mutableListOf<HistoryItem>()
        
        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            history.add(HistoryItem(
                jsonObject.getString("appName"),
                jsonObject.getString("packageName"),
                jsonObject.getLong("timestamp")
            ))
        }
        return history
    }
}
