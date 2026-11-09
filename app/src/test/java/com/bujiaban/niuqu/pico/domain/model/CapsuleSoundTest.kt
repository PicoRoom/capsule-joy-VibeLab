package com.bujiaban.niuqu.pico.domain.model

import com.bujiaban.niuqu.pico.domain.usecase.CapsuleUseCase
import com.bujiaban.niuqu.pico.domain.usecase.MemoryCapsuleRepository
import org.junit.Assert.*
import org.junit.Test

class CapsuleSoundTest {
    private val useCase=CapsuleUseCase(MemoryCapsuleRepository()){0f}
    private var game=CapsuleState(ready=true)
    private fun action(event:CapsuleEvent):List<CapsuleSound>{
        val next=useCase.reduce(game,event)
        return capsuleSounds(game,next).also{game=next}
    }
    @Test fun acceptedPurchaseSoundsFollowTheActualAnimationPhases(){
        assertEquals(listOf(CapsuleSound.COIN),action(CapsuleEvent.CoinDropped(0f)))
        repeat(3){assertEquals(listOf(CapsuleSound.CRANK_MOVE),action(CapsuleEvent.CrankMoved(90f)))}
        assertEquals(listOf(CapsuleSound.STOP_MECHANISM),action(CapsuleEvent.CrankMoved(90f)))
        assertTrue(action(CapsuleEvent.Tick(1f)).isEmpty())
        assertEquals(listOf(CapsuleSound.DROP),action(CapsuleEvent.Tick(.85f)))
        assertTrue(action(CapsuleEvent.Tick(.35f)).isEmpty())
        assertTrue(action(CapsuleEvent.CapsulePulled(.06f)).isEmpty())
        assertEquals(listOf(CapsuleSound.OPEN),action(CapsuleEvent.ShellsSeparated(.04f)))
        assertEquals(listOf(CapsuleSound.REVEAL),action(CapsuleEvent.Tick(1.4f)))
        assertEquals(listOf(CapsuleSound.COLLECT),action(CapsuleEvent.StoreToy(requireNotNull(game.activeToy))))
        assertTrue(action(CapsuleEvent.StoreAll).isEmpty())
    }
    @Test fun impactsCrossTheirAnimationMarkerOnceEvenWithSlowFrames(){
        for(series in 0..2){
            val phase=if(series==2)CapsulePhase.CLAWING else CapsulePhase.DISPENSING
            val time=listOf(1.8f,1.3f,5.12f)[series]
            val before=CapsuleState(ready=true,series=series,phase=phase,clawTarget=0,elapsed=time-.1f)
            val after=before.copy(elapsed=time+.1f)
            assertEquals(listOf(CapsuleSound.DROP),capsuleSounds(before,after))
            assertTrue(capsuleSounds(after,after.copy(elapsed=time+.2f)).isEmpty())
            assertEquals(1,capsuleSounds(before,before.copy(phase=CapsulePhase.CAPSULE_READY,elapsed=0f)).count{it==CapsuleSound.DROP})
            assertFalse(capsuleSounds(before.copy(mode=ExperienceMode.STUDY),after).contains(CapsuleSound.DROP))
        }
    }
    @Test fun rejectedOrRepeatedActionsStaySilent(){
        assertTrue(action(CapsuleEvent.CoinDropped(.5f)).isEmpty())
        assertTrue(action(CapsuleEvent.ClawGrab).isEmpty())
        action(CapsuleEvent.CoinDropped(0f))
        assertTrue(action(CapsuleEvent.CoinDropped(0f)).isEmpty())
        assertTrue(action(CapsuleEvent.CrankMoved(-10f)).isEmpty())
        assertTrue(action(CapsuleEvent.Refill).isEmpty())
    }
    @Test fun clawMissStopsMotorAndPlaysMissWithoutDrop(){
        action(CapsuleEvent.Series(2))
        action(CapsuleEvent.CoinDropped(0f))
        action(CapsuleEvent.ClawMoved(-.5f,-.48f))
        assertEquals(listOf(CapsuleSound.CLAW_LOOP),action(CapsuleEvent.ClawGrab))
        assertEquals(listOf(CapsuleSound.STOP_MECHANISM,CapsuleSound.MISS),action(CapsuleEvent.Tick(CLAW_DURATION)))
    }
    @Test fun studyStopsMotorAndReturnRestartsThePausedPhase(){
        game=game.copy(phase=CapsulePhase.CLAWING,series=2)
        assertEquals(listOf(CapsuleSound.STOP_MECHANISM,CapsuleSound.CLICK),action(CapsuleEvent.Mode(ExperienceMode.STUDY)))
        assertTrue(action(CapsuleEvent.Tick(10f)).isEmpty())
        assertEquals(listOf(CapsuleSound.CLAW_LOOP,CapsuleSound.CLICK),action(CapsuleEvent.Mode(ExperienceMode.PLAY)))
    }
    @Test fun studyStopsManualMotionAndIgnoresGestures(){
        action(CapsuleEvent.CoinDropped(0f))
        action(CapsuleEvent.CrankMoved(15f))
        assertEquals(listOf(CapsuleSound.STOP_MECHANISM,CapsuleSound.CLICK),action(CapsuleEvent.Mode(ExperienceMode.STUDY)))
        assertTrue(action(CapsuleEvent.CrankMoved(90f)).isEmpty())
    }
    @Test fun refillOnlySoundsWhenItActuallyAddsCapsules(){
        assertTrue(action(CapsuleEvent.Refill).isEmpty())
        game=game.copy(stock=23)
        assertEquals(listOf(CapsuleSound.REFILL),action(CapsuleEvent.Refill))
    }
    @Test fun stationaryClawDoesNotKeepNudgingTheMotor(){
        game=game.copy(series=2,phase=CapsulePhase.CREDITED,clawX=.70f)
        assertTrue(action(CapsuleEvent.ClawMoved(.1f,0f)).isEmpty())
        assertEquals(listOf(CapsuleSound.CLAW_MOVE),action(CapsuleEvent.ClawMoved(-.1f,0f)))
    }
    @Test fun initializationDoesNotAnnounceRestoredInventory(){
        val before=CapsuleState(stock=0)
        val restored=before.copy(ready=true,stock=24,storedIds=setOf(1L))
        assertTrue(capsuleSounds(before,restored).isEmpty())
    }
    @Test fun switchingSeriesDoesNotSoundLikeRestocking(){
        val before=game.copy(series=0,stock=10)
        assertEquals(listOf(CapsuleSound.CLICK),capsuleSounds(before,before.copy(series=1,stock=24)))
    }
}
