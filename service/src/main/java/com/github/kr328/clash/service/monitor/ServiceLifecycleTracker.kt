package com.github.kr328.clash.service.monitor

import android.content.Intent
import android.os.Process
import com.github.kr328.clash.common.log.Log

object ServiceLifecycleTracker {
    
    fun logServiceCreated(serviceName: String, pid: Int) {
        Log.i("[$serviceName] onCreate() - PID: $pid, Thread: ${Thread.currentThread().name}")
    }
    
    fun logStartCommand(serviceName: String, intent: Intent?, flags: Int, startId: Int) {
        val intentAction = intent?.action ?: "null"
        val intentData = intent?.data?.toString() ?: "null"
        val flagsStr = getStartCommandFlags(flags)
        
        Log.i("[$serviceName] onStartCommand() - Action: $intentAction, " +
              "Data: $intentData, Flags: $flagsStr, StartId: $startId, PID: ${Process.myPid()}")
    }
    
    fun logServiceDestroyed(serviceName: String, reason: String?) {
        Log.i("[$serviceName] onDestroy() - Reason: ${reason ?: "normal"}, " +
              "PID: ${Process.myPid()}")
    }
    
    fun logServiceBind(serviceName: String, intent: Intent?) {
        val intentAction = intent?.action ?: "null"
        Log.i("[$serviceName] onBind() - Action: $intentAction, PID: ${Process.myPid()}")
    }
    
    fun logServiceUnbind(serviceName: String, intent: Intent?) {
        val intentAction = intent?.action ?: "null"
        Log.i("[$serviceName] onUnbind() - Action: $intentAction, PID: ${Process.myPid()}")
    }
    
    fun logMemoryTrimming(serviceName: String, level: Int) {
        val levelName = getMemoryTrimLevelName(level)
        Log.w("[$serviceName] onTrimMemory() - Level: $levelName ($level), PID: ${Process.myPid()}")
    }
    
    private fun getStartCommandFlags(flags: Int): String {
        return when (flags) {
            0 -> "START_STICKY"
            1 -> "START_NOT_STICKY"
            2 -> "START_REDELIVER_INTENT"
            3 -> "START_STICKY_COMPATIBILITY"
            else -> "UNKNOWN($flags)"
        }
    }
    
    private fun getMemoryTrimLevelName(level: Int): String {
        return when (level) {
            5 -> "TRIM_MEMORY_RUNNING_MODERATE"
            10 -> "TRIM_MEMORY_RUNNING_LOW"
            15 -> "TRIM_MEMORY_RUNNING_CRITICAL"
            20 -> "TRIM_MEMORY_UI_HIDDEN"
            40 -> "TRIM_MEMORY_BACKGROUND"
            60 -> "TRIM_MEMORY_MODERATE"
            80 -> "TRIM_MEMORY_COMPLETE"
            else -> "UNKNOWN($level)"
        }
    }
}