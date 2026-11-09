package com.bujiaban.niuqu.pico

import com.bujiaban.niuqu.pico.ui.capsule.CapsuleScreen
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultWindowContainer
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope

fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultWindowContainer {
            PicoTheme {
                CapsuleScreen()
            }
        }
    }
