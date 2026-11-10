package com.bujiaban.niuqu.pico.scene

import com.bujiaban.niuqu.pico.domain.model.*
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3

/** The crane and the caught capsule share the same authored cabinet coordinates. */
class ClawMachine(private val rig:Entity,private val hopper:Entity?=null) {
    private val aim=hopper?.let{ClawAimIndicator(it)}
    private val hoist=requireNotNull(rig.findEntity("ClawHoist"))
    private val cable=requireNotNull(rig.findEntity("ClawCable"))
    private val fingers=List(3){requireNotNull(rig.findEntity("ClawProng$it"))}
    private val balls=hopper?.let{source->List(24){requireNotNull(source.findEntity("HopperBall$it"))}}?:emptyList()
    private fun Entity.position(value:Vector3){components[TransformComponent::class.java]?.setPosition(value)}
    private fun step(time:Float,start:Float,end:Float):Float {
        val t=((time-start)/(end-start)).coerceIn(0f,1f);return t*t*(3f-2f*t)
    }
    fun sync(s:CapsuleState,study:Boolean=false) {
        rig.enabled=s.isClaw;hopper?.enabled=s.isClaw&&!study
        aim?.sync(s)
        if(!s.isClaw)return
        val cycling=s.phase==CapsulePhase.CLAWING&&!study
        val t=if(cycling)s.elapsed else 0f
        val carry=if(s.clawTarget==null)0f else step(t,2.55f,3.7f)
        val returning=step(t,4.4f,CLAW_DURATION)
        val x=(s.clawX+(-.86f-s.clawX)*carry)*(1f-returning)+.50f*returning
        val z=(s.clawZ+(.56f-s.clawZ)*carry)*(1f-returning)+.48f*returning
        val target=s.clawTarget?.let(CLAW_SLOTS::get)
        val depth=if(target==null).81f else 3.60f-target.y
        val extension=depth*step(t,0f,1f)*(1f-step(t,1.45f,2.55f))
        val closure=step(t,1f,1.45f)*(1f-if(s.clawTarget==null)step(t,1.6f,2f) else step(t,3.7f,4.05f))
        rig.position(Vector3(x*.1f,.413f,z*.1f))
        hoist.position(Vector3(0f,-.24f-extension,0f))
        cable.components[TransformComponent::class.java]?.apply{
            setScaleVector(Vector3(1f,(.24f+extension)/.22f,1f));setPosition(Vector3(0f,(-.24f-extension)*.5f,0f))
        }
        fingers.forEach{it.components[TransformComponent::class.java]?.setEulerAngles(EulerAngles(0f,0f,closure*.50f*180f/Math.PI.toFloat()))}
        balls.forEachIndexed{index,ball->
            val slot=CLAW_SLOTS[index]
            val caught=cycling&&index==s.clawTarget&&t>=1.45f
            ball.enabled=index in s.clawAvailable&&(!caught||t<4.4f)
            if(caught){
                val lift=step(t,1.45f,2.55f)
                val fall=step(t,4.05f,4.4f)
                val grip=step(t,2.1f,2.55f)
                ball.position(Vector3(slot.x+(x-slot.x)*grip,slot.y+(3.60f-slot.y)*lift-(3.60f-2.42f)*fall,slot.z+(z-slot.z)*grip))
            }else ball.position(Vector3(slot.x,slot.y,slot.z))
        }
    }
}
