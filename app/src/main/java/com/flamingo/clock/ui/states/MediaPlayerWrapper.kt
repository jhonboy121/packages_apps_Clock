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
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri

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