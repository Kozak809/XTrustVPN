package com.v2ray.ang

import android.content.Context
import androidx.multidex.MultiDexApplication
import androidx.work.Configuration
import androidx.work.WorkManager
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig.ANG_PACKAGE
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import es.dmoral.toasty.Toasty

class AngApplication : MultiDexApplication() {
    companion object {
        lateinit var application: AngApplication
    }

    /**
     * Attaches the base context to the application.
     * @param base The base context.
     */
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        application = this
    }

    private val workManagerConfiguration: Configuration = Configuration.Builder()
        .setDefaultProcessName("${ANG_PACKAGE}:bg")
        .build()

    /**
     * Initializes the application.
     */
    override fun onCreate() {
        super.onCreate()

        MMKV.initialize(this)

        SettingsManager.setNightMode()
        // Initialize WorkManager with the custom configuration
        WorkManager.initialize(this, workManagerConfiguration)

        SettingsManager.initRoutingRulesets(this)

        es.dmoral.toasty.Toasty.Config.getInstance()
            .setGravity(android.view.Gravity.BOTTOM, 0, 200)
            .apply()

        // Initialize default Shadowsocks profile if none exists
        initializeDefaultProfile()
    }

    /**
     * Initializes a default Shadowsocks profile if no profiles are configured.
     */
    private fun initializeDefaultProfile() {
        try {
            // Check if there are any existing profiles
            val serverList = MmkvManager.decodeServerList()
            if (serverList.isEmpty()) {
                // Add default Shadowsocks profile
                val defaultProfileUrl = "ss://YWVzLTEyOC1nY206c2hhZG93c29ja3M=@149.22.87.241:443#Default Server"
                val result = AngConfigManager.importBatchConfig(defaultProfileUrl, "", false)
                
                if (result.first > 0) {
                    // Get the first (and only) server GUID and set it as selected
                    val updatedServerList = MmkvManager.decodeServerList()
                    if (updatedServerList.isNotEmpty()) {
                        MmkvManager.setSelectServer(updatedServerList.first())
                    }
                }
            }
        } catch (e: Exception) {
            // Silently fail to avoid app startup issues
            e.printStackTrace()
        }
    }
}
