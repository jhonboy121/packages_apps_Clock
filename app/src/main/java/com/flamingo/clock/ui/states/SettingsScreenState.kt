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
import android.content.Intent

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext

import com.flamingo.clock.data.settings.ClockStyle
import com.flamingo.clock.data.settings.SettingsRepository
import com.flamingo.clock.data.settings.TimeFormat
import com.flamingo.clock.data.tz.CityTimeZoneRepository
import com.flamingo.support.compose.ui.preferences.Entry

import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

import org.koin.androidx.compose.get

class SettingsScreenState(
    private val settingsRepository: SettingsRepository,
    private val cityTimeZoneRepository: CityTimeZoneRepository,
    private val coroutineScope: CoroutineScope,
    private val context: Context
) {
    val clockStyle: Flow<ClockStyle>
        get() = settingsRepository.clockStyle

    val showSeconds: Flow<Boolean>
        get() = settingsRepository.showSeconds

    val timeFormat: Flow<TimeFormat>
        get() = settingsRepository.timeFormat

    private val locale = context.resources.configuration.locales[0]

    private val _timeZoneEntries = MutableStateFlow<List<Entry<String>>>(emptyList())
    val timeZoneEntries: Flow<List<Entry<String>>>
        get() = _timeZoneEntries

    val homeTimeZone: Flow<String?>
        get() = settingsRepository.homeTimeZone.map { it.ifBlank { null } }

    val vibrateForTimers: Flow<Boolean>
        get() = settingsRepository.vibrateForTimers

    init {
        coroutineScope.launch(Dispatchers.Default) {
            loadAllTimeZones()
        }
    }

    private suspend fun loadAllTimeZones() {
        val instant = Instant.now()
        cityTimeZoneRepository.getAllTimeZoneInfo().onSuccess { list ->
            val processedList = list.map {
                ZoneId.of(it.timezone).normalized()
            }.sortedBy {
                it.rules.getOffset(instant).totalSeconds
            }.map {
                val offset = it.rules.getOffset(instant)
                val offsetText = if (offset.totalSeconds == 0)
                    "+0:00"
                else
                    offset.getDisplayName(TextStyle.FULL, locale)
                val name = it.getDisplayName(TextStyle.FULL, locale)
                Entry("(GMT$offsetText) $name", it.id)
            }
            val addedKeys = mutableListOf<String>()
            val filteredList = mutableListOf<Entry<String>>()
            processedList.forEach {
                if (!addedKeys.contains(it.name)) {
                    addedKeys.add(it.name)
                    filteredList.add(it)
                }
            }
            _timeZoneEntries.value = filteredList.toList()
        }
    }

    fun setClockStyle(clockStyle: ClockStyle) {
        coroutineScope.launch {
            settingsRepository.setClockStyle(clockStyle)
        }
    }

    fun setShowSeconds(showSeconds: Boolean) {
        coroutineScope.launch {
            settingsRepository.setShowSeconds(showSeconds)
        }
    }

    fun setTimeFormat(timeFormat: TimeFormat) {
        coroutineScope.launch {
            settingsRepository.setTimeFormat(timeFormat)
        }
    }

    fun setHomeTimeZone(homeTimeZone: String) {
        coroutineScope.launch {
            settingsRepository.setHomeTimeZone(homeTimeZone)
        }
    }

    fun openDateAndTimeSettings() {
        context.startActivity(settingsIntent)
    }

    fun setVibrateForTimers(vibrate: Boolean) {
        coroutineScope.launch {
            settingsRepository.setVibrateForTimers(vibrate)
        }
    }

    companion object {
        private const val SETTINGS_ACTION = "android.settings.DATE_SETTINGS"

        private val settingsIntent = Intent(SETTINGS_ACTION).apply {
            setPackage("com.android.settings")
        }
    }
}

@Composable
fun rememberSettingsScreenState(
    settingsRepository: SettingsRepository = get(),
    cityTimeZoneRepository: CityTimeZoneRepository = get(),
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    context: Context = LocalContext.current
) = remember(settingsRepository, cityTimeZoneRepository, coroutineScope, context) {
    SettingsScreenState(
        settingsRepository = settingsRepository,
        cityTimeZoneRepository = cityTimeZoneRepository,
        coroutineScope = coroutineScope,
        context = context
    )
}