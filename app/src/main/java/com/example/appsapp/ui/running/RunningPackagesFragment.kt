package com.example.appsapp.ui.running

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import android.net.Uri
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.appsapp.PackageKiller
import com.example.appsapp.RunningPackageHelper
import com.example.appsapp.R

class RunningPackagesFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var refreshButton: Button
    private lateinit var adapter: RunningPackagesAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_running_packages, container, false)

        recyclerView = root.findViewById(R.id.runningPackagesRecyclerView)
        refreshButton = root.findViewById(R.id.refresh_button)

        recyclerView.layoutManager = LinearLayoutManager(context)

        refreshButton.setOnClickListener {
            loadRunningPackages()
        }

        loadRunningPackages()

        return root
    }

    private fun loadRunningPackages() {
        val runningPackages = RunningPackageHelper.getRunningPackages(requireContext())
        adapter = RunningPackagesAdapter(runningPackages) { runningPackage ->
            // On modern Android, we can't kill other packages, so open app info instead
            openAppInfo(runningPackage.packageName)
        }
        recyclerView.adapter = adapter
    }

    private fun openAppInfo(packageName: String) {
        try {
            val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:$packageName")
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            requireContext().startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open app info", Toast.LENGTH_SHORT).show()
        }
    }
}