package com.github.kr328.clash.service.persistence

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Process
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.service.PreferenceProvider
import com.github.kr328.clash.service.monitor.RestartInfo

class StickyServiceManager(private val context: Context) {
    
    companion object {
        private const val PREF_SERVICE_RESTART_COUNT = "service_restart_count"
        private const val PREF_LAST_RESTART_TIME = "last_restart_time"
        private const val PREF_PERSISTENT_MODE = "persistent_mode"
        private const val PREF_RESTART_INTENT_ACTION = "restart_intent_action"
        private const val PREF_RESTART_INTENT_DATA = "restart_intent_data"
        
        private const val MAX_RESTART_COUNT = 10
        private const val RESTART_COOLDOWN_MS = 30000 // 30 seconds
    }
    
    private val prefs = PreferenceProvider.createSharedPreferencesFromContext(context)
    
    fun getOptimalStartFlags(serviceName: String, restartInfo: RestartInfo?): Int {
        val persistentMode = prefs.getBoolean(PREF_PERSISTENT_MODE, true)
        val restartCount = prefs.getInt(PREF_SERVICE_RESTART_COUNT, 0)
        
        return when {
            !persistentMode -> Service.START_NOT_STICKY
            restartCount > MAX_RESTART_COUNT -> Service.START_STICKY
            restartInfo?.isRestart == true -> Service.START_REDELIVER_INTENT
            else -> Service.START_STICKY
        }.also {
            Log.i("[$serviceName] Using start flags: ${getStartFlagName(it)}, " +
                  "RestartCount: $restartCount, PersistentMode: $persistentMode")
        }
    }
    
    fun handleServiceRestart(serviceName: String, intent: Intent?, restartInfo: RestartInfo?): Boolean {
        val currentTime = System.currentTimeMillis()
        val lastRestartTime = prefs.getLong(PREF_LAST_RESTART_TIME, 0)
        val restartCount = prefs.getInt(PREF_SERVICE_RESTART_COUNT, 0)
        
        if (restartInfo?.isRestart == true) {
            val timeSinceLastRestart = currentTime - lastRestartTime
            
            Log.i("[$serviceName] Handling service restart - " +
                  "Count: ${restartInfo.startCount}, " +
                  "TimeSinceLastRestart: ${timeSinceLastRestart}ms, " +
                  "LastDeathReason: ${restartInfo.lastDeathReason}, " +
                  "ProcessDeathReason: ${restartInfo.processDeathReason}")
            
            // Update restart statistics
            with(prefs.edit()) {
                putInt(PREF_SERVICE_RESTART_COUNT, restartCount + 1)
                putLong(PREF_LAST_RESTART_TIME, currentTime)
                apply()
            }
            
            // Check if restart is too frequent
            if (timeSinceLastRestart < RESTART_COOLDOWN_MS && restartCount > 3) {
                Log.w("[$serviceName] Frequent restarts detected, " +
                      "applying restart throttling")
                return false
            }
            
            // Restore service state from intent
            return restoreServiceState(serviceName, intent)
        }
        
        // Save intent for future restarts
        saveRestartIntent(intent)
        return true
    }
    
    private fun restoreServiceState(serviceName: String, intent: Intent?): Boolean {
        val savedAction = prefs.getString(PREF_RESTART_INTENT_ACTION, null)
        val savedData = prefs.getString(PREF_RESTART_INTENT_DATA, null)
        
        Log.i("[$serviceName] Restoring service state - " +
              "IntentAction: ${intent?.action}, SavedAction: $savedAction, " +
              "IntentData: ${intent?.dataString}, SavedData: $savedData")
        
        // Perform any state restoration logic here
        return true
    }
    
    private fun saveRestartIntent(intent: Intent?) {
        with(prefs.edit()) {
            putString(PREF_RESTART_INTENT_ACTION, intent?.action)
            putString(PREF_RESTART_INTENT_DATA, intent?.dataString)
            apply()
        }
    }
    
    fun setPersistentMode(enabled: Boolean) {
        with(prefs.edit()) {
            putBoolean(PREF_PERSISTENT_MODE, enabled)
            apply()
        }
        Log.i("Persistent mode set to: $enabled")
    }
    
    fun resetRestartStatistics() {
        with(prefs.edit()) {
            remove(PREF_SERVICE_RESTART_COUNT)
            remove(PREF_LAST_RESTART_TIME)
            remove(PREF_RESTART_INTENT_ACTION)
            remove(PREF_RESTART_INTENT_DATA)
            apply()
        }
        Log.i("Restart statistics reset")
    }
    
    fun getRestartStatistics(): Map<String, Any> {
        val restartCount = prefs.getInt(PREF_SERVICE_RESTART_COUNT, 0)
        val lastRestartTime = prefs.getLong(PREF_LAST_RESTART_TIME, 0)
        val persistentMode = prefs.getBoolean(PREF_PERSISTENT_MODE, true)
        
        return mapOf(
            "restartCount" to restartCount,
            "lastRestartTime" to lastRestartTime,
            "persistentMode" to persistentMode,
            "currentTime" to System.currentTimeMillis(),
            "pid" to Process.myPid()
        )
    }
    
    private fun getStartFlagName(flag: Int): String {
        return when (flag) {
            Service.START_STICKY -> "START_STICKY"
            Service.START_NOT_STICKY -> "START_NOT_STICKY"
            Service.START_REDELIVER_INTENT -> "START_REDELIVER_INTENT"
            Service.START_STICKY_COMPATIBILITY -> "START_STICKY_COMPATIBILITY"
            else -> "UNKNOWN($flag)"
        }
    }
}