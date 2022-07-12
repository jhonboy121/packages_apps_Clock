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
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope

import com.flamingo.clock.R
import com.flamingo.clock.data.Lap
import com.flamingo.clock.data.Time
import com.flamingo.clock.data.formatTime
import com.flamingo.clock.ui.ClockActivity

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class StopwatchService : LifecycleService() {

    private lateinit var binder: ServiceBinder
    private lateinit var configuration: Configuration
    private lateinit var notificationManager: NotificationManagerCompat
    private lateinit var activityIntent: PendingIntent
    private lateinit var startIntent: PendingIntent
    private lateinit var lapIntent: PendingIntent
    private lateinit var pauseIntent: PendingIntent
    private lateinit var resetIntent: PendingIntent

    private var stopwatchJob: Job? = null

    private val _started = MutableStateFlow(false)
    val started: StateFlow<Boolean>
        get() = _started

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean>
        get() = _running

    private val _time = MutableStateFlow(0L)
    val time: Flow<Time>
        get() = _time.map { Time.fromTimeInMillis(it) }.distinctUntilChanged()

    private val notificationTime: Flow<String>
        get() = time.map { formatTime(it, true) }.distinctUntilChanged()

    private val lapMutex = Mutex()

    @GuardedBy("lapMutex")
    private val _currentLap = MutableStateFlow(Lap(1))

    val currentLap: StateFlow<Lap>
        get() = _currentLap

    @GuardedBy("lapMutex")
    private val lapsList = mutableListOf<Lap>()

    private val _laps = MutableStateFlow<List<Lap>>(emptyList())
    val laps: StateFlow<List<Lap>>
        get() = _laps

    private var receiverRegistered = false
    private val actionBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            when (intent?.action) {
                ACTION_START -> startInternal()
                ACTION_LAP -> lap()
                ACTION_PAUSE -> pause()
                ACTION_RESET -> reset()
            }
        }
    }

    private lateinit var timeAndLapNotificationBuilder: NotificationCompat.Builder

    override fun onCreate() {
        super.onCreate()
        binder = ServiceBinder()
        configuration = resources.configuration
        notificationManager = NotificationManagerCompat.from(this)
        createIntents()
        timeAndLapNotificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.outline_stopwatch_24)
            .setContentIntent(activityIntent)
            .addAction(
                R.drawable.baseline_pause_24,
                getString(R.string.pause),
                pauseIntent
            )
            .addAction(
                R.drawable.outline_stopwatch_24,
                getString(R.string.lap),
                lapIntent
            )
        setupNotificationChannel()
    }

    private fun createIntents() {
        activityIntent = PendingIntent.getActivity(
            this,
            ACTIVITY_REQUEST_CODE,
            Intent(this, ClockActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        startIntent = PendingIntent.getBroadcast(
            this,
            START_REQUEST_CODE,
            Intent(ACTION_START),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        lapIntent = PendingIntent.getBroadcast(
            this,
            LAP_REQUEST_CODE,
            Intent(ACTION_LAP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        pauseIntent = PendingIntent.getBroadcast(
            this,
            PAUSE_REQUEST_CODE,
            Intent(ACTION_PAUSE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        resetIntent = PendingIntent.getBroadcast(
            this,
            RESET_REQUEST_CODE,
            Intent(ACTION_RESET),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun setupNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.stopwatch_service_notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (configuration.diff(newConfig) == ActivityInfo.CONFIG_LOCALE) {
            setupNotificationChannel()
        }
        configuration = newConfig
    }

    fun start() {
        if (_started.value && stopwatchJob?.isActive == true) return
        startForeground(
            NOTIFICATION_ID,
            timeAndLapNotificationBuilder
                .setContentTitle("00:00")
                .build()
        )
        if (!receiverRegistered) {
            registerReceiver(
                actionBroadcastReceiver,
                IntentFilter().apply {
                    addAction(ACTION_START)
                    addAction(ACTION_LAP)
                    addAction(ACTION_PAUSE)
                    addAction(ACTION_RESET)
                },
            )
            receiverRegistered = true
        }
        startInternal()
    }

    private fun startInternal() {
        stopwatchJob = lifecycleScope.launch(Dispatchers.Default) {
            launch {
                updateNotification()
            }
            var currentTime = SystemClock.elapsedRealtime()
            do {
                val newTime = SystemClock.elapsedRealtime()
                val diff = newTime - currentTime
                _time.value += diff
                currentTime = newTime
                lapMutex.withLock {
                    _currentLap.value = _currentLap.value.copy(
                        duration = _currentLap.value.duration + diff,
                        lapTime = _time.value
                    )
                }
                delay(10)
            } while (isActive)
        }
        _running.value = true
        _started.value = true
    }

    private suspend fun updateNotification() {
        notificationTime.combine(_laps) { time, laps ->
            val notification = timeAndLapNotificationBuilder
                .setContentTitle(time)
                .apply {
                    if (laps.isNotEmpty()) {
                        setContentText(getString(R.string.lap_text_format, laps.last().number))
                    }
                }
                .build()
            notificationManager.notify(NOTIFICATION_ID, notification)
        }.collect()
    }

    fun lap() {
        if (!_started.value || stopwatchJob?.isActive != true) return
        lifecycleScope.launch {
            lapMutex.withLock {
                lapsList.add(_currentLap.value)
                _laps.value = lapsList.toList()
                _currentLap.value =
                    _currentLap.value.copy(number = _currentLap.value.number + 1, duration = 0)
            }
        }
    }

    fun pause() {
        if (!_started.value || stopwatchJob?.isActive != true) return
        lifecycleScope.launch {
            stopwatchJob?.cancelAndJoin()
            stopwatchJob = null
            _running.value = false
            showResumeNotification()
        }
    }

    private suspend fun showResumeNotification() {
        val time = notificationTime.first()
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.outline_stopwatch_24)
            .setContentTitle(time)
            .setContentText(getString(R.string.paused))
            .setContentIntent(activityIntent)
            .addAction(
                R.drawable.baseline_play_arrow_24,
                getString(R.string.start),
                startIntent
            )
            .addAction(
                R.drawable.baseline_reset_24,
                getString(R.string.reset),
                resetIntent
            )
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun reset() {
        if (!_started.value) return
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (receiverRegistered) {
            unregisterReceiver(actionBroadcastReceiver)
            receiverRegistered = false
        }
        stopwatchJob?.cancel()
        stopwatchJob = null
        _running.value = false
        _started.value = false
        _time.value = 0L
        _currentLap.value = Lap(1)
        lapsList.clear()
        _laps.value = emptyList()
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            unregisterReceiver(actionBroadcastReceiver)
            receiverRegistered = false
        }
        super.onDestroy()
    }

    inner class ServiceBinder : Binder() {
        val service: StopwatchService
            get() = this@StopwatchService
    }

    companion object {
        private val NOTIFICATION_CHANNEL_ID =
            "${StopwatchService::class.qualifiedName}_NotificationChannel"
        private const val NOTIFICATION_ID = 1

        private const val ACTIVITY_REQUEST_CODE = 1

        private const val START_REQUEST_CODE = 1
        private const val ACTION_START = "com.flamingo.clock.action.START"

        private const val LAP_REQUEST_CODE = 2
        private const val ACTION_LAP = "com.flamingo.clock.action.LAP"

        private const val PAUSE_REQUEST_CODE = 3
        private const val ACTION_PAUSE = "com.flamingo.clock.action.PAUSE"

        private const val RESET_REQUEST_CODE = 4
        private const val ACTION_RESET = "com.flamingo.clock.action.RESET"
    }
}