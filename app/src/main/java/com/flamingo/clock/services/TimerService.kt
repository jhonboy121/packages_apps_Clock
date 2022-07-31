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
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log

import androidx.annotation.GuardedBy
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope

import com.flamingo.clock.R
import com.flamingo.clock.data.getPrependedString
import com.flamingo.clock.data.settings.SettingsRepository
import com.flamingo.clock.ui.ClockActivity

import java.util.concurrent.atomic.AtomicInteger

import kotlin.coroutines.coroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import org.koin.android.ext.android.inject

class TimerService : LifecycleService() {

    private lateinit var serviceBinder: ServiceBinder
    private lateinit var currentConfig: Configuration
    private lateinit var notificationManager: NotificationManager
    private lateinit var activityIntent: PendingIntent
    private lateinit var vibrator: Vibrator

    private val counter = AtomicInteger(1)

    private val timerMutex = Mutex()

    @GuardedBy("timerMutex")
    private val timersList = mutableListOf<Timer>()

    private val _timers = MutableStateFlow<List<Timer>>(emptyList())
    val timers: StateFlow<List<Timer>> = _timers.asStateFlow()

    private val anyTimerFinished: Flow<Boolean> =
        timers.map { timers -> timers.any { it.isNegative } }
            .distinctUntilChanged()

    private var timerJob: Job? = null

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

    private val settingsRepository by inject<SettingsRepository>()

    private var currentMediaPlayer: MediaPlayer? = null

    override fun onCreate() {
        super.onCreate()
        serviceBinder = ServiceBinder()
        notificationManager = getSystemService()!!
        vibrator = getSystemService()!!
        activityIntent = PendingIntent.getActivity(
            this,
            ACTIVITY_REQUEST_CODE,
            Intent(this, ClockActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        createNotificationChannels()
        currentConfig = resources.configuration
        registerReceiver()
        observeNotifications()
    }

    private fun createNotificationChannels() {
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
        notificationManager.createNotificationChannels(
            listOf(
                runningNotificationChannel,
                finishedNotificationChannel
            )
        )
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return serviceBinder
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.diff(currentConfig) == ActivityInfo.CONFIG_LOCALE) {
            createNotificationChannels()
        }
        currentConfig = newConfig
    }

    private fun registerReceiver() {
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

    private fun observeNotifications() {
        lifecycleScope.launch {
            launch {
                anyTimerFinished.combine(settingsRepository.vibrateForTimers) { anyTimerFinished, vibrateEnabled ->
                    vibrateEnabled && anyTimerFinished
                }.collect {
                    if (it) {
                        vibrator.vibrate(TimerVibrationEffect)
                    } else {
                        vibrator.cancel()
                    }
                }
            }
            launch(Dispatchers.IO) {
                anyTimerFinished.combine(settingsRepository.timerSoundUri.filterNot { it == Uri.EMPTY }) { anyTimerFinished, uri ->
                    disposeCurrentMediaPlayer()
                    if (!anyTimerFinished) return@combine
                    val persistedUris = contentResolver.persistedUriPermissions.map { it.uri }
                    if (persistedUris.contains(uri)) {
                        currentMediaPlayer = MediaPlayer().apply {
                            setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                    .setUsage(AudioAttributes.USAGE_ALARM)
                                    .build()
                            )
                            setDataSource(this@TimerService, uri)
                            prepare()
                            start()
                        }
                    } else {
                        Log.e(TAG, "Application does not hold read permission for current timer sound uri")
                    }
                    return@combine
                }.collect()
            }
            timers
                .map { list -> list.filter { it.hasStarted } }
                .collect { list ->
                    list.forEach {
                        notificationManager.notify(
                            BASE_NOTIFICATION_ID + it.id,
                            createNotification(it)
                        )
                    }
                    delay(1000)
                }
        }
    }

    private fun disposeCurrentMediaPlayer() {
        currentMediaPlayer?.let {
            it.stop()
            it.reset()
            it.release()
        }
        currentMediaPlayer = null
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
            Result.success(Unit)
        }
    }

    private fun updateTimerListLocked() {
        _timers.value = timersList.toList()
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
                    .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
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
            .setOngoing(true)
            .setSilent(true)
        addActions(timer, builder)
        return builder.build()
    }

