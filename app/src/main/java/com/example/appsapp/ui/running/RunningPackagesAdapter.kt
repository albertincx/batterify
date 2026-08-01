package com.example.appsapp.ui.running

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
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
        val packageCheckBox: CheckBox = itemView.findViewById(R.id.packageCheckBox)
        val hideButton: Button = itemView.findViewById(R.id.hideButton)
        val actionButton: Button = itemView.findViewById(R.id.actionButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RunningPackageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_running_package, parent, false)
        return RunningPackageViewHolder(view)
    }

    override fun onBindViewHolder(holder: RunningPackageViewHolder, position: Int) {
        val runningPackage = runningPackages[position]
        
        holder.appNameTextView.text = runningPackage.appName
        holder.packageNameTextView.text = runningPackage.packageName
        
        // Handle frozen state
        if (runningPackage.isFrozen) {
            holder.packageCheckBox.isEnabled = false
            holder.packageCheckBox.isChecked = false
            holder.itemView.alpha = 0.5f
            holder.appNameTextView.text = "${runningPackage.appName} (Frozen)"
        } else {
            holder.packageCheckBox.isEnabled = true
            holder.itemView.alpha = 1.0f
        }
        
        // Remove listener first to avoid trigger when setting state
        holder.packageCheckBox.setOnCheckedChangeListener(null)
        if (!runningPackage.isFrozen) {
            holder.packageCheckBox.isChecked = runningPackage.isSelected
        }
        
        holder.packageCheckBox.setOnCheckedChangeListener { _, isChecked ->
            if (!runningPackage.isFrozen) {
                runningPackage.isSelected = isChecked
            }
        }

        holder.itemView.setOnClickListener {
            if (!runningPackage.isFrozen) {
                holder.packageCheckBox.isChecked = !holder.packageCheckBox.isChecked
            }
        }
        
        holder.hideButton.setOnClickListener {
            runningPackage.isVisible = false
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                runningPackages.removeAt(pos)
                notifyItemRemoved(pos)
            }
        }
        
        holder.actionButton.setOnClickListener {
            onKillClick(runningPackage)
        }
    }

    override fun getItemCount(): Int = runningPackages.size
    
    fun selectAll(select: Boolean) {
        runningPackages.forEach { it.isSelected = select }
        notifyDataSetChanged()
    }

    fun getSelectedPackages(): List<RunningPackage> {
        return runningPackages.filter { it.isSelected && !it.isFrozen }
    }

    fun updatePackageFrozen(packageName: String): Int {
        val index = runningPackages.indexOfFirst { it.packageName == packageName }
        if (index != -1) {
            runningPackages[index].isFrozen = true
            runningPackages[index].isSelected = false
            notifyItemChanged(index)
        }
        return index
    }
}
