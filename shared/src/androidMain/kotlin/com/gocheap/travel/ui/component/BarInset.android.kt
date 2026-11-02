package com.gocheap.travel.ui.component

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

/**
 * The whole inset, unmodified. Under three-button navigation this is the height of the button
 * strip itself, and trimming it would slide the tabs in behind Back, Home and Recents.
 */
@Composable
internal actual fun bottomBarInset(): Dp =
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
