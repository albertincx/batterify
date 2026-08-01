package com.example.appsapp.ui.running

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.appsapp.PackageKiller
import com.example.appsapp.R
import com.example.appsapp.RunningPackage

import android.content.Context

class RunningPackagesAdapter(
    private val context: Context,
    private val runningPackages: MutableList<RunningPackage>,
    private val onKillClick: (RunningPackage) -> Unit
) : RecyclerView.Adapter<RunningPackagesAdapter.RunningPackageViewHolder>() {

    class RunningPackageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val appNameTextView: TextView = itemView.findViewById(R.id.appNameTextView)
        val packageNameTextView: TextView = itemView.findViewById(R.id.packageNameTextView)
        val processIdTextView: TextView = itemView.findViewById(R.id.processIdTextView)
        val actionButton: Button = itemView.findViewById(R.id.actionButton)
        val hideButton: Button = itemView.findViewById(R.id.hideButton)
        val killButton: Button = itemView.findViewById(R.id.killButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RunningPackageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_running_package, parent, false)
        return RunningPackageViewHolder(view)
    }

    override fun onBindViewHolder(holder: RunningPackageViewHolder, position: Int) {
        val runningPackage = runningPackages[position]
        
        // Only bind visible items
        if (runningPackage.isVisible) {
            holder.itemView.visibility = View.VISIBLE
            holder.appNameTextView.text = runningPackage.appName
            holder.packageNameTextView.text = runningPackage.packageName
            holder.processIdTextView.text = "PID: ${runningPackage.processId}"
            
            holder.actionButton.setOnClickListener {
                onKillClick(runningPackage)
            }
            
            holder.hideButton.setOnClickListener {
                // Hide the item by setting isVisible to false
                runningPackage.isVisible = false
                // Hide the item view
                holder.itemView.visibility = View.GONE
            }
            
            holder.killButton.setOnClickListener {
                // Try to kill the package using PackageKiller
                PackageKiller.killPackage(context, runningPackage.packageName)
            }
        } else {
            // If the item is not visible, hide the entire item view
            holder.itemView.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = runningPackages.size
    
    // Function to return only visible packages
    fun getVisiblePackages(): List<RunningPackage> {
        return runningPackages.filter { it.isVisible }
    }
    
    // Function to reset visibility of all items
    fun resetVisibility() {
        for (pkg in runningPackages) {
            pkg.isVisible = true
        }
        notifyDataSetChanged()
    }
}