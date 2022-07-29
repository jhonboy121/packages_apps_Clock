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

package com.flamingo.clock.data.tz

import android.content.Context
import android.util.Log

import com.flamingo.clock.R

import java.io.InputStream

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

import org.json.JSONArray
import org.json.JSONObject

private const val CITY_TIMEZONES_FILE = "city_timezones.json"
private const val TAG = "CityTimeZones"

private const val KEY_CITY = "city"
private const val KEY_COUNTRY = "country"
private const val KEY_TIMEZONE = "timezone"

class CityTimeZonesData(private val context: Context) {

    private val cacheMutex = Mutex()
    private val cityTimezoneCache = mutableListOf<CityTimeZone>()

    suspend fun getAllTimeZones(): Result<List<CityTimeZone>> {
        if (cacheMutex.isLocked) return Result.failure(Throwable(context.getString(R.string.resource_locked)))
        return cacheMutex.withLock {
            if (cityTimezoneCache.isEmpty()) {
                populateCacheLocked().onFailure {
                    return@withLock Result.failure(it)
                }
            }
            withContext(Dispatchers.Default) {
                Result.success(cityTimezoneCache.toList())
            }
        }
    }

    suspend fun findCityTimezoneInfo(keyword: String?): Result<List<CityTimeZone>> {
        if (cacheMutex.isLocked) return Result.failure(Throwable(context.getString(R.string.resource_locked)))
        return cacheMutex.withLock {
            withContext(Dispatchers.Default) {
                if (cityTimezoneCache.isEmpty()) {
                    populateCacheLocked().onFailure {
                        return@withContext Result.failure(it)
                    }
                }
                return@withContext Result.success(
                    keyword?.takeIf { it.isNotBlank() }?.let {
                        cityTimezoneCache.filter {
                            it.city.contains(keyword, ignoreCase = true)
                        }
                    } ?: cityTimezoneCache.toList()
                )
            }
        }
    }

    private suspend fun populateCacheLocked() =
        withContext(Dispatchers.IO) {
            val result = context.assets.openFd(CITY_TIMEZONES_FILE).use { fd ->
                fd.createInputStream().use {
                    parseCityTimeZoneInfo(it)
                }
            }.onFailure {
                Log.e(TAG, "Failed to parse city timezone info", it)
                return@withContext Result.failure(it)
            }
            cityTimezoneCache.addAll(result.getOrThrow())
            return@withContext Result.success(Unit)
        }

    private fun parseCityTimeZoneInfo(source: InputStream): Result<List<CityTimeZone>> =
        runCatching {
            val list = mutableListOf<CityTimeZone>()
            val json = source.bufferedReader().use {
                JSONArray(it.readText())
            }
            for (i in 0 until json.length()) {
                val info = json[i] as JSONObject
                list.add(
                    CityTimeZone.newBuilder()
                        .setCity(info.getString(KEY_CITY))
                        .setCountry(info.getString(KEY_COUNTRY))
                        .setTimezone(info.getString(KEY_TIMEZONE))
                        .build()
                )
            }
            list.toList()
        }
}