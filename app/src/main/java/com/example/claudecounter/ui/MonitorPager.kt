package com.example.claudecounter.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.claudecounter.ui.theme.StickColors
import com.example.claudecounter.ui.theme.StickDimens

/**
 * Swipeable tile carousel + bottom nav dots. Matches dots()/tileview navigation
 * in the firmware — one page per tile (Agora / Modelos / Trend / Heat).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MonitorPager(
    pageCount: Int,
    modifier: Modifier = Modifier,
    pagerState: PagerState = rememberPagerState(pageCount = { pageCount }),
    page: @Composable (index: Int) -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { index ->
            page(index)
        }
        NavDots(
            count = pageCount,
            activeIndex = pagerState.currentPage,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp)
        )
    }
}

@Composable
private fun NavDots(count: Int, activeIndex: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 0 until count) {
                if (i == activeIndex) {
                    Box(
                        Modifier
                            .size(width = StickDimens.DotActiveWidth, height = StickDimens.DotSize)
                            .clip(RoundedCornerShape(4.dp))
                            .background(StickColors.Accent)
                    )
                } else {
                    Box(
                        Modifier
                            .size(StickDimens.DotSize)
                            .clip(CircleShape)
                            .background(StickColors.Border)
                    )
                }
            }
        }
    }
}
