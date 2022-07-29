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

package com.flamingo.clock.data.settings

import android.content.Context

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(context: Context) {

    private val settings = context.settings

    val clockStyle: Flow<ClockStyle>
        get() = settings.data.map { it.clockStyle }

    val showSeconds: Flow<Boolean>
        get() = settings.data.map { it.showSeconds }

    val timeFormat: Flow<TimeFormat>
        get() = settings.data.map { it.timeFormat }

    val homeTimeZone: Flow<String>
        get() = settings.data.map { it.homeTimeZone }

    val vibrateForTimers: Flow<Boolean>
        get() = settings.data.map { it.vibrateForTimers }

    suspend fun setClockStyle(clockStyle: ClockStyle) {
        settings.updateData {
            it.toBuilder()
                .setClockStyle(clockStyle)
                .build()
        }
    }

    suspend fun setShowSeconds(showSeconds: Boolean) {
        settings.updateData {
            it.toBuilder()
                .setShowSeconds(showSeconds)
                .build()
        }
    }

    suspend fun setTimeFormat(timeFormat: TimeFormat) {
        settings.updateData {
            it.toBuilder()
                .setTimeFormat(timeFormat)
                .build()
        }
    }

    suspend fun setHomeTimeZone(homeTimeZone: String) {
        settings.updateData {
            it.toBuilder()
                .setHomeTimeZone(homeTimeZone)
                .build()
        }
    }

    suspend fun removeHomeTimeZone() {
        settings.updateData {
            it.toBuilder()
                .clearHomeTimeZone()
                .build()
        }
    }

    suspend fun setVibrateForTimers(vibrate: Boolean) {
        settings.updateData {
            it.toBuilder()
                .setVibrateForTimers(vibrate)
                .build()
        }
    }
}