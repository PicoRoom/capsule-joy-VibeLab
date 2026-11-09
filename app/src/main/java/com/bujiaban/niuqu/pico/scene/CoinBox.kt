package com.bujiaban.niuqu.pico.scene

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3

/** Open ceramic coin caddy. Decorative coins never register as purchase handles. */
class CoinBox(parent:Entity,coinTemplate:Entity) {
    val root=Entity().also{parent.addChild(it);it.setName("CoinBox");transform(it).setPosition(CapsuleLayout.coinBoxPosition)}
    private val cream=paint("#f6edda")
    private val teal=paint("#65b6ac")
    private val lining=paint("#c8dcd3")
    init {
        box("Foot",Vector3(.196f,.015f,.146f),Vector3(0f,.0075f,0f),teal,.006f)
        box("Floor",Vector3(.186f,.014f,.136f),Vector3(0f,.019f,0f),cream,.006f)
        box("Lining",Vector3(.160f,.004f,.108f),Vector3(0f,.028f,0f),lining,.001f)
        box("Back",Vector3(.186f,.060f,.014f),Vector3(0f,.048f,-.061f),cream,.006f)
        for(x in listOf(-.086f,.086f))box("Side_$x",Vector3(.014f,.060f,.128f),Vector3(x,.048f,0f),cream,.006f)
        // Low front lip leaves the coin visible and reachable from the user's side.
        box("Front",Vector3(.186f,.026f,.014f),Vector3(0f,.031f,.061f),cream,.006f)
        box("FrontAccent",Vector3(.154f,.006f,.003f),Vector3(0f,.026f,.069f),teal,.001f)
        box("CoinRest",Vector3(.045f,.012f,.034f),Vector3(.035f,.032f,.023f),lining,.004f)
        // Clone before the live coin receives interaction components.
        repeat(7){i->
            val coin=requireNotNull(coinTemplate.clone(Entity.CloneOptions(recursive=true,shouldShareMaterialInstance=true)))
            root.addChild(coin);coin.setName("CoinBoxStack_$i");coin.enabled=true
            transform(coin).apply{
                setScaleVector(Vector3(1.5f))
                val stack=i<4
                setPosition(Vector3(if(stack)-.038f else .028f,.032f+(if(stack)i else i-4)*.004f,if(stack).003f else -.029f))
                setEulerAngles(EulerAngles(-90f,0f,i*27f))
            }
        }
        // A small real coin medallion identifies the box without a floating UI label.
        val badge=requireNotNull(coinTemplate.clone(Entity.CloneOptions(recursive=true,shouldShareMaterialInstance=true)))
        root.addChild(badge);badge.setName("CoinBoxBadge")
        transform(badge).apply{setPosition(Vector3(0f,.033f,.071f));setScaleVector(Vector3(.65f))}
    }
    private fun transform(e:Entity)=requireNotNull(e.components[TransformComponent::class.java])
    private fun paint(color:String)=PhysicallyBasedMaterial.create().apply{setBaseColor(Color4.fromSRGBHex(color));setRoughness(.32f);setMetallic(0f)}
    private fun box(name:String,size:Vector3,position:Vector3,paint:PhysicallyBasedMaterial,radius:Float){
        ModelEntity(MeshResource.createBox(size,radius),paint).also{root.addChild(it);it.setName("CoinBox$name");transform(it).setPosition(position)}
    }
}
