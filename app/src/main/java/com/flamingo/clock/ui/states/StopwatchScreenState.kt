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
import android.content.res.Resources
import android.os.IBinder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.flamingo.clock.R

import com.flamingo.clock.data.Time
import com.flamingo.clock.data.formatTime
import com.flamingo.clock.services.StopwatchService

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class StopwatchScreenState(
    private val stopwatchService: StopwatchService?,
    private val resources: Resources
) {

    val time: Flow<Time>
        get() = stopwatchService?.time ?: emptyFlow()

    val started: Flow<Boolean>
        get() = stopwatchService?.started ?: emptyFlow()

    val running: Flow<Boolean>
        get() = stopwatchService?.running ?: emptyFlow()

    val laps: Flow<List<String>>
        get() = stopwatchService?.laps?.combine(stopwatchService.currentLap) { list, lap ->
            val newList = list.sortedByDescending { it.number }
            val totalDigits = getDigitsInNumber(lap.number)
            val currentLapDuration = Time.fromTimeInMillis(lap.duration)
            val currentLapTime = Time.fromTimeInMillis(lap.lapTime)
            newList.map {
                val zerosToAdd = totalDigits - getDigitsInNumber(it.number)
                resources.getString(
                    R.string.lap_text_format_full,
                    prependZeros(it.number, zerosToAdd),
                    formatTime(
                        Time.fromTimeInMillis(it.duration),
                        alwaysShowHours = currentLapDuration.hours > 0,
                        alwaysShowMinutes = currentLapDuration.minutes > 0,
                        alwaysShowMillis = true
                    ),
                    formatTime(
                        Time.fromTimeInMillis(it.lapTime),
                        alwaysShowHours = currentLapTime.hours > 0,
                        alwaysShowMinutes = currentLapTime.minutes > 0,
                        alwaysShowMillis = true
                    )
                )
            }
        }?.flowOn(Dispatchers.Default) ?: emptyFlow()

    val currentLap: Flow<Pair<Int, String>>
        get() = stopwatchService?.currentLap?.map {
            Pair(
                it.number, resources.getString(
                    R.string.lap_text_format_full,
                    it.number.toString(),
                    formatTime(
                        Time.fromTimeInMillis(it.duration),
                        alwaysShowMillis = true
                    ),
                    formatTime(Time.fromTimeInMillis(it.lapTime), alwaysShowMillis = true)
                )
            )
        } ?: emptyFlow()

    fun start() {
        stopwatchService?.start()
    }

    fun lap() {
        stopwatchService?.lap()
    }

    fun pause() {
        stopwatchService?.pause()
    }

    fun reset() {
        stopwatchService?.reset()
    }

    private fun getDigitsInNumber(number: Int): Int {
        var digits = 0
        var num = number
        do {
            num /= 10
            digits++
        } while (num > 0)
        return digits
    }

    private fun prependZeros(number: Int, zeros: Int): String {
        var result = number.toString()
        if (zeros == 0) return result
        for (i in 1..zeros) {
            result = "0$result"
        }
        return result
    }
}

@Composable
fun rememberStopwatchScreenState(context: Context = LocalContext.current): StopwatchScreenState {
    var service by remember { mutableStateOf<StopwatchService?>(null) }
    val serviceConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(componentName: ComponentName, binder: IBinder) {
                service = (binder as StopwatchService.ServiceBinder).service
            }

            override fun onServiceDisconnected(componentName: ComponentName) {
                service = null
            }
        }
    }
    var serviceBound by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        serviceBound = context.bindService(
            Intent(context, StopwatchService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
        onDispose {
            if (serviceBound) {
                context.unbindService(serviceConnection)
                serviceBound = false
            }
        }
    }
    return remember(service, context.resources) {
        StopwatchScreenState(stopwatchService = service, resources = context.resources)
    }
}