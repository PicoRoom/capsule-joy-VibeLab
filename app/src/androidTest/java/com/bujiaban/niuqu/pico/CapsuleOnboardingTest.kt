package com.bujiaban.niuqu.pico

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.inspector.WindowInspector
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bujiaban.niuqu.pico.domain.model.*
import com.bujiaban.niuqu.pico.platform.LaunchActivity
import com.bujiaban.niuqu.pico.scene.CapsuleScene
import com.bujiaban.niuqu.pico.scene.CapsuleSystem
import com.bujiaban.niuqu.pico.ui.capsule.ExperienceScreen
import com.bujiaban.niuqu.pico.ui.capsule.TutorialStep
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Native menu clicks and production gesture callbacks; real hand tracking still needs a headset. */
@RunWith(AndroidJUnit4::class)
class CapsuleOnboardingTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private fun <T> main(block:()->T):T {
        val result=AtomicReference<T>()
        instrumentation.runOnMainSync{result.set(block())}
        return result.get()
    }
    private fun awaitCondition(message:String,timeout:Long=20000,block:()->Boolean){
        val end=SystemClock.elapsedRealtime()+timeout
        while(SystemClock.elapsedRealtime()<end){if(main(block))return;Thread.sleep(100)}
        fail(message)
    }
    private fun visible(entity:Entity):Boolean {
        var current:Entity?=entity
        while(current!=null){if(!current.enabled)return false;current=current.getParent()}
        return true
    }
    private fun shown(scene:CapsuleScene,name:String)=scene.root.findEntity(name)?.let(::visible)==true
    private fun panel(width:Int,height:Int):View?=WindowInspector.getGlobalWindowViews().firstOrNull{
        it.isAttachedToWindow&&it.width==width&&it.height==height
    }
    private fun tapPanel(width:Int,height:Int,x:Float,y:Float){
        awaitCondition("Missing measured $width x $height panel"){panel(width,height)!=null}
        // Let the latest native composition apply before dispatching a new button action.
        Thread.sleep(250)
        val view=main{requireNotNull(panel(width,height))}
        val start=SystemClock.uptimeMillis()
        for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)){
            main{MotionEvent.obtain(start,SystemClock.uptimeMillis(),action,x,y,0).also{view.dispatchTouchEvent(it);it.recycle()}}
            Thread.sleep(120)
        }
    }
    private fun freshScene():CapsuleScene {
        val previous=main{CapsuleSystem.scene}
        main{context.startActivity(Intent(context,LaunchActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))}
        awaitCondition("Fresh onboarding scene did not load",240000){
            CapsuleSystem.scene?.let{it!==previous&&it.loaded&&it.volume.ready}==true
        }
        val scene=main{requireNotNull(CapsuleSystem.scene)}
        awaitCondition("Welcome panel did not appear"){shown(scene,"UI_TitleActions")}
        return scene
    }
    private fun capture(name:String){
        Thread.sleep(1200)
        val file=File(context.getExternalFilesDir(null),"onboarding-$name.png")
        instrumentation.uiAutomation.executeShellCommand("screencap -p ${file.absolutePath}").use{descriptor->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use{it.readBytes()}
        }
        assertTrue("Spatial screenshot must save",file.length()>0)
    }
    private fun isolatedPreferences(block:()->Unit){
        val collection=context.getSharedPreferences("paper_capsule_animals",Context.MODE_PRIVATE)
        val onboarding=context.getSharedPreferences("paper_capsule_onboarding",Context.MODE_PRIVATE)
        val toys=collection.getString("toys",null)
        val stored=collection.getStringSet("stored_toy_ids",null)?.toSet()
        val hadCompleted=onboarding.contains("tutorial_completed")
        val completed=onboarding.getBoolean("tutorial_completed",false)
        try{
            collection.edit().remove("toys").remove("stored_toy_ids").commit()
            onboarding.edit().remove("tutorial_completed").commit()
            block()
        }finally{
            // Flush pending manipulation before restoring the user's persistent state.
            main{CapsuleSystem.scene?.pause()}
            collection.edit().apply{
                if(toys==null)remove("toys") else putString("toys",toys)
                if(stored==null)remove("stored_toy_ids") else putStringSet("stored_toy_ids",stored)
            }.commit()
            onboarding.edit().apply{
                if(hadCompleted)putBoolean("tutorial_completed",completed) else remove("tutorial_completed")
            }.commit()
        }
    }
    private fun position(entity:Entity)=requireNotNull(entity.components[TransformComponent::class.java]).position
    private fun insertCoin(scene:CapsuleScene){main{
        val coin=requireNotNull(scene.debugEntity("coin"))
        assertTrue(scene.canDrag(coin))
        scene.beginDrag(coin);scene.drag(coin,scene.coinSlot-position(coin));scene.endDrag(coin)
        assertEquals(CapsulePhase.CREDITED,scene.vm.state.value.game.phase)
    }}
    private fun startFirstTutorial(scene:CapsuleScene){
        tapPanel(480,220,240f,56f)
        awaitCondition("Welcome primary action did not start tutorial"){
            scene.vm.state.value.flow.screen==ExperienceScreen.TUTORIAL&&shown(scene,"UI_Tutorial")
        }
        assertEquals(TutorialStep.POSITION,main{scene.vm.state.value.flow.step})
        tapPanel(480,540,350f,482f)
        awaitCondition("Position confirmation did not unlock the first coin"){
            scene.vm.state.value.flow.step==TutorialStep.COIN
        }
    }

    @Test fun titleSpatialScreensAreRendered()=isolatedPreferences {
        val scene=freshScene()
        // The emulator compositor runs asynchronously behind the attachment state.
        Thread.sleep(10000)
        capture("title-final")
        tapPanel(480,220,114f,158f)
        awaitCondition("Options missing"){shown(scene,"UI_TitleOptions")}
        Thread.sleep(10000)
        capture("settings-final")
        tapPanel(480,500,240f,381f)
        awaitCondition("Title did not return"){shown(scene,"UI_TitleActions")}
        tapPanel(480,220,240f,56f)
        awaitCondition("Tutorial missing"){shown(scene,"UI_Tutorial")}
        Thread.sleep(10000)
        capture("entered-final")
    }

    @Test fun nativeWelcomeLeadsThroughARealCapsuleAndRemembersCompletion()=isolatedPreferences {
        val scene=freshScene()
        val vm=scene.vm
        main{
            assertEquals(ExperienceScreen.HOME,vm.state.value.flow.screen)
            assertTrue(shown(scene,"TitleScene"))
            assertTrue(shown(scene,"UI_TitleLogo"))
            assertFalse(shown(scene,"UI_TitleOptions"))
            assertFalse(shown(scene,"UI_Toolbar"))
            assertFalse(shown(scene,"MachineHousing_0"))
            val coin=requireNotNull(scene.debugEntity("coin"))
            assertFalse(scene.canDrag(coin))
            assertFalse(scene.canTap(requireNotNull(scene.debugEntity("refill"))))
            vm.onEvent(CapsuleEvent.CoinDropped(0f))
            assertEquals(CapsulePhase.COIN_READY,vm.state.value.game.phase)
        }
        capture("title")
        tapPanel(480,220,114f,158f)
        awaitCondition("Settings must replace the title capsule"){
            shown(scene,"UI_TitleOptions")&&!shown(scene,"TitleScene")&&!shown(scene,"UI_TitleActions")
        }
        capture("settings")
        tapPanel(480,500,240f,381f)
        awaitCondition("Return must restore title"){shown(scene,"TitleScene")&&shown(scene,"UI_TitleActions")}

        tapPanel(480,220,240f,56f)
        awaitCondition("Native tutorial entry did not show positioning"){
            vm.state.value.flow.screen==ExperienceScreen.TUTORIAL&&shown(scene,"UI_Tutorial")
        }
        main{
            assertEquals(TutorialStep.POSITION,vm.state.value.flow.step)
            assertFalse(shown(scene,"UI_Series"))
            assertFalse(shown(scene,"UI_Bubble"))
            assertFalse(scene.canDrag(requireNotNull(scene.debugEntity("coin"))))
            assertTrue(scene.canDrag(requireNotNull(scene.debugEntity("scene"))))
        }
        main{
            for(name in listOf("TitleScene","UI_TitleLogo","UI_TitleActions","UI_TitleOptions"))assertFalse(shown(scene,name))
        }
        capture("position")
        tapPanel(480,540,350f,482f)
        awaitCondition("Position button did not advance"){vm.state.value.flow.step==TutorialStep.COIN}
        capture("coin")
        insertCoin(scene)
        main{
            assertEquals(TutorialStep.TURN,vm.state.value.flow.step)
            vm.onEvent(CapsuleEvent.Series(2))
            vm.onEvent(CapsuleEvent.Mode(ExperienceMode.STUDY))
            assertEquals(0,vm.state.value.game.series)
            assertEquals(ExperienceMode.PLAY,vm.state.value.game.mode)
            val crank=requireNotNull(scene.debugEntity("crank"))
            assertTrue(scene.canDrag(crank))
            scene.beginDrag(crank)
            repeat(4){scene.drag(crank,Vector3(.06f,0f,0f))}
            scene.endDrag(crank)
            assertEquals(CapsulePhase.DISPENSING,vm.state.value.game.phase)
        }
        awaitCondition("First capsule did not arrive"){vm.state.value.game.phase==CapsulePhase.CAPSULE_READY}
        main{
            assertEquals(TutorialStep.TAKE,vm.state.value.flow.step)
            val capsule=requireNotNull(scene.debugEntity("capsule"))
            assertTrue(scene.canHandleCapsule(capsule))
            scene.transformCapsule(Vector3(0f,.10f,.18f),1f,false)
            scene.endCapsuleTransform()
            assertEquals(TutorialStep.OPEN,vm.state.value.flow.step)
            scene.transformCapsule(Vector3.ZERO,1.8f,false)
            assertEquals("One hand must not complete the opening lesson",CapsulePhase.CAPSULE_TAKEN,vm.state.value.game.phase)
        }
        capture("open")
        main{scene.transformCapsule(Vector3.ZERO,1.5f,true);scene.endCapsuleTransform()}
        awaitCondition("Tutorial toy did not reveal"){vm.state.value.game.phase==CapsulePhase.REVEALED}
        awaitCondition("Store lesson must open the real album"){
            vm.state.value.flow.step==TutorialStep.STORE&&shown(scene,"ToyAlbum")
        }
        val toyId=main{requireNotNull(vm.state.value.flow.tutorialToyId)}
        val toy=main{requireNotNull(scene.root.findEntity("CollectedToy_$toyId"))}
        main{
            assertTrue(scene.canTransform(toy))
            scene.transformToy(toy,Vector3(-.10f,.04f,.16f),Quat.identity(),1.1f)
            scene.finishToyTransform()
            assertEquals(CapsulePhase.COIN_READY,vm.state.value.game.phase)
            assertEquals("Picking up a toy must not complete its storage lesson",ExperienceScreen.TUTORIAL,vm.state.value.flow.screen)
            assertFalse(vm.state.value.flow.tutorialCompleted)
        }
        capture("store")
        main{
            val album=requireNotNull(scene.root.findEntity("ToyAlbum"))
            val destination=album.convertPositionTo(Vector3.ZERO,scene.root)
            scene.transformToy(toy,destination-position(toy),Quat.identity(),1f)
            scene.finishToyTransform()
        }
        awaitCondition("Returning the actual toy must complete the tutorial"){
            vm.state.value.flow.screen==ExperienceScreen.COMPLETE&&vm.state.value.flow.tutorialCompleted
        }
        main{
            assertTrue(toyId in vm.state.value.game.storedIds)
            assertEquals(1,vm.state.value.game.toys.size)
            assertEquals(23,vm.state.value.game.stock)
            assertFalse(scene.canDrag(requireNotNull(scene.debugEntity("coin"))))
        }
        capture("complete")
        tapPanel(480,540,240f,482f)
        awaitCondition("Completion action did not unlock free play"){
            vm.state.value.flow.screen==ExperienceScreen.PLAY&&shown(scene,"UI_Series")
        }
        capture("free-play")
        val reopened=freshScene()
        main{
            assertEquals(ExperienceScreen.HOME,reopened.vm.state.value.flow.screen)
            assertTrue(reopened.vm.state.value.flow.tutorialCompleted)
            assertTrue(toyId in reopened.vm.state.value.game.storedIds)
        }
        // On a subsequent launch the same primary button goes straight to free play.
        tapPanel(480,220,240f,56f)
        awaitCondition("Completed players must be able to enter free play directly"){
            reopened.vm.state.value.flow.screen==ExperienceScreen.PLAY&&shown(reopened,"UI_Series")
        }
    }

    @Test fun skippingAndReturningHomePreserveAnUnfinishedRound()=isolatedPreferences {
        val scene=freshScene()
        val vm=scene.vm
        startFirstTutorial(scene)
        insertCoin(scene)
        main{
            vm.skipTutorial()
            assertEquals(ExperienceScreen.PLAY,vm.state.value.flow.screen)
            assertEquals(CapsulePhase.CREDITED,vm.state.value.game.phase)
            assertFalse(vm.state.value.flow.tutorialCompleted)
            val crank=requireNotNull(scene.debugEntity("crank"))
            scene.beginDrag(crank);repeat(4){scene.drag(crank,Vector3(.06f,0f,0f))};scene.endDrag(crank)
            assertEquals(CapsulePhase.DISPENSING,vm.state.value.game.phase)
            vm.openHome()
        }
        awaitCondition("Home must cover and disable the play scene"){
            shown(scene,"UI_TitleActions")&&!shown(scene,"UI_Toolbar")&&!shown(scene,"MachineHousing_0")
        }
        val paused=main{vm.state.value.game}
        main{
            assertFalse(scene.canDrag(requireNotNull(scene.debugEntity("coin"))))
            assertFalse(scene.canDrag(requireNotNull(scene.debugEntity("crank"))))
            assertFalse(scene.canHandleCapsule(requireNotNull(scene.debugEntity("capsule"))))
            vm.onEvent(CapsuleEvent.Tick(30f))
            vm.onEvent(CapsuleEvent.Refill)
            vm.startTutorial()
            assertEquals("Do not overwrite a paid round to replay the tutorial",paused,vm.state.value.game)
            assertEquals(ExperienceScreen.HOME,vm.state.value.flow.screen)
        }
        Thread.sleep(2500)
        assertEquals("The scene update loop must not animate a round behind the menu",paused,main{vm.state.value.game})
        capture("resume-menu")
        tapPanel(480,220,240f,56f)
        awaitCondition("Continue must restore the paid round"){vm.state.value.flow.screen==ExperienceScreen.PLAY}
        awaitCondition("Resumed round did not dispense"){vm.state.value.game.phase==CapsulePhase.CAPSULE_READY}
        main{
            assertEquals(23,vm.state.value.game.stock)
            assertEquals(paused.pending,vm.state.value.game.pending)
            assertFalse(vm.state.value.flow.tutorialCompleted)
            assertTrue(scene.canHandleCapsule(requireNotNull(scene.debugEntity("capsule"))))
        }
        capture("resumed-round")
    }
}
