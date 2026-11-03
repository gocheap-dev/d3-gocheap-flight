package com.gocheap.flights.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gocheap.flights.ui.theme.FlightColors

/**
 * The shell every pushed page wears: a plain white page with its title across the top.
 *
 * The home tabs carry the colour — their blue hero is what marks them as the top of a stack. A
 * page pushed on top of one is a step *into* that search, so it steps back and lets its own
 * content be the only thing on it. There is no band, no tint and no edge to resolve: the title
 * bar and the content stand on the same white.
 *
 * [content] is a column running to the bottom of the screen. Give a list inside it `weight(1f)`
 * and anything after that sits against the bottom edge as a footer.
 */
@Composable
fun InnerPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    /** Room under the title bar. */
    titleGap: Dp = InnerTitleGap,
    /** Clearance before the content proper. Zero for a list that should run up to the bar. */
    contentTop: Dp = InnerContentTop,
    /**
     * For the pages that type. Applied inside the page so the white still runs to the bottom of
     * the screen behind the keyboard, and only the content is lifted clear of it.
     */
    imePadding: Boolean = false,
    surface: Color = FlightColors.Surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    InnerPageScaffold(
        modifier = modifier,
        titleGap = titleGap,
        contentTop = contentTop,
        imePadding = imePadding,
        surface = surface,
        header = {
            ScreenTopBar(title = title, onBack = onBack, action = action)
        },
        content = content,
    )
}

/**
 * [InnerPage] without the back bar, for the pages that head themselves — a tab at the root of the
 * stack has nowhere to go back to, but still wants the same page.
 */
@Composable
fun InnerPageScaffold(
    modifier: Modifier = Modifier,
    titleGap: Dp = InnerTitleGap,
    contentTop: Dp = InnerContentTop,
    imePadding: Boolean = false,
    surface: Color = FlightColors.Surface,
    header: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxSize().background(surface)) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                header()
                Spacer(Modifier.height(titleGap))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                // After the nav bar inset, so the keyboard only adds whatever that has not
                // already covered.
                .then(if (imePadding) Modifier.imePadding() else Modifier),
        ) {
            if (contentTop > 0.dp) Spacer(Modifier.height(contentTop))
            content()
        }
    }
}

private val InnerTitleGap = 4.dp

private val InnerContentTop = 8.dp
