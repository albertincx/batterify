package com.example.appsapp

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.example.appsapp.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

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
            getString(R.string.settings_next_time)
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
                }
            }
            .setPositiveButton("OK", null)
            .show()
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