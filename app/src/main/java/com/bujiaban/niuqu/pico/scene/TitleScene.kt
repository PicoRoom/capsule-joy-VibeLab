package com.bujiaban.niuqu.pico.scene

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import kotlin.math.PI
import kotlin.math.sin

/** A separate title object; it has no game handles, collisions, or inventory state. */
class TitleScene(parent:Entity,bottomTemplate:Entity,topTemplate:Entity) {
    val root=Entity().also{parent.addChild(it);it.setName("TitleScene")}
    private val transform=requireNotNull(root.components[TransformComponent::class.java])
    private var phase=0f

    init {
        val shells=Entity().also{root.addChild(it)}
        fun shell(template:Entity,name:String,offset:Vector3,color:String){
            val entity=requireNotNull(template.clone(Entity.CloneOptions(recursive=true,shouldShareMaterialInstance=false)))
            shells.addChild(entity);entity.setName(name);entity.enabled=true
            requireNotNull(entity.components[TransformComponent::class.java]).apply{
                setPosition(offset);setQuaternion(Quat.identity());setScaleVector(Vector3(1f))
            }
            tint(entity,Color4.fromSRGBHex(color))
        }
        shell(bottomTemplate,"TitleCapsuleBottom",Vector3.ZERO,"#7abdb2")
        // The authored lid contains the hinge offset; restore the closed capsule alignment.
        shell(topTemplate,"TitleCapsuleTop",Vector3(0f,0f,-.0235f),"#fff2d7")
        val bounds=shells.getVisualBounds(root)
        val size=.24f/maxOf(bounds.size.x,bounds.size.y,bounds.size.z)
        requireNotNull(shells.components[TransformComponent::class.java]).apply{
            setScaleVector(Vector3(size))
            setPosition(Vector3.ZERO-bounds.center*size)
        }
        update(0f)
    }

    fun update(deltaSeconds:Float){
        phase=(phase+deltaSeconds.coerceIn(0f,.1f)*.5f)%(2f*PI.toFloat())
        val sway=sin(phase)
        transform.setPosition(Vector3(0f,1.38f+sway*.008f,.12f))
        transform.setEulerAngles(EulerAngles(-12f+sway*1.2f,sway*4f,-15f+sway*1.5f))
    }

    private fun tint(entity:Entity,color:Color4){
        entity.components[ModelComponent::class.java]?.materials?.forEach{material->
            when(material){
                is PhysicallyBasedMaterial->if(material.getName().contains("CapsuleTint"))material.setBaseColor(color)
                is UnlitMaterial->if(material.getName().contains("CapsuleTint"))material.setBaseColor(color)
            }
        }
        entity.getChildren().forEach{tint(it,color)}
    }
}
