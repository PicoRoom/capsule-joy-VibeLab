package com.bujiaban.niuqu.pico.scene

import com.pico.spatial.core.math.Vector3
import kotlin.math.sqrt

/** Independent grips; releasing either hand discards partial separation. */
internal class CapsulePull {
    private val held=booleanArrayOf(false,false)
    private val offset=arrayOf(Vector3.ZERO,Vector3.ZERO)
    val both get()=held.all{it}
    val gap:Float get(){val d=Vector3(0f,.04f,0f)+offset[1]-offset[0];return (sqrt(d.x*d.x+d.y*d.y+d.z*d.z)-.04f).coerceAtLeast(0f)}
    fun begin(half:Int){held[half]=true;if(both){offset[0]=Vector3.ZERO;offset[1]=Vector3.ZERO}}
    fun move(half:Int,delta:Vector3){if(both&&held[half])offset[half]=offset[half]+delta}
    fun displacement(half:Int)=offset[half]
    fun end(half:Int){held[half]=false;offset[0]=Vector3.ZERO;offset[1]=Vector3.ZERO}
    fun reset(){held.fill(false);offset[0]=Vector3.ZERO;offset[1]=Vector3.ZERO}
}
