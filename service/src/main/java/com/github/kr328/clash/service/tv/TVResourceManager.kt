package com.github.kr328.clash.service.tv

import android.content.Context
import android.os.Process
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.service.PreferenceProvider

class TVResourceManager(private val context: Context) {
    
    companion object {
        private const val PREF_TV_OPTIMIZATIONS_ENABLED = "tv_optimizations_enabled"
        private const val PREF_TV_MEMORY_LIMIT = "tv_memory_limit_mb"
        private const val PREF_TV_BUFFER_SIZE = "tv_buffer_size_kb"
        private const val PREF_TV_CACHE_SIZE = "tv_cache_size_mb"
        
        // TV-specific resource limits
        private const val TV_DEFAULT_MEMORY_LIMIT_MB = 64
        private const val TV_DEFAULT_BUFFER_SIZE_KB = 8
        private const val TV_DEFAULT_CACHE_SIZE_MB = 16
        
        // Standard resource limits
        private const val STANDARD_MEMORY_LIMIT_MB = 128
        private const val STANDARD_BUFFER_SIZE_KB = 32
        private const val STANDARD_CACHE_SIZE_MB = 64
    }
    
    private val prefs = PreferenceProvider.createSharedPreferencesFromContext(context)
    private val tvDetector = AndroidTVDetector(context)
    
    fun applyTVOptimizations(): Boolean {
        val isTV = tvDetector.isAndroidTV()
        val optimizationsEnabled = prefs.getBoolean(PREF_TV_OPTIMIZATIONS_ENABLED, isTV)
        
        if (!optimizationsEnabled) {
            Log.i("TV optimizations disabled")
            return false
        }
        
        val capabilities = tvDetector.getTVCapabilities()
        
        Log.i("Applying TV optimizations for device: ${capabilities}")
        
        // Apply memory optimizations
        applyMemoryOptimizations(capabilities)
        
        // Apply performance optimizations
        applyPerformanceOptimizations(capabilities)
        
        // Apply network optimizations
        applyNetworkOptimizations(capabilities)
        
        // Apply power management optimizations
        applyPowerOptimizations(capabilities)
        
        return true
    }
    
    private fun applyMemoryOptimizations(capabilities: TVCapabilities) {
        val memoryLimitMb = if (capabilities.hasLeanback) {
            prefs.getInt(PREF_TV_MEMORY_LIMIT, TV_DEFAULT_MEMORY_LIMIT_MB)
        } else {
            prefs.getInt(PREF_TV_MEMORY_LIMIT, STANDARD_MEMORY_LIMIT_MB)
        }
        
        Log.i("Setting memory limit to ${memoryLimitMb}MB")
        
        // Set JVM memory parameters
        try {
            val runtime = Runtime.getRuntime()
            val maxMemory = runtime.maxMemory() / 1024 / 1024
            val currentMemory = runtime.totalMemory() / 1024 / 1024
            
            Log.i("Memory status - Max: ${maxMemory}MB, Current: ${currentMemory}MB, Limit: ${memoryLimitMb}MB")
            
            // Trigger GC if approaching limit
            if (currentMemory > memoryLimitMb * 0.8) {
                Log.w("Memory usage high, triggering GC")
                runtime.gc()
            }
        } catch (e: Exception) {
            Log.w("Failed to apply memory optimizations: ${e.message}")
        }
    }
    
    private fun applyPerformanceOptimizations(capabilities: TVCapabilities) {
        // Set process priority for TV
        try {
            if (capabilities.hasLeanback) {
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
                Log.i("Set process priority to AUDIO for TV")
            } else {
                Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT)
                Log.i("Set process priority to DEFAULT")
            }
        } catch (e: Exception) {
            Log.w("Failed to set process priority: ${e.message}")
        }
        
        // Configure buffer sizes
        val bufferSizeKb = if (capabilities.hasLeanback) {
            prefs.getInt(PREF_TV_BUFFER_SIZE, TV_DEFAULT_BUFFER_SIZE_KB)
        } else {
            prefs.getInt(PREF_TV_BUFFER_SIZE, STANDARD_BUFFER_SIZE_KB)
        }
        
        Log.i("Setting buffer size to ${bufferSizeKb}KB")
        
