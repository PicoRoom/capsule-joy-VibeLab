package com.bujiaban.niuqu.pico.ui.capsule

import com.bujiaban.niuqu.pico.data.repository.OnboardingPreferences
import com.bujiaban.niuqu.pico.domain.model.*
import com.bujiaban.niuqu.pico.domain.usecase.CapsuleUseCase
import com.bujiaban.niuqu.pico.domain.usecase.MemoryCapsuleRepository
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class MemoryOnboardingPreferences(override var tutorialCompleted:Boolean=false,override var clawCompleted:Boolean=false):OnboardingPreferences

class ExperienceFlowTest {
    private fun vm(repo:MemoryCapsuleRepository=MemoryCapsuleRepository(),prefs:MemoryOnboardingPreferences=MemoryOnboardingPreferences())=
        CapsuleViewModel(CapsuleUseCase(repo){0f},prefs)
    private fun begin(v:CapsuleViewModel){v.onEvent(CapsuleEvent.Loaded);v.startTutorial();v.confirmPosition()}
    private fun dispense(v:CapsuleViewModel){
        v.onEvent(CapsuleEvent.CoinDropped(.01f));repeat(4){v.onEvent(CapsuleEvent.CrankMoved(90f))}
        v.onEvent(CapsuleEvent.Tick(2.1f))
    }
    private fun reveal(v:CapsuleViewModel){
        dispense(v);v.onEvent(CapsuleEvent.CapsulePulled(.06f))
        v.onEvent(CapsuleEvent.ShellsSeparated(.04f));v.onEvent(CapsuleEvent.Tick(1.4f))
    }
    @Test fun welcomeAndPlacementDoNotAcceptGameplay(){
        val v=vm();assertEquals(ExperienceScreen.HOME,v.state.value.flow.screen)
        v.play();v.startTutorial();assertEquals(ExperienceScreen.HOME,v.state.value.flow.screen)
        v.onEvent(CapsuleEvent.Loaded)
        val loaded=v.state.value.game
        v.onEvent(CapsuleEvent.CoinDropped(.01f));v.onEvent(CapsuleEvent.Series(2));v.onEvent(CapsuleEvent.Tick(10f))
        v.onEvent(CapsuleEvent.Mode(ExperienceMode.STUDY));assertEquals(loaded,v.state.value.game)
        v.startTutorial();assertEquals(TutorialStep.POSITION,v.state.value.flow.step)
        val positioned=v.state.value.game
        v.onEvent(CapsuleEvent.CoinDropped(.01f));v.onEvent(CapsuleEvent.Tick(10f));assertEquals(positioned,v.state.value.game)
        v.confirmPosition();assertEquals(TutorialStep.COIN,v.state.value.flow.step)
    }
    @Test fun loadErrorsKeepTheWelcomeMenuAndDisableStart(){
        val v=vm();v.onEvent(CapsuleEvent.Failure("bad model"));v.onEvent(CapsuleEvent.Loaded)
        v.play();v.startTutorial();assertEquals(ExperienceScreen.HOME,v.state.value.flow.screen)
        assertEquals("bad model",v.state.value.game.error)
    }
    @Test fun stepsAdvanceAfterSuccessfulActionsAndTheirAnimations(){
        val prefs=MemoryOnboardingPreferences();val v=vm(prefs=prefs);begin(v)
        v.onEvent(CapsuleEvent.CoinDropped(.8f));v.onEvent(CapsuleEvent.CrankMoved(90f));v.onEvent(CapsuleEvent.ShellsSeparated(.1f))
        assertEquals(TutorialStep.COIN,v.state.value.flow.step)
        v.onEvent(CapsuleEvent.CoinDropped(.01f));assertEquals(TutorialStep.TURN,v.state.value.flow.step)
        repeat(3){v.onEvent(CapsuleEvent.CrankMoved(90f))};assertEquals(TutorialStep.TURN,v.state.value.flow.step)
        v.onEvent(CapsuleEvent.CrankMoved(90f));assertEquals(CapsulePhase.DISPENSING,v.state.value.game.phase)
        v.onEvent(CapsuleEvent.Tick(1f));assertEquals(TutorialStep.TURN,v.state.value.flow.step)
        v.onEvent(CapsuleEvent.Tick(1.1f));assertEquals(TutorialStep.TAKE,v.state.value.flow.step)
        v.onEvent(CapsuleEvent.CapsulePulled(.01f));assertEquals(TutorialStep.TAKE,v.state.value.flow.step)
        v.onEvent(CapsuleEvent.CapsulePulled(.06f));assertEquals(TutorialStep.OPEN,v.state.value.flow.step)
        v.onEvent(CapsuleEvent.ShellsSeparated(.01f));assertEquals(CapsulePhase.CAPSULE_TAKEN,v.state.value.game.phase)
        v.onEvent(CapsuleEvent.ShellsSeparated(.04f));v.onEvent(CapsuleEvent.Tick(.7f));assertEquals(TutorialStep.OPEN,v.state.value.flow.step)
        v.onEvent(CapsuleEvent.Tick(.7f));assertEquals(TutorialStep.STORE,v.state.value.flow.step)
        assertTrue(v.state.value.game.albumOpen);assertFalse(prefs.tutorialCompleted)
        val id=requireNotNull(v.state.value.game.activeToy)
        assertEquals(id,v.state.value.flow.tutorialToyId)
        v.onEvent(CapsuleEvent.StoreToy(id));assertEquals(ExperienceScreen.COMPLETE,v.state.value.flow.screen)
        assertTrue(prefs.tutorialCompleted);assertTrue(v.state.value.flow.tutorialCompleted)
        assertEquals(1,v.state.value.game.toys.size);assertEquals(setOf(id),v.state.value.game.storedIds)
        val complete=v.state.value.game
        v.onEvent(CapsuleEvent.TakeFromAlbum(0));v.onEvent(CapsuleEvent.CoinDropped(.01f));assertEquals(complete,v.state.value.game)
        v.finishTutorial();assertEquals(ExperienceScreen.PLAY,v.state.value.flow.screen)
    }
    @Test fun tutorialKeepsItsToyAfterPickingAndCannotStartAnotherRound(){
        val v=vm();begin(v);reveal(v)
        val id=requireNotNull(v.state.value.flow.tutorialToyId)
        v.onEvent(CapsuleEvent.ToyPicked(id));assertEquals(CapsulePhase.COIN_READY,v.state.value.game.phase)
        assertEquals(TutorialStep.STORE,v.state.value.flow.step)
        v.onEvent(CapsuleEvent.CoinDropped(.01f));assertEquals(CapsulePhase.COIN_READY,v.state.value.game.phase)
        v.onEvent(CapsuleEvent.PlaceToy(v.state.value.game.toys.single().copy(x=.2f)))
        assertEquals(.2f,v.state.value.game.toys.single().x)
        v.onEvent(CapsuleEvent.StoreAll);assertEquals(ExperienceScreen.COMPLETE,v.state.value.flow.screen)
    }
    @Test fun existingCollectionCannotAdvanceTutorialAndIsPreserved(){
        val old=ToyPlacement(50,0,.1f,.9f,.2f)
        val repo=MemoryCapsuleRepository().apply{toys=listOf(old);storedIds=setOf(50)}
        val v=vm(repo);begin(v)
        v.onEvent(CapsuleEvent.TakeFromAlbum(0));v.onEvent(CapsuleEvent.StoreAll)
        v.onEvent(CapsuleEvent.Mode(ExperienceMode.STUDY));v.onEvent(CapsuleEvent.Series(1))
        assertEquals(ExperienceMode.PLAY,v.state.value.game.mode);assertEquals(0,v.state.value.game.series)
        assertEquals(TutorialStep.COIN,v.state.value.flow.step);assertEquals(repo.toys,v.state.value.game.toys)
        reveal(v);assertEquals(51L,v.state.value.flow.tutorialToyId)
        v.onEvent(CapsuleEvent.StoreToy(50));v.onEvent(CapsuleEvent.PlaceToy(old.copy(x=10f)))
        assertEquals(ExperienceScreen.TUTORIAL,v.state.value.flow.screen)
        assertEquals(old,v.state.value.game.toys.first())
        v.onEvent(CapsuleEvent.StoreToy(51));assertEquals(setOf(50L,51L),repo.storedIds)
    }
    @Test fun skippingKeepsThePaidRoundAndDoesNotMarkCompletion(){
        val prefs=MemoryOnboardingPreferences();val v=vm(prefs=prefs);begin(v)
        v.onEvent(CapsuleEvent.CoinDropped(.01f));val paid=v.state.value.game
        v.skipTutorial();assertEquals(ExperienceScreen.PLAY,v.state.value.flow.screen)
        assertEquals(paid,v.state.value.game);assertFalse(prefs.tutorialCompleted)
        repeat(4){v.onEvent(CapsuleEvent.CrankMoved(90f))};assertEquals(CapsulePhase.DISPENSING,v.state.value.game.phase)
        v.openHome();v.startTutorial();assertEquals(ExperienceScreen.HOME,v.state.value.flow.screen)
        assertEquals(CapsulePhase.DISPENSING,v.state.value.game.phase)
    }
    @Test fun reopeningTheMenuPausesTheRoundAndPlayResumesIt(){
        val v=vm();v.onEvent(CapsuleEvent.Loaded);v.play()
        v.onEvent(CapsuleEvent.CoinDropped(.01f));repeat(4){v.onEvent(CapsuleEvent.CrankMoved(90f))}
        v.onEvent(CapsuleEvent.Tick(.5f));v.openHome();val paused=v.state.value.game
        v.onEvent(CapsuleEvent.Tick(10f));v.onEvent(CapsuleEvent.StoreAll);assertEquals(paused,v.state.value.game)
        v.play();v.onEvent(CapsuleEvent.Tick(1.6f));assertEquals(CapsulePhase.CAPSULE_READY,v.state.value.game.phase)
    }
    @Test fun completionPersistsAcrossViewModelsAndReplayDoesNotEraseCollection(){
        val repo=MemoryCapsuleRepository();val prefs=MemoryOnboardingPreferences();val v=vm(repo,prefs)
        begin(v);reveal(v);v.onEvent(CapsuleEvent.StoreAll)
        val restored=vm(repo,prefs);assertTrue(restored.state.value.flow.tutorialCompleted)
        assertEquals(ExperienceScreen.HOME,restored.state.value.flow.screen)
        assertEquals(1,restored.state.value.game.toys.size)
        restored.onEvent(CapsuleEvent.Loaded);restored.startTutorial();restored.skipTutorial()
        assertTrue(prefs.tutorialCompleted);assertEquals(repo.toys,restored.state.value.game.toys)
    }
    @Test fun tutorialRestocksAnEmptyMachineWithoutClearingToys(){
        val v=vm();v.onEvent(CapsuleEvent.Loaded);v.play()
        repeat(24){reveal(v);v.onEvent(CapsuleEvent.StoreAll)}
        assertEquals(0,v.state.value.game.stock)
        v.openHome();v.startTutorial()
        assertEquals(ExperienceScreen.TUTORIAL,v.state.value.flow.screen)
        assertEquals(24,v.state.value.game.stock);assertEquals(24,v.state.value.game.toys.size)
    }
    @Test fun homeBlocksEveryClawEntryAndClearsTheHeldAim(){
        val v=vm();v.onEvent(CapsuleEvent.Loaded);v.play();v.onEvent(CapsuleEvent.Series(2))
        v.clawAuxiliary();v.beginClawAim();assertTrue(v.state.value.clawControls.aiming)
        v.openHome();assertFalse(v.state.value.clawControls.aiming)
        val paused=v.state.value
        v.beginClawAim();v.dragClawAim(-.2f,-.3f);v.pulseClawPrimary();v.clawAuxiliary();v.showClawJoystick(1f,1f)
        assertEquals(paused,v.state.value)
        v.play();v.dragClawAim(-.2f,0f);assertEquals(.5f,v.state.value.game.clawX)
        v.pulseClawPrimary();assertEquals(CapsulePhase.CLAWING,v.state.value.game.phase)
    }
    @Test fun menuStopsAndResumesTheClawMechanismSound()=runBlocking {
        val v=vm();val sounds=mutableListOf<CapsuleSound>()
        val job=launch(start=CoroutineStart.UNDISPATCHED){v.sounds.collect{sounds+=it}}
        try{
            v.onEvent(CapsuleEvent.Loaded);v.play();v.onEvent(CapsuleEvent.Series(2));v.clawAuxiliary();v.pulseClawPrimary()
            yield();assertTrue(CapsuleSound.CLAW_LOOP in sounds);sounds.clear()
            v.openHome();yield();assertEquals(listOf(CapsuleSound.STOP_MECHANISM),sounds);sounds.clear()
            v.play();yield();assertEquals(listOf(CapsuleSound.CLAW_LOOP),sounds)
        }finally{job.cancel()}
    }
}
