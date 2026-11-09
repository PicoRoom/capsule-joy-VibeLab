package com.bujiaban.niuqu.pico.scene

import com.bujiaban.niuqu.pico.domain.model.*
import com.pico.spatial.core.ecs.*
import com.pico.spatial.core.ecs.resource.*
import com.pico.spatial.core.math.*

/** A non-interactive landing marker in the cabinet's metre coordinates. */
class ClawAimIndicator(parent:Entity){
    val root=Entity().also{parent.addChild(it);it.setName("ClawAimIndicator");it.enabled=false}
    private val paint=UnlitMaterial.create()
    private val ring=ModelEntity(MeshResource.createTorus(outerRingRadius=.023f,innerRingRadius=.020f),paint).also{root.addChild(it);it.setName("ClawLandingRing")}
    private val guide=ModelEntity(MeshResource.createCylinder(height=1f,radius=.00065f),paint).also{root.addChild(it);it.setName("ClawLandingGuide")}
    fun sync(s:CapsuleState){
        root.enabled=s.canAimClaw
        if(!root.enabled)return
        val target=s.clawAimTarget()
        // A ring above the prize surface stays visible instead of disappearing inside it.
        val surface=s.clawAvailable.map{CLAW_SLOTS[it]}.filter{
            (it.x-s.clawX)*(it.x-s.clawX)+(it.z-s.clawZ)*(it.z-s.clawZ)<.28f*.28f
        }.maxOfOrNull{it.y+.19f}?:2.72f
        val y=surface*.1f+.002f
        paint.setBaseColor(Color4.fromSRGBHex(if(target==null)"#ffe3a0" else "#56d9bc"))
        ring.components[TransformComponent::class.java]?.setPosition(Vector3(s.clawX*.1f,y,s.clawZ*.1f))
        val height=(.363f-y).coerceAtLeast(.008f)
        guide.components[TransformComponent::class.java]?.apply{
            setPosition(Vector3(s.clawX*.1f,y+height/2f,s.clawZ*.1f));setScaleVector(Vector3(1f,height,1f))
        }
    }
}
