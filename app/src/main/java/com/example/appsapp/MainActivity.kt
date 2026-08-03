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
            R.id.action_policy -> {
                startActivity(Intent(this, PolicyActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}