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
import android.net.Uri

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore

import com.google.protobuf.InvalidProtocolBufferException

import java.io.InputStream
import java.io.OutputStream

val DEFAULT_CLOCK_STYLE = ClockStyle.DIGITAL
const val DEFAULT_SHOW_SECONDS = false
val DEFAULT_TIME_FORMAT = TimeFormat.TWELVE_HOUR

const val DEFAULT_VIBRATE_FOR_TIMERS = false
val DEFAULT_TIMER_SOUND_URI: Uri = Uri.EMPTY
const val DEFAULT_TIMER_VOLUME_RISE_DURATION = 0

object SettingsSerializer : Serializer<Settings> {

    override val defaultValue: Settings = Settings.newBuilder()
        .setClockStyle(DEFAULT_CLOCK_STYLE)
        .setShowSeconds(DEFAULT_SHOW_SECONDS)
        .setTimeFormat(DEFAULT_TIME_FORMAT)
        .setVibrateForTimers(DEFAULT_VIBRATE_FOR_TIMERS)
        .setTimerSoundUri(DEFAULT_TIMER_SOUND_URI.toString())
        .setTimerVolumeRiseDuration(DEFAULT_TIMER_VOLUME_RISE_DURATION)
        .build()

    override suspend fun readFrom(input: InputStream): Settings {
        try {
            return Settings.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot parse settings proto", exception)
        }
    }

    override suspend fun writeTo(
        t: Settings,
        output: OutputStream
    ) = t.writeTo(output)
}

val Context.settings: DataStore<Settings> by dataStore(
    fileName = "settings",
    serializer = SettingsSerializer
)