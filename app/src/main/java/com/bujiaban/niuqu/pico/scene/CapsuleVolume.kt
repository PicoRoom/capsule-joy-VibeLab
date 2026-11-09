package com.bujiaban.niuqu.pico.scene

import android.util.Log
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.ViewCoordinateSpace
import com.pico.spatial.core.math.Vector3

/** Fits authored metre coordinates into the actual system window, including resize. */
class CapsuleVolume(private val root:Entity) {
    val owner=Entity().also{it.setName("CapsuleVolumeContent");it.addChild(root)}
    private var half=Vector3.ZERO
    private var scale=1f
    var ready=false;private set
    private val transform get()=requireNotNull(root.components[TransformComponent::class.java])

    fun fit(content:SpatialViewContent,pixels:Vector3){
        if(pixels.x<=0f||pixels.y<=0f||pixels.z<=0f)return
        val size=content.convertSize(pixels,ViewCoordinateSpace.Local,owner)
        if(!listOf(size.x,size.y,size.z).all{it.isFinite()&&it>0f})return
        val next=size*.5f
        if(ready&&(next-half).length()<.0001f)return
        half=next
        val authored=CapsuleLayout.volumeSize
        scale=minOf(CapsuleLayout.SCENE_SCALE,size.x/authored.x,size.y/authored.y,size.z/authored.z)*.92f
        transform.setScaleVector(Vector3(scale))
        ready=true
        recenter()
        Log.i(CapsuleScene.TAG,"VOLUME_READY half=$half scale=$scale")
    }
    fun recenter(){if(ready)transform.setPosition(CapsuleLayout.volumeCenter*(-scale))}
    fun move(delta:Vector3){
        if(!ready||!listOf(delta.x,delta.y,delta.z).all{it.isFinite()})return
        val margin=half-CapsuleLayout.volumeSize*(scale*.5f)
        val center=transform.position+CapsuleLayout.volumeCenter*scale+delta*scale
        transform.setPosition(Vector3(center.x.coerceIn(-margin.x,margin.x),center.y.coerceIn(-margin.y,margin.y),center.z.coerceIn(-margin.z,margin.z))-CapsuleLayout.volumeCenter*scale)
    }
    fun contain(entity:Entity){
        if(!ready)return
        val t=entity.components[TransformComponent::class.java]?:return
        var bounds=entity.getVisualBounds(owner)
        val size=bounds.size
        val factor=minOf(1f,half.x*1.92f/size.x,half.y*1.92f/size.y,half.z*1.92f/size.z)
        if(factor.isFinite()&&factor>0f&&factor<1f){
            t.setScaleVector(t.scaleVector*factor)
            bounds=entity.getVisualBounds(owner)
        }
        fun correction(low:Float,high:Float,limit:Float)=when{
            low < -limit -> -limit-low
            high > limit -> limit-high
            else -> 0f
        }
        val offset=Vector3(correction(bounds.min.x,bounds.max.x,half.x*.98f),correction(bounds.min.y,bounds.max.y,half.y*.98f),correction(bounds.min.z,bounds.max.z,half.z*.98f))
        // Interactive objects are direct children of an untransformed play root.
        t.setPosition(t.position+offset/scale)
    }
    fun containVisibleToys(toys:Collection<Entity>){toys.filter{it.enabled}.forEach(::contain)}
    fun close(){owner.destroy()}
}
