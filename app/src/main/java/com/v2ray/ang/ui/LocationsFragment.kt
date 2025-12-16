package com.v2ray.ang.ui

import android.annotation.SuppressLint
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.databinding.FragmentLocationsBinding
import com.v2ray.ang.dto.StandardLocation
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.V2RayServiceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class LocationsFragment : Fragment(R.layout.fragment_locations) {

    private var binding: FragmentLocationsBinding? = null
    private var adapter: StandardLocationsAdapter? = null
    private val gson = Gson()

    private data class LocationApiResponse(
        val location: String?,
        val count_available: Int?,
        val keys: List<String>?
    )

    override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentLocationsBinding.bind(view)

        val activity = (requireActivity() as MainActivity)

        adapter = StandardLocationsAdapter { loc ->
            adapter?.setSelected(loc.code)
            lifecycleScope.launch {
                loadLocationAndAutoConnect(activity, loc)
            }
        }

        binding!!.recyclerView.setHasFixedSize(true)
        binding!!.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        activity.addCustomDividerToRecyclerView(binding!!.recyclerView, requireContext(), R.drawable.custom_divider)
        binding!!.recyclerView.adapter = adapter

        adapter?.submitList(getStandardLocations())
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

    @SuppressLint("NotifyDataSetChanged")
    private suspend fun loadLocationAndAutoConnect(activity: MainActivity, loc: StandardLocation) {
        val code = loc.code

        val response = withContext(Dispatchers.IO) {
            val url = URL("https://testtrustvpnapi.matrixqweqwe1123.workers.dev/api/location/$code")
            try {
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 10000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "XTrustVPN")
                }
                conn.inputStream.bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                Log.e(AppConfig.TAG, "Failed to load location $code", e)
                null
            }
        }

        if (response.isNullOrBlank()) {
            return
        }

        val parsed = try {
            gson.fromJson(response, LocationApiResponse::class.java)
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to parse location response", e)
            null
        }

        val keys = parsed?.keys.orEmpty().take(5)
        if (keys.isEmpty()) {
            return
        }

        val before = MmkvManager.decodeServerList().toList()
        for (key in keys) {
            try {
                AngConfigManager.importBatchConfig(key, "", true)
            } catch (e: Exception) {
                Log.e(AppConfig.TAG, "Failed to import config", e)
            }
        }
        val after = MmkvManager.decodeServerList().toList()
        val newGuids = after.filter { !before.contains(it) }.take(5)

        if (newGuids.isEmpty()) {
            return
        }

        for (guid in newGuids) {
            if (!isAdded) {
                return
            }

            activity.mainViewModel.connectionSuccessEvent.value = null
            MmkvManager.setSelectServer(guid)

            if (activity.mainViewModel.isRunning.value == true) {
                V2RayServiceManager.stopVService(activity)
                delay(800)
            }

            activity.startVpnFromUi()

            val startMs = System.currentTimeMillis()
            while (System.currentTimeMillis() - startMs < 15000) {
                val success = activity.mainViewModel.connectionSuccessEvent.value
                if (success != null) {
                    return
                }
                delay(250)
            }

            V2RayServiceManager.stopVService(activity)
            delay(800)
        }
    }

    override fun onDestroyView() {
        binding = null
        adapter = null
        super.onDestroyView()
    }
}
