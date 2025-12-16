package com.v2ray.ang.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.VPN
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityMainBinding
import com.v2ray.ang.extension.toast
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.V2RayServiceManager
import com.v2ray.ang.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : BaseActivity() {

    private val binding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    private val requestVpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            startV2Ray()
        }
    }

    val mainViewModel: MainViewModel by viewModels()

    private val homeFragment by lazy { HomeFragment() }
    private val locationsFragment by lazy { LocationsFragment() }
    private val settingsFragment by lazy { SettingsActivity.SettingsFragment() }

    // register activity result for requesting permission
    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                when (pendingAction) {
                    Action.POST_NOTIFICATIONS -> {}
                    else -> {}
                }
            } else {
                toast(R.string.toast_permission_denied)
            }
            pendingAction = Action.NONE
        }

    private var pendingAction: Action = Action.NONE

    enum class Action {
        NONE,
        POST_NOTIFICATIONS
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        supportActionBar?.hide()

        setupViewModel()
        initializeDefaultProfile()

        setupBottomNav(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                pendingAction = Action.POST_NOTIFICATIONS
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun setupViewModel() {
        mainViewModel.isRunning.observe(this) { isRunning ->
            // UI state is handled inside fragments
        }
        mainViewModel.startListenBroadcast()
        mainViewModel.initAssets(assets)
    }

    private fun setupBottomNav(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, homeFragment)
                .commit()
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            val fragment = when (item.itemId) {
                R.id.nav_home -> homeFragment
                R.id.nav_locations -> locationsFragment
                R.id.nav_settings -> settingsFragment
                else -> homeFragment
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit()
            true
        }

        if (binding.bottomNav.selectedItemId == 0) {
            binding.bottomNav.selectedItemId = R.id.nav_home
        }
    }

    private fun initializeDefaultProfile() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Check if the specific profile exists
                val serverList = MmkvManager.decodeServerList()
                var targetGuid: String? = null

                // Look for our specific profile
                for (guid in serverList) {
                    val config = MmkvManager.decodeServerConfig(guid)
                    if (config != null &&
                        config.server == "149.22.87.241" &&
                        config.serverPort == "443" &&
                        config.configType.toString() == "SHADOWSOCKS") {
                        targetGuid = guid
                        break
                    }
                }

                // If not found, create it
                if (targetGuid == null) {
                    val defaultProfileUrl = "ss://YWVzLTEyOC1nY206c2hhZG93c29ja3M=@149.22.87.241:443#Default Server"
                    val result = AngConfigManager.importBatchConfig(defaultProfileUrl, "", false)

                    if (result.first > 0) {
                        val updatedServerList = MmkvManager.decodeServerList()
                        if (updatedServerList.isNotEmpty()) {
                            targetGuid = updatedServerList.first()
                        }
                    }
                }

                // Set as selected
                if (targetGuid != null) {
                    MmkvManager.setSelectServer(targetGuid)
                    withContext(Dispatchers.Main) {
                        mainViewModel.reloadServerList()
                    }
                }
            } catch (e: Exception) {
                Log.e(AppConfig.TAG, "Failed to initialize default profile", e)
            }
        }
    }

    private fun startV2Ray() {
        lifecycleScope.launch {
            if (MmkvManager.getSelectServer().isNullOrEmpty()) {
                initializeDefaultProfile()
                delay(1000) // Wait for profile to be created
            }

            if (MmkvManager.getSelectServer().isNullOrEmpty()) {
                toast("Failed to load server configuration")
                return@launch
            }
            V2RayServiceManager.startVService(this@MainActivity)
        }
    }

    fun startVpnFromUi() {
        if ((MmkvManager.decodeSettingsString(AppConfig.PREF_MODE) ?: VPN) == VPN) {
            val intent = VpnService.prepare(this)
            if (intent == null) {
                startV2Ray()
            } else {
                requestVpnPermission.launch(intent)
            }
        } else {
            startV2Ray()
        }
    }

    fun toggleVpnFromUi() {
        if (mainViewModel.isRunning.value == true) {
            V2RayServiceManager.stopVService(this)
        } else if ((MmkvManager.decodeSettingsString(AppConfig.PREF_MODE) ?: VPN) == VPN) {
            val intent = VpnService.prepare(this)
            if (intent == null) {
                startV2Ray()
            } else {
                requestVpnPermission.launch(intent)
            }
        } else {
            startV2Ray()
        }
    }
}