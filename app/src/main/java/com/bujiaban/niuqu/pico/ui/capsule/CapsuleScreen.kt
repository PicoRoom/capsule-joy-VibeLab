package com.bujiaban.niuqu.pico.ui.capsule

import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bujiaban.niuqu.pico.domain.model.*
import com.bujiaban.niuqu.pico.scene.*
import com.bujiaban.niuqu.pico.platform.CapsuleAudio
import com.bujiaban.niuqu.pico.ui.capsule.components.*
import com.pico.spatial.ui.foundation.gesture.data.InteractionKind
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.gesture.*
import com.pico.spatial.ui.geometry.Offset3D
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.ViewCoordinateSpace
import com.pico.spatial.ui.platform.LocalPhysicalLengthConverter
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import androidx.compose.ui.layout.onGloballyPositioned
import com.pico.spatial.ui.foundation.layout.sizeIn3D
import com.pico.spatial.ui.foundation.layout.IntSize3D
import kotlinx.coroutines.CancellationException

@Composable
fun CapsuleScreen(){
    val context=LocalContext.current
    val vm:CapsuleViewModel=viewModel(factory=CapsuleViewModelFactory(context))
    CapsuleContent(vm)
}
@Composable
internal fun CapsuleContent(vm:CapsuleViewModel){
    val context=LocalContext.current
    val density=LocalDensity.current
    val converter=LocalPhysicalLengthConverter.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val scene=remember(vm){CapsuleScene(context.applicationContext,vm)}
    val navigator=LocalSpatialNavigator.current
    var viewSize by remember{mutableStateOf(IntSize3D.Zero)}
    var spatialContent by remember{mutableStateOf<SpatialViewContent?>(null)}
    val audio=remember(vm){CapsuleAudio(context.applicationContext)}
    var hintVersion by remember{mutableStateOf(0)}
    LaunchedEffect(audio,vm){vm.sounds.collect{audio.play(it)}}
    DisposableEffect(audio,lifecycle){
        fun resumeAudio(){
            val state=vm.state.value
            audio.play(if(state.flow.gameplayActive)mechanismSound(state.game)?:CapsuleSound.STOP_MECHANISM else CapsuleSound.STOP_MECHANISM)
            audio.resume()
        }
        val observer=LifecycleEventObserver{_,event->when(event){
            Lifecycle.Event.ON_RESUME->resumeAudio()
            Lifecycle.Event.ON_PAUSE->audio.pause()
            else->Unit
        }}
        lifecycle.addObserver(observer)
        if(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))resumeAudio()
        onDispose{lifecycle.removeObserver(observer);audio.close()}
    }
    fun scale(metres:Float,pixels:Int)=with(density){converter.lengthToDp(metres,LengthUnit.Meters).toPx()/pixels}
    fun place(entity:Entity,position:Vector3,width:Float,pixels:Int){
        scene.root.addChild(entity)
        entity.enabled=entity.getName() in listOf("UI_TitleLogo","UI_TitleActions")
        PanelBacking.attach(entity,pixels)
        entity.components[TransformComponent::class.java]?.apply{setPosition(position);setScaleVector(Vector3(scale(width,pixels)))}
    }
    fun delta(value:Offset3D):Vector3 {
        val content=spatialContent?:return Vector3.ZERO
        return content.convertPosition(Vector3(value.x,value.y,value.z),ViewCoordinateSpace.Local,scene.root)-
            content.convertPosition(Vector3.ZERO,ViewCoordinateSpace.Local,scene.root)
    }
    DisposableEffect(scene,lifecycle){
        CapsuleSystem.scene=scene;com.pico.spatial.core.ecs.System.register(CapsuleSystem::class.java)
        val observer=LifecycleEventObserver{_,event->when(event){Lifecycle.Event.ON_START->scene.resume();Lifecycle.Event.ON_STOP->scene.pause();else->Unit}}
        lifecycle.addObserver(observer)
        if(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))scene.resume()
        onDispose{lifecycle.removeObserver(observer);if(CapsuleSystem.scene===scene){CapsuleSystem.scene=null;com.pico.spatial.core.ecs.System.unregister(CapsuleSystem::class.java)};scene.close()}
    }
    fun exitGame(){scene.pause();audio.pause();navigator.closeWindowContainer()}
    fun start(){
        val state=vm.state.value
        if(!state.game.ready||state.game.error!=null)return
        scene.showTitleOptions(false)
        if(state.game.phase==CapsulePhase.COIN_READY&&!state.flow.tutorialCompleted)vm.startTutorial() else vm.play()
    }
    fun openHome(){
        scene.showTitleOptions(false)
        scene.endCapsuleTransform();scene.cancelDrag(null);scene.finishToyTransform()
        vm.openHome()
    }
    SpatialView(
        modifier=Modifier.fillMaxSize().onGloballyPositioned{viewSize=it.sizeIn3D}
            .pointerInput(scene){
                // Toys use the transform recognizer for both one-hand movement and two-hand scaling.
                detectSpatialDragGesture(context,targetedToEntity=TargetEntity.any{scene.canDrag(it)&&!scene.canTransform(it)&&!scene.canHandleCapsule(it)},
                    onDragStart={point,v->scene.beginDrag(v.targetEntity,spatialContent?.convertPosition(Vector3(point.x,point.y,point.z),ViewCoordinateSpace.Local,scene.root))},onDragEnd={scene.endDrag(it.targetEntity)},onDragCancel={scene.cancelDrag(it.targetEntity)}){
                    scene.drag(it.targetEntity,delta(it.dragAmount))
                }
            }
            .pointerInput(scene){
                detectSpatialTransformGesture(context,targetedToEntity=TargetEntity.any{scene.canHandleCapsule(it)},
                    onTransformEnd={scene.endCapsuleTransform()},onTransformCancel={scene.cancelCapsuleTransform()}){
                    scene.transformCapsule(delta(it.dragAmount),it.scaleValue,
                        it.leftInteractionKind!=InteractionKind.Unknown&&it.rightInteractionKind!=InteractionKind.Unknown,
                        it.leftInteractionKind!=InteractionKind.Unknown,it.rightInteractionKind!=InteractionKind.Unknown)
                }
            }
            .pointerInput(scene){
                detectSpatialTransformGesture(context,targetedToEntity=TargetEntity.any{scene.canTransform(it)},onTransformEnd={scene.finishToyTransform()},onTransformCancel={scene.finishToyTransform()}){
                    val basis=spatialContent?.convertRotation(Quat.identity(),ViewCoordinateSpace.Local,scene.root)?:return@detectSpatialTransformGesture
                    // Rotate a delta between bases; converting it as an absolute pose causes yaw drift.
                    scene.transformToy(it.targetEntity,delta(it.dragAmount),(basis*it.rotation.toQuaternion()*basis.conjugate()).normalize(),it.scaleValue)
                }
            }
            .pointerInput(scene){detectSpatialTapGesture(context,targetedToEntity=TargetEntity.any{scene.canTap(it)}){scene.tap(it.targetEntity)}},
        attachments={
            AttachmentPanel("title_logo",size=IntSize(CapsuleLayout.TITLE_LOGO_PIXELS_W,CapsuleLayout.TITLE_LOGO_PIXELS_H)){
                CapsulePanel(vm,density.fontScale){TitleLogo()}
            }
            AttachmentPanel("title_actions",size=IntSize(CapsuleLayout.TITLE_ACTIONS_PIXELS_W,CapsuleLayout.TITLE_ACTIONS_PIXELS_H)){
                CapsulePanel(vm,density.fontScale){state->
                    TitleActions(state,::start,{scene.showTitleOptions(true)},::exitGame)
                }
            }
            AttachmentPanel("title_options",size=IntSize(CapsuleLayout.TITLE_OPTIONS_PIXELS_W,CapsuleLayout.TITLE_OPTIONS_PIXELS_H)){
                CapsulePanel(vm,density.fontScale){state->
                    val sound by audio.settings.collectAsStateWithLifecycle()
                    TitleOptions(state,sound,{audio.setMusic(!sound.music)},{audio.setEffects(!sound.effects)},
                        {scene.showTitleOptions(false);vm.startTutorial()},{scene.showTitleOptions(false)})
                }
            }
            AttachmentPanel("tutorial",size=IntSize(CapsuleLayout.TUTORIAL_PIXELS_W,CapsuleLayout.TUTORIAL_PIXELS_H)){
                CapsulePanel(vm,density.fontScale){state->
                    TutorialPanel(state,vm::confirmPosition,vm::skipTutorial,vm::finishTutorial,{scene.volume.recenter()})
                }
            }
            AttachmentPanel("series",size=IntSize(CapsuleLayout.SERIES_PIXELS_W,CapsuleLayout.SERIES_PIXELS_H)){
                CapsulePanel(vm,density.fontScale){state->
                    fun browse(offset:Int){vm.onEvent(CapsuleEvent.Series((state.game.series+offset+state.series.size)%state.series.size))}
                    SeriesCard(state,{browse(-1)},{browse(1)})
                }
            }
            AttachmentPanel("toolbar",size=IntSize(CapsuleLayout.TOOLBAR_PIXELS_W,CapsuleLayout.TOOLBAR_PIXELS_H)){
                CapsulePanel(vm,density.fontScale){state->
                    val soundSettings by audio.settings.collectAsStateWithLifecycle()
                    SceneToolbar(state,
                        onMode={vm.onEvent(CapsuleEvent.Mode(if(state.game.mode==ExperienceMode.PLAY)ExperienceMode.STUDY else ExperienceMode.PLAY))},
                        onAlbum={vm.onEvent(CapsuleEvent.ToggleAlbum)},
                        onHint={hintVersion++;audio.play(CapsuleSound.CLICK)},
                        onRecenter={scene.volume.recenter();audio.play(CapsuleSound.CLICK)},
                        onExit=::exitGame,
                        onHome=::openHome,
                        soundSettings=soundSettings,onMenu=scene::setMenuExpanded,
                        onMusic={audio.setMusic(!soundSettings.music)},
                        onEffects={audio.setEffects(!soundSettings.effects);audio.play(CapsuleSound.CLICK)},
                    )
                }
            }
            AttachmentPanel("status",size=IntSize(CapsuleLayout.STATUS_PIXELS_W,CapsuleLayout.STATUS_PIXELS_H)){
                CapsulePanel(vm,density.fontScale){MachineStatus(it)}
            }
            AttachmentPanel("bubble",size=IntSize(CapsuleLayout.BUBBLE_PIXELS_W,CapsuleLayout.BUBBLE_PIXELS_H)){
                CapsulePanel(vm,density.fontScale){ComicBubble(it,hintVersion){vm.onEvent(CapsuleEvent.StoreRevealed)}}
            }
            AttachmentPanel("study_guide",size=IntSize(CapsuleLayout.STUDY_PIXELS_W,CapsuleLayout.STUDY_PIXELS_H)){
                CapsulePanel(vm,density.fontScale){StudyGuide(it,vm::startStudyDemo,vm::stepStudyDemo)}
            }
            AttachmentPanel("claw_controls",size=IntSize(CapsuleLayout.CLAW_PANEL_PIXELS_W,CapsuleLayout.CLAW_PANEL_PIXELS_H)){
                // Native attachment pointer capture keeps a pinch attached to the aiming surface.
                CapsulePanel(vm,density.fontScale){state->
                    ClawControlPanel(state,vm::beginClawAim,vm::dragClawAim,vm::endClawAim,vm::pulseClawPrimary,vm::clawAuxiliary)
                }
            }
            AttachmentPanel("album_heading",size=IntSize(460,110)){
                CapsulePanel(vm,density.fontScale){AlbumHeading(it,{page->vm.onEvent(CapsuleEvent.AlbumPage(page))})}
            }
            AttachmentPanel("album_store",size=IntSize(240,132)){
                CapsulePanel(vm,density.fontScale){state->
                    val game=state.game
                    CollectionActions(game.ready&&game.phase in listOf(CapsulePhase.COIN_READY,CapsulePhase.REVEALED),
                        {vm.onEvent(CapsuleEvent.StoreAll)},{vm.onEvent(CapsuleEvent.DisplaySeries)})
                }
            }
            repeat(6){index->AttachmentPanel("display_$index",size=IntSize(180,44)){
                CapsulePanel(vm,density.fontScale){state->DisplayCaption(state,index)}
            }}
            repeat(6){index->AttachmentPanel("album_$index",size=IntSize(250,65)){
                CapsulePanel(vm,density.fontScale){AlbumCaption(it,index)}
            }}
        },
        update={content,_->
            scene.volume.fit(content,Vector3(viewSize.width.toFloat(),viewSize.height.toFloat(),viewSize.depth.toFloat()))
        },
        initial={content,attachments->
            spatialContent=content
            content.addEntity(scene.volume.owner)
            attachments.entity("title_logo")?.let{it.setName("UI_TitleLogo");place(it,CapsuleLayout.titleLogoPosition,CapsuleLayout.TITLE_LOGO_WIDTH,CapsuleLayout.TITLE_LOGO_PIXELS_W)}
            attachments.entity("title_actions")?.let{it.setName("UI_TitleActions");place(it,CapsuleLayout.titleActionsPosition,CapsuleLayout.TITLE_ACTIONS_WIDTH,CapsuleLayout.TITLE_ACTIONS_PIXELS_W)}
            attachments.entity("title_options")?.let{it.setName("UI_TitleOptions");place(it,CapsuleLayout.titleOptionsPosition,CapsuleLayout.TITLE_OPTIONS_WIDTH,CapsuleLayout.TITLE_OPTIONS_PIXELS_W)}
            attachments.entity("tutorial")?.let{it.setName("UI_Tutorial");place(it,CapsuleLayout.tutorialPosition,CapsuleLayout.TUTORIAL_WIDTH,CapsuleLayout.TUTORIAL_PIXELS_W)}
            attachments.entity("series")?.let{it.setName("UI_Series");place(it,CapsuleLayout.seriesCardPosition,CapsuleLayout.SERIES_WIDTH,CapsuleLayout.SERIES_PIXELS_W)}
            attachments.entity("toolbar")?.let{it.setName("UI_Toolbar");place(it,CapsuleLayout.toolbarPosition,CapsuleLayout.TOOLBAR_WIDTH,CapsuleLayout.TOOLBAR_PIXELS_W)}
            attachments.entity("status")?.let{it.setName("UI_Status");place(it,CapsuleLayout.statusPosition,CapsuleLayout.STATUS_WIDTH,CapsuleLayout.STATUS_PIXELS_W)}
            attachments.entity("bubble")?.let{it.setName("UI_Bubble");place(it,CapsuleLayout.bubblePosition,CapsuleLayout.BUBBLE_WIDTH,CapsuleLayout.BUBBLE_PIXELS_W)}
            attachments.entity("study_guide")?.let{it.setName("UI_StudyGuide");place(it,CapsuleLayout.studyGuidePosition,CapsuleLayout.STUDY_WIDTH,CapsuleLayout.STUDY_PIXELS_W);it.enabled=false}
            attachments.entity("claw_controls")?.let{it.setName("UI_ClawControls");place(it,CapsuleLayout.clawPanelPosition,CapsuleLayout.CLAW_PANEL_WIDTH,CapsuleLayout.CLAW_PANEL_PIXELS_W);it.components[TransformComponent::class.java]?.setEulerAngles(CapsuleLayout.clawPanelRotation);it.enabled=false}
            try{
                scene.load()
                attachments.entity("album_heading")?.let{it.setName("UI_AlbumHeading");scene.attachAlbumLabel(it,-1,scale(.44f,460))}
                attachments.entity("album_store")?.let{it.setName("UI_AlbumStore");scene.attachAlbumLabel(it,-2,scale(.19f,240))}
                repeat(6){index->attachments.entity("album_$index")?.let{it.setName("UI_AlbumCaption_$index");scene.attachAlbumLabel(it,index,scale(.215f,250))}}
                repeat(6){index->attachments.entity("display_$index")?.let{it.setName("UI_DisplayCaption_$index");scene.attachDisplayLabel(it,index,scale(.12f,180))}}
                scene.finishSetup()
            }catch(e:CancellationException){throw e}catch(e:Exception){Log.e(CapsuleScene.TAG,"LOAD_FAILED",e);vm.onEvent(CapsuleEvent.Failure("资源加载失败：${e.message}"))}
        },
    )
}

/** Every spatial attachment observes current state at its own native composition boundary. */
@Composable
private fun CapsulePanel(vm:CapsuleViewModel,fontScale:Float,content:@Composable (CapsuleUiState)->Unit){
    CompositionLocalProvider(LocalDensity provides Density(1f,fontScale)){
        val state by vm.state.collectAsStateWithLifecycle()
        content(state)
    }
}
