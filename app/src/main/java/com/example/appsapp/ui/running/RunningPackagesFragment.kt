package com.example.appsapp.ui.running

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.appsapp.FreezeAccessibilityService
import com.example.appsapp.FreezeStatusObserver
import com.example.appsapp.FloatingButtonService
import com.example.appsapp.RunningPackageHelper
import com.example.appsapp.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class RunningPackagesFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var refreshButton: Button
    private lateinit var freezeButton: Button
    private lateinit var copyButton: Button
    private lateinit var accessibilityButton: Button
    private lateinit var usageStatsButton: Button
    private lateinit var overlayButton: Button
    private lateinit var selectAllCheckBox: CheckBox
    private lateinit var selectActiveCheckBox: CheckBox
    private lateinit var tabLayout: TabLayout
    private lateinit var filterEditText: EditText
    private lateinit var filterButton: Button
    private lateinit var adapter: RunningPackagesAdapter
    private var isInitialLoad = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_running_packages, container, false)

        recyclerView = root.findViewById(R.id.runningPackagesRecyclerView)
        refreshButton = root.findViewById(R.id.refresh_button)
        freezeButton = root.findViewById(R.id.freeze_button)
        copyButton = root.findViewById(R.id.copy_selected_button)
        accessibilityButton = root.findViewById(R.id.accessibility_button)
        usageStatsButton = root.findViewById(R.id.usage_stats_button)
        selectAllCheckBox = root.findViewById(R.id.selectAllCheckBox)
        selectActiveCheckBox = root.findViewById(R.id.selectActiveCheckBox)
        tabLayout = root.findViewById(R.id.tabLayout)
        filterEditText = root.findViewById(R.id.filterEditText)
        filterButton = root.findViewById(R.id.filter_button)

        recyclerView.layoutManager = LinearLayoutManager(context)

        refreshButton.setOnClickListener {
            loadRunningPackages()
            selectActiveCheckBox.isChecked = false
        }

        freezeButton.setOnClickListener {
            freezeSelectedPackages()
        }

        copyButton.setOnClickListener {
            copySelectedPackages()
        }

        filterButton.setOnClickListener {
            applyCurrentFilter()
        }

        accessibilityButton.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        usageStatsButton.setOnClickListener {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            startActivity(intent)
        }

        selectAllCheckBox.setOnCheckedChangeListener { _, isChecked ->
            if (::adapter.isInitialized) {
                adapter.selectAll(isChecked)
            }
        }

        selectActiveCheckBox.setOnCheckedChangeListener { _, isChecked ->
            if (::adapter.isInitialized) {
                adapter.selectAllActive(isChecked)
            }
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (::adapter.isInitialized) {
                    val position = tab?.position ?: 0
                    adapter.updateFilter(position)
                    updateSelectActiveVisibility()
                    
                    // Show filter for all tabs except history
                    val filterLayout = root.findViewById<View>(R.id.filter_layout)
                    if (position == 2) { // History
                        filterLayout.visibility = View.GONE
                    } else {
                        filterLayout.visibility = View.VISIBLE
                        // Load saved filter for this tab
                        val savedFilter = getSavedFilterForTab(position)
                        filterEditText.setText(savedFilter)
                        adapter.setFilter(savedFilter)
                    }

                    // Specific logic for History vs Others
                    if (position == 2) { // History
                        root.findViewById<View>(R.id.controls_layout).visibility = View.GONE
                        root.findViewById<View>(R.id.bottom_buttons).visibility = View.GONE
                    } else {
                        root.findViewById<View>(R.id.controls_layout).visibility = View.VISIBLE
                        root.findViewById<View>(R.id.bottom_buttons).visibility = View.VISIBLE
                        if (position == 3) { // Installed
                            loadAllUserPackages()
                        }
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        loadRunningPackages()
        updatePermissionStatuses()
        observeFreezeStatus()

        return root
    }

    private fun applyCurrentFilter() {
        if (::adapter.isInitialized) {
            val filter = filterEditText.text.toString()
            val currentTab = tabLayout.selectedTabPosition
            saveFilterForTab(currentTab, filter)
            adapter.setFilter(filter)
        }
    }

    private fun saveFilterForTab(tabIndex: Int, filter: String) {
        val prefs = requireContext().getSharedPreferences("tab_filters", Context.MODE_PRIVATE)
        prefs.edit().putString("filter_tab_$tabIndex", filter).apply()
    }

    private fun getSavedFilterForTab(tabIndex: Int): String {
        val prefs = requireContext().getSharedPreferences("tab_filters", Context.MODE_PRIVATE)
        return prefs.getString("filter_tab_$tabIndex", "") ?: ""
    }

    private fun observeFreezeStatus() {
        viewLifecycleOwner.lifecycleScope.launch {
            FreezeStatusObserver.frozenPackages.collectLatest { packageName ->
                if (::adapter.isInitialized) {
                    val pos = adapter.updatePackageFrozen(packageName)
                    if (pos != -1) {
                        android.util.Log.d("RunningPackages", "Marked $packageName as frozen in UI")
                    }
                    updateSelectActiveVisibility()
                    updateFreezeButtonVisibility()
                }
            }
        }
    }

    private fun updateSelectActiveVisibility() {
        if (::adapter.isInitialized) {
            selectActiveCheckBox.visibility = if (adapter.hasActivePackages()) View.VISIBLE else if (tabLayout.selectedTabPosition == 1) View.GONE else View.INVISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatuses()
    }

    private fun updatePermissionStatuses() {
        val accessibilityEnabled = FreezeAccessibilityService.isEnabled(requireContext())
        if (accessibilityEnabled) {
            accessibilityButton.text = "Access On"
            accessibilityButton.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
        } else {
            accessibilityButton.text = "Access Off"
            accessibilityButton.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
        }

        val usageStatsEnabled = RunningPackageHelper.hasUsageStatsPermission(requireContext())
        if (usageStatsEnabled) {
            usageStatsButton.text = "Stats On"
            usageStatsButton.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
        } else {
            usageStatsButton.text = "Stats Off"
            usageStatsButton.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
        }
    }

    private fun loadRunningPackages() {
        val runningPackages = RunningPackageHelper.getRunningPackages(requireContext()).toMutableList()
        adapter = RunningPackagesAdapter(
            requireContext(), 
            runningPackages,
            onKillClick = { runningPackage ->
                runningPackage.isSelected = true
                adapter.notifyDataSetChanged()
                updateFreezeButtonVisibility()
                freezeSelectedPackages(true)
            },
            onInfoClick = { runningPackage ->
                openAppInfo(runningPackage.packageName)
            },
            onSelectionChanged = {
                updateFreezeButtonVisibility()
            }
        )
        recyclerView.adapter = adapter
        val currentTab = tabLayout.selectedTabPosition
        adapter.updateFilter(currentTab)
        
        // Re-apply saved filter for this tab
        val savedFilter = getSavedFilterForTab(currentTab)
        filterEditText.setText(savedFilter)
        adapter.setFilter(savedFilter)

        updateSelectActiveVisibility()
        updateFreezeButtonVisibility()
        
        if (tabLayout.selectedTabPosition == 3) {
            loadAllUserPackages()
        }

        if (isInitialLoad) {
            if (!adapter.hasActivePackages()) {
                showNoActiveAppsDialog()
            }
            isInitialLoad = false
        }
    }

    private fun loadAllUserPackages() {
        viewLifecycleOwner.lifecycleScope.launch {
            val userPackages = RunningPackageHelper.getAllUserPackages(requireContext())
            if (::adapter.isInitialized) {
                adapter.setUserPackages(userPackages)
            }
        }
    }

    private fun updateFreezeButtonVisibility() {
        if (::adapter.isInitialized) {
            val hasSelected = adapter.getSelectedPackages().isNotEmpty()
            freezeButton.visibility = if (hasSelected) View.VISIBLE else View.GONE
            copyButton.visibility = if (hasSelected) View.VISIBLE else View.GONE
        }
    }

    private fun showNoActiveAppsDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("No Active Apps")
            .setMessage("All applications are already optimized. There are no active processes to stop.")
            .setPositiveButton("Close App") { _, _ ->
                requireActivity().finish()
            }
            .setNegativeButton("Continue") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun freezeSelectedPackages(isMenuFreeze: Boolean = false) {
        if (!FreezeAccessibilityService.isEnabled(requireContext())) {
            Toast.makeText(context, "Please enable Accessibility Service first", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            return
        }

        val selected = adapter.getSelectedPackages()
        if (selected.isEmpty()) {
            Toast.makeText(context, "No apps selected", Toast.LENGTH_SHORT).show()
            return
        }

        // Use the new sequential freezing logic
        val packageNames = selected.map { it.packageName }
        FreezeAccessibilityService.startFreezing(requireContext(), packageNames, isMenuFreeze)
        
        Toast.makeText(context, "Stopping ${selected.size} apps...", Toast.LENGTH_SHORT).show()
    }

    private fun openAppInfo(packageName: String) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            requireContext().startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open app info", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copySelectedPackages() {
        if (::adapter.isInitialized) {
            val selected = adapter.getSelectedPackages()
            if (selected.isNotEmpty()) {
                val packageNames = selected.joinToString("\n") { it.packageName }
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("package names", packageNames)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Copied ${selected.size} package names", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
