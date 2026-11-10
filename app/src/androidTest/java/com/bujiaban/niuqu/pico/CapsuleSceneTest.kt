package com.bujiaban.niuqu.pico

import android.content.Context
import android.content.Intent
import com.pico.spatial.core.container.getSelfSpaceState
import com.pico.spatial.core.container.SpaceState
import android.view.MotionEvent
import android.view.View
import android.view.inspector.WindowInspector
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bujiaban.niuqu.pico.data.repository.LocalCapsuleRepository
import com.bujiaban.niuqu.pico.domain.model.*
import com.bujiaban.niuqu.pico.platform.LaunchActivity
import com.bujiaban.niuqu.pico.scene.*
import com.pico.spatial.core.ecs.*
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.math.*
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Production callbacks and native meshes; no claim of automated hand tracking. */
@RunWith(AndroidJUnit4::class)
class CapsuleSceneTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private fun <T> main(block:()->T):T {
        val result=AtomicReference<T>();instrumentation.runOnMainSync{result.set(block())};return result.get()
    }
    private fun awaitCondition(message:String,timeout:Long=18000,block:()->Boolean){
        val end=android.os.SystemClock.elapsedRealtime()+timeout
        while(android.os.SystemClock.elapsedRealtime()<end){if(main(block))return;Thread.sleep(100)}
        fail(message)
    }
    private fun capture(name:String){
        Thread.sleep(6000)
        captureNow(name)
    }
    private fun captureNow(name:String){
        val file=java.io.File(instrumentation.targetContext.getExternalFilesDir(null),name)
        instrumentation.uiAutomation.executeShellCommand("screencap -p ${file.absolutePath}").use{descriptor->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use{it.readBytes()}
        }
    }
    private fun descendants(e:Entity):List<Entity> = listOf(e)+e.getChildren().flatMap(::descendants)
    private fun pullOpen(scene:CapsuleScene){
        scene.transformCapsule(Vector3.ZERO,1.5f,true)
        scene.endCapsuleTransform()
    }
    @Test fun unifiedCapsuleGestureOpensOnlyWithTwoHands(){
        val context=instrumentation.targetContext
        val previous=main{CapsuleSystem.scene}
        main{context.startActivity(Intent(context,LaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))}
        awaitCondition("Scene did not load",180000){CapsuleSystem.scene?.let{it!==previous&&it.loaded}==true}
        val scene=main{requireNotNull(CapsuleSystem.scene)}
        main{scene.vm.play()}
        awaitCondition("Play scene did not leave the welcome menu"){scene.root.findEntity("UI_Toolbar")?.enabled==true}
        main{
            scene.pause()
            val vm=scene.vm
            vm.onEvent(CapsuleEvent.Mode(ExperienceMode.PLAY))
            vm.onEvent(CapsuleEvent.CoinDropped(0f))
            repeat(4){vm.onEvent(CapsuleEvent.CrankMoved(90f))}
            vm.onEvent(CapsuleEvent.Tick(2.2f))
            vm.onEvent(CapsuleEvent.CapsulePulled(.1f))
            assertEquals(CapsulePhase.CAPSULE_TAKEN,vm.state.value.game.phase)
            scene.transformCapsule(Vector3.ZERO,2f,false)
            assertEquals(CapsulePhase.CAPSULE_TAKEN,vm.state.value.game.phase)
            scene.transformCapsule(Vector3.ZERO,.05f,true)
            assertEquals(CapsulePhase.CAPSULE_TAKEN,vm.state.value.game.phase)
            scene.transformCapsule(Vector3.ZERO,1.2f,true)
            scene.endCapsuleTransform()
            scene.transformCapsule(Vector3.ZERO,1.2f,true)
            assertEquals("Release must discard the first partial pull",CapsulePhase.CAPSULE_TAKEN,vm.state.value.game.phase)
            scene.transformCapsule(Vector3.ZERO,1.3f,true)
            assertEquals(CapsulePhase.OPENING,vm.state.value.game.phase)
            scene.endCapsuleTransform()
        }
    }
    @Test fun revealEffectsFollowPurchaseAndYieldToHands(){
        val context=instrumentation.targetContext
        val prefs=context.getSharedPreferences("paper_capsule_animals",Context.MODE_PRIVATE)
        val saved=prefs.getString("toys",null)
        val stored=prefs.getStringSet("stored_toy_ids",null)?.toSet()
        try{
            prefs.edit().remove("toys").remove("stored_toy_ids").commit()
            val previous=main{CapsuleSystem.scene}
            main{context.startActivity(Intent(context,LaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))}
            awaitCondition("Fresh scene did not load",180000){CapsuleSystem.scene?.let{it!==previous&&it.loaded&&it.volume.ready}==true}
            val scene=main{requireNotNull(CapsuleSystem.scene)}
            main{scene.vm.play()}
            awaitCondition("Play scene did not leave the welcome menu"){scene.root.findEntity("UI_Toolbar")?.enabled==true}
            val vm=scene.vm
            fun effect()=requireNotNull(scene.root.findEntity("RevealEffects"))
            val pool=main{effect().getChildren().map{it.id}}
            main{
                val box=requireNotNull(scene.root.findEntity("CoinBox"))
                assertTrue(box.getVisualBounds(scene.root).size.x>.18f)
                assertTrue(descendants(box).all{it.components[CollisionComponent::class.java]==null})
                assertNotNull(box.findEntity("CoinBoxStack_6"))
            }
            capture("coin-box-ready.png")
            for(series in 0..2){
                main{
                    vm.onEvent(CapsuleEvent.Series(series))
                    val coin=requireNotNull(scene.debugEntity("coin"))
                    val transform=requireNotNull(coin.components[TransformComponent::class.java])
                    assertEquals(CapsuleLayout.coinHome,transform.position)
                    scene.beginDrag(coin);scene.drag(coin,Vector3(-.08f,.04f,.10f));scene.endDrag(coin)
                    assertEquals(CapsulePhase.COIN_READY,vm.state.value.game.phase)
                    assertEquals("Missed coins return to the caddy",CapsuleLayout.coinHome,transform.position)
                    scene.beginDrag(coin)
                    scene.drag(coin,CapsuleLayout.machinePosition+Vector3(0f,.43f,-1f)-transform.position)
                    assertEquals(CapsulePhase.COIN_READY,vm.state.value.game.phase)
                    val body=requireNotNull(scene.root.findEntity("MachineHousing_$series")).getVisualBounds(scene.root)
                    assertTrue("Coin must stay in front of the housing",transform.position.z>=body.max.z)
                    scene.cancelDrag(null)
                    assertEquals(CapsuleLayout.coinHome,transform.position)
                    scene.beginDrag(coin);scene.drag(coin,Vector3(-.08f,0f,.08f));scene.endDrag(null)
                    assertEquals("Missing release target must still return the coin",CapsuleLayout.coinHome,transform.position)
                    scene.beginDrag(coin)
                    // Approach slowly: the cabinet front must not trap the coin before it reaches the slot.
                    scene.drag(coin,scene.coinSlot+Vector3(.04f,0f,.3f)-transform.position)
                    repeat(50){scene.drag(coin,Vector3(0f,0f,-.01f))}
                    assertEquals("Entering slot range credits before release",CapsulePhase.CREDITED,vm.state.value.game.phase)
                    val accepted=transform.position
                    scene.drag(coin,Vector3(0f,0f,-2f));scene.cancelDrag(null)
                    assertEquals("Accepted coins no longer follow the hand",accepted,transform.position)
                    assertFalse(scene.canDrag(coin))
                }
                awaitCondition("Accepted coin must disappear after its short insertion"){
                    scene.debugEntity("coin")?.enabled==false
                }
                main{
                    assertEquals(CapsulePhase.CREDITED,vm.state.value.game.phase)
                    if(series==2){
                        val s=vm.state.value.game
                        vm.onEvent(CapsuleEvent.ClawMoved(.40f-s.clawX,-.40f-s.clawZ));vm.onEvent(CapsuleEvent.ClawGrab)
                    } else repeat(4){vm.onEvent(CapsuleEvent.CrankMoved(90f))}
                }
                awaitCondition("Purchase did not dispense"){vm.state.value.game.phase==CapsulePhase.CAPSULE_READY}
                main{vm.onEvent(CapsuleEvent.CapsulePulled(.1f))
                    scene.transformCapsule(Vector3(0f,.05f,0f),2f,false)
                    assertEquals("One grip must not open the capsule",CapsulePhase.CAPSULE_TAKEN,vm.state.value.game.phase)
                    scene.endCapsuleTransform()
                    scene.transformCapsule(Vector3.ZERO,.05f,true)
                    assertEquals("Squeezing must not open the capsule",CapsulePhase.CAPSULE_TAKEN,vm.state.value.game.phase)
                    scene.transformCapsule(Vector3.ZERO,1.15f,true);scene.endCapsuleTransform()
                    assertEquals(CapsulePhase.CAPSULE_TAKEN,vm.state.value.game.phase)
                    assertEquals(Vector3(0f,0f,-.0235f),requireNotNull(scene.debugEntity("lid")!!.components[TransformComponent::class.java]).position)
                    pullOpen(scene)}
                awaitCondition("Opening particles missing"){vm.state.value.game.phase==CapsulePhase.OPENING&&effect().enabled}
                main{
                    assertEquals(12,effect().getChildren().count{it.enabled})
                    assertTrue(descendants(effect()).all{it.components[CollisionComponent::class.java]==null})
                }
                if(series==0){
                    awaitCondition("Shells did not separate"){vm.state.value.game.phase==CapsulePhase.OPENING&&vm.state.value.game.elapsed>=.6f}
                    main{scene.pause()}
                    try{capture("two-hand-open.png")}finally{main{scene.resume()}}
                }
                awaitCondition("Toy not revealed"){vm.state.value.game.phase==CapsulePhase.REVEALED}
                val toy=main{requireNotNull(scene.root.findEntity("CollectedToy_${vm.state.value.game.activeToy}"))}
                val visual=main{toy.getChildren().first()}
                main{assertEquals(TOY_DEFAULT_SCALE,requireNotNull(toy.components[TransformComponent::class.java]).scaleVector.x,.001f)}
                awaitCondition("Discovery ring missing"){effect().getChildren().count{it.enabled}==36}
                captureNow("reveal-effects-$series.png")
                if(series==0){
                    awaitCondition("Celebration did not finish"){!effect().enabled}
                    assertFalse(main{requireNotNull(scene.debugEntity("capsule")).enabled})
                }else if(series==1){
                    main{
                        assertTrue(scene.canTransform(toy))
                        fun size()=requireNotNull(toy.components[TransformComponent::class.java]).scaleVector.x
                        scene.transformToy(toy,Vector3.ZERO,Quat.identity(),1.5f);assertEquals(11.25f,size(),.001f)
                        scene.transformToy(toy,Vector3.ZERO,Quat.identity(),2f);assertEquals(16f,size(),.001f)
                        scene.transformToy(toy,Vector3.ZERO,Quat.identity(),.5f);assertEquals(8f,size(),.001f)
                        scene.transformToy(toy,Vector3.ZERO,Quat.identity(),Float.NaN);assertEquals(8f,size(),.001f)
                        scene.transformToy(toy,Vector3.ZERO,Quat.identity(),.5f);assertEquals(4f,size(),.001f)
                        scene.finishToyTransform()
                        assertEquals(4f,LocalCapsuleRepository(context).read().last().scale,.001f)
                    }
                    assertFalse(main{effect().enabled})
                }else{
                    main{scene.pause()};assertFalse(main{effect().enabled});main{scene.resume()}
                }
                main{
                    assertEquals("Restore the authored GLB metre conversion",.1f,requireNotNull(visual.components[TransformComponent::class.java]).scaleVector.x,.001f)
                    assertEquals(pool,effect().getChildren().map{it.id})
                    vm.onEvent(CapsuleEvent.StoreAll)
                }
                awaitCondition("Stored toy remained visible"){!toy.enabled}
                if(series==1){
                    main{
                        assertFalse(scene.canTransform(toy))
                        if(!vm.state.value.game.albumOpen)vm.onEvent(CapsuleEvent.ToggleAlbum)
                        vm.onEvent(CapsuleEvent.AlbumPage(vm.state.value.game.series))
                        vm.onEvent(CapsuleEvent.TakeFromAlbum(vm.state.value.game.toys.last().toy))
                    }
                    awaitCondition("Stored scale was not restored"){
                        toy.enabled&&kotlin.math.abs(requireNotNull(toy.components[TransformComponent::class.java]).scaleVector.x-4f)<.001f
                    }
                    main{
                        val book=requireNotNull(scene.root.findEntity("ToyAlbum"))
                        val destination=book.convertPositionTo(Vector3.ZERO,scene.root)
                        val current=requireNotNull(toy.components[TransformComponent::class.java]).position
                        scene.transformToy(toy,destination-current,Quat.identity(),1.5f)
                        scene.finishToyTransform()
                        assertEquals(6f,LocalCapsuleRepository(context).read().last().scale,.001f)
                        assertTrue(vm.state.value.game.toys.last().id in vm.state.value.game.storedIds)
                    }
                }
            }
            assertEquals(3,main{vm.state.value.game.toys.size})
        }finally{
            val edit=prefs.edit()
            if(saved==null)edit.remove("toys") else edit.putString("toys",saved)
            if(stored==null)edit.remove("stored_toy_ids") else edit.putStringSet("stored_toy_ids",stored)
            edit.commit()
        }
    }
    @Test fun sharedSpaceContainsHandheldObjectsAndScenePlacement(){
        val context=instrumentation.targetContext
        val prefs=context.getSharedPreferences("paper_capsule_animals",Context.MODE_PRIVATE)
        val saved=prefs.getString("toys",null)
        val stored=prefs.getStringSet("stored_toy_ids",null)?.toSet()
        try{
            val previous=main{CapsuleSystem.scene}
            main{context.startActivity(Intent(context,LaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))}
            awaitCondition("Shared-space scene did not load",240000){CapsuleSystem.scene?.let{it!==previous&&it.loaded&&it.volume.ready}==true}
            val scene=main{requireNotNull(CapsuleSystem.scene)}
            main{scene.vm.play()}
            awaitCondition("Play scene did not leave the welcome menu"){scene.root.findEntity("UI_Toolbar")?.enabled==true}
            assertEquals(SpaceState.SHARED_SPACE,context.getSelfSpaceState())
            fun transform(e:Entity)=requireNotNull(e.components[TransformComponent::class.java])
            val initialPosition=main{transform(scene.root).position}
            main{
                for(name in listOf("UI_Series","UI_Status","UI_Toolbar")){
                    val panel=requireNotNull(scene.root.findEntity(name))
                    assertTrue("$name must be enabled after stage placement",panel.enabled)
                    assertTrue("$name must have a paper backing",requireNotNull(panel.findEntity("PanelBacking")).enabled)
                }
                scene.pause()
                val vm=scene.vm
                assertTrue(transform(scene.root).scaleVector.x>0f)
                assertEquals(18,vm.state.value.definitions.size)
                val owner=transform(scene.root)
                val before=owner.position
                scene.volume.move(Vector3(.2f,0f,0f))
                assertTrue((owner.position-before).length()>0f)
                vm.onEvent(CapsuleEvent.CoinDropped(0f))
                repeat(4){vm.onEvent(CapsuleEvent.CrankMoved(90f))}
                vm.onEvent(CapsuleEvent.Tick(2.2f))
                vm.onEvent(CapsuleEvent.CapsulePulled(.1f))
                val capsule=requireNotNull(scene.debugEntity("capsule"))
                val original=transform(capsule).position
                scene.transformCapsule(Vector3(0f,0f,2f),1f,false)
                assertTrue(transform(capsule).position.z<original.z+2f)
                pullOpen(scene)
                vm.onEvent(CapsuleEvent.Tick(1.4f,0f,1.2f,1.8f))
                scene.resume()
            }
            awaitCondition("Toy did not appear"){
                scene.vm.state.value.game.activeToy?.let{scene.root.findEntity("CollectedToy_$it")}!=null
            }
            main{
                scene.pause()
                val id=requireNotNull(scene.vm.state.value.game.activeToy)
                val toy=requireNotNull(scene.root.findEntity("CollectedToy_$id"))
                val start=transform(toy).position
                scene.transformToy(toy,Vector3(0f,0f,2f),Quat.identity(),1f)
                scene.finishToyTransform()
                assertTrue(transform(toy).position.z<start.z+2f)
                assertTrue(transform(toy).scaleVector.x<=TOY_DEFAULT_SCALE)
                assertEquals(transform(toy).position.z,LocalCapsuleRepository(context).read().last().z,.001f)
                scene.resume()
            }
            fun toolbarView():View?=WindowInspector.getGlobalWindowViews().firstOrNull{
                it.isAttachedToWindow&&it.width==CapsuleLayout.TOOLBAR_PIXELS_W&&it.height==CapsuleLayout.TOOLBAR_PIXELS_H
            }
            fun tapToolbar(x:Float){
                awaitCondition("Current toolbar native composition did not attach"){toolbarView()!=null}
                Thread.sleep(400)
                val toolbar=main{requireNotNull(toolbarView())}
                val down=android.os.SystemClock.uptimeMillis()
                main{MotionEvent.obtain(down,down,MotionEvent.ACTION_DOWN,x,122f,0).also{toolbar.dispatchTouchEvent(it);it.recycle()}}
                Thread.sleep(120)
                main{MotionEvent.obtain(down,android.os.SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,x,122f,0).also{toolbar.dispatchTouchEvent(it);it.recycle()}}
            }
            tapToolbar(180f)
            awaitCondition("Reset button must restore the window-relative placement"){
                (transform(scene.root).position-initialPosition).length()<.01f
            }
            tapToolbar(640f)
            awaitCondition("Exit button did not dispose the shared window scene"){CapsuleSystem.scene!==scene}
        }finally{
            val edit=prefs.edit()
            if(saved==null)edit.remove("toys") else edit.putString("toys",saved)
            if(stored==null)edit.remove("stored_toy_ids") else edit.putStringSet("stored_toy_ids",stored)
            edit.commit()
        }
    }
    @Test fun clawAimPanelInSharedSpace(){
        val context=instrumentation.targetContext
        val previous=main{CapsuleSystem.scene}
        main{context.startActivity(Intent(context,LaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))}
        awaitCondition("Assets did not load",180000){CapsuleSystem.scene?.let{it!==previous&&it.loaded}==true}
        val scene=main{requireNotNull(CapsuleSystem.scene)}
        main{scene.vm.play()}
        awaitCondition("Play scene did not leave the welcome menu"){scene.root.findEntity("UI_Toolbar")?.enabled==true}
        val vm=scene.vm
        fun state()=vm.state.value.game
        main{vm.onEvent(CapsuleEvent.Series(2))}
        fun panelView():View?=WindowInspector.getGlobalWindowViews().firstOrNull{
            it.width==CapsuleLayout.CLAW_PANEL_PIXELS_W&&it.height==CapsuleLayout.CLAW_PANEL_PIXELS_H
        }
        awaitCondition("Panel must be present"){scene.root.findEntity("UI_ClawControls")?.enabled==true&&panelView()!=null}
        val view=main{requireNotNull(panelView())}
        fun touch(action:Int,x:Float,y:Float,down:Long){main{
            MotionEvent.obtain(down,android.os.SystemClock.uptimeMillis(),action,x,y,0).also{view.dispatchTouchEvent(it);it.recycle()}
        }}
        fun tap(x:Float,y:Float){val t=android.os.SystemClock.uptimeMillis();touch(MotionEvent.ACTION_DOWN,x,y,t);Thread.sleep(100);touch(MotionEvent.ACTION_UP,x,y,t)}
        tap(500f,160f)
        assertEquals("Drop cannot buy credit",CapsulePhase.COIN_READY,state().phase)
        tap(500f,280f)
        awaitCondition("Auxiliary coin button must work"){state().phase==CapsulePhase.CREDITED}
        awaitCondition("Landing marker must be visible"){scene.root.findEntity("ClawAimIndicator")?.enabled==true}
        val before=state()
        val t=android.os.SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN,260f,210f,t)
        repeat(6){i->Thread.sleep(40);touch(MotionEvent.ACTION_MOVE,260f-(i+1)*14f,210f-(i+1)*10f,t)}
        awaitCondition("Dragging must move both axes"){state().clawX<before.clawX-.1f&&state().clawZ<before.clawZ-.1f}
        val held=state();Thread.sleep(250)
        assertEquals("Stationary pinch cannot drift",held.clawX,state().clawX,.001f)
        touch(MotionEvent.ACTION_UP,176f,150f,t)
        awaitCondition("Release must stop aiming"){!vm.state.value.clawControls.aiming}
        assertEquals(CapsulePhase.CREDITED,state().phase)
        val stopped=state();Thread.sleep(250);assertEquals(stopped.clawX,state().clawX,.001f)
        val cancel=android.os.SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN,200f,170f,cancel)
        touch(MotionEvent.ACTION_MOVE,220f,190f,cancel)
        touch(MotionEvent.ACTION_CANCEL,220f,190f,cancel)
        awaitCondition("Cancelled gesture must stop"){!vm.state.value.clawControls.aiming}
        main{
            vm.beginClawAim();scene.pause();assertFalse(vm.state.value.clawControls.aiming);scene.resume()
            vm.onEvent(CapsuleEvent.Mode(ExperienceMode.STUDY))
        }
        awaitCondition("Study hides marker"){scene.root.findEntity("ClawAimIndicator")?.enabled==false}
        main{
            vm.onEvent(CapsuleEvent.Mode(ExperienceMode.PLAY))
            vm.beginClawAim();vm.dragClawAim(.4f-state().clawX,.4f-state().clawZ);vm.endClawAim()
        }
        awaitCondition("Marker restores with aim mode"){scene.root.findEntity("ClawAimIndicator")?.enabled==true}
        main{
            val ring=requireNotNull(scene.root.findEntity("ClawLandingRing"))
            val transform=requireNotNull(ring.components[TransformComponent::class.java])
            assertEquals(.04f,transform.position.x,.001f);assertEquals(.04f,transform.position.z,.001f)
            val bounds=ring.getVisualBounds(scene.root)
            assertTrue("Landing ring must lie horizontally",bounds.size.y<bounds.size.x*.3f)
            assertTrue("Marker must fit a capsule",bounds.size.x in .06f.. .09f)
            val guide=requireNotNull(scene.root.findEntity("ClawLandingGuide")).getVisualBounds(scene.root)
            assertTrue("Guide must be thin and vertical",guide.size.x<.005f&&guide.size.y>guide.size.x*5f)
        }
        capture("claw-aim-ready.png")
        tap(500f,160f)
        awaitCondition("Independent drop must start grab"){state().phase==CapsulePhase.CLAWING}
        awaitCondition("Marker hides during animation"){scene.root.findEntity("ClawAimIndicator")?.enabled==false}
        awaitCondition("Grab must finish"){state().phase==CapsulePhase.CAPSULE_READY}
        assertEquals(23,state().stock)
        capture("claw-aim-delivered.png")
        tap(500f,160f);assertEquals(CapsulePhase.CAPSULE_READY,state().phase)
        tap(500f,280f)
        awaitCondition("Auxiliary take still works"){state().phase==CapsulePhase.CAPSULE_TAKEN}
        capture("claw-aim-taken.png")
    }
    @Test fun frontPanelsUseTheirAuthoredControls(){
        val context=instrumentation.targetContext
        val prefs=context.getSharedPreferences("paper_capsule_animals",Context.MODE_PRIVATE)
        val saved=prefs.getString("toys",null)
        val savedStored=prefs.getStringSet("stored_toy_ids",null)?.toSet()
        try{
            val previous=main{CapsuleSystem.scene}
            main{context.startActivity(Intent(context,LaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))}
            awaitCondition("Assets did not load",180000){CapsuleSystem.scene?.let{it!==previous&&it.loaded}==true}
            val scene=main{requireNotNull(CapsuleSystem.scene)}
            main{scene.vm.play()}
            awaitCondition("Play scene did not leave the welcome menu"){scene.root.findEntity("UI_Toolbar")?.enabled==true}
            fun state()=main{scene.vm.state.value.game}
            fun position(e:Entity)=requireNotNull(e.components[TransformComponent::class.java]).position
            fun drag(kind:String,delta:Vector3){main{
                val entity=requireNotNull(scene.debugEntity(kind));assertTrue(scene.canDrag(entity))
                scene.beginDrag(entity);scene.drag(entity,delta);scene.endDrag(entity)
            }}
            fun coinTo(point:Vector3)=drag("coin",point-main{position(requireNotNull(scene.debugEntity("coin")))})
            main{scene.vm.onEvent(CapsuleEvent.Mode(ExperienceMode.PLAY));scene.vm.onEvent(CapsuleEvent.StoreAll)}
            for(series in 0..1){
                main{scene.vm.onEvent(CapsuleEvent.Series(series))}
                awaitCondition("Front panel core did not switch"){scene.root.findEntity("MachineCore_$series")?.enabled==true}
                main{
                    val crank=requireNotNull(scene.debugEntity("crank"))
                    assertTrue(crank.enabled);assertNotNull(crank.components[CollisionComponent::class.java])
                    assertEquals(if(series==0).062f else 0f,position(crank).x,.001f)
                    assertEquals(if(series==0).140f else .145f,position(crank).y,.001f)
                    assertEquals(CapsuleLayout.machinePosition.x+(if(series==0).062f else 0f)*CapsuleLayout.MACHINE_SCALE,scene.coinSlot.x,.001f)
                }
                capture("front-panel-$series.png")
                if(series==0){
                    coinTo(CapsuleLayout.machinePosition+Vector3(-.10f,.188f,.091f)*CapsuleLayout.MACHINE_SCALE)
                    assertEquals("A coin outside the slot attraction radius must not be accepted",CapsulePhase.COIN_READY,state().phase)
                }
                coinTo(main{scene.coinSlot});assertEquals(CapsulePhase.CREDITED,state().phase)
                main{val crank=requireNotNull(scene.debugEntity("crank"));scene.beginDrag(crank);turnCrank(scene,crank);scene.endDrag(crank)}
                assertEquals(CapsulePhase.DISPENSING,state().phase)
                awaitCondition("Panel-specific delivery animation did not finish"){scene.vm.state.value.game.phase==CapsulePhase.CAPSULE_READY}
                val capsule=main{requireNotNull(scene.debugEntity("capsule"))}
                assertEquals(CapsuleLayout.capsuleRest.x,main{position(capsule).x},.002f)
                assertEquals(CapsuleLayout.capsuleRest.y,main{position(capsule).y},.002f)
                drag("capsule",Vector3(.06f,.12f,.2f));main{pullOpen(scene)}
                awaitCondition("Panel-specific capsule did not reveal its toy"){scene.vm.state.value.game.phase==CapsulePhase.REVEALED}
                main{scene.vm.onEvent(CapsuleEvent.StoreAll)}
                awaitCondition("Collecting must unlock the next machine"){scene.vm.state.value.game.phase==CapsulePhase.COIN_READY}
            }
        }finally{
            val edit=prefs.edit()
            if(saved==null)edit.remove("toys") else edit.putString("toys",saved)
            if(savedStored==null)edit.remove("stored_toy_ids") else edit.putStringSet("stored_toy_ids",savedStored)
            edit.commit()
        }
    }
    @Test fun redesignedControlsFollowSeriesAndMode(){
        val context=instrumentation.targetContext
        val previous=main{CapsuleSystem.scene}
        main{context.startActivity(Intent(context,LaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))}
        awaitCondition("Assets did not load",180000){CapsuleSystem.scene?.let{it!==previous&&it.loaded}==true}
        val scene=main{requireNotNull(CapsuleSystem.scene)}
        main{scene.vm.play()}
        awaitCondition("Play scene did not leave the welcome menu"){scene.root.findEntity("UI_Toolbar")?.enabled==true}
        main{scene.vm.onEvent(CapsuleEvent.Mode(ExperienceMode.PLAY))}
        for(series in 0..2){
            main{scene.vm.onEvent(CapsuleEvent.Series(series))}
            awaitCondition("Miniatures did not update"){
                scene.vm.state.value.definitions.indices.all{index->scene.root.findEntity("SeriesMiniature_$index")?.enabled==(scene.vm.state.value.definitions[index].series==series)}
            }
            main{
                for(index in 0..2){
                    assertEquals(index==series,scene.root.findEntity("MachineHousing_$index")?.enabled)
                    assertFalse(requireNotNull(scene.root.findEntity("StudyHousing_$index")).enabled)
                }
                val bounds=requireNotNull(scene.root.findEntity("MachineHousing_$series")).getVisualBounds(scene.root)
                assertTrue("Housing must retain authored metre scale",bounds.size.y in .60f.. .82f)
                assertTrue("Theme silhouette must fit beside album",bounds.size.x in .30f.. .55f)
                val control=requireNotNull(scene.debugEntity(if(series==2)"joystick" else "crank"))
                val slot=scene.coinSlot
                assertTrue("Controls and slot must stay reachable",control.getVisualBounds(scene.root).center.y<slot.y)
            }
            capture("machine-series-$series.png")
        }
        main{scene.vm.onEvent(CapsuleEvent.Mode(ExperienceMode.STUDY))}
        awaitCondition("Study mode must hide catalogue and book"){
            scene.root.findEntity("SeriesPreview")?.enabled==false&&scene.root.findEntity("ToyAlbum")?.enabled==false&&scene.root.findEntity("UI_Series")?.enabled==false&&scene.root.findEntity("UI_StudyGuide")?.enabled==true
        }
        main{
            assertTrue(requireNotNull(scene.root.findEntity("StudyMachine")?.getParent()).enabled)
            for(index in 0..2){
                assertEquals(index==scene.vm.state.value.game.series,scene.root.findEntity("StudyHousing_$index")?.enabled)
                assertFalse(requireNotNull(scene.root.findEntity("MachineHousing_$index")).enabled)
            }
        }
        Thread.sleep(12000)
        capture("redesigned-study.png")
        main{scene.vm.onEvent(CapsuleEvent.Mode(ExperienceMode.PLAY));scene.vm.onEvent(CapsuleEvent.Series(0))}
        awaitCondition("Play mode must restore catalogue"){
            scene.root.findEntity("SeriesPreview")?.enabled==true&&scene.root.findEntity("UI_Series")?.enabled==true&&scene.root.findEntity("UI_StudyGuide")?.enabled==false
        }
        Thread.sleep(12000)
        capture("redesigned-play.png")
    }
    @Test fun realAssetsAndDirectInteractionRoundTrip(){
        val context=instrumentation.targetContext
        val prefs=context.getSharedPreferences("paper_capsule_animals",Context.MODE_PRIVATE)
        val saved=prefs.getString("toys",null)
        val savedStored=prefs.getStringSet("stored_toy_ids",null)?.toSet()
        try {
            val previous=main{CapsuleSystem.scene}
            main{context.startActivity(Intent(context,LaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))}
            awaitCondition("Assets did not load",180000){CapsuleSystem.scene?.let{it!==previous&&it.loaded}==true}
            val scene=main{requireNotNull(CapsuleSystem.scene)}
            main{scene.vm.play()}
            awaitCondition("Play scene did not leave the welcome menu"){scene.root.findEntity("UI_Toolbar")?.enabled==true}
            fun state()=main{scene.vm.state.value.game}
            fun position(e:Entity)=requireNotNull(e.components[TransformComponent::class.java]).position
            fun move(entity:Entity,delta:Vector3){main{
                assertTrue(scene.canDrag(entity))
                scene.beginDrag(entity);scene.drag(entity,delta);scene.endDrag(entity)
            }}
            fun drag(kind:String,delta:Vector3)=move(main{requireNotNull(scene.debugEntity(kind))},delta)
            main{
                val portals=descendants(scene.root).filter{it.components[PortalComponent::class.java]!=null}
                assertEquals(6,portals.size)
                assertNull(scene.root.findEntity("control"))
                val bubble=requireNotNull(scene.root.findEntity("UI_Bubble"))
                assertEquals(CapsuleLayout.bubblePosition.y,position(bubble).y,.001f)
                val catalogue=requireNotNull(scene.root.findEntity("UI_Series"))
                assertTrue("Series navigation belongs to one catalogue",catalogue.enabled)
                assertTrue(requireNotNull(scene.root.findEntity("UI_Toolbar")).enabled)
                assertTrue(requireNotNull(scene.root.findEntity("UI_Status")).enabled)
                for(index in 0..8)assertNull("Detached action attachments were removed",scene.root.findEntity("UI_Action_$index"))
                val coin=requireNotNull(scene.debugEntity("coin"))
                assertTrue(coin.getVisualBounds(scene.root).size.x in .034f.. .040f)
                assertNotNull(coin.components[CollisionComponent::class.java]);assertNotNull(coin.components[InteractableComponent::class.java])
            }
            val count=state().toys.size
            for(series in 0..2){
                main{scene.vm.onEvent(CapsuleEvent.Series(series))}
                awaitCondition("Series miniatures did not follow selection"){
                    scene.vm.state.value.definitions.indices.all{index->
                        scene.root.findEntity("SeriesMiniature_$index")?.enabled==(scene.vm.state.value.definitions[index].series==series)
                    }
                }
                assertEquals("Catalogue previews must not mint owned toys",count,state().toys.size)
                capture("series-selector-$series.png")
            }
            main{scene.vm.onEvent(CapsuleEvent.Series(0))}
            main{scene.vm.onEvent(CapsuleEvent.StoreAll)}
            awaitCondition("Stored models must disappear from room"){
                scene.vm.state.value.game.toys.all{scene.root.findEntity("CollectedToy_${it.id}")?.enabled==false}
            }
            capture("finite-album-layout.png")
            main{scene.vm.onEvent(CapsuleEvent.Series(2))}
            awaitCondition("Sky housing not selected"){scene.root.findEntity("MachineHousing_2")?.enabled==true}
            drag("coin",scene.coinSlot-CapsuleLayout.coinHome)
            assertEquals(CapsulePhase.CREDITED,state().phase)
            fun aim(x:Float,z:Float){
                val s=state();drag("joystick",Vector3((x-s.clawX)*.1f*CapsuleLayout.MACHINE_SCALE/2f,0f,(z-s.clawZ)*.1f*CapsuleLayout.MACHINE_SCALE/2f))
            }
            fun grab(){main{val button=requireNotNull(scene.debugEntity("grab"));assertTrue(scene.canTap(button));scene.tap(button)}}
            main{
                assertNull("The crane must not expose a capsule crank",scene.debugEntity("crank"))
                assertTrue(requireNotNull(scene.root.findEntity("MachineCore_2")).enabled)
                assertFalse(requireNotNull(scene.root.findEntity("MachineCore_0")).enabled)
            }
            aim(0f,0f);grab()
            assertEquals(CapsulePhase.CLAWING,state().phase);assertNull("Empty centre must miss",state().clawTarget)
            awaitCondition("Missed claw cycle did not finish"){scene.vm.state.value.game.phase==CapsulePhase.COIN_READY}
            assertEquals("A miss must leave all capsules in the cabinet",24,state().stock)
            drag("coin",scene.coinSlot-CapsuleLayout.coinHome)
            aim(.40f,-.40f);grab()
            assertEquals(CapsulePhase.CLAWING,state().phase)
            val caught=requireNotNull(state().clawTarget)
            awaitCondition("Claw did not lift its selected capsule"){
                val s=scene.vm.state.value.game
                s.phase==CapsulePhase.CLAWING&&s.elapsed in 2.6f..3.7f
            }
            main{
                val assembly=requireNotNull(scene.root.findEntity("ClawAssembly"))
                val hoist=requireNotNull(assembly.findEntity("ClawHoist"))
                assertTrue("Hoist must rise after closing",position(hoist).y>-.30f)
                val finger=requireNotNull(assembly.findEntity("ClawProng0"))
                assertTrue("Claw fingers must close during carry",kotlin.math.abs(requireNotNull(finger.components[TransformComponent::class.java]).quaternion.z)>.1f)
                val ball=requireNotNull(scene.root.findEntity("ClawCapsules")?.findEntity("HopperBall$caught"))
                assertTrue("The selected visible capsule must travel with the crane",position(ball).y>3.5f)
                val bounds=ball.getVisualBounds(scene.root,enabledOnly=true)
                assertTrue("The carried capsule must remain visibly enabled",bounds.size.x>.04f)
                val grip=hoist.convertPositionTo(Vector3(0f,-.29f,0f),scene.root)
                val delta=bounds.center-grip
                assertTrue("Visible capsule geometry must sit inside the fingers",kotlin.math.sqrt(delta.x*delta.x+delta.y*delta.y+delta.z*delta.z)<.02f)
            }
            captureNow("claw-carry.png")
            awaitCondition("Capsule not dispensed"){scene.vm.state.value.game.phase==CapsulePhase.CAPSULE_READY}
            assertEquals(23,state().stock);assertEquals("The delivered capsule must retain the caught capsule colour",caught%CapsuleColor.entries.size,state().capsuleColor)
            assertFalse(caught in state().clawAvailable)
            main{
                val material=descendants(requireNotNull(scene.debugEntity("capsule"))).flatMap{it.components[ModelComponent::class.java]?.materials?.toList()?:emptyList()}
                    .filterIsInstance<PhysicallyBasedMaterial>().first{it.getName().contains("CapsuleTint")}
                val expected=Color4.fromSRGBHex(CapsuleColor.entries[scene.vm.state.value.game.capsuleColor].bottom)
                assertEquals(expected.red,material.getBaseColor().red,.001f)
            }
            drag("capsule",Vector3(.06f,.12f,.2f));assertEquals(CapsulePhase.CAPSULE_TAKEN,state().phase)
            main{pullOpen(scene)}
            awaitCondition("No revealed toy"){scene.vm.state.value.game.phase==CapsulePhase.REVEALED}
            assertEquals(count+1,state().toys.size)
            assertEquals(2,state().series)
            assertEquals(2,main{scene.vm.state.value.definitions[scene.vm.state.value.game.toys.last().toy].series})
            val won=state().toys.last()
            val toy=main{requireNotNull(scene.root.findEntity("CollectedToy_${won.id}"))}
            main{
                assertTrue(toy.getVisualBounds(scene.root,enabledOnly=true).size.y>.01f)
                assertTrue(descendants(toy).flatMap{it.components[ModelComponent::class.java]?.materials?.toList()?:emptyList()}.all{it is PhysicallyBasedMaterial})
            }
            move(toy,Vector3(-.02f,.85f,.40f)-main{position(toy)})
            assertEquals(CapsulePhase.COIN_READY,state().phase)
            assertEquals(.85f,main{LocalCapsuleRepository(context).read().last().y},.001f)
            main{
                scene.vm.onEvent(CapsuleEvent.StoreAll)
                if(!scene.vm.state.value.game.albumOpen)scene.vm.onEvent(CapsuleEvent.ToggleAlbum)
                scene.vm.onEvent(CapsuleEvent.AlbumPage(scene.vm.state.value.game.series))
            }
            awaitCondition("Opening the album must expose its portal cells"){scene.root.findEntity("ToyAlbum")?.enabled==true}
            val available=state().available(won.toy)
            val slot=main{scene.vm.state.value.definitions.indices.filter{scene.vm.state.value.definitions[it].series==scene.vm.state.value.game.series}.indexOf(won.toy)}
            val portal=main{requireNotNull(scene.root.findEntity("AlbumPortal_$slot"))}
            val preview=main{requireNotNull(scene.root.findEntity("AlbumPreview_${won.toy}"))}
            val ids=state().toys.map{it.id}.toSet()
            repeat(available+1){main{scene.tap(portal)}}
            assertEquals(0,state().available(won.toy));assertEquals(count+1,state().toys.size)
            assertEquals(ids,state().toys.map{it.id}.toSet())
            assertFalse(main{scene.canDrag(portal)})
            assertEquals(preview.id,main{scene.root.findEntity("AlbumPreview_${won.toy}")?.id})
            capture("finite-album-empty.png")
            main{scene.vm.onEvent(CapsuleEvent.StoreAll)}
            val takeId=state().toys.first{it.toy==won.toy&&it.id in state().storedIds}.id
            move(portal,Vector3(-.2f,.05f,.35f))
            assertEquals(available-1,state().available(won.toy));assertEquals(count+1,state().toys.size)
            val taken=main{requireNotNull(scene.root.findEntity("CollectedToy_$takeId"))}
            val returnTo=main{portal.convertPositionTo(Vector3(0f,-.05f,.07f),scene.root)}
            move(taken,returnTo-main{position(taken)})
            awaitCondition("Returning toy did not restore stock"){scene.vm.state.value.game.available(won.toy)==available}
            assertEquals(state().storedIds,main{LocalCapsuleRepository(context).readStored()})
            capture("finite-album-returned.png")
            main{scene.vm.onEvent(CapsuleEvent.Mode(ExperienceMode.STUDY))}
            assertFalse(main{scene.canDrag(portal)});capture("refined-study.png")
        }finally{
            val edit=prefs.edit()
            if(saved==null)edit.remove("toys") else edit.putString("toys",saved)
            if(savedStored==null)edit.remove("stored_toy_ids") else edit.putStringSet("stored_toy_ids",savedStored)
            edit.commit()
        }
    }
}
