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

package com.flamingo.clock.ui.states

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.provider.MediaStore

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

import com.flamingo.clock.R
import com.flamingo.clock.data.settings.SettingsRepository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import org.koin.androidx.compose.get

class TimerSoundScreenState(
    private val settingsRepository: SettingsRepository,
    private val coroutineScope: CoroutineScope,
    private val context: Context
) {

    private val ringtoneManager = RingtoneManager(context).apply {
        setType(RingtoneManager.TYPE_ALARM)
    }

    private val _deviceAudio = MutableStateFlow<List<Audio>>(emptyList())
    val deviceAudio: StateFlow<List<Audio>> = _deviceAudio.asStateFlow()

    private val _userAudio = MutableStateFlow<List<UserAudio>>(emptyList())
    val userAudio: StateFlow<List<UserAudio>> = _userAudio.asStateFlow()

    val selectedAudio: Flow<Audio?> = deviceAudio.combine(userAudio) { deviceAudio, userAudio ->
        mutableListOf<Audio>().apply {
            addAll(deviceAudio)
            addAll(userAudio)
        }.toList()
    }.combine(settingsRepository.timerSoundUri) { audios, uri ->
        audios.find { it.uri == uri }
    }.flowOn(Dispatchers.Default)

    private var currentPlayer: MediaPlayerWrapper? = null
    var nowPlayingSound by mutableStateOf<Audio?>(null)
        private set

    init {
        coroutineScope.launch(Dispatchers.IO) {
            launch {
                loadDeviceSounds()
            }
            settingsRepository.userSoundUris.collect {
                loadUserSounds(it)
            }
        }
    }

    private fun loadUserSounds(uris: List<Uri>) {
        _userAudio.value = mutableListOf<UserAudio>().apply {
            uris.forEach { uri ->
                val trackName = getTrackName(uri) ?: return@forEach
                add(UserAudio(title = trackName, uri = uri))
            }
        }.toList()
    }

    private fun loadDeviceSounds() {
        val ringtones =
            mutableListOf<Audio>(Audio.Silent(context.getString(R.string.silent)))
        ringtoneManager.cursor.use { cursor ->
            cursor.moveToFirst()
            do {
                val ringtoneInfo = DeviceAudio(
                    uri = ringtoneManager.getRingtoneUri(cursor.position),
                    title = ringtoneManager.getRingtone(cursor.position).getTitle(context)
                )
                val ringtoneWithNameExists = ringtones.filterIsInstance<DeviceAudio>()
                    .any { it.title == ringtoneInfo.title }
                if (!ringtoneWithNameExists) {
                    ringtones.add(ringtoneInfo)
                }
            } while (cursor.moveToNext())
        }
        _deviceAudio.value = ringtones.toList()
    }

    private fun getTrackName(uri: Uri): String? {
        return context.contentResolver.query(
            uri,
            arrayOf(MediaStore.Audio.AudioColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use {
            it.moveToFirst()
            it.getString(0).substringBeforeLast('.')
        }
    }

    fun setAsTimerSoundAndPlay(audio: Audio) {
        coroutineScope.launch {
            disposeCurrentPlayer()
            settingsRepository.setTimerSound(audio.uri)
            when (audio) {
                is Audio.Silent -> {}
                is DeviceAudio, is UserAudio -> withContext(Dispatchers.IO) {
                    currentPlayer = MediaPlayerWrapper(context, audio.uri)
                    startCurrentPlayer()
                }
            }
        }
    }

    fun saveUserSound(uri: Uri) {
        coroutineScope.launch(Dispatchers.Default) {
            if (!context.contentResolver.persistedUriPermissions.any { it.uri == uri }) {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            if (_userAudio.value.any { it.uri == uri }) return@launch
            settingsRepository.addUserSound(uri)
        }
    }

    private fun startCurrentPlayer() {
        currentPlayer?.let { mpw ->
            mpw.start()
            val nowPlayingSound = _deviceAudio.value.find { it.uri == mpw.uri }
                ?: _userAudio.value.find { it.uri == mpw.uri }
            if (nowPlayingSound != null) {
                this.nowPlayingSound = nowPlayingSound
            }
        }
    }

    private fun disposeCurrentPlayer() {
        currentPlayer?.let {
            it.stop()
            it.reset()
            it.release()
        }
        nowPlayingSound = null
        currentPlayer = null
    }

    internal fun dispose() {
        disposeCurrentPlayer()
    }
}

sealed interface Audio {
    val title: String
    val uri: Uri
    val iconRes: Int

    data class Silent(
        override val title: String,
        override val uri: Uri = Uri.EMPTY,
        @DrawableRes override val iconRes: Int = R.drawable.outline_ringer_mute_24
    ) : Audio
}

data class DeviceAudio(
    override val title: String,
    override val uri: Uri,
    @DrawableRes override val iconRes: Int = R.drawable.outline_ringer_24
) : Audio

data class UserAudio(
    override val title: String,
    override val uri: Uri,
    @DrawableRes override val iconRes: Int = R.drawable.baseline_library_music_24
) : Audio

class MediaPlayerWrapper(
    context: Context,
    val uri: Uri
) {

    private val mediaPlayer: MediaPlayer

    init {
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()
            )
            setDataSource(context, uri)
            prepare()
        }
    }

    fun start() {
        if (mediaPlayer.isPlaying) return
        mediaPlayer.apply {
            if (!isLooping) isLooping = true
            start()
        }
    }

    fun reset() {
        mediaPlayer.reset()
    }

    fun stop() {
        if (!mediaPlayer.isPlaying) return
        mediaPlayer.apply {
            stop()
            if (isLooping) isLooping = false
        }
    }

    fun release() {
        mediaPlayer.release()
    }
}

@Composable
fun rememberTimerSoundScreenState(
    context: Context = LocalContext.current,
    settingsRepository: SettingsRepository = get(),
    coroutineScope: CoroutineScope = rememberCoroutineScope()
): TimerSoundScreenState {
    val state = remember(context, settingsRepository, coroutineScope) {
        TimerSoundScreenState(
            context = context,
            settingsRepository = settingsRepository,
            coroutineScope = coroutineScope
        )
    }
    DisposableEffect(state) {
        onDispose {
            state.dispose()
        }
    }
    return state
}