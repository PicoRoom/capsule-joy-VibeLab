package com.bujiaban.niuqu.pico.scene

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.PortalWorldComponent
import com.pico.spatial.core.ecs.PortalComponent
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.ecs.resource.PortalMaterial
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Color4

/** One portal per type; the preview is independent of the finite owned inventory. */
class ToyAlbum(parent:Entity,private val templates:List<Entity>,registerTarget:(Entity,String)->Unit) {
    val root=Entity().also{parent.addChild(it);it.setName("ToyAlbum")}
    private val worlds=mutableListOf<Entity>()
    private val previews=mutableMapOf<Int,Entity>()
    private var page=emptyList<Int>()
    fun toyIndex(slot:Int)=page.getOrNull(slot)
    private val meshes=mutableMapOf<String,MeshResource>()
    private val paints=mutableMapOf<String,UnlitMaterial>()
    init {
        root.components[TransformComponent::class.java]?.apply{setPosition(CapsuleLayout.albumPosition);setScaleVector(Vector3(CapsuleLayout.ALBUM_SCALE));setEulerAngles(CapsuleLayout.albumRotation)}
        box(Vector3(.79f,.64f,.018f),Vector3.ZERO,"#30392f",root)
        box(Vector3(.776f,.626f,.018f),Vector3(0f,0f,.004f),"#eee7cb",root)
        // A binding and coloured page tabs make this a small physical collection book.
        box(Vector3(.028f,.62f,.026f),Vector3(-.36f,0f,.018f),"#c08b96",root)
        repeat(6){index->
            val p=cell(index)
            box(Vector3(.222f,.216f,.012f),p,"#30392f",root)
            val world=Entity().also{root.addChild(it);it.setName("AlbumWorld_$index");it.components.set(PortalWorldComponent());it.components[TransformComponent::class.java]?.setPosition(p+Vector3(0f,0f,.010f))}
            worlds+=world
            box(Vector3(.60f,.60f,.012f),Vector3(0f,0f,-.16f),if(index<3)"#d8dfc4" else "#e8d8ce",world)
            box(Vector3(.60f,.012f,.40f),Vector3(0f,-.092f,-.12f),"#a3b394",world)
            val portal=ModelEntity(MeshResource.createPlane(.21f,.204f,.010f),PortalMaterial())
            root.addChild(portal);portal.setName("AlbumPortal_$index")
            portal.components[TransformComponent::class.java]?.setPosition(p+Vector3(0f,0f,.011f))
            portal.components.set(PortalComponent(world,allowClipping=true,doubleSide=false,allowEntityCrossing=false))
            portal.components.set(CollisionComponent(collisionShape=listOf(ShapeResource.createBox(Vector3(.21f,.204f,.018f))),physicsMaterial=PhysicsMaterialResource()))
            portal.components.set(InteractableComponent());portal.components.set(HoverEffectComponent())
            registerTarget(portal,"album:$index")
        }
    }
    private fun box(size:Vector3,position:Vector3,color:String,parent:Entity){
        val key="${size.x}/${size.y}/${size.z}"
        val mesh=meshes.getOrPut(key){MeshResource.createBox(size,.005f)}
        val paint=paints.getOrPut(color){UnlitMaterial.create().apply{setBaseColor(Color4.fromSRGBHex(color))}}
        ModelEntity(mesh,paint).also{parent.addChild(it);it.components[TransformComponent::class.java]?.setPosition(position)}
    }
    fun sync(owned:Set<Int>,visible:Boolean,indices:List<Int>){
        root.enabled=visible
        if(page!=indices){
            previews.values.forEach{it.destroy()};previews.clear();page=indices
        }
        for((slot,index) in page.withIndex())if(index in owned&&index !in previews){
            val model=requireNotNull(templates[index].clone(Entity.CloneOptions(recursive=true,shouldShareMaterialInstance=true)))
            worlds[slot].addChild(model);model.enabled=true;model.setName("AlbumPreview_$index")
            val transform=requireNotNull(model.components[TransformComponent::class.java])
            transform.setEulerAngles(EulerAngles(0f,-18f,0f))
            var bounds=model.getVisualBounds(worlds[slot])
            val scale=minOf(.142f/bounds.size.y,.153f/bounds.size.x,.075f/bounds.size.z)
            transform.setScaleVector(Vector3(scale))
            bounds=model.getVisualBounds(worlds[slot])
            transform.setPosition(Vector3(-bounds.center.x,-.018f-bounds.center.y,-.067f-bounds.center.z))
            previews[index]=model
        }
    }
    fun attach(entity:Entity,index:Int,scale:Float){
        root.addChild(entity)
        entity.components[TransformComponent::class.java]?.apply{
            setPosition(when(index){-1->Vector3(-.10f,.275f,.025f);-2->Vector3(.265f,.273f,.033f);else->cell(index)+Vector3(0f,-.132f,.028f)})
            setScaleVector(Vector3(scale))
        }
    }
    fun takePosition(index:Int,relativeTo:Entity)=root.convertPositionTo(cell(page.indexOf(index))+Vector3(0f,-.075f,.09f),relativeTo)
    fun accepts(position:Vector3,relativeTo:Entity):Boolean {
        val local=root.convertPositionFrom(position,relativeTo)
        return local.x in -.40f.. .40f&&local.y in -.33f.. .33f&&local.z in -.04f.. .20f
    }
    companion object {
        fun cell(index:Int)=Vector3(-.225f+(index%3)*.238f,.125f-(index/3)*.275f,.02f)
    }
}
