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

import android.os.Parcelable
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarScrollState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

import com.flamingo.clock.R
import com.flamingo.clock.ui.ContentOrientation
import com.flamingo.clock.ui.NavigationType

import kotlinx.parcelize.Parcelize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    navigationType: NavigationType,
    orientation: ContentOrientation,
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val pages = rememberSaveable { listOf(Page.Alarm, Page.Clock, Page.Timer, Page.Stopwatch) }
    var selectedPage by rememberSaveable { mutableStateOf(pages.first()) }
    val pageSwitchCallback by rememberUpdatedState(newValue = { page: Page ->
        selectedPage = page
    })
    val scrollState = rememberTopAppBarScrollState()
    val topAppBarScrollBehavior =
        remember(scrollState) { TopAppBarDefaults.pinnedScrollBehavior(scrollState) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopBar(
                labelId = selectedPage.labelId,
                topAppBarScrollBehavior = topAppBarScrollBehavior
            )
        },
    ) { padding ->
        when (navigationType) {
            NavigationType.BottomBar -> {
                MainScreenWithBottomBar(
                    pages = pages,
                    selectedPage = selectedPage,
                    onPageSwitchRequest = pageSwitchCallback,
                    orientation = orientation,
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    navController = navController,
                    nestedScrollConnection = topAppBarScrollBehavior.nestedScrollConnection
                )
            }
            NavigationType.SideRail -> {
                MainScreenWithSideRail(
                    pages = pages,
                    selectedPage = selectedPage,
                    onPageSwitchRequest = pageSwitchCallback,
                    orientation = orientation,
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    navController = navController,
                    nestedScrollConnection = topAppBarScrollBehavior.nestedScrollConnection
                )
            }
            NavigationType.PermanentDrawer -> {
                MainScreenWithPermanentDrawer(
                    pages = pages,
                    selectedPage = selectedPage,
                    onPageSwitchRequest = pageSwitchCallback,
                    orientation = orientation,
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    navController = navController,
                    nestedScrollConnection = topAppBarScrollBehavior.nestedScrollConnection
                )
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun TopBar(
    labelId: Int,
    topAppBarScrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier
) {
    SmallTopAppBar(
        modifier = modifier,
        title = {
            AnimatedContent(targetState = labelId, modifier = Modifier.padding(start = 8.dp)) {
                Text(text = stringResource(it))
            }
        },
        actions = {
            IconButton(onClick = { /*TODO*/ }) {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = stringResource(id = R.string.menu_button_content_desc)
                )
            }
        },
        colors = TopAppBarDefaults.smallTopAppBarColors(
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
        ),
        scrollBehavior = topAppBarScrollBehavior
    )
}

@Composable
fun MainScreenWithBottomBar(
    pages: List<Page>,
    selectedPage: Page,
    onPageSwitchRequest: (Page) -> Unit,
    orientation: ContentOrientation,
    navController: NavHostController,
    nestedScrollConnection: NestedScrollConnection,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        MainScreenContent(
            selectedPage = selectedPage,
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            orientation = orientation,
            navController = navController,
            nestedScrollConnection = nestedScrollConnection
        )
        NavigationBar(modifier = Modifier.fillMaxWidth()) {
            pages.forEach {
                NavigationBarItem(
                    selected = it == selectedPage,
                    onClick = {
                        onPageSwitchRequest(it)
                    },
                    icon = {
                        Icon(
                            painter = painterResource(id = it.iconId),
                            contentDescription = stringResource(
                                id = R.string.nav_bar_button_content_desc,
                                it.toString()
                            )
                        )
                    },
                    label = {
                        Text(text = stringResource(id = it.labelId))
                    }
                )
            }
        }
    }
}

@Composable
fun MainScreenWithSideRail(
    pages: List<Page>,
    selectedPage: Page,
    onPageSwitchRequest: (Page) -> Unit,
    orientation: ContentOrientation,
    navController: NavHostController,
    nestedScrollConnection: NestedScrollConnection,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavigationRail(modifier = Modifier.fillMaxHeight()) {
            pages.forEach {
                NavigationRailItem(
                    selected = it == selectedPage,
                    onClick = {
                        onPageSwitchRequest(it)
                    },
                    icon = {
                        Icon(
                            painter = painterResource(id = it.iconId),
                            contentDescription = stringResource(
                                id = R.string.nav_bar_button_content_desc,
                                it.toString()
                            )
                        )
                    }
                )
            }
        }
        MainScreenContent(
            selectedPage = selectedPage,
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            orientation = orientation,
            navController = navController,
            nestedScrollConnection = nestedScrollConnection
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenWithPermanentDrawer(
    pages: List<Page>,
    selectedPage: Page,
    onPageSwitchRequest: (Page) -> Unit,
    orientation: ContentOrientation,
    navController: NavHostController,
    nestedScrollConnection: NestedScrollConnection,
    modifier: Modifier = Modifier
) {
    PermanentNavigationDrawer(
        modifier = modifier,
        drawerContent = {
            pages.forEach {
                NavigationDrawerItem(
                    selected = it == selectedPage,
                    onClick = {
                        onPageSwitchRequest(it)
                    },
                    icon = {
                        Icon(
                            painter = painterResource(id = it.iconId),
                            contentDescription = stringResource(
                                id = R.string.nav_bar_button_content_desc,
                                it.toString()
                            )
                        )
                    },
                    label = {
                        Text(text = stringResource(id = it.labelId))
                    }
                )
            }
        }
    ) {
        MainScreenContent(
            selectedPage = selectedPage,
            modifier = Modifier.fillMaxSize(),
            orientation = orientation,
            navController = navController,
            nestedScrollConnection = nestedScrollConnection
        )
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MainScreenContent(
    selectedPage: Page,
    orientation: ContentOrientation,
    navController: NavHostController,
    nestedScrollConnection: NestedScrollConnection,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = selectedPage,
        modifier = modifier.fillMaxSize()
    ) {
        when (it) {
            Page.Alarm -> AlarmScreen()
            Page.Clock -> ClockScreen(
                orientation = orientation,
                modifier = Modifier.fillMaxSize(),
                navController = navController,
                nestedScrollConnection = nestedScrollConnection
            )
            Page.Timer -> TimerScreen()
            Page.Stopwatch -> StopwatchScreen(
                orientation = orientation,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Parcelize
sealed class Page(@StringRes val labelId: Int, @DrawableRes val iconId: Int) : Parcelable {
    object Alarm : Page(labelId = R.string.alarm, iconId = R.drawable.baseline_alarm_24)
    object Clock : Page(labelId = R.string.clock, iconId = R.drawable.baseline_clock_24)
    object Timer : Page(labelId = R.string.timer, iconId = R.drawable.baseline_timer_24)
    object Stopwatch : Page(labelId = R.string.stopwatch, iconId = R.drawable.outline_stopwatch_24)
}
