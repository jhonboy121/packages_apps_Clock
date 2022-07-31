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

import android.content.Context

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext

import com.flamingo.clock.R
import com.flamingo.clock.data.Date
import com.flamingo.clock.data.Resolution
import com.flamingo.clock.data.Time
import com.flamingo.clock.data.TimeZoneDifference
import com.flamingo.clock.data.settings.ClockStyle
import com.flamingo.clock.data.settings.SettingsRepository
import com.flamingo.clock.data.settings.TimeFormat
import com.flamingo.clock.data.user.UserDataRepository

import java.time.ZoneId

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    context: Context,
) {

    private val _time = MutableStateFlow(Time.now(resolution = Resolution.SECOND))
    val time: StateFlow<Time> = _time.asStateFlow()

    val cityTimes: Flow<List<CityTime>> = userDataRepository.cityTimeZones.combine(
        time.map { it.minute }.distinctUntilChanged()
    ) { list, _ ->
        list.map {
            CityTime.fromCityTimeZone(it)
        }
    }.flowOn(Dispatchers.Default)

    val showSeconds: Flow<Boolean>
        get() = settingsRepository.showSeconds

    private val locale = context.resources.configuration.locales[0]

    private val _date = MutableStateFlow(Date.now(locale))
    val date: StateFlow<Date> = _date.asStateFlow()

    val clockStyle: Flow<ClockStyle>
        get() = settingsRepository.clockStyle

    val timeFormat: Flow<TimeFormat>
        get() = settingsRepository.timeFormat

    private val homeTimeZoneName = context.getString(R.string.home)
    val homeTime: Flow<CityTime?> = settingsRepository.homeTimeZone.map {
        if (it.isBlank()) {
            return@map null
        }
        val time = Time.now(
            ZoneId.of(it),
            resolution = Resolution.MINUTE,
            ignoreTimeZoneDifference = false
        )
        if (time.timeZoneDifference is TimeZoneDifference.Zero) {
            return@map null
        }
        CityTime(
            city = homeTimeZoneName,
            country = homeTimeZoneName,
            timezone = it,
            time = time
        )
    }

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

    fun removeHomeTimeZone() {
        coroutineScope.launch {
            settingsRepository.removeHomeTimeZone()
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
    context: Context = LocalContext.current
) = remember(
    userDataRepository,
    settingsRepository,
    coroutineScope,
    context
) {
    ClockScreenState(
        userDataRepository = userDataRepository,
        settingsRepository = settingsRepository,
        coroutineScope = coroutineScope,
        context = context
    )
}
