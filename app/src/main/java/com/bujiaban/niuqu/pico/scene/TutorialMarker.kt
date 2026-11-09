package com.bujiaban.niuqu.pico.scene

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3

/** A quiet, non-interactive outline points to the current hands-on target. */
class TutorialMarker(parent:Entity) {
    private val ring=ModelEntity(
        MeshResource.createTorus(outerRingRadius=1f,innerRingRadius=.94f),
        UnlitMaterial.create().apply{setBaseColor(Color4.fromSRGBHex("#65c9ad"))},
    ).also{
        parent.addChild(it);it.setName("TutorialTarget");it.enabled=false
        it.components[TransformComponent::class.java]?.setEulerAngles(EulerAngles(90f,0f,0f))
    }
    fun sync(position:Vector3?,radius:Float=.055f){
        ring.enabled=position!=null
        if(position!=null)ring.components[TransformComponent::class.java]?.apply{
            setPosition(position+Vector3(0f,0f,.035f));setScaleVector(Vector3(radius))
        }
    }
}
