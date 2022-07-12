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

const val HOUR = 1000 * 60 * 60
const val MINUTE = 1000 * 60
const val SECOND = 1000

data class Time(
    val hours: Int,
    val minutes: Int,
    val seconds: Int,
    val milliseconds: Int
) {
    companion object {
        val Zero = Time(0, 0, 0, 0)

        fun fromTimeInMillis(time: Long): Time {
            val milliseconds = (time % SECOND) / 10
            val seconds = (time / SECOND) % 60
            val minutes = (time / MINUTE) % 60
            val hours = (time / HOUR) % 60
            return Time(
                hours = hours.toInt(),
                minutes = minutes.toInt(),
                seconds = seconds.toInt(),
                milliseconds = milliseconds.toInt()
            )
        }
    }
}

data class Lap(
    val number: Int,
    var duration: Long = 0,
    var lapTime: Long = 0
)