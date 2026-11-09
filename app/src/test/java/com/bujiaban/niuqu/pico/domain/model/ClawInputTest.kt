package com.bujiaban.niuqu.pico.domain.model

import com.bujiaban.niuqu.pico.domain.usecase.CapsuleUseCase
import com.bujiaban.niuqu.pico.domain.usecase.MemoryCapsuleRepository
import com.bujiaban.niuqu.pico.ui.capsule.CapsuleViewModel
import com.bujiaban.niuqu.pico.ui.capsule.MemoryOnboardingPreferences
import org.junit.Assert.*
import org.junit.Test

class ClawInputTest {
    private fun vm(credit:Boolean=true)=CapsuleViewModel(CapsuleUseCase(MemoryCapsuleRepository()){0f},MemoryOnboardingPreferences()).also{
        it.onEvent(CapsuleEvent.Loaded);it.play();it.onEvent(CapsuleEvent.Series(2))
        if(credit)it.clawAuxiliary()
    }
    @Test fun dragIsRelativeAndTicksNeverMoveTheCrane(){
        val v=vm();v.beginClawAim();v.dragClawAim(-.2f,-.3f)
        val moved=v.state.value.game
        assertEquals(.3f,moved.clawX,.00001f);assertEquals(.18f,moved.clawZ,.00001f)
        repeat(20){v.onEvent(CapsuleEvent.Tick(.1f))}
        assertEquals(moved.clawX,v.state.value.game.clawX)
        v.endClawAim();v.dragClawAim(.2f,.2f)
        assertEquals(moved.clawX,v.state.value.game.clawX)
        assertEquals(CapsulePhase.CREDITED,v.state.value.game.phase)
        v.beginClawAim();v.dragClawAim(.1f,0f);v.endClawAim()
        assertEquals(.4f,v.state.value.game.clawX,.00001f)
    }
    @Test fun dropAndAuxiliaryNeverSubstituteForEachOther(){
        val v=vm(false);v.pulseClawPrimary()
        assertEquals(CapsulePhase.COIN_READY,v.state.value.game.phase)
        v.clawAuxiliary();v.clawAuxiliary()
        assertEquals(CapsulePhase.CREDITED,v.state.value.game.phase)
        v.beginClawAim();v.pulseClawPrimary()
        assertEquals(CapsulePhase.CREDITED,v.state.value.game.phase)
        v.endClawAim();v.pulseClawPrimary()
        assertEquals(CapsulePhase.CLAWING,v.state.value.game.phase)
        repeat(5){v.pulseClawPrimary()}
        v.onEvent(CapsuleEvent.Tick(CLAW_DURATION))
        assertEquals(23,v.state.value.game.stock)
        v.pulseClawPrimary()
        assertEquals(CapsulePhase.CAPSULE_READY,v.state.value.game.phase)
        v.clawAuxiliary();assertEquals(CapsulePhase.CAPSULE_TAKEN,v.state.value.game.phase)
    }
    @Test fun cancellationAndModeChangesRequireANewDrag(){
        val v=vm();v.beginClawAim();v.clearClawInputs();v.dragClawAim(-1f,-1f)
        assertEquals(.5f,v.state.value.game.clawX)
        v.beginClawAim();v.onEvent(CapsuleEvent.Mode(ExperienceMode.STUDY))
        v.onEvent(CapsuleEvent.Mode(ExperienceMode.PLAY));v.dragClawAim(-1f,-1f)
        assertEquals(.5f,v.state.value.game.clawX);assertFalse(v.state.value.clawControls.aiming)
        v.beginClawAim();v.dragClawAim(-1f,-1f)
        assertEquals(-.5f,v.state.value.game.clawX)
    }
    @Test fun boundsInvalidInputAndInactiveStateAreSafe(){
        val v=vm();v.beginClawAim();v.dragClawAim(Float.NaN,1f)
        assertEquals(.5f,v.state.value.game.clawX);assertEquals(.48f,v.state.value.game.clawZ)
        v.dragClawAim(100f,100f)
        assertEquals(.7f,v.state.value.game.clawX);assertEquals(.65f,v.state.value.game.clawZ)
        v.dragClawAim(-100f,-100f)
        assertEquals(-.7f,v.state.value.game.clawX);assertEquals(-.65f,v.state.value.game.clawZ)
        val inactive=vm(false);inactive.beginClawAim();inactive.dragClawAim(-1f,-1f)
        assertEquals(.5f,inactive.state.value.game.clawX)
    }
    @Test fun previewMatchesActualGrabAcrossCabinetAndDepletedStock(){
        val useCase=CapsuleUseCase(MemoryCapsuleRepository()){0f}
        for(available in listOf(CLAW_SLOTS.indices.toSet(),(0..15).toSet(),setOf(0,5,17),emptySet())){
            for(x in -7..7)for(z in -6..6){
                val s=CapsuleState(series=2,ready=true,phase=CapsulePhase.CREDITED,clawAvailable=available,clawX=x*.1f,clawZ=z*.1f)
                assertEquals(s.clawAimTarget(),useCase.reduce(s,CapsuleEvent.ClawGrab).clawTarget)
            }
        }
        val emptyCentre=CapsuleState(clawX=0f,clawZ=0f)
        assertNull(emptyCentre.clawAimTarget())
        assertEquals(16,emptyCentre.copy(clawX=-.4f,clawZ=-.4f).clawAimTarget())
        assertNull(emptyCentre.copy(clawAvailable=emptySet()).clawAimTarget())
    }
}
