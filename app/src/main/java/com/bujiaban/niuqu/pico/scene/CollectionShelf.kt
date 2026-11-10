package com.bujiaban.niuqu.pico.scene

import com.bujiaban.niuqu.pico.domain.model.*
import com.pico.spatial.core.ecs.*
import com.pico.spatial.core.ecs.resource.*
import com.pico.spatial.core.math.*
import kotlin.math.*

/** A stage for owned instances; it never creates collectible copies. */
class CollectionShelf(parent:Entity) {
    val root=Entity().also{parent.addChild(it);it.setName("CollectionShelf")}
    private val paints=List(6){UnlitMaterial.create()}
    private val pads=List(6){i->
        ModelEntity(MeshResource.createCylinder(height=.012f,radius=.064f),paints[i]).also{
            root.addChild(it);it.setName("CollectionPad_$i")
            val p=CollectionDisplay.position(i)
            it.components[TransformComponent::class.java]?.setPosition(Vector3(p[0],p[1]-.012f,p[2]))
        }
    }
    private var pulseSlot=-1;private var age=1f
    fun pulse(slot:Int){pulseSlot=slot;age=0f}
    fun sync(s:CapsuleState,definitions:List<ToyDefinition>,visible:Boolean){
        root.enabled=visible
        val entries=definitions.indices.filter{definitions[it].series==s.displaySeries}
        val complete=entries.all{s.total(it)>0}
        entries.forEachIndexed{i,toy->paints[i].setBaseColor(Color4.fromSRGBHex(
            if(complete)"#edc76b" else if(s.total(toy)>0)"#83c6ae" else "#d7d6cc"))}
    }
    fun update(dt:Float){
        age=(age+dt).coerceAtMost(1f)
        pads.forEachIndexed{i,e->e.components[TransformComponent::class.java]?.setScaleVector(
            Vector3(if(i==pulseSlot)1f+.12f*sin(age*PI.toFloat()) else 1f))}
    }
}
