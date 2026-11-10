package com.bujiaban.niuqu.pico.ui.capsule

import com.bujiaban.niuqu.pico.domain.model.*

data class CapsuleUiState(val game:CapsuleState=CapsuleState(),val definitions:List<ToyDefinition> = emptyList(),val series:List<ToySeries> = emptyList(),val clawControls:ClawControls=ClawControls(),val flow:ExperienceFlow=ExperienceFlow(),
    val clawLearned:Boolean=false,
    val leftGrip:Boolean=false,val rightGrip:Boolean=false,val openingProgress:Float=0f,val trackingPaused:Boolean=false,
    val studyTime:Float=0f,val studyRunning:Boolean=false)

sealed interface CapsuleUiEvent {
    data class SelectMode(val mode:ExperienceMode):CapsuleUiEvent
    data class SelectSeries(val index:Int):CapsuleUiEvent
}
