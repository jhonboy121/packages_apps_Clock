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

package com.flamingo.clock.ui.states

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

import com.flamingo.clock.services.TimerService

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class TimerScreenState(
    private val timerService: TimerService?,
    private val coroutineScope: CoroutineScope,
    private val snackbarHostState: SnackbarHostState,
    private val context: Context
) {

    val timers: Flow<List<TimerInfo>> = timerService?.timers?.map { list ->
        list.map {
            val progress =
                it.remainingDuration.inWholeNanoseconds / it.totalDuration.inWholeNanoseconds.toFloat()
            it.remainingDuration.absoluteValue.toComponents { hours, minutes, seconds, _ ->
                TimerInfo(
                    id = it.id,
                    label = it.label,
                    remainingHours = hours.toInt(),
                    remainingMinutes = minutes,
                    remainingSeconds = seconds,
                    isNegative = it.isNegative,
                    progress = progress.coerceAtLeast(0f),
                    hasStarted = it.hasStarted,
                    isPaused = it.isPaused
                )
            }
        }
    }?.distinctUntilChanged()?.flowOn(Dispatchers.Default) ?: emptyFlow()

    var userDurationInput by mutableStateOf(DEFAULT_USER_INPUT_TEXT)
        private set

    fun handleButtonPress(button: Button) {
        when (button) {
            Button.Backspace -> {
                // There are no non-zero digits, skip operation
                if (!userDurationInput.any { it != '0' }) return
                userDurationInput = "0" + userDurationInput.substring(0..4)
            }
            Button.DoubleZero -> {
                // There are no non-zero digits, skip operation
                if (!userDurationInput.any { it != '0' }) return
                // Cannot add any more numbers
                if (userDurationInput.first() != '0') return
                if (userDurationInput[1] != '0') {
                    // Can only add a zero at the end
                    handleButtonPress(Button.Number(0))
                } else {
                    // Shifting left by 2 and adding 00 at end
                    userDurationInput = userDurationInput.substring(2..5) + "00"
                }
            }
            is Button.Number -> {
                // There are no non-zero digits, skip operation
                if (button.number == 0 && !userDurationInput.any { it != '0' }) return
                // Cannot add any more numbers
                if (userDurationInput.first() != '0') return
                // Shifting left by 1 and adding number at end
                userDurationInput = userDurationInput.substring(1..5) + button.number
            }
        }
    }

    fun addTimer() {
        coroutineScope.launch {
            val currentTimers = timers.first().size
            if (currentTimers == 0) {
                context.startService(Intent(context, TimerService::class.java))
            }
            val hours = userDurationInput.substring(0..1).toInt()
            val minutes = userDurationInput.substring(2..3).toInt()
            val seconds = userDurationInput.substring(4..5).toInt()
            val timerDuration = Duration.parse("${hours}h ${minutes}m ${seconds}s")
            timerService?.addTimer(timerDuration)?.onFailure { showExceptionAsSnackbar(it) }
                ?.onSuccess { resetInput() }
        }
    }

    fun setTimerLabel(id: Int, label: String) {
        coroutineScope.launch {
            timerService?.setTimerLabel(id, label)?.onFailure {
                showExceptionAsSnackbar(it)
            }
        }
    }

    fun startTimer(id: Int) {
        coroutineScope.launch {
            timerService?.startTimer(id)?.onFailure {
                showExceptionAsSnackbar(it)
            }
        }
    }

    fun incrementTimer(id: Int) {
        coroutineScope.launch {
            timerService?.incrementTimer(id, duration = 1.minutes)?.onFailure {
                showExceptionAsSnackbar(it)
            }
        }
    }

    fun pauseTimer(id: Int) {
        coroutineScope.launch {
            timerService?.pauseTimer(id)?.onFailure {
                showExceptionAsSnackbar(it)
            }
        }
    }

    fun resumeTimer(id: Int) {
        coroutineScope.launch {
            timerService?.resumeTimer(id)?.onFailure {
                showExceptionAsSnackbar(it)
            }
        }
    }

    fun resetTimer(id: Int) {
        coroutineScope.launch {
            timerService?.resetTimer(id)?.onFailure {
                showExceptionAsSnackbar(it)
            }
        }
    }

    fun deleteTimer(id: Int) {
        coroutineScope.launch {
            timerService?.deleteTimer(id)?.onFailure {
                showExceptionAsSnackbar(it)
            }
            val currentTimers = timers.first().size
            if (currentTimers == 0) {
                context.stopService(Intent(context, TimerService::class.java))
            }
        }
    }

    private fun showExceptionAsSnackbar(exception: Throwable) {
        coroutineScope.launch {
            exception.localizedMessage?.let {
                snackbarHostState.showSnackbar(it)
            }
        }
    }

    fun resetInput() {
        userDurationInput = DEFAULT_USER_INPUT_TEXT
    }

    companion object {
        const val DEFAULT_USER_INPUT_TEXT = "000000"
    }
}

data class TimerInfo(
    val id: Int,
    val label: String?,
    val remainingHours: Int,
    val remainingMinutes: Int,
    val remainingSeconds: Int,
    val progress: Float,
    val hasStarted: Boolean,
    val isPaused: Boolean,
    val isNegative: Boolean,
)

sealed interface Button {
    data class Number(val number: Int) : Button
    object DoubleZero : Button
    object Backspace : Button
}

@Composable
fun rememberTimerScreenState(
    context: Context = LocalContext.current,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    snackbarHostState: SnackbarHostState
): TimerScreenState {
    var service by remember { mutableStateOf<TimerService?>(null) }
    val serviceConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(componentName: ComponentName, binder: IBinder) {
                service = (binder as TimerService.ServiceBinder).service
            }

            override fun onServiceDisconnected(componentName: ComponentName) {
                service = null
            }
        }
    }
    var bound by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        bound = context.bindService(
            Intent(context, TimerService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
        onDispose {
            if (bound) {
                context.unbindService(serviceConnection)
                bound = false
            }
        }
    }
    return remember(service, coroutineScope, snackbarHostState, context) {
        TimerScreenState(
            timerService = service,
            coroutineScope = coroutineScope,
            snackbarHostState = snackbarHostState,
            context = context
        )
    }
}