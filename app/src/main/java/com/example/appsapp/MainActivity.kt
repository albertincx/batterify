package com.example.appsapp

import android.os.Bundle
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.appcompat.app.AppCompatActivity
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
                    "This app is open-source and respects your privacy. Its ONLY purpose is to force-stop background applications to save your battery. " +
                    "You can review the code at any time to verify its safety.\n\n" +
                    "Do you accept these terms and want to proceed?")
            .setPositiveButton("I Accept") { dialog, _ ->
                setFirstLaunchDone()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}