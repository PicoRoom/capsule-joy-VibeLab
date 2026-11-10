package com.bujiaban.niuqu.pico.scene

import com.bujiaban.niuqu.pico.domain.model.*
import com.pico.spatial.core.ecs.*
import com.pico.spatial.core.math.*

/** Separate study entities and clock: watching a mechanism cannot spend a coin or alter a round. */
class MechanismDemo(
    private val parent:Entity,private val motions:MotionLibrary,bodies:List<Entity>,cranks:List<Entity>,
    private val claw:ClawMachine,capsule:Entity,
) {
    private val maps=bodies.mapIndexed{index,body->
        val result=mutableMapOf<String,Entity>()
        fun cache(e:Entity){result[e.getName()]=e;e.getChildren().forEach(::cache)}
        cache(body);cranks.getOrNull(index)?.let(::cache);result
    }
    private val ball=requireNotNull(capsule.clone(Entity.CloneOptions(recursive=true,shouldShareMaterialInstance=true))).also{
        parent.addChild(it);it.setName("StudyDeliveryCapsule");it.enabled=false
    }
    private val marker=TutorialMarker(parent)
    fun hide(){ball.enabled=false;marker.sync(null,.04f)}
    fun update(series:Int,time:Float){
        val motion=motions.machines.getValue(listOf("garden","friends","sky")[series])
        val stage=(time/2f).toInt().coerceIn(0,3)
        val point=when(stage){0->motion.anchors.getValue("coinSlot");1->motion.anchors.getValue("crank");else->motion.anchors.getValue("outlet")}
        marker.sync(point,.04f)
        if(series==2){
            val t=((time-2f)/6f*CLAW_DURATION).coerceIn(0f,CLAW_DURATION)
            val slot=CLAW_SLOTS.indexOfFirst{it.x==.4f&&it.z==.4f}
            claw.sync(CapsuleState(ready=true,series=2,phase=CapsulePhase.CLAWING,clawX=.4f,clawZ=.4f,clawTarget=slot,elapsed=t))
            fun step(start:Float,end:Float):Float{val u=((t-start)/(end-start)).coerceIn(0f,1f);return u*u*(3f-2f*u)}
            val target=CLAW_SLOTS[slot]
            val carry=step(2.55f,3.7f);val lift=step(1.45f,2.55f);val fall=step(4.05f,4.4f)
            var point=Vector3(target.x+(-.86f-target.x)*carry,target.y+(3.60f-target.y)*lift-(3.60f-2.42f)*fall,target.z+(.56f-target.z)*carry)
            if(t>=4.4f){val drop=step(4.4f,CLAW_DURATION)
                point=if(drop<.35f){val u=drop/.35f;Vector3(-.86f+.86f*u,2.42f+(.63f-2.42f)*u,.56f+.14f*u)}
                    else{val u=(drop-.35f)/.65f;Vector3(.72f*u,.63f+(.255f-.63f)*u,.70f+(1.56f-.70f)*u)}}
            ball.enabled=true
            ball.components[TransformComponent::class.java]?.apply{setPosition(point*.1f);setScaleVector(Vector3(1f));setQuaternion(Quat.identity())}
            return
        }
        val t=when(stage){0->0f;1->(time-2f)/2f*1.1f;2->1.1f+(time-4f)/2f*2.1f;else->3.2f}
        ball.enabled=stage>=2
        for(track in motion.clips.getValue("Capsule_Dispense")){
            val name=track.name.substringBefore('.');val prop=track.name.substringAfter('.')
            val e=if(name=="DeliveryCapsule")ball else maps[series][name]?:continue
            val v=track.sample(t);val transform=e.components[TransformComponent::class.java]?:continue
            when(prop){
                "position"->transform.setPosition(Vector3(v[0],v[1],v[2])*(if(name=="DeliveryCapsule").1f else 1f))
                "quaternion"->transform.setQuaternion(Quat(v[0],v[1],v[2],v[3]).normalize())
                "scale"->transform.setScaleVector(Vector3(v[0]))
            }
        }
    }
}
