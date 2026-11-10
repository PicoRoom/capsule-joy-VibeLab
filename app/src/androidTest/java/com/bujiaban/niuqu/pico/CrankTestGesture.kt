package com.bujiaban.niuqu.pico
import com.bujiaban.niuqu.pico.scene.CapsuleScene
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.math.Vector3
import kotlin.math.*
internal fun turnCrank(scene:CapsuleScene,crank:Entity){
    var previous=Vector3(.04f,0f,0f)
    repeat(24){i->val a=-(i+1)*PI.toFloat()/12f;val next=Vector3(.04f*cos(a),.04f*sin(a),0f)
        scene.drag(crank,next-previous);previous=next}
}
