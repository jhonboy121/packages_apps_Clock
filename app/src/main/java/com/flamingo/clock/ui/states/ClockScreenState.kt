/*
 * Copyright (C) 2022 FlamingoOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

import android.content.res.Configuration

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalConfiguration

import com.flamingo.clock.data.ClockStyle
import com.flamingo.clock.data.Date
import com.flamingo.clock.data.Resolution
import com.flamingo.clock.data.Time
import com.flamingo.clock.data.TimeFormat
import com.flamingo.clock.repositories.SettingsRepository
import com.flamingo.clock.repositories.UserDataRepository

import java.util.Locale

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

import org.koin.androidx.compose.get

class ClockScreenState(
    private val userDataRepository: UserDataRepository,
    private val settingsRepository: SettingsRepository,
    private val coroutineScope: CoroutineScope,
    private val configuration: Configuration
) {

    val cityTimes: Flow<List<CityTime>>
        get() = userDataRepository.cityTimeZones.combine(
            _time.map { it.minute }.distinctUntilChanged()
        ) { list, _ ->
            list.map {
                CityTime.fromCityTimeZone(it)
            }
        }.flowOn(Dispatchers.Default)

    private val _time = MutableStateFlow(Time.now(resolution = Resolution.SECOND))
    val time: StateFlow<Time>
        get() = _time

    val showSeconds: Flow<Boolean>
        get() = settingsRepository.showSeconds

    private val _date = MutableStateFlow(Date.now(locale))
    val date: StateFlow<Date>
        get() = _date

    val clockStyle: Flow<ClockStyle>
        get() = settingsRepository.clockStyle

    val timeFormat: Flow<TimeFormat>
        get() = settingsRepository.timeFormat

    private val locale: Locale
        get() = configuration.locales[0]

    init {
        coroutineScope.launch {
            launch(Dispatchers.Default) {
                updateCurrentTime()
            }
        }
    }

    fun removeSavedCityTime(cityTime: CityTime) {
        coroutineScope.launch {
            userDataRepository.removeCityTime(cityTime.toCityTimeZone())
        }
    }

    private suspend fun CoroutineScope.updateCurrentTime() {
        do {
            _time.value = Time.now(resolution = Resolution.SECOND)
            delay(1000)
        } while (isActive)
    }
}

@Composable
fun rememberClockScreenState(
    userDataRepository: UserDataRepository = get(),
    settingsRepository: SettingsRepository = get(),
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    configuration: Configuration = LocalConfiguration.current
) = remember(
    userDataRepository,
    settingsRepository,
    coroutineScope,
    configuration
) {
    ClockScreenState(
        userDataRepository = userDataRepository,
        settingsRepository = settingsRepository,
        coroutineScope = coroutineScope,
        configuration = configuration
    )
}
