package com.example.jetsoncontroller.ui.components.geo.shell

import kotlin.math.max

fun stickyAction(
    viewportDp: Float,
    topChromeDp: Float,
    bottomChromeDp: Float,
    imeDp: Float,
    actionDp: Float,
    scene: Boolean = false,
    compactHeight: Boolean = viewportDp < 480f,
    inlineForIme: Boolean = false
): Boolean = scene || ((!inlineForIme || imeDp <= 0f) && !compactHeight &&
    viewportDp - topChromeDp - bottomChromeDp - imeDp - actionDp >= 280f)

fun retainedActionHeight(previousDp: Float, measuredDp: Float): Float = max(previousDp, measuredDp)

