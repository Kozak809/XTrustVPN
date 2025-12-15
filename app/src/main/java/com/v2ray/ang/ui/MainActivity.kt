package com.v2ray.ang.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.VPN
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityMainBinding
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.V2RayServiceManager
import com.v2ray.ang.util.Utils
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

        binding.fab.setOnClickListener {
            // Simple click without animation for now
            
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

        setupViewModel()
        initializeDefaultProfile()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                pendingAction = Action.POST_NOTIFICATIONS
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun setupViewModel() {
        mainViewModel.updateTestResultAction.observe(this) { setTestState(it) }
        mainViewModel.isRunning.observe(this) { isRunning ->
            if (isRunning) {
                binding.fab.setImageResource(R.drawable.ic_stop_24dp)
                binding.fab.setBackgroundResource(R.drawable.btn_connect_selector)
                binding.fab.isSelected = true
                setTestState(getString(R.string.connection_connected))
                updateStatusUI(true)
            } else {
                binding.fab.setImageResource(R.drawable.ic_play_24dp)
                binding.fab.setBackgroundResource(R.drawable.btn_connect_selector)
                binding.fab.isSelected = false
                setTestState(getString(R.string.connection_test_fail))
                updateStatusUI(false)
            }
        }
        mainViewModel.startListenBroadcast()
        mainViewModel.initAssets(assets)
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

    private fun setTestState(content: String?) {
        binding.tvTestState.text = content
    }

    private fun updateStatusUI(isConnected: Boolean) {
        if (isConnected) {
            binding.statusDot.setBackgroundResource(R.drawable.status_dot)
            binding.statusDot.setBackgroundColor(ContextCompat.getColor(this, R.color.success_green))
            binding.statusText.text = "Подключено"
        } else {
            binding.statusDot.setBackgroundColor(ContextCompat.getColor(this, R.color.danger_red))
            binding.statusText.text = "Отключено"
        }
    }
}