package com.v2ray.ang.ui

import android.annotation.SuppressLint
import android.text.Editable
import android.text.TextWatcher
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.databinding.FragmentLocationsBinding
import com.v2ray.ang.dto.StandardLocation
import com.v2ray.ang.handler.MmkvManager

class LocationsFragment : Fragment(R.layout.fragment_locations) {

    private var binding: FragmentLocationsBinding? = null
    private var adapter: StandardLocationsAdapter? = null
    private var allLocations: List<StandardLocation> = emptyList()

    private var mapReady: Boolean = false
    private var pendingMapUpdate: Triple<Double, Double, String?>? = null

    override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentLocationsBinding.bind(view)

        val activity = (requireActivity() as MainActivity)

        adapter = StandardLocationsAdapter { loc ->
            adapter?.setSelected(loc.code)

            MmkvManager.encodeSettings(AppConfig.PREF_SELECTED_LOCATION, loc.code)
            MmkvManager.encodeSettings(AppConfig.PREF_SELECTED_LOCATION_MANUAL, true)
            activity.navigateToHomeTab()
            activity.startAutoConnectForLocation(loc.code)
        }

        binding!!.recyclerView.setHasFixedSize(true)
        binding!!.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        activity.addCustomDividerToRecyclerView(binding!!.recyclerView, requireContext(), R.drawable.custom_divider)
        binding!!.recyclerView.adapter = adapter

        setupMap(binding!!.mapWebview)

        allLocations = getStandardLocations()
        adapter?.submitList(allLocations)

        val selected = MmkvManager.decodeSettingsString(AppConfig.PREF_SELECTED_LOCATION, "")?.uppercase().orEmpty()
        if (selected.isNotBlank()) {
            adapter?.setSelected(selected)
        }

        binding!!.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString().orEmpty().trim().lowercase()
                val filtered = if (q.isBlank()) {
                    allLocations
                } else {
                    allLocations.filter { it.name.lowercase().contains(q) || it.code.lowercase().contains(q) }
                }
                adapter?.submitList(filtered)
            }
        })

        updateMapForSelectedLocation()
    }

    override fun onDestroyView() {
        binding?.mapWebview?.destroy()
        binding = null
        adapter = null
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        updateMapForSelectedLocation()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupMap(webView: WebView) {
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

    private fun applyPendingMapUpdateIfReady() {
        if (!mapReady) return
        val b = binding ?: return
        val (lat, lon, label) = pendingMapUpdate ?: return
        b.mapWebview.evaluateJavascript(
            "window.setServerLocation(${lat}, ${lon}, ${org.json.JSONObject.quote(label)});",
            null
        )
    }

    private fun updateMapForSelectedLocation() {
        val b = binding ?: return
        val code = MmkvManager.decodeSettingsString(AppConfig.PREF_SELECTED_LOCATION, "")?.uppercase().orEmpty()
        val loc = allLocations.firstOrNull { it.code == code } ?: return
        val coords = when (code) {
            "RU" -> 55.7558 to 37.6173
            "US" -> 40.7128 to -74.0060
            "NL" -> 52.3676 to 4.9041
            "DE" -> 52.5200 to 13.4050
            "GB" -> 51.5074 to -0.1278
            "CH" -> 47.3769 to 8.5417
            "SG" -> 1.3521 to 103.8198
            "JP" -> 35.6762 to 139.6503
            "FR" -> 48.8566 to 2.3522
            "CA" -> 43.6532 to -79.3832
            "TR" -> 41.0082 to 28.9784
            "AR" -> -34.6037 to -58.3816
            "SE" -> 59.3293 to 18.0686
            "IS" -> 64.1466 to -21.9426
            "AU" -> -33.8688 to 151.2093
            "ES" -> 40.4168 to -3.7038
            "IT" -> 41.9028 to 12.4964
            "BR" -> -23.5505 to -46.6333
            "KR" -> 37.5665 to 126.9780
            "RO" -> 44.4268 to 26.1025
            "HK" -> 22.3193 to 114.1694
            "PL" -> 52.2297 to 21.0122
            else -> null
        } ?: return

        val (lat, lon) = coords
        pendingMapUpdate = Triple(lat, lon, loc.name)
        applyPendingMapUpdateIfReady()
    }

    private fun getStandardLocations(): List<StandardLocation> {
        return listOf(
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
    }

    
}
