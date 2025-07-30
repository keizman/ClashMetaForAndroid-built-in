package com.github.kr328.clash

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.design.MainDesign
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.remote.Remote
import com.github.kr328.clash.remote.StatusClient
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.util.startClashService
import com.github.kr328.clash.util.stopClashService
import com.github.kr328.clash.util.withProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.util.*
import com.github.kr328.clash.design.R

class ExternalControlActivity : Activity(), CoroutineScope by MainScope() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        when(intent.action) {
            Intent.ACTION_VIEW -> {
                val uri = intent.data ?: return finish()
                val url = uri.getQueryParameter("url") ?: return finish()

                launch {
                    val uuid = withProfile {
                        val type = when (uri.getQueryParameter("type")?.lowercase(Locale.getDefault())) {
                            "url" -> Profile.Type.Url
                            "file" -> Profile.Type.File
                            else -> Profile.Type.Url
                        }
                        val name = uri.getQueryParameter("name") ?: getString(R.string.new_profile)

                        create(type, name).also {
                            patch(it, name, url, 0)
                        }
                    }
                    startActivity(PropertiesActivity::class.intent.setUUID(uuid))
                    finish()
                }
            }

            Intents.ACTION_TOGGLE_CLASH -> if(Remote.broadcasts.clashRunning) {
                stopClash()
            }
            else {
                startClash()
            }

            Intents.ACTION_START_CLASH -> if(!Remote.broadcasts.clashRunning) {
                startClash()
            }
            else {
                Toast.makeText(this, R.string.external_control_started, Toast.LENGTH_LONG).show()
            }

            Intents.ACTION_STOP_CLASH -> if(Remote.broadcasts.clashRunning) {
                stopClash()
            }
            else {
                Toast.makeText(this, R.string.external_control_stopped, Toast.LENGTH_LONG).show()
            }

            Intents.ACTION_TOGGLE_PROFILE -> {
                toggleProfile()
            }

            Intents.ACTION_REFRESH_PROFILE -> {
                refreshAllProfiles()
            }
        }
        return finish()
    }

    private fun startClash() {
//        if (currentProfile == null) {
//            Toast.makeText(this, R.string.no_profile_selected, Toast.LENGTH_LONG).show()
//            return
//        }
        val vpnRequest = startClashService()
        if (vpnRequest != null) {
            Toast.makeText(this, R.string.unable_to_start_vpn, Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, R.string.external_control_started, Toast.LENGTH_LONG).show()
    }

    private fun stopClash() {
        stopClashService()
        Toast.makeText(this, R.string.external_control_stopped, Toast.LENGTH_LONG).show()
    }

    private fun toggleProfile() {
        val TAG = "ClashMetaForAndroid"
        
        launch {
            try {
                val allProfiles = withProfile {
                    queryAll()
                }
                
                if (allProfiles.isEmpty()) {
                    android.util.Log.i(TAG, "[ToggleProfile] No profiles available to toggle")
                    Toast.makeText(this@ExternalControlActivity, "No profiles available", Toast.LENGTH_LONG).show()
                    return@launch
                }
                
                val currentActive = withProfile {
                    queryActive()
                }
                
                val nextProfile = if (currentActive == null) {
                    allProfiles.first()
                } else {
                    val currentIndex = allProfiles.indexOfFirst { it.uuid == currentActive.uuid }
                    val nextIndex = (currentIndex + 1) % allProfiles.size
                    allProfiles[nextIndex]
                }
                
                withProfile {
                    setActive(nextProfile)
                }
                
                android.util.Log.i(TAG, "[ToggleProfile] Switched to profile: ${nextProfile.name}")
                android.util.Log.i(TAG, "[ToggleProfile] Profile URL: ${nextProfile.source}")
                android.util.Log.i(TAG, "[ToggleProfile] Profile UUID: ${nextProfile.uuid}")
                android.util.Log.i(TAG, "[ToggleProfile] Profile Type: ${nextProfile.type}")
                android.util.Log.i(TAG, "[ToggleProfile] Total profiles available: ${allProfiles.size}")
                
                Toast.makeText(this@ExternalControlActivity, "Switched to: ${nextProfile.name}", Toast.LENGTH_LONG).show()
                
            } catch (e: Exception) {
                android.util.Log.e(TAG, "[ToggleProfile] Error switching profile", e)
                Toast.makeText(this@ExternalControlActivity, "Error switching profile: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun refreshAllProfiles() {
        val TAG = "ClashMetaForAndroid"
        
        launch {
            try {
                android.util.Log.i(TAG, "[RefreshAllProfiles] Starting profile refresh operation")
                Toast.makeText(this@ExternalControlActivity, "Refreshing profiles...", Toast.LENGTH_LONG).show()
                
                withProfile {
                    try {
                        val profiles = queryAll()
                        val updatableProfiles = profiles.filter { it.imported && it.type != Profile.Type.File }
                        
                        android.util.Log.i(TAG, "[RefreshAllProfiles] Found ${profiles.size} total profiles, ${updatableProfiles.size} updatable")
                        
                        if (updatableProfiles.isEmpty()) {
                            android.util.Log.i(TAG, "[RefreshAllProfiles] No profiles to refresh")
                            Toast.makeText(this@ExternalControlActivity, "No profiles to refresh", Toast.LENGTH_LONG).show()
                            return@withProfile
                        }
                        
                        var successCount = 0
                        var failedCount = 0
                        val refreshedProfiles = mutableListOf<String>()
                        val failedProfiles = mutableListOf<String>()
                        
                        for (profile in updatableProfiles) {
                            try {
                                android.util.Log.i(TAG, "[RefreshAllProfiles] Refreshing profile: ${profile.name} (${profile.source})")
                                update(profile.uuid)
                                successCount++
                                refreshedProfiles.add(profile.name)
                                android.util.Log.i(TAG, "[RefreshAllProfiles] Successfully refreshed: ${profile.name}")
                            } catch (e: Exception) {
                                android.util.Log.w(TAG, "[RefreshAllProfiles] Failed to refresh profile ${profile.name}: ${e.message}")
                                failedCount++
                                failedProfiles.add(profile.name)
                            }
                        }
                        
                        // Log summary of refreshed profiles
                        if (refreshedProfiles.isNotEmpty()) {
                            android.util.Log.i(TAG, "[RefreshAllProfiles] Successfully refreshed profiles: ${refreshedProfiles.joinToString(", ")}")
                        }
                        if (failedProfiles.isNotEmpty()) {
                            android.util.Log.w(TAG, "[RefreshAllProfiles] Failed to refresh profiles: ${failedProfiles.joinToString(", ")}")
                        }
                        
                        val message = when {
                            failedCount == 0 -> "All profiles refreshed successfully"
                            successCount == 0 -> "Failed to refresh profiles"
                            else -> "Refreshed $successCount profiles, $failedCount failed"
                        }
                        
                        android.util.Log.i(TAG, "[RefreshAllProfiles] Operation completed: $message")
                        Toast.makeText(this@ExternalControlActivity, message, Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "[RefreshAllProfiles] Error refreshing profiles", e)
                        Toast.makeText(this@ExternalControlActivity, "Error refreshing profiles: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "[RefreshAllProfiles] Unexpected error in refreshAllProfiles", e)
                Toast.makeText(this@ExternalControlActivity, "Error refreshing profiles: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}