package com.example.appsapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.example.appsapp.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    private val exportConfigLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { exportConfiguration(it) }
    }

    private val importConfigLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { importConfiguration(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (isFirstLaunch()) {
            showFirstLaunchDialog()
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarMain.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_running_packages
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
    }

    private fun isFirstLaunch(): Boolean {
        val sharedPref = getPreferences(MODE_PRIVATE)
        return sharedPref.getBoolean("first_launch", true)
    }

    private fun setFirstLaunchDone() {
        val sharedPref = getPreferences(MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean("first_launch", false)
            apply()
        }
    }

    private fun showFirstLaunchDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Permissions Required")
            .setMessage("To function correctly, Batterify requires Accessibility and Usage Access permissions.\n\n" +
                    "This app is open-source and respects your privacy. Its ONLY purpose is to force-stop background applications to save your battery.\n\n" +
                    "You can verify the source code and build it yourself for proof: https://github.com/albertincx/batterify\n\n" +
                    "Your data is under your control. Do you accept these terms and want to proceed?")
            .setPositiveButton("I Accept") { dialog, _ ->
                setFirstLaunchDone()
                dialog.dismiss()
            }
            .setNeutralButton("Privacy Policy") { _, _ ->
                startActivity(Intent(this, PolicyActivity::class.java))
            }
            .setNegativeButton("Cancel") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                showSettingsDialog()
                true
            }
            R.id.action_policy -> {
                startActivity(Intent(this, PolicyActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showSettingsDialog() {
        val options = arrayOf(
            getString(R.string.settings_turn_off_screen),
            getString(R.string.settings_theme),
            getString(R.string.settings_last_action_time),
            getString(R.string.settings_return_time),
            getString(R.string.settings_next_time),
            getString(R.string.settings_export_config),
            getString(R.string.settings_import_config)
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_settings)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showTurnOffScreenSetting()
                    1 -> showThemeSelectionDialog()
                    2 -> showNumericSettingDialog(R.string.settings_last_action_time, "last_action_time", 500, arrayOf("100", "200", "300", "400", "500"))
                    3 -> showNumericSettingDialog(R.string.settings_return_time, "return_time", 300, arrayOf("100", "150", "200", "250", "300"))
                    4 -> showNumericSettingDialog(R.string.settings_next_time, "next_time", 400, arrayOf("100", "200", "300", "400"))
                    5 -> exportConfigLauncher.launch("batterify-settings.json")
                    6 -> importConfigLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                }
            }
            .setPositiveButton("OK", null)
            .show()
    }

    private fun exportConfiguration(uri: Uri) {
        try {
            val batterifyPrefs = getSharedPreferences("batterify_prefs", MODE_PRIVATE)
            val tabFiltersPrefs = getSharedPreferences("tab_filters", MODE_PRIVATE)
            val settingsPrefs = getSharedPreferences("settings", MODE_PRIVATE)

            val root = JSONObject().apply {
                put("hidden_packages", JSONArray(batterifyPrefs.getStringSet("hidden_packages", emptySet())?.toList() ?: emptyList<String>()))
                put("hidden_marked_packages", JSONArray(batterifyPrefs.getStringSet("hidden_marked_packages", emptySet())?.toList() ?: emptyList<String>()))

                val filtersObj = JSONObject()
                for (tabIndex in listOf(0, 1, 3)) {
                    val key = "filter_tab_$tabIndex"
                    filtersObj.put(key, tabFiltersPrefs.getString(key, "") ?: "")
                }
                put("tab_filters", filtersObj)

                val settingsObj = JSONObject().apply {
                    put("turn_off_screen", settingsPrefs.getBoolean("turn_off_screen", false))
                    put("theme_mode", settingsPrefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM))
                    put("last_action_time", settingsPrefs.getInt("last_action_time", 500))
                    put("return_time", settingsPrefs.getInt("return_time", 300))
                    put("next_time", settingsPrefs.getInt("next_time", 400))
                }
                put("settings", settingsObj)
            }

            contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(root.toString(2).toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(this, "Settings exported", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun importConfiguration(uri: Uri) {
        try {
            val content = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                it.readText()
            } ?: return

            val root = JSONObject(content)
            val batterifyEditor = getSharedPreferences("batterify_prefs", MODE_PRIVATE).edit()

            if (root.has("hidden_packages")) {
                val arr = root.getJSONArray("hidden_packages")
                val set = mutableSetOf<String>()
                for (i in 0 until arr.length()) {
                    set.add(arr.getString(i))
                }
                batterifyEditor.putStringSet("hidden_packages", set)
            }

            if (root.has("hidden_marked_packages")) {
                val arr = root.getJSONArray("hidden_marked_packages")
                val set = mutableSetOf<String>()
                for (i in 0 until arr.length()) {
                    set.add(arr.getString(i))
                }
                batterifyEditor.putStringSet("hidden_marked_packages", set)
            }
            batterifyEditor.apply()

            if (root.has("tab_filters")) {
                val filtersObj = root.getJSONObject("tab_filters")
                val filtersEditor = getSharedPreferences("tab_filters", MODE_PRIVATE).edit()
                filtersObj.keys().forEach { key ->
                    filtersEditor.putString(key, filtersObj.optString(key, ""))
                }
                filtersEditor.apply()
            }

            if (root.has("settings")) {
                val settingsObj = root.getJSONObject("settings")
                val settingsEditor = getSharedPreferences("settings", MODE_PRIVATE).edit()
                if (settingsObj.has("turn_off_screen")) {
                    settingsEditor.putBoolean("turn_off_screen", settingsObj.getBoolean("turn_off_screen"))
                }
                if (settingsObj.has("theme_mode")) {
                    val mode = settingsObj.getInt("theme_mode")
                    settingsEditor.putInt("theme_mode", mode)
                    AppCompatDelegate.setDefaultNightMode(mode)
                }
                if (settingsObj.has("last_action_time")) {
                    settingsEditor.putInt("last_action_time", settingsObj.getInt("last_action_time"))
                }
                if (settingsObj.has("return_time")) {
                    settingsEditor.putInt("return_time", settingsObj.getInt("return_time"))
                }
                if (settingsObj.has("next_time")) {
                    settingsEditor.putInt("next_time", settingsObj.getInt("next_time"))
                }
                settingsEditor.apply()
            }

            Toast.makeText(this, "Settings imported. Refresh to apply list changes.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showNumericSettingDialog(titleRes: Int, key: String, defaultValue: Int, options: Array<String>) {
        val sharedPref = getSharedPreferences("settings", MODE_PRIVATE)
        val currentValue = sharedPref.getInt(key, defaultValue)
        val currentIndex = options.indexOf(currentValue.toString()).coerceAtLeast(0)

        MaterialAlertDialogBuilder(this)
            .setTitle(titleRes)
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                sharedPref.edit().putInt(key, options[which].toInt()).apply()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTurnOffScreenSetting() {
        val sharedPref = getSharedPreferences("settings", MODE_PRIVATE)
        val currentSetting = sharedPref.getBoolean("turn_off_screen", false)
        val options = arrayOf(getString(R.string.settings_turn_off_screen))
        val checkedItems = booleanArrayOf(currentSetting)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_turn_off_screen)
            .setMultiChoiceItems(options, checkedItems) { _, which, isChecked ->
                if (which == 0) {
                    sharedPref.edit().putBoolean("turn_off_screen", isChecked).apply()
                }
            }
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showThemeSelectionDialog() {
        val sharedPref = getSharedPreferences("settings", MODE_PRIVATE)
        val currentTheme = sharedPref.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        
        val themes = arrayOf(
            getString(R.string.theme_light),
            getString(R.string.theme_dark),
            getString(R.string.theme_system)
        )
        
        val themeModes = intArrayOf(
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_YES,
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        )
        
        var checkedItem = themeModes.indexOf(currentTheme)
        if (checkedItem == -1) checkedItem = 2 // Default to system

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_theme)
            .setSingleChoiceItems(themes, checkedItem) { dialog, which ->
                val selectedMode = themeModes[which]
                sharedPref.edit().putInt("theme_mode", selectedMode).apply()
                AppCompatDelegate.setDefaultNightMode(selectedMode)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}