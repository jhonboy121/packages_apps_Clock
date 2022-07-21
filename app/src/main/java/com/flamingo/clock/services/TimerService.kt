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

import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.SystemClock

import androidx.annotation.GuardedBy
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope

import com.flamingo.clock.R

import java.util.UUID

import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TimerService : LifecycleService() {

    private lateinit var serviceBinder: ServiceBinder

    private val timerMutex = Mutex()

    @GuardedBy("timerMutex")
    private val timersList = mutableListOf<Timer>()

    private val _timers = MutableStateFlow(emptyList<Timer>())
    val timers: StateFlow<List<Timer>>
        get() = _timers

    private var timerJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        serviceBinder = ServiceBinder()
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return serviceBinder
    }

    suspend fun addTimer(duration: Duration): Result<Unit> {
        val timer = Timer(totalDuration = duration)
        val result = timerMutex.withLock {
            val timerExists = timersList.any { it.id == timer.id }
            return@withLock if (!timerExists) {
                timersList.add(timer)
                updateTimerListLocked()
                Result.success(Unit)
            } else {
                Result.failure(Throwable(getString(R.string.job_with_id_exists)))
            }
        }
        maybeStartTimerJob()
        return result
    }

    private fun updateTimerListLocked() {
        _timers.value = timersList.toList()
    }

    private fun maybeStartTimerJob() {
        if (timerJob?.isActive != true) {
            timerJob = lifecycleScope.launch {
                updateAllTimers()
            }
        }
    }

    private suspend fun updateAllTimers() {
        var now = SystemClock.elapsedRealtime()
        do {
            val next = SystemClock.elapsedRealtime()
            val duration = Duration.from(next - now)
            now = next
            timerMutex.withLock {
                timersList.replaceAll {
                    if (!it.isPaused) {
                        it.copy(remainingDuration = it.remainingDuration - duration)
                    } else {
                        it
                    }
                }
                updateTimerListLocked()
            }
            delay(1000)
        } while (coroutineContext.isActive)
    }

    suspend fun pauseTimer(id: UUID): Result<Unit> {
        val result = updateTimerState(id, true)
        timerMutex.withLock {
            maybeCancelJobIfAllInactiveLocked()
        }
        return result
    }

    suspend fun resumeTimer(id: UUID): Result<Unit> {
        val result = updateTimerState(id, false)
        maybeStartTimerJob()
        return result
    }

    private suspend fun updateTimerState(id: UUID, isPaused: Boolean): Result<Unit> {
        timerMutex.withLock {
            val timerIndex = timersList.indexOfFirst { it.id == id }.takeIf { it >= 0 }
                ?: return Result.failure(Throwable(getString(R.string.timer_with_id_does_not_exist)))
            timersList[timerIndex] = timersList[timerIndex].copy(isPaused = isPaused)
        }
        return Result.success(Unit)
    }

    suspend fun incrementTimer(id: UUID, duration: Duration): Result<Unit> {
        timerMutex.withLock {
            val timerIndex = timersList.indexOfFirst { it.id == id }.takeIf { it >= 0 }
                ?: return Result.failure(Throwable(getString(R.string.timer_with_id_does_not_exist)))
            val timer = timersList[timerIndex]
            timersList[timerIndex] = timer.copy(
                totalDuration = timer.totalDuration + duration,
                remainingDuration = timer.remainingDuration + duration
            )
        }
        return Result.success(Unit)
    }

    suspend fun resetTimer(id: UUID): Result<Unit> {
        timerMutex.withLock {
            val timerIndex = timersList.indexOfFirst { it.id == id }.takeIf { it >= 0 }
                ?: return Result.failure(Throwable(getString(R.string.timer_with_id_does_not_exist)))
            timersList[timerIndex] =
                timersList[timerIndex].copy(isPaused = false, hasStarted = false)
            maybeCancelJobIfAllInactiveLocked()
        }
        return Result.success(Unit)
    }

    suspend fun deleteTimer(id: UUID): Result<Unit> {
        return timerMutex.withLock {
            val result = if (timersList.removeAll { it.id == id })
                Result.success(Unit)
            else
                Result.failure(Throwable(getString(R.string.timer_with_id_does_not_exist)))
            maybeCancelJobIfAllInactiveLocked()
            return@withLock result
        }
    }

    private fun maybeCancelJobIfAllInactiveLocked() {
        if (timerJob?.isActive == true && !timersList.any { it.hasStarted && !it.isPaused }) {
            cancelJob()
        }
    }

    private suspend fun resetAllTimers() {
        timerMutex.withLock {
            timersList.replaceAll {
                it.copy(hasStarted = false, isPaused = false)
            }
        }
        cancelJob()
    }

    private suspend fun deleteAllTimers() {
        timerMutex.withLock {
            timersList.clear()
        }
        cancelJob()
    }

    private fun cancelJob() {
        if (timerJob?.isActive != true) return
        timerJob?.cancel()
        timerJob = null
    }

    inner class ServiceBinder : Binder() {
        val service: TimerService
            get() = this@TimerService
    }
}

data class Duration(
    val hours: Int,
    val minutes: Int,
    val seconds: Int
) {
    operator fun plus(duration: Duration): Duration {
        val totalSeconds = duration.seconds + seconds
        val newSeconds = totalSeconds % 60
        val totalMinutes = duration.minutes + minutes + (totalSeconds / 60)
        val newMinutes = totalMinutes % 60
        val newHours = duration.hours + hours + (totalMinutes / 60)
        return Duration(newHours, newMinutes, newSeconds)
    }

    operator fun minus(duration: Duration): Duration {
        TODO()
    }

    companion object {
        val Zero = Duration(hours = 0, minutes = 0, seconds = 0)

        fun from(millis: Long): Duration {
            val totalSeconds = millis / 1000
            val seconds = totalSeconds % 60
            val totalMinutes = totalSeconds / 60
            val minutes = totalMinutes % 60
            val hours = totalMinutes / 60
            return Duration(
                hours = hours.toInt(),
                minutes = minutes.toInt(),
                seconds = seconds.toInt()
            )
        }
    }
}

data class Timer(
    val id: UUID = UUID.randomUUID(),
    val label: String? = null,
    val totalDuration: Duration = Duration.Zero,
    val remainingDuration: Duration = totalDuration,
    val hasStarted: Boolean = true,
    val isPaused: Boolean = false
)