package com.kozak.xtrustvpn.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kozak.xtrustvpn.BuildConfig
import com.kozak.xtrustvpn.database.DataStore
import com.kozak.xtrustvpn.database.ProfileManager
import com.kozak.xtrustvpn.ktx.parseProxies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class MainViewModel : ViewModel() {

    private val client = OkHttpClient()

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Idle)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _locationData = MutableStateFlow<LocationData?>(null)
    val locationData: StateFlow<LocationData?> = _locationData.asStateFlow()

    fun fetchRealLocation() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = client.newCall(Request.Builder().url("http://ip-api.com/json").build()).execute()
                val json = JSONObject(response.body?.string() ?: "{}")
                val country = json.optString("country", "Unknown")
                val city = json.optString("city", "")
                val text = if (city.isNotEmpty()) "$city, $country" else country
                val lat = json.optDouble("lat", 0.0)
                val lon = json.optDouble("lon", 0.0)

                _locationData.value = LocationData(text, lat, lon)
            } catch (e: Exception) {
                _locationData.value = LocationData("Location not found", 0.0, 0.0)
            }
        }
    }

    fun connectToLocation(loc: LocationItem) {
        if (_uiState.value is MainUiState.Connecting) return

        _uiState.value = MainUiState.Connecting("Fetching keys...")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val code = loc.code
                // Use the URL from BuildConfig
                val url = BuildConfig.API_URL + code
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) throw Exception("API Error: ${response.code}")

                val json = response.body?.string() ?: throw Exception("Empty response")
                val jsonObject = JSONObject(json)
                val keys = jsonObject.getJSONArray("keys")

                if (keys.length() == 0) throw Exception("No keys found")

                val keyBase64 = keys.getString(0)
                val key = try {
                    String(android.util.Base64.decode(keyBase64, android.util.Base64.DEFAULT))
                } catch (e: Exception) {
                    keyBase64
                }

                val profiles = parseProxies(key)
                if (profiles.isEmpty()) throw Exception("Invalid profile config")

                val profile = profiles[0]
                profile.name = "${loc.name} (Auto)"

                val entity = ProfileManager.createProfile(DataStore.selectedGroupForImport(), profile)
                DataStore.selectedProxy = entity.id

                _uiState.value = MainUiState.ConnectReady
            } catch (e: Exception) {
                _uiState.value = MainUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun resetState() {
        _uiState.value = MainUiState.Idle
    }
}

data class LocationData(val text: String, val lat: Double, val lon: Double)

sealed class MainUiState {
    object Idle : MainUiState()
    data class Connecting(val status: String) : MainUiState()
    object ConnectReady : MainUiState()
    data class Error(val message: String) : MainUiState()
}
