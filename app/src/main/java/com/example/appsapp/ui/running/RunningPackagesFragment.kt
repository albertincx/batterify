package com.example.appsapp.ui.running

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.appsapp.FreezeAccessibilityService
import com.example.appsapp.FreezeStatusObserver
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
    private lateinit var accessibilityButton: Button
    private lateinit var usageStatsButton: Button
    private lateinit var selectAllCheckBox: CheckBox
    private lateinit var selectActiveCheckBox: CheckBox
    private lateinit var tabLayout: TabLayout
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
        accessibilityButton = root.findViewById(R.id.accessibility_button)
        usageStatsButton = root.findViewById(R.id.usage_stats_button)
        selectAllCheckBox = root.findViewById(R.id.selectAllCheckBox)
        selectActiveCheckBox = root.findViewById(R.id.selectActiveCheckBox)
        tabLayout = root.findViewById(R.id.tabLayout)

        recyclerView.layoutManager = LinearLayoutManager(context)

        refreshButton.setOnClickListener {
            loadRunningPackages()
        }

        freezeButton.setOnClickListener {
            freezeSelectedPackages()
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
                    adapter.updateFilter(tab?.position == 1)
                    updateSelectActiveVisibility()
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
        val accessibilityEnabled = isAccessibilityServiceEnabled(requireContext(), FreezeAccessibilityService::class.java)
        if (accessibilityEnabled) {
            accessibilityButton.text = "Accessibility On"
            accessibilityButton.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
        } else {
            accessibilityButton.text = "Accessibility Off"
            accessibilityButton.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
        }

        val usageStatsEnabled = RunningPackageHelper.hasUsageStatsPermission(requireContext())
        if (usageStatsEnabled) {
            usageStatsButton.text = "Usage Stats On"
            usageStatsButton.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
        } else {
            usageStatsButton.text = "Usage Stats Off"
            usageStatsButton.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context, service: Class<out AccessibilityService>): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        for (enabledService in enabledServices) {
            val enabledServiceInfo = enabledService.resolveInfo.serviceInfo
            if (enabledServiceInfo.packageName == context.packageName && enabledServiceInfo.name == service.name) {
                return true
            }
        }
        return false
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
                freezeSelectedPackages()
            },
            onInfoClick = { runningPackage ->
                openAppInfo(runningPackage.packageName)
            },
            onSelectionChanged = {
                updateFreezeButtonVisibility()
            }
        )
        recyclerView.adapter = adapter
        adapter.updateFilter(tabLayout.selectedTabPosition == 1)
        updateSelectActiveVisibility()
        updateFreezeButtonVisibility()

        if (isInitialLoad) {
            if (!adapter.hasActivePackages()) {
                showNoActiveAppsDialog()
            }
            isInitialLoad = false
        }
    }

    private fun updateFreezeButtonVisibility() {
        if (::adapter.isInitialized) {
            freezeButton.visibility = if (adapter.getSelectedPackages().isNotEmpty()) View.VISIBLE else View.GONE
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

    private fun freezeSelectedPackages() {
        if (!isAccessibilityServiceEnabled(requireContext(), FreezeAccessibilityService::class.java)) {
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
        FreezeAccessibilityService.startFreezing(requireContext(), packageNames)
        
        Toast.makeText(context, "Freezing ${selected.size} apps...", Toast.LENGTH_SHORT).show()
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
}
