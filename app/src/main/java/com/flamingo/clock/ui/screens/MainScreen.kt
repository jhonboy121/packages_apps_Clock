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

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallTopAppBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource

import com.flamingo.clock.R
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState

import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPagerApi::class)
@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val pages = remember { listOf(Page.Alarm, Page.Clock, Page.Timer, Page.Stopwatch) }
    val pagerState = rememberPagerState()
    val currentPage by remember {
        derivedStateOf {
            pages[pagerState.currentPage]
        }
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopBar(labelId = currentPage.labelId)
        },
        bottomBar = {
            val coroutineScope = rememberCoroutineScope()
            BottomNavigationBar(
                selectedIndex = pagerState.currentPage,
                items = pages,
                onPageSwitchRequest = {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(it)
                    }
                }
            )
        }
    ) { padding ->
        HorizontalPager(
            modifier = Modifier.padding(padding),
            count = pages.size,
            state = pagerState
        ) {
            when (currentPage) {
                Page.Alarm -> AlarmScreen()
                Page.Clock -> ClockScreen()
                Page.Timer -> TimerScreen()
                Page.Stopwatch -> StopwatchScreen()
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun TopBar(labelId: Int, modifier: Modifier = Modifier) {
    SmallTopAppBar(
        modifier = modifier,
        title = {
            AnimatedContent(targetState = labelId) {
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
        }
    )
}

@Composable
fun BottomNavigationBar(
    items: List<Page>,
    selectedIndex: Int,
    onPageSwitchRequest: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(modifier = modifier) {
        items.forEachIndexed { index, page ->
            NavigationBarItem(
                selected = index == selectedIndex,
                onClick = {
                    onPageSwitchRequest(index)
                },
                icon = {
                    Icon(
                        painter = painterResource(id = page.iconId),
                        contentDescription = stringResource(
                            id = R.string.item_alarm_content_desc,
                            page.toString()
                        )
                    )
                },
                label = {
                    Text(text = stringResource(id = page.labelId))
                }
            )
        }
    }
}

sealed class Page(@StringRes val labelId: Int, @DrawableRes val iconId: Int) {
    object Alarm : Page(labelId = R.string.alarm, iconId = R.drawable.baseline_alarm_24)
    object Clock : Page(labelId = R.string.clock, iconId = R.drawable.baseline_clock_24)
    object Timer : Page(labelId = R.string.timer, iconId = R.drawable.baseline_timer_24)
    object Stopwatch : Page(labelId = R.string.stopwatch, iconId = R.drawable.outline_stopwatch_24)
}
