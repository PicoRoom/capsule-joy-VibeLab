package com.bujiaban.niuqu.pico

import android.content.Intent
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bujiaban.niuqu.pico.domain.model.*
import com.bujiaban.niuqu.pico.scene.*
import com.bujiaban.niuqu.pico.platform.LaunchActivity
import com.pico.spatial.core.math.*
import com.pico.spatial.core.ecs.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class ExperienceImprovementsTest {
    private val i=InstrumentationRegistry.getInstrumentation()
    private fun <T> main(block:()->T):T{val r=AtomicReference<T>();i.runOnMainSync{r.set(block())};return r.get()}
    private fun await(message:String,timeout:Long=20000,test:()->Boolean){val end=SystemClock.elapsedRealtime()+timeout;while(SystemClock.elapsedRealtime()<end){if(main(test))return;Thread.sleep(100)};fail(message)}
    private fun tap(w:Int,h:Int,x:Float,y:Float){
        i.waitForIdleSync();Thread.sleep(1500)
        val view=main{android.view.inspector.WindowInspector.getGlobalWindowViews().first{it.width==w&&it.height==h}}
        val t=SystemClock.uptimeMillis()
        for(action in listOf(android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP)){
            main{android.view.MotionEvent.obtain(t,SystemClock.uptimeMillis(),action,x,y,0).also{view.dispatchTouchEvent(it);it.recycle()}};Thread.sleep(120)
        }
    }
    private fun capture(name:String){
        Thread.sleep(6000)
        val file=java.io.File(i.targetContext.getExternalFilesDir(null),"improved-$name.png")
        i.uiAutomation.executeShellCommand("screencap -p ${file.absolutePath}").use{p->android.os.ParcelFileDescriptor.AutoCloseInputStream(p).use{it.readBytes()}}
    }
    @Test fun realCrankTrackingRecoveryIndependentAlbumShelfAndStudy(){
        val prefs=i.targetContext.getSharedPreferences("paper_capsule_animals",android.content.Context.MODE_PRIVATE)
        val saved=prefs.getString("toys",null);val stored=prefs.getStringSet("stored_toy_ids",null)?.toSet()
        try {
        val previous=main{CapsuleSystem.scene}
        main{i.targetContext.startActivity(Intent(i.targetContext,LaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))}
        await("scene",180000){CapsuleSystem.scene?.let{it!==previous&&it.loaded&&it.volume.ready}==true}
        val scene=main{requireNotNull(CapsuleSystem.scene)};val vm=scene.vm
        main{
            vm.play();vm.onEvent(CapsuleEvent.CoinDropped(0f))
            val crank=requireNotNull(scene.debugEntity("crank"));scene.beginDrag(crank)
            repeat(10){scene.drag(crank,Vector3(.01f,0f,0f))}
            assertEquals(0f,vm.state.value.game.crank,.01f)
            scene.endDrag(crank);scene.beginDrag(crank);turnCrank(scene,crank);scene.endDrag(crank)
        }
        await("dispense"){vm.state.value.game.phase==CapsulePhase.CAPSULE_READY}
        main{scene.transformCapsule(Vector3(0f,.1f,.1f),1f,false);scene.endCapsuleTransform()
            scene.transformCapsule(Vector3.ZERO,1.2f,true);scene.cancelCapsuleTransform()
            // A stalled render frame must not extend the real-time grace deadline.
            Thread.sleep(350)
            scene.transformCapsule(Vector3.ZERO,1.25f,true)
            assertEquals(CapsulePhase.CAPSULE_TAKEN,vm.state.value.game.phase)
            scene.endCapsuleTransform()
            scene.transformCapsule(Vector3.ZERO,1.2f,true);scene.cancelCapsuleTransform()
            assertTrue(vm.state.value.trackingPaused);scene.transformCapsule(Vector3.ZERO,1.25f,true);scene.endCapsuleTransform()}
        await("reveal"){vm.state.value.game.phase==CapsulePhase.REVEALED}
        val count=main{vm.state.value.game.toys.size}
        tap(900,240,450f,100f)
        await("reveal button stores only the revealed toy"){vm.state.value.game.phase==CapsulePhase.COIN_READY}
        main{if(!vm.state.value.game.albumOpen)vm.onEvent(CapsuleEvent.ToggleAlbum)}
        tap(460,110,435f,25f)
        await("album next page"){vm.state.value.game.albumSeries==1}
        main{assertEquals(0,vm.state.value.game.series)}
        tap(460,110,25f,25f)
        await("album previous page"){vm.state.value.game.albumSeries==0}
        tap(240,132,120f,100f)
        await("display action applied"){vm.state.value.game.message.startsWith("已摆好")||vm.state.value.game.message.startsWith("整组伙伴")}
        main{assertEquals(count,vm.state.value.game.toys.size);assertTrue(vm.state.value.game.toys.any{it.id !in vm.state.value.game.storedIds})}
        await("shelf"){scene.root.findEntity("CollectionShelf")?.enabled==true}
        main{
            val toy=vm.state.value.game.toys.first{vm.state.value.definitions[it.toy].series==0}
            val slot=vm.state.value.definitions.indices.filter{vm.state.value.definitions[it].series==0}.indexOf(toy.toy)
            val e=requireNotNull(scene.root.findEntity("CollectedToy_${toy.id}"));val p=CollectionDisplay.position(slot)
            val at=requireNotNull(e.components[TransformComponent::class.java]).position
            scene.transformToy(e,Vector3(p[0]+.02f,p[1],p[2])-at,Quat.identity(),1f);scene.finishToyTransform()
            assertFalse(toy.id in vm.state.value.game.storedIds)
            assertEquals(p[0],vm.state.value.game.toys.first{it.id==toy.id}.x,.001f)
        }
        capture("collection")
        main{
            val toy=vm.state.value.game.toys.first{vm.state.value.definitions[it.toy].series==0}
            assertFalse("Displayed toy must remain outside storage after render frames",toy.id in vm.state.value.game.storedIds)
            assertTrue(requireNotNull(scene.root.findEntity("CollectedToy_${toy.id}")).enabled)
        }
        for(series in 0..2){
            main{vm.onEvent(CapsuleEvent.Series(series));vm.onEvent(CapsuleEvent.Mode(ExperienceMode.STUDY))}
            val before=main{vm.state.value.game}
            main{vm.startStudyDemo()}
            await("demo advances"){vm.state.value.studyTime>2.5f}
            main{assertEquals(before,vm.state.value.game);vm.stepStudyDemo();assertFalse(vm.state.value.studyRunning)}
            await("study capsule visible"){scene.root.findEntity("StudyDeliveryCapsule")?.enabled==true}
            capture("study-$series")
            main{vm.onEvent(CapsuleEvent.Mode(ExperienceMode.PLAY))}
        }
        } finally {
            main{CapsuleSystem.scene?.pause()}
            prefs.edit().apply{
                if(saved==null)remove("toys") else putString("toys",saved)
                if(stored==null)remove("stored_toy_ids") else putStringSet("stored_toy_ids",stored)
            }.commit()
        }
    }
}
