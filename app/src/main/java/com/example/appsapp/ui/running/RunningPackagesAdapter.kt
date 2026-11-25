package com.example.appsapp.ui.running

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.appsapp.R
import com.example.appsapp.RunningPackage
import com.example.appsapp.RunningPackageHelper
import android.content.Context
import android.widget.ImageButton
import androidx.appcompat.widget.PopupMenu
import android.content.ClipboardManager
import android.content.ClipData
import android.widget.Toast
import com.example.appsapp.HistoryHelper
import com.example.appsapp.HistoryItem

class RunningPackagesAdapter(
    private val context: Context,
    private val allPackages: MutableList<RunningPackage>,
    private var allUserPackages: MutableList<RunningPackage> = mutableListOf(),
    private val onKillClick: (RunningPackage) -> Unit,
    private val onInfoClick: (RunningPackage) -> Unit,
    private val onSelectionChanged: () -> Unit
) : RecyclerView.Adapter<RunningPackagesAdapter.RunningPackageViewHolder>() {

    private var filteredPackages: List<Any> = emptyList()
    private var currentMode: Int = 0 // 0: All, 1: Hidden, 2: History, 3: Installed
    private var filterPattern: Regex? = null

    init {
        updateFilter(0)
    }

    fun setUserPackages(packages: List<RunningPackage>) {
        allUserPackages = packages.toMutableList()
        if (currentMode == 3) {
            updateFilter(3)
        }
    }

    fun setFilter(regex: String) {
        filterPattern = try {
            if (regex.isNotEmpty()) Regex(regex, RegexOption.IGNORE_CASE) else null
        } catch (e: Exception) {
            null
        }
        updateFilter(currentMode)
    }

    fun updateFilter(mode: Int) {
        currentMode = mode
        val baseList = when (mode) {
            0 -> allPackages.filter { !it.isHidden }
            1 -> {
                // Show all hidden apps (from both running and full list)
                val hiddenInRunning = allPackages.filter { it.isHidden }
                val hiddenInUser = allUserPackages.filter { it.isHidden }
                (hiddenInRunning + hiddenInUser).distinctBy { it.packageName }
            }
            2 -> HistoryHelper.getHistory(context)
            3 -> allUserPackages.filter { !it.isHidden }
            else -> emptyList()
        }

        filteredPackages = if (filterPattern != null && mode != 2) {
            baseList.filter { item ->
                if (item is RunningPackage) {
                    // Exclude if matches
                    !(filterPattern!!.containsMatchIn(item.packageName) || 
                      filterPattern!!.containsMatchIn(item.appName))
                } else {
                    true
                }
            }
        } else {
            baseList
        }
        notifyDataSetChanged()
    }

    class RunningPackageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val appNameTextView: TextView = itemView.findViewById(R.id.appNameTextView)
        val packageNameTextView: TextView = itemView.findViewById(R.id.packageNameTextView)
        val packageCheckBox: CheckBox = itemView.findViewById(R.id.packageCheckBox)
        val menuButton: ImageButton = itemView.findViewById(R.id.menuButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RunningPackageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_running_package, parent, false)
        return RunningPackageViewHolder(view)
    }

    override fun onBindViewHolder(holder: RunningPackageViewHolder, position: Int) {
        val item = filteredPackages[position]
        
        if (item is RunningPackage) {
            bindRunningPackage(holder, item)
        } else if (item is HistoryItem) {
            bindHistoryItem(holder, item)
        }
    }

    private fun bindRunningPackage(holder: RunningPackageViewHolder, runningPackage: RunningPackage) {
        // Remove listener first to avoid trigger when setting state
        holder.packageCheckBox.setOnCheckedChangeListener(null)
        holder.packageCheckBox.visibility = View.VISIBLE

        val displayAppName = if (runningPackage.appName.length > 50) {
            runningPackage.appName.substring(0, 30) + "..."
        } else {
            runningPackage.appName
        }

        holder.appNameTextView.text = displayAppName
        holder.packageNameTextView.text = runningPackage.packageName
        
        // Handle frozen state
        if (runningPackage.isFrozen) {
            holder.packageCheckBox.isEnabled = false
            holder.packageCheckBox.isChecked = false
            holder.itemView.alpha = 0.5f
            holder.appNameTextView.text = "$displayAppName (Dead)"
        } else {
            holder.packageCheckBox.isEnabled = true
            holder.itemView.alpha = 1.0f
        }
        
        if (!runningPackage.isFrozen) {
            holder.packageCheckBox.isChecked = runningPackage.isSelected
        }
        
        holder.packageCheckBox.setOnCheckedChangeListener { _, isChecked ->
            if (!runningPackage.isFrozen) {
                runningPackage.isSelected = isChecked
                if (currentMode == 1) {
                    RunningPackageHelper.setPackageHiddenMarked(context, runningPackage.packageName, isChecked)
                    allPackages.find { it.packageName == runningPackage.packageName }?.isSelected = isChecked
                    allUserPackages.find { it.packageName == runningPackage.packageName }?.isSelected = isChecked
                }
                onSelectionChanged()
            }
        }

        holder.itemView.setOnClickListener {
            if (!runningPackage.isFrozen) {
                holder.packageCheckBox.isChecked = !holder.packageCheckBox.isChecked
            }
        }
        
        holder.menuButton.setOnClickListener { view ->
            showPopupMenu(view, runningPackage)
        }
    }

    private fun bindHistoryItem(holder: RunningPackageViewHolder, historyItem: HistoryItem) {
        holder.packageCheckBox.visibility = View.GONE
        holder.appNameTextView.text = historyItem.appName
        holder.packageNameTextView.text = historyItem.packageName
        holder.itemView.alpha = 1.0f
        holder.itemView.setOnClickListener(null)
        
        holder.menuButton.setOnClickListener { view ->
            showHistoryPopupMenu(view, historyItem)
        }
    }

    private fun showPopupMenu(view: View, runningPackage: RunningPackage) {
        val popup = PopupMenu(context, view)
        
        // Add items manually
        val hideTitle = if (runningPackage.isHidden) "Unhide" else "Hide"
        popup.menu.add(0, 1, 0, hideTitle)
        
        if (!runningPackage.isFrozen) {
            popup.menu.add(0, 2, 1, "Kill")
        }
        
        popup.menu.add(0, 3, 2, "Info")
        popup.menu.add(0, 4, 3, "Copy Package")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> { // Hide/Unhide
                    val targetHidden = !runningPackage.isHidden
                    if (targetHidden) {
                        RunningPackageHelper.hidePackage(context, runningPackage.packageName)
                    } else {
                        RunningPackageHelper.unhidePackage(context, runningPackage.packageName)
                    }
                    
                    // Sync status in both master lists
                    allPackages.find { it.packageName == runningPackage.packageName }?.let {
                        it.isHidden = targetHidden
                        if (!targetHidden) it.isSelected = false
                    }
                    allUserPackages.find { it.packageName == runningPackage.packageName }?.let {
                        it.isHidden = targetHidden
                        if (!targetHidden) it.isSelected = false
                    }
                    
                    updateFilter(currentMode)
                    onSelectionChanged()
                    true
                }
                2 -> { // Kill
                    onKillClick(runningPackage)
                    true
                }
                3 -> { // Info
                    onInfoClick(runningPackage)
                    true
                }
                4 -> { // Copy Package
                    copyToClipboard(runningPackage.packageName)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showHistoryPopupMenu(view: View, historyItem: HistoryItem) {
        val popup = PopupMenu(context, view)
        popup.menu.add(0, 1, 0, "Copy Package")
        popup.menu.add(0, 2, 1, "Info")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    copyToClipboard(historyItem.packageName)
                    true
                }
                2 -> {
                    val rp = RunningPackage(historyItem.packageName, historyItem.packageName, 0, historyItem.appName)
                    onInfoClick(rp)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("package name", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied: $text", Toast.LENGTH_SHORT).show()
    }

    override fun getItemCount(): Int = filteredPackages.size
    
    fun selectAll(select: Boolean) {
        val changedPackages = mutableListOf<String>()
        filteredPackages.forEach { 
            if (it is RunningPackage && !it.isFrozen) {
                it.isSelected = select
                changedPackages.add(it.packageName)
            }
        }
        if (currentMode == 1 && changedPackages.isNotEmpty()) {
            RunningPackageHelper.setPackagesHiddenMarked(context, changedPackages, select)
            syncSelectionInLists(changedPackages, select)
        }
        notifyDataSetChanged()
        onSelectionChanged()
    }

    fun selectAllActive(select: Boolean) {
        val changedPackages = mutableListOf<String>()
        filteredPackages.forEach { 
            if (it is RunningPackage && !it.isFrozen && !it.isHidden) {
                it.isSelected = select
                changedPackages.add(it.packageName)
            }
        }
        if (currentMode == 1 && changedPackages.isNotEmpty()) {
            RunningPackageHelper.setPackagesHiddenMarked(context, changedPackages, select)
            syncSelectionInLists(changedPackages, select)
        }
        notifyDataSetChanged()
        onSelectionChanged()
    }

    private fun syncSelectionInLists(packageNames: Collection<String>, isSelected: Boolean) {
        val packageSet = packageNames.toSet()
        allPackages.forEach {
            if (it.packageName in packageSet && !it.isFrozen) {
                it.isSelected = isSelected
            }
        }
        allUserPackages.forEach {
            if (it.packageName in packageSet && !it.isFrozen) {
                it.isSelected = isSelected
            }
        }
    }

    fun getSelectedPackages(): List<RunningPackage> {
        val selected = mutableListOf<RunningPackage>()
        allPackages.filter { it.isSelected && !it.isFrozen }.forEach { selected.add(it) }
        allUserPackages.filter { it.isSelected && !it.isFrozen }.forEach { pkg ->
            if (selected.none { it.packageName == pkg.packageName }) {
                selected.add(pkg)
            }
        }
        return selected
    }

    fun hasActivePackages(): Boolean {
        return when (currentMode) {
            0 -> allPackages.any { !it.isFrozen && !it.isHidden }
            1 -> (allPackages + allUserPackages).any { !it.isFrozen && it.isHidden }
            3 -> allUserPackages.any { !it.isFrozen && !it.isHidden }
            else -> false
        }
    }

    fun updatePackageFrozen(packageName: String): Int {
        // Update in master list
        allPackages.find { it.packageName == packageName }?.let { pkg ->
            pkg.isFrozen = true
            pkg.isSelected = false
            pkg.frozenTimestamp = System.currentTimeMillis()
            HistoryHelper.addHistory(context, pkg.appName, pkg.packageName)
        }

        // Update in user apps list
        allUserPackages.find { it.packageName == packageName }?.let { pkg ->
            pkg.isFrozen = true
            pkg.isSelected = false
            pkg.frozenTimestamp = System.currentTimeMillis()
        }

        // Re-sort user apps list
        allUserPackages.sortWith(Comparator { p1, p2 ->
            if (p1.isFrozen != p2.isFrozen) {
                p1.isFrozen.compareTo(p2.isFrozen)
            } else if (p1.isFrozen) {
                p2.frozenTimestamp.compareTo(p1.frozenTimestamp) // Recently frozen first
            } else {
                p1.appName.compareTo(p2.appName, ignoreCase = true)
            }
        })

        // Re-sort the master list
        allPackages.sortWith(Comparator { p1, p2 ->
            if (p1.isFrozen != p2.isFrozen) {
                p1.isFrozen.compareTo(p2.isFrozen)
            } else if (p1.isFrozen) {
                p2.frozenTimestamp.compareTo(p1.frozenTimestamp) // Recently frozen first
            } else {
                p1.appName.compareTo(p2.appName, ignoreCase = true)
            }
        })
        
        updateFilter(currentMode)
        return 0 // Index doesn't matter much as we re-filter everything
    }
}
