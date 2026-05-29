package com.carriez.flutter_hbb

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * JobScheduler service for keeping MainService alive.
 * Detects if MainService is running and restarts it if killed by system (MIUI/HyperOS/etc).
 */
class KeepAliveJobService : JobService() {

    private val logTag = "KeepAliveJobService"

    override fun onStartJob(params: JobParameters?): Boolean {
        Log.d(logTag, "Job started")
        
        // Check if MainService is running
        if (!isMainServiceRunning()) {
            Log.w(logTag, "MainService not running, attempting to restart")
            restartMainService()
        } else {
            Log.d(logTag, "MainService is running normally")
        }
        
        // Reschedule next job
        scheduleKeepAliveJob(this)
        
        // Return false as this is a quick check, no async work needed
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        Log.d(logTag, "Job stopped")
        return false
    }

    private fun isMainServiceRunning(): Boolean {
        // Check via MainService companion object
        return MainService.isReady
    }

    private fun restartMainService() {
        try {
            val intent = Intent(this, MainService::class.java).apply {
                action = ACT_INIT_MEDIA_PROJECTION_AND_SERVICE
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Log.d(logTag, "MainService restart initiated")
        } catch (e: Exception) {
            Log.e(logTag, "Failed to restart MainService: ${e.message}")
        }
    }

    companion object {
        private const val JOB_ID = 1001
        private const val INTERVAL_MINUTES = 15L // Check every 15 minutes

        fun scheduleKeepAliveJob(context: Context) {
            val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            
            // Check if job already scheduled
            val pendingJob = jobScheduler.getPendingJob(JOB_ID)
            if (pendingJob != null) {
                Log.d("KeepAliveJob", "Job already scheduled")
                return
            }

            val componentName = ComponentName(context, KeepAliveJobService::class.java)
            
            val builder = JobInfo.Builder(JOB_ID, componentName)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true) // Persist across reboots (requires RECEIVE_BOOT_COMPLETED)
                .setPeriodic(INTERVAL_MINUTES * 60 * 1000) // 15 minutes in milliseconds

            // For Android N and above, also set minimum latency for first run
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                builder.setMinimumLatency(60 * 1000) // First run after 1 minute
            }

            val result = jobScheduler.schedule(builder.build())
            if (result == JobScheduler.RESULT_SUCCESS) {
                Log.d("KeepAliveJob", "KeepAliveJobService scheduled successfully")
            } else {
                Log.e("KeepAliveJob", "Failed to schedule KeepAliveJobService")
            }
        }

        fun cancelKeepAliveJob(context: Context) {
            val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            jobScheduler.cancel(JOB_ID)
            Log.d("KeepAliveJob", "KeepAliveJobService cancelled")
        }

        fun isJobScheduled(context: Context): Boolean {
            val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            return jobScheduler.getPendingJob(JOB_ID) != null
        }
    }
}
