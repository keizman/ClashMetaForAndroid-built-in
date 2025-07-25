package com.github.kr328.clash.service.monitor

import android.content.Context
import android.content.SharedPreferences
import android.os.Process
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.service.PreferenceProvider
import java.io.BufferedReader
import java.io.FileReader

data class RestartInfo(
    val isRestart: Boolean,
    val startCount: Int,
    val lastDeathReason: String?,
    val processDeathReason: String?
)

class ServiceRestartDetector(private val context: Context) {
    
    companion object {
        private const val PREF_LAST_SERVICE_PID = "last_service_pid"
        private const val PREF_SERVICE_START_COUNT = "service_start_count"
        private const val PREF_LAST_SERVICE_TIMESTAMP = "last_service_timestamp"
        private const val PREF_LAST_DEATH_REASON = "last_death_reason"
        private const val PREF_PROCESS_START_TIME = "process_start_time"
    }
    
    private val prefs: SharedPreferences = PreferenceProvider.createSharedPreferencesFromContext(context)
    
    fun detectRestart(serviceName: String): RestartInfo {
        val currentPid = Process.myPid()
        val currentTime = System.currentTimeMillis()
        val lastPid = prefs.getInt(PREF_LAST_SERVICE_PID, -1)
        val lastTimestamp = prefs.getLong(PREF_LAST_SERVICE_TIMESTAMP, 0)
        val lastDeathReason = prefs.getString(PREF_LAST_DEATH_REASON, null)
        val processStartTime = prefs.getLong(PREF_PROCESS_START_TIME, 0)
        
        // Increment start count
        val startCount = prefs.getInt(PREF_SERVICE_START_COUNT, 0) + 1
        
        // Check if this is a restart
        val isRestart = lastPid != -1 && lastPid != currentPid
        val timeSinceLastStart = currentTime - lastTimestamp
        
        // Try to determine process death reason
        val processDeathReason = if (isRestart) {
            analyzeProcessDeathReason(lastPid, timeSinceLastStart, processStartTime, currentTime)
        } else null
        
        // Save current state
        with(prefs.edit()) {
            putInt(PREF_LAST_SERVICE_PID, currentPid)
            putInt(PREF_SERVICE_START_COUNT, startCount)
            putLong(PREF_LAST_SERVICE_TIMESTAMP, currentTime)
            putLong(PREF_PROCESS_START_TIME, currentTime)
            apply()
        }
        
        val restartInfo = RestartInfo(isRestart, startCount, lastDeathReason, processDeathReason)
        
        Log.i("[$serviceName] Restart detection - " +
              "IsRestart: $isRestart, StartCount: $startCount, " +
              "LastPID: $lastPid, CurrentPID: $currentPid, " +
              "TimeSinceLastStart: ${timeSinceLastStart}ms, " +
              "LastDeathReason: $lastDeathReason, " +
              "ProcessDeathReason: $processDeathReason")
        
        return restartInfo
    }
    
    fun recordDeathReason(serviceName: String, reason: String?) {
        with(prefs.edit()) {
            putString(PREF_LAST_DEATH_REASON, reason)
            apply()
        }
        Log.i("[$serviceName] Death reason recorded: $reason")
    }
    
    private fun analyzeProcessDeathReason(lastPid: Int, timeSinceLastStart: Long, processStartTime: Long, currentTime: Long): String {
        val reasons = mutableListOf<String>()
        
        // Check time-based indicators
        when {
            timeSinceLastStart < 1000 -> reasons.add("QUICK_RESTART(<1s)")
            timeSinceLastStart < 5000 -> reasons.add("FAST_RESTART(<5s)")
            timeSinceLastStart < 30000 -> reasons.add("NORMAL_RESTART(<30s)")
            else -> reasons.add("DELAYED_RESTART(>30s)")
        }
        
        // Check OOM killer
        if (checkOOMKiller(lastPid)) {
            reasons.add("OOM_KILLER")
        }
        
        // Check process still exists
        if (isProcessStillRunning(lastPid)) {
            reasons.add("PROCESS_STILL_ALIVE")
        }
        
        // Check system memory state
        val memoryState = getSystemMemoryState()
        if (memoryState.isNotEmpty()) {
            reasons.add("MEMORY_STATE($memoryState)")
        }
        
        // Check if process was killed by system
        if (timeSinceLastStart < 5000) {
            reasons.add("POSSIBLE_SYSTEM_KILL")
        }
        
        return reasons.joinToString(", ")
    }
    
    private fun checkOOMKiller(pid: Int): Boolean {
        return try {
            val reader = BufferedReader(FileReader("/proc/$pid/oom_score"))
            val score = reader.readLine()?.toIntOrNull() ?: 0
            reader.close()
            score > 0
        } catch (e: Exception) {
            false
        }
    }
    
    private fun isProcessStillRunning(pid: Int): Boolean {
        return try {
            android.os.Process.sendSignal(pid, 0)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    private fun getSystemMemoryState(): String {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memoryInfo = android.app.ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            
            when {
                memoryInfo.lowMemory -> "LOW_MEMORY"
                memoryInfo.availMem < memoryInfo.threshold * 1.5 -> "MEMORY_PRESSURE"
                else -> "NORMAL_MEMORY"
            }
        } catch (e: Exception) {
            "UNKNOWN_MEMORY"
        }
    }
    
    fun getRestartStatistics(): Map<String, Any> {
        val startCount = prefs.getInt(PREF_SERVICE_START_COUNT, 0)
        val lastPid = prefs.getInt(PREF_LAST_SERVICE_PID, -1)
        val lastTimestamp = prefs.getLong(PREF_LAST_SERVICE_TIMESTAMP, 0)
        val lastDeathReason = prefs.getString(PREF_LAST_DEATH_REASON, "unknown")
        
        return mapOf(
            "startCount" to startCount,
            "lastPid" to lastPid,
            "lastTimestamp" to lastTimestamp,
            "lastDeathReason" to (lastDeathReason ?: "unknown"),
            "currentPid" to Process.myPid(),
            "currentTime" to System.currentTimeMillis()
        )
    }
    
    fun resetStatistics() {
        with(prefs.edit()) {
            remove(PREF_LAST_SERVICE_PID)
            remove(PREF_SERVICE_START_COUNT)
            remove(PREF_LAST_SERVICE_TIMESTAMP)
            remove(PREF_LAST_DEATH_REASON)
            remove(PREF_PROCESS_START_TIME)
            apply()
        }
        Log.i("Restart statistics reset")
    }
}