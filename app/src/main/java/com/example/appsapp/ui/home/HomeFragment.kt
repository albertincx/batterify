package com.example.appsapp.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.appsapp.PackageKiller
import com.example.appsapp.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val homeViewModel =
            ViewModelProvider(this).get(HomeViewModel::class.java)

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.textHome
        homeViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }
        
        // Set up the kill package button using view binding
        binding.killPackageButton.setOnClickListener {
            var packageName = binding.packageNameInput.text.toString().trim()
            if (packageName.isEmpty()) {
                // Use a demo package if none is entered
                packageName = "com.android.chrome" // Common example package
            }
            PackageKiller.killPackage(requireContext(), packageName)
        }

        binding.pauseSocialAppsButton.setOnClickListener {
            val socialPackages = listOf(
                "com.instagram.android",
                "com.zhiliaoapp.musically",
                "com.ss.android.ugc.trill",
                "com.ss.android.ugc.aweme"
            )
            
            var anyFound = false
            socialPackages.forEach { pkg ->
                if (PackageKiller.isPackageInstalled(requireContext(), pkg)) {
                    anyFound = true
                    PackageKiller.killPackage(requireContext(), pkg)
                }
            }
            
            if (anyFound) {
                android.widget.Toast.makeText(
                    requireContext(), 
                    "Фоновые процессы соцсетей остановлены. Для полной остановки используйте 'Остановить' в настройках.", 
                    android.widget.Toast.LENGTH_LONG
                ).show()
            } else {
                android.widget.Toast.makeText(requireContext(), "Instagram или TikTok не найдены", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}