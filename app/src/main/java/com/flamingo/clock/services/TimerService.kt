/*
 * Copyright (C) 2022 FlamingoOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.flamingo.clock.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Binder
import android.os.IBinder
import android.os.SystemClock

import androidx.annotation.GuardedBy
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope

import com.flamingo.clock.R
import com.flamingo.clock.data.getPrependedString
import com.flamingo.clock.ui.ClockActivity

import java.util.concurrent.atomic.AtomicInteger

import kotlin.coroutines.coroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TimerService : LifecycleService() {

    private lateinit var serviceBinder: ServiceBinder
    private lateinit var currentConfig: Configuration
    private lateinit var notificationManager: NotificationManager
    private lateinit var activityIntent: PendingIntent

    private val counter = AtomicInteger(1)

    private val timerMutex = Mutex()

    @GuardedBy("timerMutex")
    private val timersList = mutableListOf<Timer>()

    private val _timers = MutableStateFlow<List<Timer>>(emptyList())
    val timers: StateFlow<List<Timer>> = _timers.asStateFlow()

    private var timerJob: Job? = null
    private var notificationJob: Job? = null

    private var receiverRegistered = false
    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            when (intent?.action) {
                ACTION_PAUSE -> {
                    val id = intent.getIntExtra(EXTRA_TIMER_ID, 0)
                    lifecycleScope.launch {
                        pauseTimer(id)
                    }
                }
                ACTION_ADD_ONE_MINUTE -> {
                    val id = intent.getIntExtra(EXTRA_TIMER_ID, 0)
                    lifecycleScope.launch {
                        incrementTimer(id, 1.minutes)
                    }
                }
                ACTION_RESUME -> {
                    val id = intent.getIntExtra(EXTRA_TIMER_ID, 0)
                    lifecycleScope.launch {
                        resumeTimer(id)
                    }
                }
                ACTION_RESET -> {
                    val id = intent.getIntExtra(EXTRA_TIMER_ID, 0)
                    lifecycleScope.launch {
                        resetTimer(id)
                    }
                }
                ACTION_DELETE -> {
                    val id = intent.getIntExtra(EXTRA_TIMER_ID, 0)
                    lifecycleScope.launch {
                        deleteTimer(id)
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        serviceBinder = ServiceBinder()
        notificationManager = getSystemService()!!
        activityIntent = PendingIntent.getActivity(
            this,
            ACTIVITY_REQUEST_CODE,
            Intent(this, ClockActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        createNotificationChannel()
        currentConfig = resources.configuration
    }

    private fun createNotificationChannel() {
        val runningNotificationChannel = NotificationChannel(
            RUNNING_TIMER_NOTIFICATION_CHANNEL_ID,
            getString(R.string.running_timer_notification_channel),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val finishedNotificationChannel = NotificationChannel(
            FINISHED_TIMER_NOTIFICATION_CHANNEL_ID,
            getString(R.string.finished_timer_notification_channel),
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(runningNotificationChannel)
        notificationManager.createNotificationChannel(finishedNotificationChannel)
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return serviceBinder
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.diff(currentConfig) == ActivityInfo.CONFIG_LOCALE) {
            createNotificationChannel()
        }
        currentConfig = newConfig
    }

    suspend fun addTimer(duration: Duration): Result<Unit> {
        if (!duration.isPositive()) {
            return Result.failure(Throwable(getString(R.string.timer_duration_must_be_greater_than_zero)))
        }
        val timer = Timer(
            id = counter.getAndIncrement(),
            totalDuration = duration,
            hasStarted = true
        )
        return timerMutex.withLock {
            if (timersList.any { it.id == timer.id }) {
                return@withLock Result.failure(Throwable(getString(R.string.job_with_id_exists)))
            }
            timersList.add(0, timer)
            updateTimerListLocked()
            maybeStartTimerJob()
            startObservingNotifications()
            Result.success(Unit)
        }
    }

    private fun updateTimerListLocked() {
        _timers.value = timersList.toList()
    }

    private fun startObservingNotifications() {
        if (notificationJob?.isActive != true) {
            notificationJob = lifecycleScope.launch {
                do {
                    timers.value.filter { it.hasStarted }.forEach {
                        notificationManager.notify(
                            BASE_NOTIFICATION_ID + it.id,
                            createNotification(it)
                        )
                    }
                    delay(1000)
                } while (isActive)
            }
        }
        if (!receiverRegistered) {
            registerReceiver(
                broadcastReceiver,
                IntentFilter().apply {
                    addAction(ACTION_PAUSE)
                    addAction(ACTION_ADD_ONE_MINUTE)
                    addAction(ACTION_RESUME)
                    addAction(ACTION_RESET)
                    addAction(ACTION_DELETE)
                },
            )
            receiverRegistered = true
        }
    }

    private fun maybeStartTimerJob() {
        if (timerJob?.isActive != true) {
            startForeground(
                FG_SERVICE_NOTIFICATION_ID,
                NotificationCompat.Builder(this, RUNNING_TIMER_NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.baseline_timer_24)
                    .setContentTitle(getString(R.string.timer_service_running))
                    .setOngoing(true)
                    .setSilent(true)
                    .build()
            )
            timerJob = lifecycleScope.launch(Dispatchers.Default) {
                updateAllTimers()
            }
        }
    }

    private fun createNotification(timer: Timer): Notification {
        val builder = NotificationCompat.Builder(
            this,
            if (timer.isNegative)
                FINISHED_TIMER_NOTIFICATION_CHANNEL_ID
            else
                RUNNING_TIMER_NOTIFICATION_CHANNEL_ID
        )
        builder.setSmallIcon(R.drawable.baseline_timer_24)
            .setContentTitle(getNotificationTitle(timer))
            .setContentText(getNotificationText(timer))
            .setContentIntent(activityIntent)
            .setCategory(Notification.CATEGORY_ALARM)
            .setPriority(
                if (timer.isNegative)
                    NotificationCompat.PRIORITY_MAX
                else
                    NotificationCompat.PRIORITY_DEFAULT
            )
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(!timer.isNegative)
        addActions(timer, builder)
        return builder.build()
    }

    private fun getNotificationTitle(timer: Timer): String {
        return buildString {
            append(getString(R.string.timer))
            append(' ')
            if (timer.label != null) {
                append(timer.label)
                append(' ')
            }
            append(
                getString(
                    if (timer.isPaused)
                        R.string.paused
                    else
                        R.string.running
                )
            )
        }
    }

    private fun getNotificationText(timer: Timer): String {
        return timer.remainingDuration.absoluteValue.toComponents { hours, minutes, seconds, _ ->
            buildString {
                if (timer.isNegative) {
                    append('-')
                }
                if (hours > 0) {
                    append(hours)
                    append(':')
                }
                append(getPrependedString(minutes))
                append(':')
                append(getPrependedString(seconds))
            }
        }
    }

    private fun addActions(
        timer: Timer,
        builder: NotificationCompat.Builder
    ) {
        if (timer.isPaused) {
            val resumeIntent = getBroadcastIntent(timer.id, ResumeIntent)
            builder.addAction(
                R.drawable.baseline_play_arrow_24,
                getString(R.string.resume),
                resumeIntent
            )

            val resetIntent = getBroadcastIntent(timer.id, ResetIntent)
            builder.addAction(
                R.drawable.baseline_reset_24,
                getString(R.string.reset),
                resetIntent
            )
        } else {
            if (timer.isNegative) {
                val stopIntent = getBroadcastIntent(timer.id, ResetIntent)
                builder.addAction(
                    R.drawable.baseline_stop_24,
                    getString(R.string.stop),
                    stopIntent
                )
            } else {
                val pauseIntent = getBroadcastIntent(timer.id, PauseIntent)
                builder.addAction(
                    R.drawable.baseline_pause_24,
                    getString(R.string.pause),
                    pauseIntent
                )
            }

            val addOneMinuteIntent = getBroadcastIntent(timer.id, AddOneMinuteIntent)
            builder.addAction(
                R.drawable.baseline_alarm_add_24,
                getString(R.string.add_one_minute),
                addOneMinuteIntent
            )
        }

        val deleteIntent = getBroadcastIntent(timer.id, DeleteIntent)
        builder.addAction(
            R.drawable.baseline_delete_24,
            getString(R.string.delete),
            deleteIntent
        )
    }

    private fun getBroadcastIntent(id: Int, intent: Intent): PendingIntent {
        val requestCode = ACTION_REQUEST_CODE_SHIFT + id
        return PendingIntent.getBroadcast(
            this,
            requestCode,
            intent.cloneFilter().apply {
                putExtra(EXTRA_TIMER_ID, id)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private suspend fun updateAllTimers() {
        var now = SystemClock.elapsedRealtimeNanos()
        do {
            val next = SystemClock.elapsedRealtimeNanos()
            val duration = (next - now).nanoseconds
            now = next
            timerMutex.withLock {
                timersList.forEach { timer ->
                    if (timer.hasStarted && !timer.isPaused) {
                        timer.apply {
                            remainingDuration = timer.remainingDuration - duration
                        }
                    }
                }
                updateTimerListLocked()
            }
        } while (coroutineContext.isActive)
    }

    suspend fun setTimerLabel(id: Int, label: String): Result<Unit> {
        return timerMutex.withLock {
            val timer = timersList.find { it.id == id } ?: return@withLock Result.failure(
                Throwable(getString(R.string.timer_with_id_does_not_exist))
            )
            timer.label = label
            updateTimerListLocked()
            Result.success(Unit)
        }
    }

    suspend fun startTimer(id: Int): Result<Unit> {
        return timerMutex.withLock {
            val timer = timersList.find { it.id == id } ?: return@withLock Result.failure(
                Throwable(getString(R.string.timer_with_id_does_not_exist))
            )
            timer.hasStarted = true
            updateTimerListLocked()
            maybeStartTimerJob()
            startObservingNotifications()
            Result.success(Unit)
        }
    }

    suspend fun pauseTimer(id: Int): Result<Unit> {
        return timerMutex.withLock {
            val timer = timersList.find { it.id == id } ?: return@withLock Result.failure(
                Throwable(getString(R.string.timer_with_id_does_not_exist))
            )
            timer.isPaused = true
            updateTimerListLocked()
            maybeCancelJobIfAllInactiveLocked()
            Result.success(Unit)
        }
    }

    suspend fun resumeTimer(id: Int): Result<Unit> {
        return timerMutex.withLock {
            val timer = timersList.find { it.id == id } ?: return@withLock Result.failure(
                Throwable(getString(R.string.timer_with_id_does_not_exist))
            )
            timer.isPaused = false
            updateTimerListLocked()
            maybeStartTimerJob()
            Result.success(Unit)
        }
    }

    suspend fun incrementTimer(id: Int, duration: Duration): Result<Unit> {
        return timerMutex.withLock {
            val timer = timersList.find { it.id == id } ?: return@withLock Result.failure(
                Throwable(getString(R.string.timer_with_id_does_not_exist))
            )
            val newTotalDuration = timer.totalDuration + duration
            timer.apply {
                totalDuration = newTotalDuration
                remainingDuration = newTotalDuration
            }
            updateTimerListLocked()
            Result.success(Unit)
        }
    }

    suspend fun resetTimer(id: Int): Result<Unit> {
        return timerMutex.withLock {
            val timer = timersList.find { it.id == id } ?: return@withLock Result.failure(
                Throwable(getString(R.string.timer_with_id_does_not_exist))
            )
            timer.apply {
                remainingDuration = timer.totalDuration
                hasStarted = false
                isPaused = false
            }
            updateTimerListLocked()
            maybeCancelJobIfAllInactiveLocked()
            maybeStopObservingNotificationsLocked()
            notificationManager.cancel(BASE_NOTIFICATION_ID + id)
            Result.success(Unit)
        }
    }

    suspend fun deleteTimer(id: Int): Result<Unit> {
        return timerMutex.withLock {
            if (timersList.removeAll { it.id == id }) {
                updateTimerListLocked()
                maybeCancelJobIfAllInactiveLocked()
                maybeStopObservingNotificationsLocked()
                notificationManager.cancel(BASE_NOTIFICATION_ID + id)
                Result.success(Unit)
            } else {
                Result.failure(Throwable(getString(R.string.timer_with_id_does_not_exist)))
            }
        }
    }

    private fun maybeCancelJobIfAllInactiveLocked() {
        if (
            timerJob?.isActive == true &&
            !timersList.any { it.hasStarted && !it.isPaused }
        ) {
            cancelJob()
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    private fun maybeStopObservingNotificationsLocked() {
        if (notificationJob?.isActive != true) return
        if (!timersList.any { it.hasStarted }) {
            notificationJob?.cancel()
            notificationJob = null
            unregisterReceiver()
            counter.set(1)
        }
    }

    private fun unregisterReceiver() {
        if (receiverRegistered) {
            unregisterReceiver(broadcastReceiver)
            receiverRegistered = false
        }
    }

    private fun cancelJob() {
        if (timerJob?.isActive != true) return
        timerJob?.cancel()
        timerJob = null
    }

    override fun onDestroy() {
        unregisterReceiver()
        notificationManager.cancelAll()
        super.onDestroy()
    }

    inner class ServiceBinder : Binder() {
        val service: TimerService
            get() = this@TimerService
    }

    companion object {
        private val RUNNING_TIMER_NOTIFICATION_CHANNEL_ID =
            "${TimerService::class.qualifiedName}_Running_Timers_NotificationChannel"
        private val FINISHED_TIMER_NOTIFICATION_CHANNEL_ID =
            "${TimerService::class.qualifiedName}_Finished_Timers_NotificationChannel"

        private const val FG_SERVICE_NOTIFICATION_ID = 10000
        private const val BASE_NOTIFICATION_ID = 10001

        private const val ACTIVITY_REQUEST_CODE = 10

        private const val ACTION_REQUEST_CODE_SHIFT = 1000

        private const val ACTION_PAUSE = "com.flamingo.clock.timer.action.PAUSE"
        private val PauseIntent = Intent(ACTION_PAUSE)

        private const val ACTION_ADD_ONE_MINUTE = "com.flamingo.clock.timer.action.ADD_ONE_MINUTE"
        private val AddOneMinuteIntent = Intent(ACTION_ADD_ONE_MINUTE)

        private const val ACTION_RESUME = "com.flamingo.clock.timer.action.RESUME"
        private val ResumeIntent = Intent(ACTION_RESUME)

        private const val ACTION_RESET = "com.flamingo.clock.timer.action.RESET"
        private val ResetIntent = Intent(ACTION_RESET)

        private const val ACTION_DELETE = "com.flamingo.clock.timer.action.DELETE"
        private val DeleteIntent = Intent(ACTION_DELETE)

        private const val EXTRA_TIMER_ID = "com.flamingo.clock.timer.action.TIMER_ID"
    }
}

data class Timer(
    val id: Int,
    var totalDuration: Duration,
    var remainingDuration: Duration = totalDuration,
    var label: String? = null,
    var hasStarted: Boolean = false,
    var isPaused: Boolean = false
) {
    val isNegative: Boolean
        get() = remainingDuration.isNegative()
}