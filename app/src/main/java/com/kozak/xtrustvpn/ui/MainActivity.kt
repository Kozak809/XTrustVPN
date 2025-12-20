package com.kozak.xtrustvpn.ui

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.RemoteException
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.kozak.xtrustvpn.R
import com.kozak.xtrustvpn.SagerNet
import com.kozak.xtrustvpn.aidl.ISagerNetService
import com.kozak.xtrustvpn.aidl.SpeedDisplayData
import com.kozak.xtrustvpn.aidl.TrafficData
import com.kozak.xtrustvpn.bg.BaseService
import com.kozak.xtrustvpn.bg.SagerConnection
import com.kozak.xtrustvpn.database.DataStore
import com.kozak.xtrustvpn.database.ProfileManager
import com.kozak.xtrustvpn.databinding.LayoutMainNewBinding
import com.kozak.xtrustvpn.ktx.parseProxies
import com.kozak.xtrustvpn.ktx.runOnDefaultDispatcher
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class MainActivity : ThemedActivity(), SagerConnection.Callback {

    lateinit var binding: LayoutMainNewBinding
    private val viewModel: MainViewModel by viewModels()
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var selectedLocation: LocationItem? = null
    private var locatingAnimator: ObjectAnimator? = null

    // Country Code Mapping
    private val countryCodes = mapOf(
        "Russia" to "RU", "United States" to "US", "Netherlands" to "NL",
        "Germany" to "DE", "United Kingdom" to "GB", "Switzerland" to "CH",
        "Singapore" to "SG", "Japan" to "JP", "France" to "FR",
        "Canada" to "CA", "Turkey" to "TR", "Argentina" to "AR",
        "Sweden" to "SE", "Iceland" to "IS", "Australia" to "AU",
        "Spain" to "ES", "Italy" to "IT", "Brazil" to "BR",
        "South Korea" to "KR", "Romania" to "RO", "Hong Kong" to "HK",
        "Poland" to "PL"
    )

    private val locations = listOf(
        LocationItem("Russia", "RU", "🇷🇺", "25 ms", 61.5, 105.3),
        LocationItem("United States", "US", "🇺🇸", "120 ms", 39.8, -98.6),
        LocationItem("Netherlands", "NL", "🇳🇱", "55 ms", 52.1, 5.3),
        LocationItem("Germany", "DE", "🇩🇪", "45 ms", 51.2, 10.4),
        LocationItem("United Kingdom", "GB", "🇬🇧", "85 ms", 55.4, -3.4),
        LocationItem("Switzerland", "CH", "🇨🇭", "60 ms", 46.8, 8.2),
        LocationItem("Singapore", "SG", "🇸🇬", "180 ms", 1.3, 103.8),
        LocationItem("Japan", "JP", "🇯🇵", "200 ms", 36.2, 138.3),
        LocationItem("France", "FR", "🇫🇷", "60 ms", 46.2, 2.2),
        LocationItem("Canada", "CA", "🇨🇦", "110 ms", 56.1, -106.3),
        LocationItem("Turkey", "TR", "🇹🇷", "95 ms", 38.9, 35.2),
        LocationItem("Argentina", "AR", "🇦🇷", "250 ms", -38.4, -63.6),
        LocationItem("Sweden", "SE", "🇸🇪", "70 ms", 60.1, 18.6),
        LocationItem("Iceland", "IS", "🇮🇸", "80 ms", 64.9, -19.0),
        LocationItem("Australia", "AU", "🇦🇺", "220 ms", -25.2, 133.7),
        LocationItem("Spain", "ES", "🇪🇸", "65 ms", 40.4, -3.7),
        LocationItem("Italy", "IT", "🇮🇹", "75 ms", 41.8, 12.5),
        LocationItem("Brazil", "BR", "🇧🇷", "180 ms", -14.2, -51.9),
        LocationItem("South Korea", "KR", "🇰🇷", "150 ms", 35.9, 127.7),
        LocationItem("Romania", "RO", "🇷🇴", "40 ms", 45.9, 24.9),
        LocationItem("Hong Kong", "HK", "🇭🇰", "170 ms", 22.3, 114.1),
        LocationItem("Poland", "PL", "🇵🇱", "50 ms", 51.9, 19.1)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = LayoutMainNewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Fix Bottom Insets for navigation
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.navContainer.setPadding(0, 15.dp, 0, 15.dp + insets.bottom)
            WindowInsetsCompat.CONSUMED
        }

        setupMap()
        setupNavigation()
        setupRecycler()
        setupConnection()
        setupSettings()

        connection.connect(this, this)

        // Initial Fetch
        startLocatingAnimation()
        viewModel.fetchRealLocation()

        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                launch {
                    viewModel.locationData.collect { data ->
                        if (data != null) {
                            stopLocatingAnimation()
                            binding.locText.text = data.text
                            if (data.lat != 0.0 && data.lon != 0.0) {
                                binding.mapWebView.evaluateJavascript("setLocation(${data.lat}, ${data.lon})", null)
                            }
                        }
                    }
                }
                launch {
                    viewModel.uiState.collect { state ->
                        when (state) {
                            is MainUiState.Connecting -> {
                                binding.statusText.text = state.status
                                binding.btnConnect.background = getDrawable(R.drawable.bg_connect_btn_off)
                            }
                            is MainUiState.ConnectReady -> {
                                binding.statusText.text = "Connecting..."
                                val intent = VpnRequestActivity.StartService()
                                connectLauncher.launch(null)
                                viewModel.resetState()
                            }
                            is MainUiState.Error -> {
                                Toast.makeText(this@MainActivity, "Error: ${state.message}", Toast.LENGTH_SHORT).show()
                                binding.statusText.text = "Connection Failed"
                                viewModel.resetState()
                            }
                            is MainUiState.Idle -> {
                                // Handled by service state callback usually
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupSettings() {
        // Settings are now handled by SettingsPreferenceFragment in layout XML
    }

    private fun startLocatingAnimation() {
        binding.locText.text = "Locating..."
        locatingAnimator = ObjectAnimator.ofPropertyValuesHolder(
            binding.locText,
            PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0.5f, 1f)
        ).apply {
            duration = 1500
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun stopLocatingAnimation() {
        locatingAnimator?.cancel()
        binding.locText.alpha = 1f
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupMap() {
        binding.mapWebView.settings.javaScriptEnabled = true
        binding.mapWebView.webViewClient = WebViewClient()
        binding.mapWebView.loadUrl("file:///android_asset/map.html")
    }

    private fun updateMap(item: LocationItem) {
        binding.locText.text = item.name
        binding.mapWebView.evaluateJavascript("setLocation(${item.lat}, ${item.lng})", null)
    }

    private fun setupNavigation() {
        fun updateNav(selected: Int) {
            binding.tabHome.visibility = if (selected == 0) View.VISIBLE else View.GONE
            binding.tabLoc.visibility = if (selected == 1) View.VISIBLE else View.GONE
            binding.tabSet.visibility = if (selected == 2) View.VISIBLE else View.GONE

            binding.iconHome.setColorFilter(if(selected==0) getColor(R.color.primary_blue) else getColor(R.color.text_gray))
            binding.textHome.setTextColor(if(selected==0) getColor(R.color.primary_blue) else getColor(R.color.text_gray))

            binding.iconLoc.setColorFilter(if(selected==1) getColor(R.color.primary_blue) else getColor(R.color.text_gray))
            binding.textLoc.setTextColor(if(selected==1) getColor(R.color.primary_blue) else getColor(R.color.text_gray))

            binding.iconSet.setColorFilter(if(selected==2) getColor(R.color.primary_blue) else getColor(R.color.text_gray))
            binding.textSet.setTextColor(if(selected==2) getColor(R.color.primary_blue) else getColor(R.color.text_gray))
        }

        binding.navHome.setOnClickListener { updateNav(0) }
        binding.navLoc.setOnClickListener { updateNav(1) }
        binding.navSet.setOnClickListener { updateNav(2) }

        // Default
        updateNav(0)
    }

    private fun setupRecycler() {
        val adapter = LocationAdapter(locations) { item ->
            selectedLocation = item
            binding.navHome.performClick()
            startLocatingAnimation()

            // Auto connect logic
            if (DataStore.serviceState.connected) {
                SagerNet.stopService()
                scope.launch {
                    delay(1000) // Give it a second to stop
                    viewModel.connectToLocation(item)
                }
            } else {
                viewModel.connectToLocation(item)
            }
        }
        binding.recyclerLoc.layoutManager = LinearLayoutManager(this)
        binding.recyclerLoc.adapter = adapter

        binding.search.addTextChangedListener {
            adapter.filter(it.toString())
        }
    }

    private fun setupConnection() {
        binding.btnConnect.setOnClickListener {
            // Check UI State to prevent double clicks
            if (viewModel.uiState.value is MainUiState.Connecting) return@setOnClickListener

            if (DataStore.serviceState.connected) {
                SagerNet.stopService()
            } else {
                val loc = selectedLocation
                if (loc != null) {
                    viewModel.connectToLocation(loc)
                } else {
                    Toast.makeText(this, "Please select a location first", Toast.LENGTH_SHORT).show()
                    binding.navLoc.performClick()
                }
            }
        }
    }

    private val connectLauncher = registerForActivityResult(VpnRequestActivity.StartService()) {
        if (it) {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
            viewModel.resetState()
            binding.statusText.text = "Disconnected"
        } else {
            // Success (service starting)
        }
    }

    // Service Callbacks
    val connection = SagerConnection(SagerConnection.CONNECTION_ID_MAIN_ACTIVITY_FOREGROUND, true)
    
    override fun onServiceConnected(service: ISagerNetService) {
        try {
             changeState(BaseService.State.values()[service.state])
        } catch (_: RemoteException) { }
    }
    
    override fun onServiceDisconnected() = changeState(BaseService.State.Idle)
    override fun onBinderDied() {
        connection.disconnect(this)
        connection.connect(this, this)
    }
    
    override fun stateChanged(state: BaseService.State, profileName: String?, msg: String?) {
        changeState(state, msg)
    }

    private fun changeState(state: BaseService.State, msg: String? = null) {
        DataStore.serviceState = state
        
        when (state) {
            BaseService.State.Connected -> {
                binding.btnConnect.background = getDrawable(R.drawable.bg_connect_btn_on)
                binding.statusDot.background = getDrawable(R.drawable.bg_dot_on)
                binding.statusText.text = "Connected"
                selectedLocation?.let {
                    updateMap(it)
                    stopLocatingAnimation()
                }
            }
            BaseService.State.Stopping, BaseService.State.Idle -> {
                binding.btnConnect.background = getDrawable(R.drawable.bg_connect_btn_off)
                binding.statusDot.background = getDrawable(R.drawable.bg_dot_off)
                binding.statusText.text = "Disconnected"
            }
            BaseService.State.Connecting -> {
                 binding.statusText.text = "Connecting..."
            }
            else -> {}
        }
        
        if (msg != null) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun cbSpeedUpdate(stats: SpeedDisplayData) {}
    override fun cbTrafficUpdate(data: TrafficData) {}
    override fun cbSelectorUpdate(id: Long) {}

    override fun onStart() {
        connection.updateConnectionId(SagerConnection.CONNECTION_ID_MAIN_ACTIVITY_FOREGROUND)
        super.onStart()
    }

    override fun onStop() {
        connection.updateConnectionId(SagerConnection.CONNECTION_ID_MAIN_ACTIVITY_BACKGROUND)
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        connection.disconnect(this)
        scope.cancel()
    }

    fun importSubscription(uri: android.net.Uri) {}
    fun refreshNavMenu(enable: Boolean) {}
    fun urlTest(): Int = 0

    // Extension property for dp conversion
    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}