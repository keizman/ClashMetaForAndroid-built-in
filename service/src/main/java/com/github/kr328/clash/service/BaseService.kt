package com.github.kr328.clash.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.github.kr328.clash.service.monitor.ServiceLifecycleTracker
import com.github.kr328.clash.service.monitor.ServiceRestartDetector
import com.github.kr328.clash.service.monitor.RestartInfo
import com.github.kr328.clash.service.persistence.StickyServiceManager
import com.github.kr328.clash.service.tv.TVResourceManager
import com.github.kr328.clash.service.util.cancelAndJoinBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

abstract class BaseService : Service(), CoroutineScope by CoroutineScope(Dispatchers.Default) {
    
    protected lateinit var restartDetector: ServiceRestartDetector
    protected lateinit var stickyServiceManager: StickyServiceManager
    protected lateinit var tvResourceManager: TVResourceManager
    protected var restartInfo: RestartInfo? = null
    
    override fun onCreate() {
        super.onCreate()
        restartDetector = ServiceRestartDetector(this)
        stickyServiceManager = StickyServiceManager(this)
        tvResourceManager = TVResourceManager(this)
        restartInfo = restartDetector.detectRestart(this::class.simpleName ?: "BaseService")
        
        ServiceLifecycleTracker.logServiceCreated(this::class.simpleName ?: "BaseService", android.os.Process.myPid())
        
        // Apply TV optimizations if needed
        tvResourceManager.applyTVOptimizations()
        
        if (restartInfo?.isRestart == true) {
            onServiceRestarted(restartInfo!!)
        }
    }
    
    protected open fun onServiceRestarted(restartInfo: RestartInfo) {
        // Override in subclasses to handle restart-specific logic
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceLifecycleTracker.logStartCommand(this::class.simpleName ?: "BaseService", intent, flags, startId)
        
        // Handle service restart and get optimal flags
        val serviceName = this::class.simpleName ?: "BaseService"
        val canStart = stickyServiceManager.handleServiceRestart(serviceName, intent, restartInfo)
        
        if (!canStart) {
            // Service restart throttled
            return Service.START_NOT_STICKY
        }
        
        return stickyServiceManager.getOptimalStartFlags(serviceName, restartInfo)
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        ServiceLifecycleTracker.logServiceBind(this::class.simpleName ?: "BaseService", intent)
        return null
    }
    
    override fun onUnbind(intent: Intent?): Boolean {
        ServiceLifecycleTracker.logServiceUnbind(this::class.simpleName ?: "BaseService", intent)
        return super.onUnbind(intent)
    }
    
    override fun onTrimMemory(level: Int) {
        ServiceLifecycleTracker.logMemoryTrimming(this::class.simpleName ?: "BaseService", level)
        super.onTrimMemory(level)
    }

    override fun onDestroy() {
        val reason = getDestructionReason()
        if (::restartDetector.isInitialized) {
            restartDetector.recordDeathReason(this::class.simpleName ?: "BaseService", reason)
        }
        ServiceLifecycleTracker.logServiceDestroyed(this::class.simpleName ?: "BaseService", reason)
        super.onDestroy()
        cancelAndJoinBlocking()
    }
    
    protected open fun getDestructionReason(): String? {
        // Override in subclasses to provide specific destruction reason
        return null
    }
    
    fun getTVResourceStatus(): Map<String, Any> {
        return if (::tvResourceManager.isInitialized) {
            tvResourceManager.getTVResourceStatus()
        } else {
            mapOf("error" to "TVResourceManager not initialized")
        }
    }
}