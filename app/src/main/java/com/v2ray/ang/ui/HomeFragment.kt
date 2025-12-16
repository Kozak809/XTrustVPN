package com.v2ray.ang.ui

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.databinding.FragmentHomeBinding
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.dto.StandardLocation
import com.v2ray.ang.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class HomeFragment : Fragment(R.layout.fragment_home) {

    private var binding: FragmentHomeBinding? = null

    private val gson = Gson()

    private var mapReady: Boolean = false
    private var pendingMapUpdate: Triple<Double, Double, String?>? = null

    private var countryLookupStarted: Boolean = false

    private data class IpApiResponse(
        val country_code: String?
    )

    private val standardLocations = listOf(
        StandardLocation(code = "RU", name = "Russia", flag = "🇷🇺", pingText = "25 ms"),
        StandardLocation(code = "US", name = "United States", flag = "🇺🇸", pingText = "120 ms"),
        StandardLocation(code = "NL", name = "Netherlands", flag = "🇳🇱", pingText = "55 ms"),
        StandardLocation(code = "DE", name = "Germany", flag = "🇩🇪", pingText = "45 ms"),
        StandardLocation(code = "GB", name = "United Kingdom", flag = "🇬🇧", pingText = "85 ms"),
        StandardLocation(code = "CH", name = "Switzerland", flag = "🇨🇭", pingText = "60 ms"),
        StandardLocation(code = "SG", name = "Singapore", flag = "🇸🇬", pingText = "180 ms"),
        StandardLocation(code = "JP", name = "Japan", flag = "🇯🇵", pingText = "200 ms"),
        StandardLocation(code = "FR", name = "France", flag = "🇫🇷", pingText = "60 ms"),
        StandardLocation(code = "CA", name = "Canada", flag = "🇨🇦", pingText = "110 ms"),
        StandardLocation(code = "TR", name = "Turkey", flag = "🇹🇷", pingText = "95 ms"),
        StandardLocation(code = "AR", name = "Argentina", flag = "🇦🇷", pingText = "250 ms"),
        StandardLocation(code = "SE", name = "Sweden", flag = "🇸🇪", pingText = "70 ms"),
        StandardLocation(code = "IS", name = "Iceland", flag = "🇮🇸", pingText = "80 ms"),
        StandardLocation(code = "AU", name = "Australia", flag = "🇦🇺", pingText = "220 ms"),
        StandardLocation(code = "ES", name = "Spain", flag = "🇪🇸", pingText = "65 ms"),
        StandardLocation(code = "IT", name = "Italy", flag = "🇮🇹", pingText = "75 ms"),
        StandardLocation(code = "BR", name = "Brazil", flag = "🇧🇷", pingText = "180 ms"),
        StandardLocation(code = "KR", name = "South Korea", flag = "🇰🇷", pingText = "150 ms"),
        StandardLocation(code = "RO", name = "Romania", flag = "🇷🇴", pingText = "40 ms"),
        StandardLocation(code = "HK", name = "Hong Kong", flag = "🇭🇰", pingText = "170 ms"),
        StandardLocation(code = "PL", name = "Poland", flag = "🇵🇱", pingText = "50 ms")
    )

    private val codeToLatLon = mapOf(
        "RU" to (55.7558 to 37.6173),
        "US" to (40.7128 to -74.0060),
        "NL" to (52.3676 to 4.9041),
        "DE" to (52.5200 to 13.4050),
        "GB" to (51.5074 to -0.1278),
        "CH" to (47.3769 to 8.5417),
        "SG" to (1.3521 to 103.8198),
        "JP" to (35.6762 to 139.6503),
        "FR" to (48.8566 to 2.3522),
        "CA" to (43.6532 to -79.3832),
        "TR" to (41.0082 to 28.9784),
        "AR" to (-34.6037 to -58.3816),
        "SE" to (59.3293 to 18.0686),
        "IS" to (64.1466 to -21.9426),
        "AU" to (-33.8688 to 151.2093),
        "ES" to (40.4168 to -3.7038),
        "IT" to (41.9028 to 12.4964),
        "BR" to (-23.5505 to -46.6333),
        "KR" to (37.5665 to 126.9780),
        "RO" to (44.4268 to 26.1025),
        "HK" to (22.3193 to 114.1694),
        "PL" to (52.2297 to 21.0122)
    )

    override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentHomeBinding.bind(view)

        setupWebView(binding!!.mapWebview)

        // Show selected location immediately (no external geo lookup)
        updateLocationFromSelectedCode()

        binding!!.btnConnect.setOnClickListener {
            binding?.btnConnect?.animate()?.cancel()
            binding?.btnConnect?.animate()?.scaleX(0.92f)?.scaleY(0.92f)?.setDuration(90)
                ?.withEndAction {
                    binding?.btnConnect?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(140)?.start()
                }?.start()
            (requireActivity() as MainActivity).toggleVpnFromUi()
        }

        val vm: MainViewModel = (requireActivity() as MainActivity).mainViewModel

        vm.connectionState.observe(viewLifecycleOwner) { state ->
            when (state) {
                MainViewModel.ConnectionState.CONNECTED -> {
                    binding?.btnConnect?.isSelected = true
                    updateStatusUIConnected()
                }

                MainViewModel.ConnectionState.CONNECTING -> {
                    binding?.btnConnect?.isSelected = true
                    updateStatusUIConnecting()
                }

                else -> {
                    binding?.btnConnect?.isSelected = false
                    updateStatusUIDisconnected()
                }
            }
        }

        vm.connectionSuccessEvent.observe(viewLifecycleOwner) { info ->
            if (info != null) {
                // Once connected, keep showing selected location (not server host geo)
                updateLocationFromSelectedCode()
            }
        }
    }

    private fun updateLocationFromSelectedCode() {
        val b = binding ?: return
        val manualLoc = MmkvManager.decodeSettingsBool(AppConfig.PREF_SELECTED_LOCATION_MANUAL, false)
        val code = MmkvManager.decodeSettingsString(AppConfig.PREF_SELECTED_LOCATION, "")?.uppercase().orEmpty()
        if (!manualLoc && !countryLookupStarted) {
            countryLookupStarted = true
            fetchCountryCodeByIp()
        }
        if (code.isBlank()) {
            if (!countryLookupStarted) {
                countryLookupStarted = true
                fetchCountryCodeByIp()
            }
            b.tvLocation.text = getString(R.string.location_unknown)
            return
        }
        val loc = standardLocations.firstOrNull { it.code == code }
        if (loc == null) {
            b.tvLocation.text = getString(R.string.location_unknown)
            return
        }
        b.tvLocation.text = loc.name

        val coords = codeToLatLon[code]
        if (coords != null) {
            val (lat, lon) = coords
            pendingMapUpdate = Triple(lat, lon, loc.name)
            applyPendingMapUpdateIfReady()
        }
    }

    private fun applyPendingMapUpdateIfReady() {
        if (!mapReady) return
        val b = binding ?: return
        val (lat, lon, label) = pendingMapUpdate ?: return
        b.mapWebview.evaluateJavascript(
            "window.setServerLocation(${lat}, ${lon}, ${gson.toJson(label)});",
            null
        )
    }

    override fun onDestroyView() {
        binding?.mapWebview?.destroy()
        binding = null
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        updateLocationFromSelectedCode()
    }

    private fun updateStatusUIConnected() {
        val b = binding ?: return
        b.statusDot.setBackgroundResource(R.drawable.status_dot)
        b.statusDot.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.success_green))
        b.statusText.text = getString(R.string.status_connected)
    }

    private fun updateStatusUIConnecting() {
        val b = binding ?: return
        b.statusDot.setBackgroundResource(R.drawable.status_dot)
        b.statusDot.setBackgroundColor(0xFFFFC107.toInt())
        b.statusText.text = getString(R.string.status_connecting)
    }

    private fun updateStatusUIDisconnected() {
        val b = binding ?: return
        b.statusDot.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.danger_red))
        b.statusText.text = getString(R.string.status_disconnected)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(webView: WebView) {
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                mapReady = true
                applyPendingMapUpdateIfReady()
            }
        }
        webView.webChromeClient = WebChromeClient()

        webView.setOnTouchListener { _, _ -> true }

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true

        webView.loadUrl("file:///android_asset/vpn_map.html")
    }

    private fun fetchCountryCodeByIp() {
        lifecycleScope.launch(Dispatchers.IO) {
            val response = try {
                val url = URL("https://ipapi.co/json/")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "XTrustVPN")
                }
                conn.inputStream.bufferedReader().use { it.readText() }
            } catch (_: Exception) {
                null
            }

            val countryCode = try {
                val parsed = gson.fromJson(response, IpApiResponse::class.java)
                parsed?.country_code?.uppercase().orEmpty()
            } catch (_: Exception) {
                ""
            }

            if (countryCode.isBlank()) return@launch

            MmkvManager.encodeSettings(AppConfig.PREF_SELECTED_LOCATION, countryCode)
            withContext(Dispatchers.Main) {
                updateLocationFromSelectedCode()
            }
        }
    }

}
