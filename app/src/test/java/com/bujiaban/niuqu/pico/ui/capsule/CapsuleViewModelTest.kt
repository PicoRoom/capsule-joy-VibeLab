package com.bujiaban.niuqu.pico.ui.capsule

import com.bujiaban.niuqu.pico.domain.model.*
import com.bujiaban.niuqu.pico.domain.usecase.*
import org.junit.Assert.*
import org.junit.Test

class CapsuleViewModelTest {
    private fun vm()=CapsuleViewModel(CapsuleUseCase(MemoryCapsuleRepository()){0f},MemoryOnboardingPreferences())
    @Test fun startsWithThreeSeriesAndSixToys(){val v=vm();assertEquals(3,v.state.value.series.size);assertEquals(6,v.state.value.definitions.size);assertFalse(v.state.value.game.ready)}
    @Test fun acceptsCoinAfterModelsLoad(){val v=vm();v.onEvent(CapsuleEvent.Loaded);v.play();v.onEvent(CapsuleEvent.CoinDropped(.01f));assertEquals(CapsulePhase.CREDITED,v.state.value.game.phase)}
    @Test fun rejectsPurchaseBeforeModelsLoad(){val v=vm();v.onEvent(CapsuleEvent.CoinDropped(.01f));assertEquals(CapsulePhase.COIN_READY,v.state.value.game.phase)}
    @Test fun modeAndSeriesAreTypedEvents(){val v=vm();v.onEvent(CapsuleEvent.Loaded);v.play();v.onEvent(CapsuleEvent.Series(2));assertEquals(2,v.state.value.game.series);v.onEvent(CapsuleEvent.Mode(ExperienceMode.STUDY));v.onEvent(CapsuleEvent.Series(0));assertEquals(2,v.state.value.game.series);assertEquals(ExperienceMode.STUDY,v.state.value.game.mode)}
}
