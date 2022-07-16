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

package com.flamingo.clock.data

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

import kotlin.math.absoluteValue

const val HOUR = 1000 * 60 * 60
const val MINUTE = 1000 * 60
const val SECOND = 1000

enum class Resolution {
    MINUTE,
    SECOND,
    MILLISECOND
}

data class Time(
    val hour: Int,
    val minute: Int,
    val second: Int,
    val millisecond: Int
) {
    var timeZoneDifference: TimeZoneDifference = TimeZoneDifference.Zero
        private set

    fun withTwelveHourFormat() =
        copy(
            hour = when {
                hour == 0 -> 12
                hour > 12 -> hour - 12
                else -> hour
            }
        )

    companion object {
        val Zero = Time(0, 0, 0, 0)

        fun fromMillis(time: Long): Time {
            val milliseconds = (time % SECOND) / 10
            val seconds = (time / SECOND) % 60
            val minutes = (time / MINUTE) % 60
            val hours = (time / HOUR) % 60
            return Time(
                hour = hours.toInt(),
                minute = minutes.toInt(),
                second = seconds.toInt(),
                millisecond = milliseconds.toInt()
            )
        }

        private fun fromZoneId(zoneId: ZoneId, resolution: Resolution): Time {
            val zoneTime = ZonedDateTime.now(zoneId)
            return when (resolution) {
                Resolution.MINUTE -> Time(
                    hour = zoneTime.hour,
                    minute = zoneTime.minute,
                    second = 0,
                    millisecond = 0
                )
                Resolution.SECOND -> Time(
                    hour = zoneTime.hour,
                    minute = zoneTime.minute,
                    second = zoneTime.second,
                    millisecond = 0
                )
                Resolution.MILLISECOND -> Time(
                    hour = zoneTime.hour,
                    minute = zoneTime.minute,
                    second = zoneTime.second,
                    millisecond = zoneTime.nano / 1000000
                )
            }
        }

        fun now(
            zoneId: ZoneId = ZoneId.systemDefault(),
            resolution: Resolution = Resolution.MILLISECOND,
            ignoreTimeZoneDifference: Boolean = true
        ): Time {
            val time = fromZoneId(zoneId, resolution)
            if (ignoreTimeZoneDifference) {
                return time
            }
            val nativeZoneId = ZoneId.systemDefault()
            val instant = Instant.now()
            val zoneOffset = zoneId.rules.getOffset(instant)
            val nativeZoneOffset = nativeZoneId.rules.getOffset(instant)
            val relativeOffset = (zoneOffset.totalSeconds - nativeZoneOffset.totalSeconds) * 1000
            if (relativeOffset == 0) {
                return time
            }
            val timeDiff = fromMillis(relativeOffset.absoluteValue.toLong())
            val nativeTime = fromZoneId(nativeZoneId, resolution)
            time.apply {
                timeZoneDifference = if (relativeOffset < 0) {
                    if (timeDiff.hour >= nativeTime.hour) {
                        TimeZoneDifference.Yesterday(
                            hour = timeDiff.hour - nativeTime.hour,
                            minute = timeDiff.minute
                        )
                    } else {
                        TimeZoneDifference.Today(
                            hour = timeDiff.hour,
                            minute = timeDiff.minute,
                            sign = -1
                        )
                    }
                } else {
                    if (timeDiff.hour >= 24) {
                        TimeZoneDifference.Tomorrow(
                            hour = timeDiff.hour + nativeTime.hour - 24,
                            minute = timeDiff.minute
                        )
                    } else {
                        TimeZoneDifference.Today(
                            hour = timeDiff.hour,
                            minute = timeDiff.minute,
                            sign = +1
                        )
                    }
                }
            }
            return time
        }
    }
}

sealed class TimeZoneDifference(
    val hour: Int,
    val minute: Int,
    val sign: Int
) {
    object Zero : TimeZoneDifference(hour = 0, minute = 0, sign = 0)
    class Yesterday(hour: Int, minute: Int) :
        TimeZoneDifference(hour = hour, minute = minute, sign = -1)

    class Today(hour: Int, minute: Int, sign: Int) :
        TimeZoneDifference(hour = hour, minute = minute, sign = sign)

    class Tomorrow(hour: Int, minute: Int) :
        TimeZoneDifference(hour = hour, minute = minute, sign = +1)
}

data class Date(
    val day: String,
    val month: String,
    val date: Int
) {
    companion object {
        val Unspecified = Date(day = "Monday", month = "January", date = 1)

        fun now(locale: Locale): Date {
            val zoneTime = ZonedDateTime.now()
            return Date(
                day = zoneTime.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                month = zoneTime.month.getDisplayName(TextStyle.SHORT, locale),
                date = zoneTime.dayOfMonth
            )
        }
    }
}

data class Lap(
    val number: Int,
    var duration: Long = 0,
    var lapTime: Long = 0
)