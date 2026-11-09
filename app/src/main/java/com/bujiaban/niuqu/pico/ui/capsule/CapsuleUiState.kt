package com.bujiaban.niuqu.pico.ui.capsule

import com.bujiaban.niuqu.pico.domain.model.*

data class CapsuleUiState(val game:CapsuleState=CapsuleState(),val definitions:List<ToyDefinition> = emptyList(),val series:List<ToySeries> = emptyList(),val clawControls:ClawControls=ClawControls(),val flow:ExperienceFlow=ExperienceFlow())

sealed interface CapsuleUiEvent {
    data class SelectMode(val mode:ExperienceMode):CapsuleUiEvent
    data class SelectSeries(val index:Int):CapsuleUiEvent
}
