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

package com.flamingo.clock.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

import com.flamingo.clock.R
import com.flamingo.clock.data.getPrependedString
import com.flamingo.clock.data.settings.DEFAULT_TIME_FORMAT
import com.flamingo.clock.data.settings.TimeFormat
import com.flamingo.clock.ui.states.AddCityTimeScreenState
import com.flamingo.clock.ui.states.CityTime
import com.flamingo.clock.ui.states.rememberAddCityTimeScreenState

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalComposeUiApi::class
)
@Composable
fun AddCityTimeScreen(
    navController: NavHostController,
    isEnterAnimationRunning: Boolean,
    modifier: Modifier = Modifier,
    state: AddCityTimeScreenState = rememberAddCityTimeScreenState()
) {
    var searchText by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(searchText, isEnterAnimationRunning) {
        if (!isEnterAnimationRunning) {
            state.findCityTime(searchText)
        }
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            val softwareKeyboardController = LocalSoftwareKeyboardController.current
            SearchBar(
                modifier = Modifier.fillMaxWidth(),
                onBackPressed = {
                    navController.popBackStack()
                },
                searchText = searchText,
                onTextUpdate = {
                    searchText = it
                },
                onClearRequest = {
                    searchText = null
                    softwareKeyboardController?.hide()
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            val dividerHeight = with(LocalDensity.current) { 1f.toDp() }
            if (isEnterAnimationRunning) {
                LinearProgressIndicator(modifier = Modifier.height(1.dp))
            } else {
                Divider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(dividerHeight)
                )
            }
            val cityTimesList by state.cityTimes.collectAsState(emptyList())
            val timeFormat by state.timeFormat.collectAsState(initial = DEFAULT_TIME_FORMAT)
            val bottomNavigationBarPadding =
                with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = bottomNavigationBarPadding)
            ) {
                items(cityTimesList, key = { it.city + it.country + it.timezone }) {
                    CityTimeItem(
                        cityTime = it,
                        timeFormat = timeFormat,
                        modifier = Modifier
                            .animateItemPlacement()
                            .fillMaxWidth(),
                        onSelected = {
                            state.saveCityTime(it)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(
    searchText: String?,
    onTextUpdate: (String?) -> Unit,
    onBackPressed: () -> Unit,
    onClearRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    SmallTopAppBar(
        modifier = modifier,
        title = {
            TextField(
                modifier = Modifier.fillMaxWidth(),
                value = searchText ?: "",
                placeholder = {
                    Text(text = stringResource(id = R.string.search_for_a_city))
                },
                singleLine = true,
                colors = TextFieldDefaults.textFieldColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                onValueChange = onTextUpdate,
                trailingIcon = {
                    if (searchText?.isNotBlank() == true) {
                        IconButton(onClick = onClearRequest) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = stringResource(id = R.string.clear_button_content_desc)
                            )
                        }
                    }
                },
                keyboardActions = KeyboardActions(
                    onSearch = {
                        onTextUpdate(searchText)
                    },
                    onDone = {
                        onTextUpdate(searchText)
                    },
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
            )
        },
        navigationIcon = {
            IconButton(onClick = onBackPressed, modifier = Modifier.then(Modifier.size(36.dp))) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = stringResource(id = R.string.add_city_time_back_button_content_desc)
                )
            }
        },
    )
}

@Composable
private fun CityTimeItem(
    cityTime: CityTime,
    timeFormat: TimeFormat,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.clickable(onClick = onSelected)) {
        Row(
            modifier = Modifier
                .padding(start = 60.dp, end = 24.dp, top = 12.dp, bottom = 12.dp)
                .fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${cityTime.city}, ${cityTime.country}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.75f)
            )
            when (timeFormat) {
                TimeFormat.TWELVE_HOUR -> {
                    val hour = cityTime.time.withTwelveHourFormat().hour
                    val minute = getPrependedString(cityTime.time.minute)
                    val amPMString = if (cityTime.time.hour >= 12) "PM" else "AM"
                    Text(
                        text = "$hour:$minute $amPMString",
                        maxLines = 1,
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.25f)
                    )
                }
                TimeFormat.TWENTY_FOUR_HOUR -> {
                    val minute = getPrependedString(cityTime.time.minute)
                    Text(
                        text = "${cityTime.time.hour}:$minute",
                        maxLines = 1,
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
                else -> throw IllegalArgumentException("Unknown time format $timeFormat")
            }
        }
    }
}