        // Store buffer size for core to use
        with(prefs.edit()) {
            putInt("optimized_buffer_size_kb", bufferSizeKb)
            apply()
        }
    }
    
    private fun applyNetworkOptimizations(capabilities: TVCapabilities) {
        // TV-specific network optimizations
        if (capabilities.hasLeanback) {
            Log.i("Applying TV network optimizations")
            
            // Reduce concurrent connections for TV
            with(prefs.edit()) {
                putInt("max_concurrent_connections", 4)
                putInt("connection_timeout_ms", 10000)
                putInt("read_timeout_ms", 15000)
                apply()
            }
        }
    }
    
    private fun applyPowerOptimizations(capabilities: TVCapabilities) {
        // TV-specific power management
        if (capabilities.hasLeanback) {
            Log.i("Applying TV power optimizations")
            
            // Disable battery optimization detection for TV
            with(prefs.edit()) {
                putBoolean("ignore_battery_optimization", true)
                putBoolean("use_foreground_service", true)
                apply()
            }
        }
    }
    
    fun getOptimizedResourceLimits(): Map<String, Int> {
        val isTV = tvDetector.isAndroidTV()
        val capabilities = tvDetector.getTVCapabilities()
        
        return if (isTV) {
            mapOf(
                "memoryLimitMb" to prefs.getInt(PREF_TV_MEMORY_LIMIT, TV_DEFAULT_MEMORY_LIMIT_MB),
                "bufferSizeKb" to prefs.getInt(PREF_TV_BUFFER_SIZE, TV_DEFAULT_BUFFER_SIZE_KB),
                "cacheSizeMb" to prefs.getInt(PREF_TV_CACHE_SIZE, TV_DEFAULT_CACHE_SIZE_MB),
                "maxConnections" to 4,
                "connectionTimeoutMs" to 10000,
                "readTimeoutMs" to 15000
            )
        } else {
            mapOf(
                "memoryLimitMb" to STANDARD_MEMORY_LIMIT_MB,
                "bufferSizeKb" to STANDARD_BUFFER_SIZE_KB,
                "cacheSizeMb" to STANDARD_CACHE_SIZE_MB,
                "maxConnections" to 8,
                "connectionTimeoutMs" to 5000,
                "readTimeoutMs" to 10000
            )
        }
    }
    
    fun setTVOptimizationsEnabled(enabled: Boolean) {
        with(prefs.edit()) {
            putBoolean(PREF_TV_OPTIMIZATIONS_ENABLED, enabled)
            apply()
        }
        Log.i("TV optimizations enabled: $enabled")
    }
    
    fun configureTVResourceLimits(memoryLimitMb: Int, bufferSizeKb: Int, cacheSizeMb: Int) {
        with(prefs.edit()) {
            putInt(PREF_TV_MEMORY_LIMIT, memoryLimitMb)
            putInt(PREF_TV_BUFFER_SIZE, bufferSizeKb)
            putInt(PREF_TV_CACHE_SIZE, cacheSizeMb)
            apply()
        }
        Log.i("TV resource limits configured - Memory: ${memoryLimitMb}MB, Buffer: ${bufferSizeKb}KB, Cache: ${cacheSizeMb}MB")
    }
    
    fun getTVResourceStatus(): Map<String, Any> {
        val isTV = tvDetector.isAndroidTV()
        val capabilities = tvDetector.getTVCapabilities()
        val optimizationsEnabled = prefs.getBoolean(PREF_TV_OPTIMIZATIONS_ENABLED, isTV)
        val limits = getOptimizedResourceLimits()
        
        val runtime = Runtime.getRuntime()
        val totalMemory = runtime.totalMemory() / 1024 / 1024
        val freeMemory = runtime.freeMemory() / 1024 / 1024
        val maxMemory = runtime.maxMemory() / 1024 / 1024
        val usedMemory = totalMemory - freeMemory
        
        return mapOf(
            "isTV" to isTV,
            "capabilities" to capabilities,
            "optimizationsEnabled" to optimizationsEnabled,
            "resourceLimits" to limits,
            "memoryUsage" to mapOf(
                "usedMb" to usedMemory,
                "totalMb" to totalMemory,
                "maxMb" to maxMemory,
                "usagePercent" to (usedMemory * 100 / maxMemory)
            )
        )
    }
}