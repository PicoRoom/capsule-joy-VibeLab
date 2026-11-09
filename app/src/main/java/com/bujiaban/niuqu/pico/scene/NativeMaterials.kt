package com.bujiaban.niuqu.pico.scene

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.resource.*
import com.pico.spatial.core.math.Color4

/** Explicit native alpha blending; glass must not depend on glTF transmission support. */
object NativeMaterials {
    fun apply(entity:Entity,study:Boolean){
        entity.components[ModelComponent::class.java]?.materials?.forEach{material->
            if(material is PhysicallyBasedMaterial && material.getName() in setOf("Globe_Clear_Plastic","Cabinet_Clear_Glass")){
                material.setBlendingMode(BlendingMode.TRANSPARENT)
                material.setBaseColor(Color4.fromSRGBHex("#dceeea"))
                material.setOpacity(if(study).045f else .30f)
                material.setDepthWrite(false)
                material.setCullingMode(MaterialCullingMode.NONE)
                material.setRoughness(.22f)
                material.setMetallic(0f)
            }
        }
        entity.getChildren().forEach{apply(it,study)}
    }
}
