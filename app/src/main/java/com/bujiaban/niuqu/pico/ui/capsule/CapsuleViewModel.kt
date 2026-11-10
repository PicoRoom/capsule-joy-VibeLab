package com.bujiaban.niuqu.pico.ui.capsule

import androidx.lifecycle.ViewModel
import com.bujiaban.niuqu.pico.data.repository.OnboardingPreferences
import com.bujiaban.niuqu.pico.domain.model.*
import com.bujiaban.niuqu.pico.domain.usecase.CapsuleUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.asStateFlow

class CapsuleViewModel(private val useCase:CapsuleUseCase,private val onboarding:OnboardingPreferences):ViewModel() {
    private val mutable=MutableStateFlow(CapsuleUiState(useCase.initial(),useCase.definitions,useCase.series,
        flow=ExperienceFlow(tutorialCompleted=onboarding.tutorialCompleted),clawLearned=onboarding.clawCompleted))
    private val input=ClawInput()
    val state=mutable.asStateFlow()
    private val soundEvents=MutableSharedFlow<CapsuleSound>(extraBufferCapacity=64,onBufferOverflow=BufferOverflow.DROP_OLDEST)
    val sounds=soundEvents.asSharedFlow()

    private fun publish(game:CapsuleState=mutable.value.game,flow:ExperienceFlow=mutable.value.flow){
        val before=mutable.value
        val cues=capsuleSounds(before.game,game).toMutableList()
        if(before.flow.gameplayActive!=flow.gameplayActive){
            cues+=if(flow.gameplayActive)mechanismSound(game)?:CapsuleSound.STOP_MECHANISM else CapsuleSound.STOP_MECHANISM
        }
        mutable.value=before.copy(game=game,flow=flow,clawControls=input.state(game))
        cues.distinct().forEach{soundEvents.tryEmit(it)}
    }
    private fun canStart()=mutable.value.game.let{it.ready&&it.error==null}

    @Synchronized fun play(){
        if(!canStart()||mutable.value.flow.screen!=ExperienceScreen.HOME)return
        publish(flow=mutable.value.flow.copy(screen=ExperienceScreen.PLAY))
    }
    @Synchronized fun openHome(){
        if(mutable.value.flow.screen!=ExperienceScreen.PLAY)return
        input.clear()
        publish(flow=mutable.value.flow.copy(screen=ExperienceScreen.HOME))
    }
    @Synchronized fun startTutorial(){
        val before=mutable.value
        if(!canStart()||before.flow.screen!=ExperienceScreen.HOME||before.game.phase!=CapsulePhase.COIN_READY)return
        input.clear()
        var game=useCase.reduce(before.game,CapsuleEvent.Mode(ExperienceMode.PLAY))
        game=useCase.reduce(game,CapsuleEvent.Series(0))
        if(game.stock==0)game=useCase.reduce(game,CapsuleEvent.Refill)
        game=useCase.reduce(game,CapsuleEvent.AlbumPage(0))
        if(game.albumOpen)game=useCase.reduce(game,CapsuleEvent.ToggleAlbum)
        publish(game,before.flow.copy(screen=ExperienceScreen.TUTORIAL,step=TutorialStep.POSITION,tutorialToyId=null))
    }
    @Synchronized fun confirmPosition(){
        val flow=mutable.value.flow
        if(!canStart()||flow.screen!=ExperienceScreen.TUTORIAL||flow.step!=TutorialStep.POSITION)return
        publish(flow=flow.copy(step=TutorialStep.COIN))
    }
    @Synchronized fun skipTutorial(){
        if(mutable.value.flow.screen!=ExperienceScreen.TUTORIAL)return
        input.clear()
        publish(flow=mutable.value.flow.copy(screen=ExperienceScreen.PLAY,tutorialToyId=null))
    }
    @Synchronized fun finishTutorial(){
        if(mutable.value.flow.screen!=ExperienceScreen.COMPLETE)return
        publish(flow=mutable.value.flow.copy(screen=ExperienceScreen.PLAY,tutorialToyId=null))
    }

