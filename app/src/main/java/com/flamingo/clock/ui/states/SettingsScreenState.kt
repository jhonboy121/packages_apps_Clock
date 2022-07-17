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

import com.flamingo.clock.data.ClockStyle
import com.flamingo.clock.data.TimeFormat
import com.flamingo.clock.repositories.SettingsRepository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

import org.koin.androidx.compose.get

class SettingsScreenState(
    private val settingsRepository: SettingsRepository,
    private val coroutineScope: CoroutineScope,
    private val context: Context
) {
    val clockStyle: Flow<ClockStyle>
        get() = settingsRepository.clockStyle

    val showSeconds: Flow<Boolean>
        get() = settingsRepository.showSeconds

    val timeFormat: Flow<TimeFormat>
        get() = settingsRepository.timeFormat

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

    fun openDateAndTimeSettings() {
        context.startActivity(settingsIntent)
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
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    context: Context = LocalContext.current
) = remember(settingsRepository, coroutineScope, context) {
    SettingsScreenState(
        settingsRepository = settingsRepository,
        coroutineScope = coroutineScope,
        context = context
    )
}