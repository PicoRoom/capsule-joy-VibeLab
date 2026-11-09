package com.bujiaban.niuqu.pico

import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.inspector.WindowInspector
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bujiaban.niuqu.pico.domain.model.ExperienceMode
import com.bujiaban.niuqu.pico.platform.LaunchActivity
import com.bujiaban.niuqu.pico.scene.CapsuleLayout
import com.bujiaban.niuqu.pico.scene.CapsuleScene
import com.bujiaban.niuqu.pico.scene.CapsuleSystem
import com.pico.spatial.core.ecs.AttachmentPanelComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.PortalComponent
import com.pico.spatial.core.ecs.TransformComponent
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Tests the spatial composition against the loaded meshes; screenshots support visual review. */
@RunWith(AndroidJUnit4::class)
class CapsuleLayoutTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private fun <T> main(block:()->T):T {
        val result=AtomicReference<T>()
        instrumentation.runOnMainSync{result.set(block())}
        return result.get()
    }
    private fun awaitCondition(message:String,timeout:Long=18000,block:()->Boolean){
        val end=SystemClock.elapsedRealtime()+timeout
        while(SystemClock.elapsedRealtime()<end){
            if(main(block))return
            Thread.sleep(100)
        }
        fail(message)
    }
    private fun visible(entity:Entity):Boolean {
        var current:Entity?=entity
        while(current!=null){
            if(!current.enabled)return false
            current=current.getParent()
        }
        return true
    }
    private fun descendantOf(entity:Entity,ancestor:Entity):Boolean {
        var current=entity.getParent()
        while(current!=null){
            if(current.id==ancestor.id)return true
            current=current.getParent()
        }
        return false
    }
    private data class FaceBounds(val left:Float,val right:Float,val bottom:Float,val top:Float)
    private fun meshBounds(entity:Entity,scene:CapsuleScene):FaceBounds {
        val bounds=entity.getVisualBounds(scene.root)
        assertTrue("A loaded model must have measurable bounds",bounds.size.x>0f&&bounds.size.y>0f)
        return FaceBounds(bounds.center.x-bounds.size.x/2f,bounds.center.x+bounds.size.x/2f,
            bounds.center.y-bounds.size.y/2f,bounds.center.y+bounds.size.y/2f)
    }
    // Attachment panels are planar surfaces. Their declared physical width and pixel aspect
    // define the hit rectangle; compare it with the actual loaded machine silhouette.
    private fun panelBounds(entity:Entity,width:Float,pixelsW:Int,pixelsH:Int):FaceBounds {
        val position=requireNotNull(entity.components[TransformComponent::class.java]).position
        val height=width*pixelsH/pixelsW
        return FaceBounds(position.x-width/2f,position.x+width/2f,position.y-height/2f,position.y+height/2f)
    }
    private fun leftOf(message:String,left:FaceBounds,right:FaceBounds){
        assertTrue("$message (gap=${right.left-left.right} m)",left.right+.02f<=right.left)
    }
    private fun below(message:String,lower:FaceBounds,upper:FaceBounds){
        assertTrue("$message (gap=${upper.bottom-lower.top} m)",lower.top+.02f<=upper.bottom)
    }
    private fun panelView(width:Int,height:Int):View?=WindowInspector.getGlobalWindowViews().firstOrNull{
        it.isAttachedToWindow&&it.width==width&&it.height==height
    }
    private fun tapPanel(width:Int,height:Int,x:Float,y:Float){
        val view=main{requireNotNull(panelView(width,height)){"Missing measured $width x $height attachment view"}}
        val downTime=SystemClock.uptimeMillis()
        fun touch(action:Int){main{
            MotionEvent.obtain(downTime,SystemClock.uptimeMillis(),action,x,y,0).also{
                view.dispatchTouchEvent(it)
                it.recycle()
            }
        }}
        touch(MotionEvent.ACTION_DOWN)
        Thread.sleep(150)
        touch(MotionEvent.ACTION_UP)
    }
    private fun capture(name:String){
        Thread.sleep(6000)
        // PICO's screencap includes the spatial compositor; UiAutomation captures a black 2D display.
        val file=File(instrumentation.targetContext.getExternalFilesDir(null),name)
        instrumentation.uiAutomation.executeShellCommand("screencap -p ${file.absolutePath}").use{descriptor->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use{it.readBytes()}
        }
        assertTrue("The spatial screenshot must save",file.length()>0)
    }

    @Test fun catalogueMachineAlbumAndStudyHaveSeparateRegions(){
        val context=instrumentation.targetContext
        val previous=main{CapsuleSystem.scene}
        main{context.startActivity(Intent(context,LaunchActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))}
        awaitCondition("A fresh scene did not load",180000){CapsuleSystem.scene?.let{it!==previous&&it.loaded}==true}
        val scene=main{requireNotNull(CapsuleSystem.scene)}
        main{scene.vm.play()}
        awaitCondition("Play scene did not leave the welcome menu"){scene.root.findEntity("UI_Toolbar")?.enabled==true}
        fun entity(name:String)=requireNotNull(scene.root.findEntity(name)){"Missing scene entity $name"}
        fun shown(name:String)=visible(entity(name))
        val albumUiNames=listOf("UI_AlbumHeading","UI_AlbumStore")+(0..5).map{"UI_AlbumCaption_$it"}
        fun albumLabelsVisible()=albumUiNames.all{name->
            scene.root.findEntity(name)?.let{
                descendantOf(it,scene.root)&&descendantOf(it,entity("ToyAlbum"))&&visible(it)
            }==true
        }
        fun albumContentAttached():Boolean {
            // SDK 6.1.9's component path exposes the exact Android content view. Legacy
            // AttachmentPanels keep their views in a DisplayOwner without that component.
            val componentsReady=albumUiNames.all{name->
                val component=entity(name).components[AttachmentPanelComponent::class.java]
                component==null||component.content?.let{it.isAttachedToWindow&&it.visibility==View.VISIBLE&&it.width>0&&it.height>0}==true
            }
            // Legacy attachment IDs are not Android view tags. These three unique window
            // sizes identify the heading, store button and all six caption views as a group.
            val windows=WindowInspector.getGlobalWindowViews()
            val measuredViewsReady=listOf(Triple(460,64,1),Triple(240,66,1),Triple(250,65,6)).all{(width,height,count)->
                windows.count{it.isAttachedToWindow&&it.visibility==View.VISIBLE&&it.width==width&&it.height==height}==count
            }
            return componentsReady&&measuredViewsReady
        }
        awaitCondition("Catalogue and toolbar must expose measured native views"){
            panelView(CapsuleLayout.SERIES_PIXELS_W,CapsuleLayout.SERIES_PIXELS_H)!=null&&
                panelView(CapsuleLayout.TOOLBAR_PIXELS_W,CapsuleLayout.TOOLBAR_PIXELS_H)!=null
        }
        // SeriesCard's right arrow is centred at (352,253) in its 400 x 360 view.
        fun nextSeries()=tapPanel(CapsuleLayout.SERIES_PIXELS_W,CapsuleLayout.SERIES_PIXELS_H,352f,253f)
        // SceneToolbar has three actions on its first row.
        fun toggleMode()=tapPanel(CapsuleLayout.TOOLBAR_PIXELS_W,CapsuleLayout.TOOLBAR_PIXELS_H,98f,48f)
        fun toggleAlbum()=tapPanel(CapsuleLayout.TOOLBAR_PIXELS_W,CapsuleLayout.TOOLBAR_PIXELS_H,370f,48f)
        main{
            assertFalse("The initial view must keep the album closed",scene.vm.state.value.game.albumOpen)
            assertFalse(shown("ToyAlbum"))
            for(index in 0..8)assertNull("Detached action panels must be removed",scene.root.findEntity("UI_Action_$index"))
        }
        for(series in 0..2){
            if(series>0)nextSeries()
            awaitCondition("The selected machine and its controls did not appear"){
                scene.vm.state.value.game.series==series&&shown("MachineHousing_$series")&&shown("UI_Series")&&shown("SeriesPreview")&&
                    shown("UI_ClawControls")== (series==2)
            }
            main{
                val machine=meshBounds(entity("MachineHousing_$series"),scene)
                val catalogue=panelBounds(entity("UI_Series"),CapsuleLayout.SERIES_WIDTH,CapsuleLayout.SERIES_PIXELS_W,CapsuleLayout.SERIES_PIXELS_H)
                val toolbar=panelBounds(entity("UI_Toolbar"),CapsuleLayout.TOOLBAR_WIDTH,CapsuleLayout.TOOLBAR_PIXELS_W,CapsuleLayout.TOOLBAR_PIXELS_H)
                val status=panelBounds(entity("UI_Status"),CapsuleLayout.STATUS_WIDTH,CapsuleLayout.STATUS_PIXELS_W,CapsuleLayout.STATUS_PIXELS_H)
                leftOf("The catalogue must leave the machine unobscured",catalogue,machine)
                leftOf("Tools must sit to the right of the machine",machine,toolbar)
                leftOf("Status must accompany the right menu",machine,status)
                below("Tools and status need separate hit regions",toolbar,status)
                assertEquals("Left and right menus must share their centre height",(catalogue.bottom+catalogue.top)/2f,(toolbar.bottom+toolbar.top)/2f,.001f)
                assertTrue(shown("UI_Toolbar"));assertTrue(shown("UI_Status"))
                assertFalse(shown("UI_StudyGuide"));assertFalse(shown("ToyAlbum"))
                if(series==2){
                    val controls=requireNotNull(entity("UI_ClawControls").components[TransformComponent::class.java])
                    assertEquals("Aim panel must be centred",0f,controls.position.x,.001f)
                    assertTrue("Aim panel must be forward of the machine centre",controls.position.z>CapsuleLayout.machinePosition.z+.1f)
                    assertTrue("Aim panel must sit below the outlet",controls.position.y<CapsuleLayout.capsuleRest.y-.1f)
                    assertEquals("Aim panel must tilt up by 40 degrees",kotlin.math.sin(-20f*Math.PI.toFloat()/180f),controls.quaternion.x,.001f)
                }
            }
            capture("layout-series-$series-play.png")
            toggleAlbum()
            awaitCondition("The toolbar album action did not open the book"){scene.vm.state.value.game.albumOpen&&shown("ToyAlbum")}
            awaitCondition("All eight album labels must join the visible scene hierarchy and attach their native content"){
                albumLabelsVisible()&&albumContentAttached()
            }
            main{
                albumUiNames.forEach{name->
                    val label=entity(name)
                    assertTrue("$name must belong to the scene root",descendantOf(label,scene.root))
                    assertTrue("$name must move and hide with the book",descendantOf(label,entity("ToyAlbum")))
                    assertTrue("$name must be enabled through its full hierarchy",visible(label))
                }
                assertTrue("All album Android content views must be attached and measured",albumContentAttached())
                val album=entity("ToyAlbum")
                // Portal-world backdrops extend beyond their clipping windows. The physical
                // cover, rather than those virtual interiors, defines the book's footprint.
                val cover=album.getChildren().first{it.components[ModelComponent::class.java]!=null&&it.components[PortalComponent::class.java]==null}
                leftOf("The open album must leave the machine unobscured",meshBounds(entity("MachineHousing_$series"),scene),meshBounds(cover,scene))
                below("Open album must stay below the right menu",meshBounds(cover,scene),panelBounds(entity("UI_Toolbar"),CapsuleLayout.TOOLBAR_WIDTH,CapsuleLayout.TOOLBAR_PIXELS_W,CapsuleLayout.TOOLBAR_PIXELS_H))
                assertTrue(scene.canTap(entity("AlbumPortal_0")))
            }
            capture("layout-series-$series-album.png")
            toggleMode()
            awaitCondition("The toolbar study action did not replace the play catalogue"){
                scene.vm.state.value.game.mode==ExperienceMode.STUDY&&shown("UI_StudyGuide")&&!shown("UI_Series")&&!shown("SeriesPreview")&&
                    !shown("UI_ClawControls")&&!shown("ToyAlbum")&&shown("StudyHousing_$series")
            }
            main{
                val guide=panelBounds(entity("UI_StudyGuide"),CapsuleLayout.STUDY_WIDTH,CapsuleLayout.STUDY_PIXELS_W,CapsuleLayout.STUDY_PIXELS_H)
                leftOf("The study directory must leave the mechanism unobscured",guide,meshBounds(entity("StudyHousing_$series"),scene))
                assertTrue(shown("UI_Toolbar"));assertTrue(shown("UI_Status"))
                assertFalse(shown("MachineHousing_$series"))
                assertFalse(scene.canTap(entity("AlbumPortal_0")))
                assertFalse(scene.canDrag(requireNotNull(scene.debugEntity("coin"))))
            }
            capture("layout-series-$series-study.png")
            toggleMode()
            awaitCondition("The toolbar return action must restore the open album and catalogue"){
                scene.vm.state.value.game.mode==ExperienceMode.PLAY&&shown("ToyAlbum")&&shown("UI_Series")&&!shown("UI_StudyGuide")&&shown("MachineHousing_$series")
            }
            toggleAlbum()
            awaitCondition("The toolbar close action must clear the album interaction region"){
                !scene.vm.state.value.game.albumOpen&&!shown("ToyAlbum")&&!scene.canTap(entity("AlbumPortal_0"))&&albumUiNames.all{!shown(it)}
            }
        }
        nextSeries()
        awaitCondition("The catalogue next arrow must wrap from the last series to the first"){
            scene.vm.state.value.game.series==0&&shown("MachineHousing_0")
        }
    }
}