    private fun getNotificationTitle(timer: Timer): String {
        return timer.label?.takeIf { it.isNotBlank() }?.let {
            getString(
                when {
                    timer.isNegative -> R.string.timer_is_up
                    timer.isPaused -> R.string.timer_paused
                    else -> R.string.timer_running
                },
                it
            )
        } ?: getString(
            when {
                timer.isNegative -> R.string.timer_is_up_no_label
                timer.isPaused -> R.string.timer_paused_no_label
                else -> R.string.timer_running_no_label
            }
        )
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
                timersList.forEachIndexed { index, timer ->
                    if (timer.hasStarted && !timer.isPaused) {
                        timersList[index] =
                            timer.copy(remainingDuration = timer.remainingDuration - duration)
                    }
                }
                updateTimerListLocked()
            }
        } while (coroutineContext.isActive)
    }

    suspend fun setTimerLabel(id: Int, label: String): Result<Unit> {
        return timerMutex.withLock {
            val timerIndex = timersList.indexOfFirst { it.id == id }.takeIf { it >= 0 }
                ?: return@withLock Result.failure(Throwable(getString(R.string.timer_with_id_does_not_exist)))
            timersList[timerIndex] =
                timersList[timerIndex].copy(label = label)
            updateTimerListLocked()
            Result.success(Unit)
        }
    }

    suspend fun startTimer(id: Int): Result<Unit> {
        return timerMutex.withLock {
            val timerIndex = timersList.indexOfFirst { it.id == id }.takeIf { it >= 0 }
                ?: return@withLock Result.failure(Throwable(getString(R.string.timer_with_id_does_not_exist)))
            timersList[timerIndex] =
                timersList[timerIndex].copy(hasStarted = true, isPaused = false)
            updateTimerListLocked()
            maybeStartTimerJob()
            Result.success(Unit)
        }
    }

    suspend fun pauseTimer(id: Int): Result<Unit> {
        return timerMutex.withLock {
            val timerIndex = timersList.indexOfFirst { it.id == id }.takeIf { it >= 0 }
                ?: return@withLock Result.failure(Throwable(getString(R.string.timer_with_id_does_not_exist)))
            timersList[timerIndex] = timersList[timerIndex].copy(isPaused = true)
            updateTimerListLocked()
            maybeCancelJobIfAllInactiveLocked()
            Result.success(Unit)
        }
    }

    suspend fun resumeTimer(id: Int): Result<Unit> {
        return timerMutex.withLock {
            val timerIndex = timersList.indexOfFirst { it.id == id }.takeIf { it >= 0 }
                ?: return@withLock Result.failure(Throwable(getString(R.string.timer_with_id_does_not_exist)))
            timersList[timerIndex] = timersList[timerIndex].copy(isPaused = false)
            updateTimerListLocked()
            maybeStartTimerJob()
            Result.success(Unit)
        }
    }

    suspend fun incrementTimer(id: Int, duration: Duration): Result<Unit> {
        return timerMutex.withLock {
            val timerIndex = timersList.indexOfFirst { it.id == id }.takeIf { it >= 0 }
                ?: return@withLock Result.failure(
                    Throwable(getString(R.string.timer_with_id_does_not_exist))
                )
            val timer = timersList[timerIndex]
            val newTotalDuration =
                duration + if (timer.isNegative) Duration.ZERO else timer.totalDuration
            timersList[timerIndex] =
                timer.copy(totalDuration = newTotalDuration, remainingDuration = newTotalDuration)
            updateTimerListLocked()
            Result.success(Unit)
        }
    }

    suspend fun resetTimer(id: Int): Result<Unit> {
        return timerMutex.withLock {
            val timerIndex = timersList.indexOfFirst { it.id == id }.takeIf { it >= 0 }
                ?: return@withLock Result.failure(Throwable(getString(R.string.timer_with_id_does_not_exist)))
            val timer = timersList[timerIndex]
            timersList[timerIndex] = timer.copy(
                remainingDuration = timer.totalDuration,
                hasStarted = false,
                isPaused = false
            )
            updateTimerListLocked()
            maybeCancelJobIfAllInactiveLocked()
            notificationManager.cancel(BASE_NOTIFICATION_ID + id)
            Result.success(Unit)
        }
    }

    suspend fun deleteTimer(id: Int): Result<Unit> {
        return timerMutex.withLock {
            if (timersList.removeAll { it.id == id }) {
                updateTimerListLocked()
                maybeCancelJobIfAllInactiveLocked()
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
        disposeCurrentMediaPlayer()
        unregisterReceiver()
        notificationManager.cancelAll()
        super.onDestroy()
    }

    inner class ServiceBinder : Binder() {
        val service: TimerService
            get() = this@TimerService
    }

    companion object {
        private const val TAG = "TimerService"

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

        private val TimerVibrationEffect = VibrationEffect.createWaveform(
            longArrayOf(0, 300, 400, 300, 400, 300, 1400),
            intArrayOf(0, 255, 0, 255, 0, 255, 0),
            1
        )
    }
}

data class Timer(
    val id: Int,
    val totalDuration: Duration,
    val remainingDuration: Duration = totalDuration,
    val label: String? = null,
    val hasStarted: Boolean = false,
    val isPaused: Boolean = false
) {
    val isNegative: Boolean
        get() = remainingDuration.isNegative()
}