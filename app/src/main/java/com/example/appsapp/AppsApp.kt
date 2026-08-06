package com.example.appsapp

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class AppsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        applyTheme()
    }

    fun applyTheme() {
        val sharedPref = getSharedPreferences("settings", MODE_PRIVATE)
        val theme = sharedPref.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(theme)
    }
}