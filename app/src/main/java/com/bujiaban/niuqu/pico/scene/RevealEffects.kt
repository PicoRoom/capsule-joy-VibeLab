package com.bujiaban.niuqu.pico.scene

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import kotlin.math.*

/** Small, reusable, window-local celebration. No colliders, lights or per-frame allocations of entities. */
class RevealEffects(parent:Entity) {
    val root=Entity().also{parent.addChild(it);it.setName("RevealEffects");it.enabled=false}
    private val mesh=MeshResource.createBox(Vector3(1f),0f)
    private val colors=listOf("#fff1b8","#ffc879","#80dace","#ffa99b","#b6b9ff").map{
        UnlitMaterial.create().apply{setBaseColor(Color4.fromSRGBHex(it))}
    }
    private val particles=List(36){i->
        Entity().also{star->
            root.addChild(star);star.setName("RevealSpark_$i")
            val cross=i<12||i>=24
            repeat(if(cross)2 else 1){axis->
                ModelEntity(mesh,colors[i%colors.size]).also{bar->
                    star.addChild(bar)
                    bar.components[TransformComponent::class.java]?.setScaleVector(
                        if(cross)if(axis==0)Vector3(.003f,.013f,.002f) else Vector3(.010f,.003f,.002f)
                        else Vector3(.005f,.009f,.002f))
                }
            }
        }
    }
    private val halo=ModelEntity(MeshResource.createTorus(outerRingRadius=.12f,innerRingRadius=.116f),colors.first()).also{
        root.addChild(it);it.enabled=false;it.setName("SeriesCompleteHalo")
    }
    private var completed=false
    private var age=Float.POSITIVE_INFINITY
    private var discovery=false
    private var model:Entity?=null
    private var restPosition=Vector3.ZERO
    private var restScale=Vector3(1f)
    private fun transform(e:Entity)=requireNotNull(e.components[TransformComponent::class.java])

    fun opening(position:Vector3,time:Float){
        root.enabled=true;transform(root).setPosition(position)
        val p=(time/1.4f).coerceIn(0f,1f)
        particles.forEachIndexed{i,e->
            e.enabled=i<12
            if(e.enabled){
                val angle=i*PI.toFloat()/6f+p*2.4f
                val radius=.075f-.035f*p
                transform(e).apply{
                    setPosition(Vector3(cos(angle)*radius,sin(angle*2f)*.012f+.009f,sin(angle)*radius))
                    setScaleVector(Vector3(.22f+.48f*p))
                    setEulerAngles(EulerAngles(0f,0f,45f+p*90f))
                }
            }
        }
    }
    fun reveal(position:Vector3,toy:Entity,isDiscovery:Boolean,seriesComplete:Boolean=false){
        stop();age=0f;discovery=isDiscovery;completed=seriesComplete
        root.enabled=true;transform(root).setPosition(position)
        // Animate only the visual child, leaving saved poses and manipulation scale untouched.
        model=toy.getChildren().firstOrNull()
        model?.let{restPosition=transform(it).position;restScale=transform(it).scaleVector}
        update(0f)
    }
    fun update(dt:Float){
        if(!age.isFinite())return
        age+=dt.coerceAtLeast(0f)
        model?.let{
            val t=(age/.65f).coerceIn(0f,1f)
            val pop=RevealMotion.toyScale(t)
            transform(it).setScaleVector(restScale*pop)
            transform(it).setPosition(restPosition+Vector3(0f,sin(t*PI.toFloat())*.015f,0f))
            if(t>=1f)releaseToy()
        }
        particles.forEachIndexed{i,e->
            val life=if(i<12)1.15f else 1.45f+(i%4)*.12f
            val localAge=age-if(i>=24).22f else 0f
            val t=localAge/life
            e.enabled=t>=0f&&t<1f&&(discovery||i<24)
            if(e.enabled){
                val angle=i*2.399963f
                val speed=.06f+(i%5)*.018f
                val radius=.025f+speed*localAge
                val y=if(i>=24).045f+sin(angle)*radius*1.25f else if(i<12).045f+sin(angle)*radius else .018f+(.13f+(i%3)*.035f)*age-.12f*age*age
                transform(e).apply{
                    setPosition(Vector3(cos(angle)*radius,y,if(i<12||i>=24).02f else sin(angle)*radius*.65f))
                    setScaleVector(Vector3(RevealMotion.particleScale(t)))
                    setEulerAngles(EulerAngles(if(i<12)0f else age*150f,age*45f,i*37f+age*90f))
                }
            }
        }
        halo.enabled=completed&&age<3f
        if(halo.enabled)transform(halo).apply{setScaleVector(Vector3(1f+.06f*sin(age*5f)));setPosition(Vector3(0f,.04f,0f))}
        if(age>=if(completed)3f else 1.81f)stop()
    }
    /** Taking a toy immediately yields its full visual scale to the user's hand. */
    fun releaseToy(){model?.let{transform(it).setPosition(restPosition);transform(it).setScaleVector(restScale)};model=null}
    fun stop(){releaseToy();age=Float.POSITIVE_INFINITY;root.enabled=false;halo.enabled=false}
}

internal object RevealMotion {
    fun toyScale(progress:Float):Float {
        val t=progress.coerceIn(0f,1f)
        val u=t-1f
        return .35f+.65f*(1f+2.70158f*u*u*u+1.70158f*u*u)
    }
    fun particleScale(progress:Float):Float {
        val t=progress.coerceIn(0f,1f)
        return minOf(1f,t/.06f)*(1f-t*t)
    }
}
