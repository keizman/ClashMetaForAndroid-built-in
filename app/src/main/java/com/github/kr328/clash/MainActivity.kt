package com.github.kr328.clash

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.ticker
import com.github.kr328.clash.design.MainDesign
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.util.startClashService
import com.github.kr328.clash.util.stopClashService
import com.github.kr328.clash.util.withClash
import com.github.kr328.clash.util.withProfile
import com.github.kr328.clash.core.bridge.*
import com.github.kr328.clash.service.model.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import com.github.kr328.clash.design.R

class MainActivity : BaseActivity<MainDesign>() {
    override suspend fun main() {
        val design = MainDesign(this)

        setContentDesign(design)

        design.fetch()

        val ticker = ticker(TimeUnit.SECONDS.toMillis(1))

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ActivityStart,
                        Event.ServiceRecreated,
                        Event.ClashStop, Event.ClashStart,
                        Event.ProfileLoaded, Event.ProfileChanged -> design.fetch()
                        else -> Unit
                    }
                }
                design.requests.onReceive {
                    when (it) {
                        MainDesign.Request.ToggleStatus -> {
                            if (clashRunning)
                                stopClashService()
                            else
                                design.startClash()
                        }
                        MainDesign.Request.OpenProxy ->
                            startActivity(ProxyActivity::class.intent)
                        MainDesign.Request.OpenProfiles ->
                            startActivity(ProfilesActivity::class.intent)
                        MainDesign.Request.OpenProviders ->
                            startActivity(ProvidersActivity::class.intent)
                        MainDesign.Request.OpenLogs -> {
                            if (LogcatService.running) {
                                startActivity(LogcatActivity::class.intent)
                            } else {
                                startActivity(LogsActivity::class.intent)
                            }
                        }
                        MainDesign.Request.OpenSettings ->
                            startActivity(SettingsActivity::class.intent)
                        MainDesign.Request.OpenHelp ->
                            startActivity(HelpActivity::class.intent)
                        MainDesign.Request.OpenAbout ->
                            design.showAbout(queryAppVersionName())
                        MainDesign.Request.SyncProfile ->
                            syncBuiltInProfiles(design)
                        MainDesign.Request.RefreshProfiles ->
                            refreshAllProfiles(design)
                        MainDesign.Request.ShowFloatingWindow -> {
                            showFloatingWindow()
                            design.fetch()
                        }
                    }
                }
                if (clashRunning) {
                    ticker.onReceive {
                        design.fetchTraffic()
                    }
                }
            }
        }
    }

    private suspend fun MainDesign.fetch() {
        setClashRunning(clashRunning)

        val state = withClash {
            queryTunnelState()
        }
        val providers = withClash {
            queryProviders()
        }

        setMode(state.mode)
        setHasProviders(providers.isNotEmpty())
        setFloatingWindowShowing(FloatingWindowService.isShowing)

        withProfile {
            setProfileName(queryActive()?.name)
        }
    }

    private suspend fun MainDesign.fetchTraffic() {
        withClash {
            setForwarded(queryTrafficTotal())
        }
    }

    private suspend fun MainDesign.startClash() {
        val active = withProfile { queryActive() }

        if (active == null || !active.imported) {
            showToast(R.string.no_profile_selected, ToastDuration.Long) {
                setAction(R.string.profiles) {
                    startActivity(ProfilesActivity::class.intent)
                }
            }

            return
        }

        val vpnRequest = startClashService()

        try {
            if (vpnRequest != null) {
                val result = startActivityForResult(
                    ActivityResultContracts.StartActivityForResult(),
                    vpnRequest
                )

                if (result.resultCode == RESULT_OK)
                    startClashService()
            }
        } catch (e: Exception) {
            design?.showToast(R.string.unable_to_start_vpn, ToastDuration.Long)
        }
    }

    private suspend fun queryAppVersionName(): String {
        return withContext(Dispatchers.IO) {
            packageManager.getPackageInfo(packageName, 0).versionName + "\n" + Bridge.nativeCoreVersion().replace("_", "-")
        }
    }

    private fun showFloatingWindow() {
        if (FloatingWindowService.isShowing) {
            // Hide floating window
            val intent = Intent(this, FloatingWindowService::class.java)
            intent.action = FloatingWindowService.ACTION_HIDE
            startService(intent)
        } else {
            // Show floating window
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.canDrawOverlays(this)) {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    startActivity(intent)
                    return
                }
            }
            
            val intent = Intent(this, FloatingWindowService::class.java)
            intent.action = FloatingWindowService.ACTION_SHOW
            startService(intent)
        }
    }

    private suspend fun syncBuiltInProfiles(design: MainDesign) {
        launch {
            try {
                // Show syncing toast
                design.showToast("Syncing profiles...", ToastDuration.Long)
                
                // Define profiles to download
                val builtInProfiles = listOf(
                    "http://192.168.1.118:59996/clash/dns_67.yaml" to "dns_67",
                    "http://192.168.1.118:59996/clash/dns_65.yaml" to "dns_65", 
                    "http://192.168.1.118:59996/clash/dns_64.yaml" to "dns_64",
                    // "http://192.168.1.118:59996/clash/dns_62.yaml" to "dns_62",
                    "http://192.168.1.118:59996/clash/dns_reject.yaml" to "dns_reject",
                    "http://192.168.1.118:59996/clash/pre-product.yaml" to "pre-product",
                    "http://192.168.1.118:59996/clash/product.yaml" to "product"
                )
                
                var successCount = 0
                val totalCount = builtInProfiles.size
                val failedProfiles = mutableListOf<String>()
                
                // Create HTTP client with reasonable timeouts
                val client = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build()
                
                // Process each profile individually
                for ((url, name) in builtInProfiles) {
                    try {
                        // Test network connectivity with GET request (not HEAD)
                        val request = Request.Builder()
                            .url(url)
                            .header("User-Agent", "ClashforWindows/0.19.23")
                            .get() // Use GET request instead of HEAD
                            .build()
                        
                        val isAccessible = withContext(Dispatchers.IO) {
                            try {
                                client.newCall(request).execute().use { response ->
                                    response.isSuccessful && response.body != null
                                }
                            } catch (e: Exception) {
                                android.util.Log.w("SyncProfile", "Failed to access $name: ${e.message}")
                                false
                            }
                        }
                        
                        if (isAccessible) {
                            // Network accessible, try to create profile
                            withProfile {
                                try {
                                    // Check if profile with same name already exists
                                    val existingProfiles = queryAll()
                                    val exists = existingProfiles.any { it.name == name }
                                    
                                    if (!exists) {
                                        val uuid = create(Profile.Type.Url, name, url)
                                        commit(uuid)
                                        successCount++
                                        android.util.Log.i("SyncProfile", "Successfully added profile: $name")
                                    } else {
                                        // Already exists, skip but count as success
                                        successCount++
                                        android.util.Log.i("SyncProfile", "Profile already exists: $name")
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.w("SyncProfile", "Failed to create profile $name: ${e.message}")
                                    failedProfiles.add(name)
                                }
                            }
                        } else {
                            failedProfiles.add(name)
                        }
                    } catch (e: Exception) {
                        // Network error, log but continue with other profiles
                        android.util.Log.w("SyncProfile", "Network error for $name: ${e.message}")
                        failedProfiles.add(name)
                    }
                }
                
                // Show detailed result message
                when {
                    successCount == totalCount -> {
                        design.showToast("All profiles synced successfully", ToastDuration.Long)
                    }
                    successCount > 0 -> {
                        val message = "Successfully added $successCount/$totalCount profiles"
                        if (failedProfiles.isNotEmpty()) {
                            android.util.Log.i("SyncProfile", "Failed profiles: ${failedProfiles.joinToString()}")
                        }
                        design.showToast(message, ToastDuration.Long)
                    }
                    else -> {
                        val firstFailedUrl = builtInProfiles.firstOrNull { p -> failedProfiles.contains(p.second) }?.first
                        val message = "Sync failed for ${firstFailedUrl ?: "profiles"}. Check network."
                        design.showToast(message, ToastDuration.Long)
                    }
                }
                
            } catch (e: Exception) {
                android.util.Log.e("SyncProfile", "Unexpected error in syncBuiltInProfiles", e)
                design.showToast("Error syncing profiles: ${e.message}", ToastDuration.Long)
            }
        }
    }

    private suspend fun refreshAllProfiles(design: MainDesign) {
        launch {
            try {
                design.showToast("Refreshing profiles...", ToastDuration.Long)
                
                withProfile {
                    try {
                        val profiles = queryAll()
                        val updatableProfiles = profiles.filter { it.imported && it.type != Profile.Type.File }
                        
                        if (updatableProfiles.isEmpty()) {
                            design.showToast("No profiles to refresh", ToastDuration.Long)
                            return@withProfile
                        }
                        
                        var successCount = 0
                        var failedCount = 0
                        
                        for (profile in updatableProfiles) {
                            try {
                                update(profile.uuid)
                                successCount++
                            } catch (e: Exception) {
                                android.util.Log.w("RefreshProfiles", "Failed to refresh profile ${profile.name}: ${e.message}")
                                failedCount++
                            }
                        }
                        
                        val message = when {
                            failedCount == 0 -> "All profiles refreshed successfully"
                            successCount == 0 -> "Failed to refresh profiles"
                            else -> "Refreshed $successCount profiles, $failedCount failed"
                        }
                        
                        design.showToast(message, ToastDuration.Long)
                    } catch (e: Exception) {
                        android.util.Log.e("RefreshProfiles", "Error refreshing profiles", e)
                        design.showToast("Error refreshing profiles: ${e.message}", ToastDuration.Long)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("RefreshProfiles", "Unexpected error in refreshAllProfiles", e)
                design.showToast("Error refreshing profiles: ${e.message}", ToastDuration.Long)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val requestPermissionLauncher =
                registerForActivityResult(RequestPermission()
                ) { isGranted: Boolean ->
                }
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

val mainActivityAlias = "${MainActivity::class.java.name}Alias"