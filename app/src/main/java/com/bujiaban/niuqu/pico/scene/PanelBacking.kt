package com.bujiaban.niuqu.pico.scene

import com.pico.spatial.core.ecs.*
import com.pico.spatial.core.ecs.resource.*
import com.pico.spatial.core.math.*

/** Opaque scene geometry remains visible behind attachment text in spatial compositing. */
object PanelBacking {
    fun attach(panel:Entity,width:Int){
        val height=when(panel.getName()){
            "UI_Tutorial"->CapsuleLayout.TUTORIAL_PIXELS_H
            "UI_Series"->CapsuleLayout.SERIES_PIXELS_H
            "UI_Toolbar"->CapsuleLayout.TOOLBAR_PIXELS_H
            "UI_Status"->CapsuleLayout.STATUS_PIXELS_H
            "UI_StudyGuide"->CapsuleLayout.STUDY_PIXELS_H
            "UI_ClawControls"->CapsuleLayout.CLAW_PANEL_PIXELS_H
            else->return
        }
        val material=UnlitMaterial.create().apply{setBaseColor(Color4.fromSRGBHex("#f5eed9"))}
        val backing=ModelEntity(MeshResource.createBox(Vector3(width.toFloat(),height.toFloat(),4f),2f),material)
        backing.setName("PanelBacking")
        panel.addChild(backing)
        backing.components[TransformComponent::class.java]?.setPosition(Vector3(0f,0f,-4f))
    }
}
