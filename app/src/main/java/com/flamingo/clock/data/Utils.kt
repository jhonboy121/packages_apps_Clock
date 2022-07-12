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

fun formatTime(
    time: Time,
    alwaysShowHours: Boolean = false,
    alwaysShowMinutes: Boolean = false,
    alwaysShowMillis: Boolean = false
) = (if (time.hours > 0 || alwaysShowHours) "${getPrependedString(time.hours)}:" else "") +
        (if (time.minutes > 0 || alwaysShowMinutes) "${getPrependedString(time.minutes)}:" else "") +
        getPrependedString(time.seconds) +
        (if (alwaysShowMillis) ".${getPrependedString(time.milliseconds)}" else "")

fun getPrependedString(time: Int) = if (time < 10) "0$time" else time.toString()