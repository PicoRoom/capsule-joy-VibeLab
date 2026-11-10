package com.bujiaban.niuqu.pico.scene

import com.pico.spatial.core.ecs.*
import com.pico.spatial.core.ecs.resource.*
import com.pico.spatial.core.math.*
import kotlin.math.*

/** Clockwise marks next to the physical shaft, readable without looking at a menu. */
class CrankGuide(parent:Entity){
    private val root=Entity().also{parent.addChild(it);it.setName("CrankProgress");it.enabled=false}
    private val paints=List(12){UnlitMaterial.create()}
    private val dots=List(12){i->ModelEntity(MeshResource.createSphere(.0035f),paints[i]).also{
        root.addChild(it);val a=PI.toFloat()/2f-i*PI.toFloat()/6f
        it.components[TransformComponent::class.java]?.setPosition(Vector3(cos(a)*.055f,sin(a)*.055f,.035f))}}
    fun sync(position:Vector3?,degrees:Float){
        root.enabled=position!=null
        if(position==null)return
        root.components[TransformComponent::class.java]?.setPosition(position)
        paints.forEachIndexed{i,paint->paint.setBaseColor(Color4.fromSRGBHex(if(i*30<degrees)"#3ba98b" else "#ffd16f"))}
    }
}
