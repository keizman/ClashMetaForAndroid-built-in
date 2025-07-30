package com.github.kr328.clash.service.tv

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import com.github.kr328.clash.common.log.Log

data class TVCapabilities(
    val hasLeanback: Boolean,
    val hasTouch: Boolean,
    val isTelevision: Boolean,
    val isLargeScreen: Boolean,
    val hasHardwareKeyboard: Boolean,
    val hasGamepad: Boolean,
    val density: Float,
    val screenSize: String
)

class AndroidTVDetector(private val context: Context) {
    
    fun isAndroidTV(): Boolean {
        val packageManager = context.packageManager
        val isTV = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
                   packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION) ||
                   packageManager.hasSystemFeature("android.hardware.type.television")
        
        Log.i("TV detection result: $isTV")
        return isTV
    }
    
    fun getTVCapabilities(): TVCapabilities {
        val packageManager = context.packageManager
        val configuration = context.resources.configuration
        
        val capabilities = TVCapabilities(
            hasLeanback = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK),
            hasTouch = packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN),
            isTelevision = packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION),
            isLargeScreen = (configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE,
            hasHardwareKeyboard = configuration.keyboard == Configuration.KEYBOARD_QWERTY,
            hasGamepad = packageManager.hasSystemFeature(PackageManager.FEATURE_GAMEPAD),
            density = context.resources.displayMetrics.density,
            screenSize = getScreenSizeCategory(configuration)
        )
        
        Log.i("TV capabilities: $capabilities")
        return capabilities
    }
    
    private fun getScreenSizeCategory(configuration: Configuration): String {
        return when (configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) {
            Configuration.SCREENLAYOUT_SIZE_SMALL -> "SMALL"
            Configuration.SCREENLAYOUT_SIZE_NORMAL -> "NORMAL"
            Configuration.SCREENLAYOUT_SIZE_LARGE -> "LARGE"
            Configuration.SCREENLAYOUT_SIZE_XLARGE -> "XLARGE"
            else -> "UNDEFINED"
        }
    }
    
    fun getDeviceInfo(): Map<String, Any> {
        val capabilities = getTVCapabilities()
        
        return mapOf(
            "isTV" to isAndroidTV(),
            "brand" to Build.BRAND,
            "model" to Build.MODEL,
            "device" to Build.DEVICE,
            "androidVersion" to Build.VERSION.RELEASE,
            "apiLevel" to Build.VERSION.SDK_INT,
            "capabilities" to capabilities,
            "totalMemory" to getTotalMemory(),
            "availableMemory" to getAvailableMemory()
        )
    }
    
    private fun getTotalMemory(): Long {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memoryInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo.totalMem
    }
    
    private fun getAvailableMemory(): Long {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memoryInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo.availMem
    }
}