package com.example.claudecounter.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Geometry constants ported from the firmware layout (480x320 logical canvas).
 * See tools/gen_mockups.py for the coordinates these were lifted from.
 */
object StickDimens {
    /** The firmware's LCD is 480x320 (landscape). All tile layouts are authored against this. */
    val CanvasWidth = 480.dp
    val CanvasHeight = 320.dp

    /**
     * Cap on the logical width [MonitorScaffold] will stretch landscape content
     * to on screens wider than 480x320's 1.5:1 aspect (most modern phones —
     * e.g. an iPhone Pro Max is ~926x428dp landscape, aspect ~2.16:1). Without a
     * cap, a tablet/foldable landscape would stretch the tiles out to the point
     * the layout looks sparse rather than filled; this bounds the fill to
     * "large phone", which is what was actually asked for.
     */
    val CanvasWidthMax = 720.dp

    val CardRadius = 18.dp
    val ChipRadius = 12.dp
    val PillRadius = 10.dp
    val PeriodPillRadius = 15.dp

    val HeaderHeight = 40.dp
    val RefreshBarHeight = 3.dp
    val ContentTop = 46.dp

    val CardPadding = 14.dp

    // Segment meter (18 segments)
    val SegmentWidth = 8.dp
    val SegmentHeight = 16.dp
    val SegmentRadius = 2.dp
    val SegmentStep = 11.dp
    val SegmentCount = 18

    // Nav dots
    val DotSize = 8.dp
    val DotStep = 18.dp
    val DotActiveWidth = 18.dp

    // Header buttons
    val RefreshButtonSize = 56.dp
    val SettingsButtonWidth = 78.dp
    val HeaderButtonHeight = 40.dp

    // Heatmap bars
    val HeatBarWidth = 13.dp
    val HeatBarStep = 18.dp
    val HeatBarMaxHeight = 114.dp
}