    private fun accepts(event:CapsuleEvent):Boolean {
        if(event==CapsuleEvent.Loaded||event is CapsuleEvent.Failure)return true
        val flow=mutable.value.flow
        if(!canStart()||!flow.gameplayActive)return false
        if(flow.screen==ExperienceScreen.PLAY)return true
        // A step only exposes its real action. Old toys and other machines cannot finish the tutorial.
        return when(event){
            is CapsuleEvent.Tick -> true
            is CapsuleEvent.CoinDropped -> flow.step==TutorialStep.COIN
            is CapsuleEvent.CrankMoved -> flow.step==TutorialStep.TURN
            is CapsuleEvent.CapsulePulled -> flow.step==TutorialStep.TAKE
            is CapsuleEvent.ShellsSeparated -> flow.step==TutorialStep.OPEN
            is CapsuleEvent.ToyPicked -> flow.step==TutorialStep.STORE&&event.id==flow.tutorialToyId
            is CapsuleEvent.PlaceToy -> flow.step==TutorialStep.STORE&&event.pose.id==flow.tutorialToyId
            is CapsuleEvent.StoreToy -> flow.step==TutorialStep.STORE&&event.id==flow.tutorialToyId
            CapsuleEvent.StoreAll,CapsuleEvent.ToggleAlbum -> flow.step==TutorialStep.STORE
            else -> false
        }
    }
    private fun commit(game:CapsuleState){
        var next=game
        var flow=mutable.value.flow
        if(flow.screen==ExperienceScreen.TUTORIAL){
            flow=when(flow.step){
                TutorialStep.COIN -> if(game.phase==CapsulePhase.CREDITED)flow.copy(step=TutorialStep.TURN) else flow
                TutorialStep.TURN -> if(game.phase==CapsulePhase.CAPSULE_READY)flow.copy(step=TutorialStep.TAKE) else flow
                TutorialStep.TAKE -> if(game.phase==CapsulePhase.CAPSULE_TAKEN)flow.copy(step=TutorialStep.OPEN) else flow
                TutorialStep.OPEN -> if(game.phase==CapsulePhase.REVEALED&&game.activeToy!=null){
                    if(!game.albumOpen)next=useCase.reduce(game,CapsuleEvent.ToggleAlbum)
                    flow.copy(step=TutorialStep.STORE,tutorialToyId=game.activeToy)
                } else flow
                TutorialStep.STORE -> if(flow.tutorialToyId!=null&&flow.tutorialToyId in game.storedIds){
                    onboarding.tutorialCompleted=true
                    flow.copy(screen=ExperienceScreen.COMPLETE,tutorialCompleted=true)
                } else flow
                TutorialStep.POSITION -> flow
            }
        }
        if(next.isClaw&&next.phase==CapsulePhase.CAPSULE_READY&&!onboarding.clawCompleted){
            onboarding.clawCompleted=true;mutable.value=mutable.value.copy(clawLearned=true)
        }
        publish(next,flow)
    }
    @Synchronized fun onEvent(event:CapsuleEvent){
        if(!accepts(event))return
        val game=mutable.value.game
        if(event is CapsuleEvent.Tick){
            val dt=if(event.dt.isFinite())event.dt.coerceAtLeast(0f) else 0f
            input.tick(dt)
        }
        commit(useCase.reduce(game,event))
        if(event is CapsuleEvent.Mode)mutable.value=mutable.value.copy(studyTime=0f,studyRunning=false)
    }
    @Synchronized fun updateGrip(left:Boolean,right:Boolean,progress:Float,paused:Boolean=false){
        val s=mutable.value
        if(s.leftGrip!=left||s.rightGrip!=right||s.openingProgress!=progress||s.trackingPaused!=paused)
            mutable.value=s.copy(leftGrip=left,rightGrip=right,openingProgress=progress,trackingPaused=paused)
    }
    @Synchronized fun startStudyDemo(){if(mutable.value.game.mode==ExperienceMode.STUDY)mutable.value=mutable.value.copy(studyTime=0f,studyRunning=true)}
    @Synchronized fun stepStudyDemo(){if(mutable.value.game.mode==ExperienceMode.STUDY)mutable.value=mutable.value.copy(studyTime=((mutable.value.studyTime.toInt()/2+1)%4*2+1).toFloat(),studyRunning=false)}
    @Synchronized fun tickStudyDemo(dt:Float){val s=mutable.value;if(s.studyRunning){val t=(s.studyTime+dt).coerceAtMost(7.99f);mutable.value=s.copy(studyTime=t,studyRunning=t<7.99f)}}
    fun onEvent(event:CapsuleUiEvent){onEvent(when(event){is CapsuleUiEvent.SelectMode->CapsuleEvent.Mode(event.mode);is CapsuleUiEvent.SelectSeries->CapsuleEvent.Series(event.index)})}
    private fun canUseClaw()=canStart()&&mutable.value.flow.screen==ExperienceScreen.PLAY&&
        mutable.value.game.isClaw&&mutable.value.game.mode==ExperienceMode.PLAY
    @Synchronized fun clawAuxiliary(){
        val game=mutable.value.game
        if(!canUseClaw())return
        val event=when(game.phase){
            CapsulePhase.COIN_READY->if(game.stock>0)if(game.clawMissed)CapsuleEvent.RetryClaw else CapsuleEvent.CoinDropped(0f) else return
            CapsulePhase.CAPSULE_READY->CapsuleEvent.CapsulePulled(.06f)
            CapsulePhase.CAPSULE_TAKEN->return
            CapsulePhase.REVEALED->game.activeToy?.let{CapsuleEvent.StoreToy(it)}?:return
            else->return
        }
        onEvent(event)
    }
    @Synchronized fun beginClawAim(){if(!canUseClaw())return;input.begin(mutable.value.game);publish()}
    @Synchronized fun dragClawAim(dx:Float,dz:Float){
        if(!canUseClaw()||!input.isDragging(mutable.value.game)||!dx.isFinite()||!dz.isFinite())return
        input.hand(dx*12f,dz*12f)
        onEvent(CapsuleEvent.ClawMoved(dx,dz))
    }
    @Synchronized fun endClawAim(){input.clearHand();publish()}
    private fun dropClaw(){
        if(!canUseClaw()||!mutable.value.game.canAimClaw||input.isDragging(mutable.value.game))return
        onEvent(CapsuleEvent.ClawGrab)
    }
    @Synchronized fun pulseClawPrimary(){if(!canUseClaw())return;input.pulse();dropClaw();publish()}
    @Synchronized fun clearClawInputs(){input.clear();publish()}
    @Synchronized fun showClawJoystick(x:Float,z:Float){if(!canUseClaw())return;input.hand(x,z);publish()}
    @Synchronized fun clearClawJoystick(){input.clearHand();publish()}
}
