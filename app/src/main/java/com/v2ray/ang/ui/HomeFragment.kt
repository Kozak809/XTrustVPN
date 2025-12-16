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
import com.v2ray.ang.R
import com.v2ray.ang.databinding.FragmentHomeBinding
import com.v2ray.ang.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class HomeFragment : Fragment(R.layout.fragment_home) {

    private var binding: FragmentHomeBinding? = null

    private val gson = Gson()

    override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentHomeBinding.bind(view)

        setupWebView(binding!!.mapWebview)

        binding!!.btnConnect.setOnClickListener {
            (requireActivity() as MainActivity).toggleVpnFromUi()
        }

        val vm: MainViewModel = (requireActivity() as MainActivity).mainViewModel

        vm.updateTestResultAction.observe(viewLifecycleOwner) {
            binding?.tvTestState?.text = it
        }

        vm.isRunning.observe(viewLifecycleOwner) { isRunning ->
            val running = isRunning == true
            if (running) {
                binding?.btnConnect?.setImageResource(R.drawable.ic_stop_24dp)
                binding?.btnConnect?.isSelected = true
                updateStatusUI(true)
            } else {
                binding?.btnConnect?.setImageResource(R.drawable.ic_play_24dp)
                binding?.btnConnect?.isSelected = false
                updateStatusUI(false)
            }
        }

        vm.connectionSuccessEvent.observe(viewLifecycleOwner) { info ->
            if (info != null) {
                binding?.tvLocation?.text = info.serverName
                fetchGeoAndUpdateMap(info.serverAddress, info.serverName)
            }
        }
    }

    override fun onDestroyView() {
        binding?.mapWebview?.destroy()
        binding = null
        super.onDestroyView()
    }

    private fun updateStatusUI(isConnected: Boolean) {
        val b = binding ?: return
        if (isConnected) {
            b.statusDot.setBackgroundResource(R.drawable.status_dot)
            b.statusDot.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.success_green))
            b.statusText.text = getString(R.string.status_connected)
        } else {
            b.statusDot.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.danger_red))
            b.statusText.text = getString(R.string.status_disconnected)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(webView: WebView) {
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()

        webView.setOnTouchListener { _, _ -> true }

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true

        webView.loadUrl("file:///android_asset/vpn_map.html")
    }

    private data class GeoResponse(
        val latitude: Double?,
        val longitude: Double?,
        val city: String?,
        val country_name: String?
    )

    private fun fetchGeoAndUpdateMap(host: String, serverName: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val url = URL("https://ipapi.co/${host}/json/")
            val result = try {
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

            if (result.isNullOrBlank()) {
                return@launch
            }

            val geo = try {
                gson.fromJson(result, GeoResponse::class.java)
            } catch (_: Exception) {
                null
            }

            val lat = geo?.latitude
            val lon = geo?.longitude
            if (lat == null || lon == null) {
                return@launch
            }

            val label = listOfNotNull(geo.city, geo.country_name).joinToString(", ").ifBlank { serverName }

            withContext(Dispatchers.Main) {
                binding?.tvLocation?.text = label
                binding?.mapWebview?.evaluateJavascript(
                    "window.setServerLocation(${lat}, ${lon}, ${gson.toJson(label)});",
                    null
                )
            }
        }
    }
}
