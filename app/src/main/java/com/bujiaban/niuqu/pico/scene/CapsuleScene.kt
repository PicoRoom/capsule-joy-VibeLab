package com.bujiaban.niuqu.pico.scene

import android.content.Context
import android.util.Log
import com.pico.spatial.core.container.getSelfSpaceState
import com.bujiaban.niuqu.pico.domain.model.*
import com.bujiaban.niuqu.pico.ui.capsule.*
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.DirectionalLightComponent
import com.pico.spatial.core.ecs.SceneUpdateContext
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.EulerAngles
import kotlin.math.*

class CapsuleScene(private val context:Context,val vm:CapsuleViewModel) {
    val root by lazy{Entity()}
    val volume by lazy{CapsuleVolume(root)}
    private val play by lazy{Entity().also{root.addChild(it);it.enabled=false}}
    private val study by lazy{Entity().also{root.addChild(it);it.enabled=false}}
    private val machine by lazy{Entity().also{play.addChild(it);it.position(CapsuleLayout.machinePosition);it.scale(CapsuleLayout.MACHINE_SCALE)}}
    private val studyMachine by lazy{Entity().also{study.addChild(it);it.setName("StudyMachine");it.position(CapsuleLayout.machinePosition);it.scale(CapsuleLayout.MACHINE_SCALE)}}
    private val housings=mutableListOf<Entity>()
    private val studyHousings=mutableListOf<Entity>()
    private val tutorialMarker by lazy{TutorialMarker(root)}
    private var lastSeries=-1
    private var lastScreen=ExperienceScreen.HOME
    private var lastTutorialStep=TutorialStep.POSITION
    private val acceptsGameInput get()=vm.state.value.flow.let{it.screen==ExperienceScreen.PLAY || (it.screen==ExperienceScreen.TUTORIAL && it.step!=TutorialStep.POSITION)}
    private val canPlaceScene get()=vm.state.value.flow.screen in listOf(ExperienceScreen.PLAY,ExperienceScreen.TUTORIAL)
    private fun canHandleToy(id:Long)=vm.state.value.flow.let{it.screen==ExperienceScreen.PLAY || (it.screen==ExperienceScreen.TUTORIAL && it.step==TutorialStep.STORE && it.tutorialToyId==id)}
    private fun albumVisible()=vm.state.value.let{it.game.albumOpen && it.game.mode==ExperienceMode.PLAY && it.flow.screen!=ExperienceScreen.HOME}
    private val templates=mutableListOf<Entity>()
    private val toyEntities=mutableMapOf<Long,Entity>()
    private val toyIds=mutableMapOf<Entity,Long>()
    private val handles=mutableMapOf<Entity,String>()
    private val nodes=mutableMapOf<String,Entity>()
    private val hopperNodes=mutableMapOf<String,Entity>()
    private val bodyNodes=mutableListOf<Map<String,Entity>>()
    private val bodies=mutableListOf<Entity>()
    private val studyBodies=mutableListOf<Entity>()
    private val cranks=mutableListOf<Entity>()
    private val studyCranks=mutableListOf<Entity>()
    private val hopperStart=mutableMapOf<String,Pair<Vector3,Quat>>()
    private val hopperEnd=mutableMapOf<String,Pair<Vector3,Quat>>()
    private var grabButtonRest=Vector3.ZERO
    private lateinit var motions:MotionLibrary
    private lateinit var coin:Entity
    private lateinit var capsule:Entity
    private lateinit var lid:Entity
    private lateinit var standardHopper:Entity
    private lateinit var skyFlap:Entity
    private lateinit var clawMachine:ClawMachine
    private lateinit var studyClaw:ClawMachine
    private lateinit var joystick:Entity
    private lateinit var grabButton:Entity
    private lateinit var capsuleBottom:Entity
    private lateinit var capsuleTop:Entity
    private var titleScene:TitleScene?=null
    private var titleOptionsOpen=false
    private lateinit var seriesPreview:SeriesPreview
    private lateinit var album:ToyAlbum
    private lateinit var revealEffects:RevealEffects
    private var lastCapsuleColor=-1
    private var albumDragToy:Long?=null
    private var ready=false;private var active=false
    private var lastNanos=0L;private var coinAge=2f;private var lastPhase=CapsulePhase.COIN_READY
    private var cachedClipTime=Float.NaN;private var lastMode=ExperienceMode.PLAY
    private var dragged:String?=null
    private val shellPull=CapsulePull()
    private var shellFade=0f
    private var movingToy:Long?=null
    var loaded=false;private set
    private val machineMotion get()=motions.machines.getValue(vm.state.value.series[vm.state.value.game.series].id)
    val coinSlot get()=CapsuleLayout.machinePosition+machineMotion.anchors.getValue("coinSlot")*CapsuleLayout.MACHINE_SCALE

    fun resume(){active=true;lastNanos=0;}
    fun pause(){active=false;if(vm.state.value.game.phase==CapsulePhase.CAPSULE_TAKEN)resetShells();if(::coin.isInitialized&&dragged=="coin")coin.position(CapsuleLayout.coinHome);if(::revealEffects.isInitialized)revealEffects.stop();dragged=null;finishToyTransform();vm.clearClawInputs()}
    private fun Entity.position(value:Vector3){components[TransformComponent::class.java]?.setPosition(value)}
    private fun Entity.rotation(value:Quat){components[TransformComponent::class.java]?.setQuaternion(value)}
    private fun Entity.scale(value:Float){components[TransformComponent::class.java]?.setScaleVector(Vector3(value))}
    private fun position(e:Entity)=e.components[TransformComponent::class.java]?.position?:Vector3.ZERO
    private fun rotation(e:Entity)=e.components[TransformComponent::class.java]?.quaternion?:Quat.identity()
    private fun distance(a:Vector3,b:Vector3)=sqrt((a.x-b.x).pow(2)+(a.y-b.y).pow(2)+(a.z-b.z).pow(2))
    private fun makeHandle(e:Entity,radius:Float,name:String,offset:Vector3=Vector3.ZERO){
        e.components.set(CollisionComponent(collisionShape=listOf(ShapeResource.createSphere(radius).offsetByTranslation(offset)),physicsMaterial=PhysicsMaterialResource()))
        e.components.set(InteractableComponent());e.components.set(HoverEffectComponent());handles[e]=name
    }
    private suspend fun loadAsset(name:String,parent:Entity):Entity {
        Log.i(TAG,"LOAD_BEGIN $name")
        val model=Entity.loadSuspend("asset://art/$name.glb")
        NativeMaterials.apply(model,name.endsWith("-study"))
        // Preserve the authored metre conversion below a separate manipulation transform.
        val entity=Entity();entity.addChild(model);parent.addChild(entity)
        Log.i(TAG,"LOAD_DONE $name");return entity
    }
    suspend fun load() {
        val grip=com.pico.spatial.core.ecs.ModelEntity(
            com.pico.spatial.core.ecs.resource.MeshResource.createBox(Vector3(.24f,.018f,.018f),.008f),
            UnlitMaterial.create().apply{setBaseColor(Color4.fromSRGBHex("#c4dab5"))})
        root.addChild(grip);grip.enabled=false;grip.setName("SceneMoveGrip");grip.position(Vector3(0f,.535f,.16f))
        makeHandle(grip,.03f,"scene")
        grip.components.set(CollisionComponent(collisionShape=listOf(ShapeResource.createBox(Vector3(.28f,.045f,.045f))),physicsMaterial=PhysicsMaterialResource()))
        motions=MotionLibrary(context)
        val hopper=loadAsset("hopper",machine);standardHopper=hopper
        hopper.setName("Hopper")
        fun cache(e:Entity,target:MutableMap<String,Entity>){if(e.getName().isNotEmpty())target[e.getName()]=e;e.getChildren().forEach{cache(it,target)}}
        cache(hopper,hopperNodes)
        coin=loadAsset("coin",play);coin.position(CapsuleLayout.coinHome);coin.scale(1.5f);CoinBox(play,coin);makeHandle(coin,.032f,"coin")
        capsule=Entity();play.addChild(capsule);capsule.enabled=false;capsule.scale(CapsuleLayout.MACHINE_SCALE);capsule.position(CapsuleLayout.capsuleRest)
        capsuleBottom=loadAsset("capsule-bottom",capsule)
        lid=Entity();capsule.addChild(lid);lid.position(Vector3(0f,0f,-.0235f));capsuleTop=loadAsset("capsule-lid",lid)
        titleScene=TitleScene(root,capsuleBottom,capsuleTop)
        makeHandle(capsule,.034f,"capsule")
        vm.state.value.series.forEachIndexed{index,series->
            val body=loadAsset("machine-${series.id}-core",machine).also{it.setName("MachineCore_$index");it.enabled=index==0}
            bodies+=body
            bodyNodes+=mutableMapOf<String,Entity>().also{cache(body,it)}
            studyBodies+=loadAsset("machine-${series.id}-core-study",studyMachine).also{it.setName("StudyCore_$index");it.enabled=index==0}
            if(index<2){
                val anchor=motions.machines.getValue(series.id).anchors.getValue("crank")
                cranks+=loadAsset("crank-${series.id}",machine).also{it.setName("MachineCrank_$index");it.position(anchor);makeHandle(it,if(index==0).032f else .043f,"crank");it.enabled=index==0}
                studyCranks+=loadAsset("crank-${series.id}",studyMachine).also{it.setName("StudyCrank_$index");it.position(anchor);it.enabled=index==0}
            }
            housings+=loadAsset("machine-${series.id}",machine).also{it.setName("MachineHousing_$index");it.enabled=index==0}
            studyHousings+=loadAsset("machine-${series.id}-study",studyMachine).also{it.setName("StudyHousing_$index");it.enabled=index==0}
        }
        skyFlap=requireNotNull(bodies[2].findEntity("OutletFlap"))
        syncSeries(vm.state.value.game.series)
        val skyHopper=loadAsset("hopper-sky",machine).also{it.setName("ClawCapsules")}
        clawMachine=ClawMachine(loadAsset("claw-rig",machine).also{it.setName("ClawAssembly")},skyHopper)
        studyClaw=ClawMachine(loadAsset("claw-rig",studyMachine).also{it.setName("StudyClawAssembly")})
        joystick=requireNotNull(housings[2].findEntity("ClawJoystick"))
        grabButton=requireNotNull(housings[2].findEntity("ClawButton"))
        grabButtonRest=position(grabButton)
        makeHandle(joystick,.27f,"joystick")
        makeHandle(grabButton,.24f,"grab")
        vm.state.value.definitions.forEach { definition ->
            Log.i(TAG,"LOAD_BEGIN toy-${definition.id}")
            val model=Entity.loadSuspend("asset://art/toy-${definition.id}.glb")
            templates+=Entity().also{it.addChild(model);it.enabled=false}
        }
        seriesPreview=SeriesPreview(root,templates,vm.state.value.definitions)
        album=ToyAlbum(root,templates){entity,key->handles[entity]=key}
        revealEffects=RevealEffects(play)
        // Only this utility target refills; none of the purchase stages has a UI button.
        val lidTarget=Entity();machine.addChild(lidTarget);lidTarget.position(Vector3(0f,.435f,0f));makeHandle(lidTarget,.04f,"refill")
        listOf(Triple(-32f,-28f,1250f),Triple(25f,140f,400f)).forEach{(pitch,yaw,lux)->
            Entity().also{root.addChild(it);it.components.set(DirectionalLightComponent(Color4.WHITE,lux,false));it.components[TransformComponent::class.java]?.setEulerAngles(EulerAngles(pitch,yaw,0f))}
        }
        machineMotion.clips.getValue("Capsule_Dispense").filter{it.name.startsWith("HopperBall")}.map{it.name.substringBefore('.')}.distinct().forEach{name->
            val e=requireNotNull(nodes[name]){"Missing hopper node $name"};hopperStart[name]=position(e) to rotation(e)
        }
        captureHopper()
    }
    /** Bind native attachments before applying the initial hidden state of the book. */
    fun finishSetup(){
        ready=true;loaded=true;vm.onEvent(CapsuleEvent.Loaded)
        syncToys();applyMode();Log.i(TAG,"ASSETS_READY hopper=24 series=${vm.state.value.series.size} toys=${templates.size} space=${context.getSelfSpaceState()}")
    }
    fun attachAlbumLabel(entity:Entity,index:Int,scale:Float){album.attach(entity,index,scale)}
    private fun albumIndex(entity:Entity?)=target(entity)?.substringAfter("album:","")?.toIntOrNull()?.let{album.toyIndex(it)}
    private fun albumAvailable(entity:Entity?):Boolean {
        val s=vm.state.value.game
        val index=albumIndex(entity)?:return false
        return vm.state.value.flow.screen==ExperienceScreen.PLAY&&s.albumOpen&&s.available(index)>0
    }
    fun target(entity:Entity?):String? {
        var e=entity
        while(e!=null){handles[e]?.let{return it};e=e.getParent()}
        return null
    }
    fun toyId(entity:Entity?):Long?{var e=entity;while(e!=null){toyIds[e]?.let{return it};e=e.getParent()};return null}
    fun canDrag(entity:Entity):Boolean {
        if(ready&&canPlaceScene&&target(entity)=="scene")return true
        if(!ready||!acceptsGameInput||vm.state.value.game.mode!=ExperienceMode.PLAY)return false
        val flow=vm.state.value.flow
        if(flow.screen==ExperienceScreen.TUTORIAL){
            val allowed=when(flow.step){
                TutorialStep.COIN->target(entity)=="coin"
                TutorialStep.TURN->target(entity)=="crank"
                TutorialStep.TAKE,TutorialStep.OPEN->target(entity)=="capsule"
                TutorialStep.STORE->toyId(entity)==flow.tutorialToyId
                TutorialStep.POSITION->false
            }
            if(!allowed)return false
        }
        val phase=vm.state.value.game.phase
        return (toyId(entity)?.let{canHandleToy(it)&&it !in vm.state.value.game.storedIds}==true)||albumAvailable(entity)||when(target(entity)){
            "coin"->phase==CapsulePhase.COIN_READY&&vm.state.value.game.stock>0
            "crank"->phase==CapsulePhase.CREDITED&&!vm.state.value.game.isClaw
            "joystick"->phase==CapsulePhase.CREDITED&&vm.state.value.game.isClaw
            "capsule"->phase in listOf(CapsulePhase.CAPSULE_READY,CapsulePhase.CAPSULE_TAKEN)
            "lid"->phase in listOf(CapsulePhase.CAPSULE_READY,CapsulePhase.CAPSULE_TAKEN)
            else->false
        }
    }
    fun canTransform(entity:Entity)=ready&&acceptsGameInput&&vm.state.value.game.mode==ExperienceMode.PLAY&&toyId(entity)?.let{canHandleToy(it)&&it !in vm.state.value.game.storedIds}==true
    fun canTap(entity:Entity)=ready&&vm.state.value.flow.screen==ExperienceScreen.PLAY&&vm.state.value.game.mode==ExperienceMode.PLAY&&(target(entity)=="refill"||(target(entity)=="grab"&&vm.state.value.game.isClaw&&vm.state.value.game.phase==CapsulePhase.CREDITED)||(vm.state.value.game.albumOpen&&albumIndex(entity)!=null))
    fun tap(entity:Entity?){
        if(entity==null||!canTap(entity))return
        if(target(entity)=="refill")vm.onEvent(CapsuleEvent.Refill)
        else if(target(entity)=="grab")vm.pulseClawPrimary()
        else if(vm.state.value.game.albumOpen&&albumIndex(entity)!=null)vm.onEvent(CapsuleEvent.TakeFromAlbum(requireNotNull(albumIndex(entity))))
    }
    fun beginDrag(entity:Entity?){
        if(entity==null||!canDrag(entity))return
        dragged=target(entity);albumDragToy=null
        if(albumAvailable(entity)){
            val index=requireNotNull(albumIndex(entity))
            val id=vm.state.value.game.toys.first{it.toy==index&&it.id in vm.state.value.game.storedIds}.id
            vm.onEvent(CapsuleEvent.TakeFromAlbum(index));syncToys();albumDragToy=id
            toyEntities[id]?.position(album.takePosition(index,root))
        }
    }
    fun drag(entity:Entity?,rootDelta:Vector3) {
        if(albumDragToy!=null){transformToy(toyEntities[albumDragToy],rootDelta,Quat.identity(),1f);return}
        if(toyId(entity)!=null){transformToy(entity,rootDelta,Quat.identity(),1f);return}
        val kind=target(entity)?:return;val s=vm.state.value.game
        if(ready&&canPlaceScene&&kind=="scene"){volume.move(rootDelta);return}
        if(!ready||!acceptsGameInput||s.mode!=ExperienceMode.PLAY)return
        val delta=rootDelta
        when(kind) {
            "coin" -> if(s.phase==CapsulePhase.COIN_READY&&s.stock>0){
                if(!listOf(delta.x,delta.y,delta.z).all{it.isFinite()})return
                val next=position(coin)+delta
                val approach=CoinMotion.distanceToPath(position(coin),next,coinSlot)
                if(approach<COIN_ACCEPT_RADIUS){
                    vm.onEvent(CapsuleEvent.CoinDropped(approach))
                    if(vm.state.value.game.phase==CapsulePhase.CREDITED){coinAge=0f;coin.position(coinSlot+Vector3(0f,0f,.025f))}
                }else{
                    val bounds=housings[s.series].getVisualBounds(root)
                    // The protruding cabinet/globe must not block a slow approach to the recessed slot.
                    val dx=next.x-coinSlot.x;val dy=next.y-coinSlot.y
                    val front=if(dx*dx+dy*dy<.09f*.09f)Vector3(bounds.max.x,bounds.max.y,coinSlot.z) else bounds.max
                    coin.position(CoinMotion.outsideMachine(next,bounds.min,front));volume.contain(coin)
                }
            }
            "crank" -> if(s.phase==CapsulePhase.CREDITED&&!s.isClaw){val arc=sqrt(delta.x*delta.x+delta.y*delta.y+delta.z*delta.z);val radius=if(s.series==0).038f else .055f;vm.onEvent(CapsuleEvent.CrankMoved(arc/radius*180f/PI.toFloat()))}
            "joystick" -> if(s.phase==CapsulePhase.CREDITED&&s.isClaw){
                val dx=delta.x/(.1f*CapsuleLayout.MACHINE_SCALE)*2f;val dz=delta.z/(.1f*CapsuleLayout.MACHINE_SCALE)*2f
                vm.onEvent(CapsuleEvent.ClawMoved(dx,dz));vm.showClawJoystick(dx*10f,dz*10f)
            }
            "capsule", "lid" -> if(s.phase in listOf(CapsulePhase.CAPSULE_READY,CapsulePhase.CAPSULE_TAKEN)){
                capsule.position(position(capsule)+delta);volume.contain(capsule);vm.onEvent(CapsuleEvent.CapsulePulled(distance(position(capsule),CapsuleLayout.capsuleRest)))
            }
        }
    }
    fun canHandleCapsule(entity:Entity)=ready&&acceptsGameInput&&vm.state.value.game.mode==ExperienceMode.PLAY&&
        vm.state.value.game.phase in listOf(CapsulePhase.CAPSULE_READY,CapsulePhase.CAPSULE_TAKEN)&&target(entity)=="capsule"
    fun transformCapsule(delta:Vector3,scale:Float,twoHands:Boolean){
        if(!canHandleCapsule(capsule)||!scale.isFinite()||scale<=0f||!listOf(delta.x,delta.y,delta.z).all{it.isFinite()})return
        if(vm.state.value.game.phase==CapsulePhase.CAPSULE_READY){dragged="capsule";drag(capsule,delta);return}
        capsule.position(position(capsule)+delta);volume.contain(capsule)
        if(!twoHands){endCapsuleTransform();return}
        if(!shellPull.both){beginHalf(0);beginHalf(1)}
        // Scale is incremental hand separation, applied to the current virtual grip span.
        val separation=((.08f+shellPull.gap)*(scale-1f)).coerceAtLeast(-shellPull.gap)
        moveHalf(0,Vector3(0f,-separation*.5f,0f))
        moveHalf(1,Vector3(0f,separation*.5f,0f))
    }
    fun endCapsuleTransform(){endHalf(0);endHalf(1);if(dragged=="capsule")dragged=null}
    fun beginHalf(half:Int){if(vm.state.value.game.phase==CapsulePhase.CAPSULE_TAKEN)shellPull.begin(half)}
    fun moveHalf(half:Int,delta:Vector3){
        if(!acceptsGameInput||vm.state.value.game.phase!=CapsulePhase.CAPSULE_TAKEN||vm.state.value.game.mode!=ExperienceMode.PLAY)return
        if(!listOf(delta.x,delta.y,delta.z).all{it.isFinite()})return
        if(!shellPull.both){capsule.position(position(capsule)+delta);volume.contain(capsule);return}
        shellPull.move(half,delta)
        fun local(v:Vector3)=capsule.convertPositionFrom(v,root)-capsule.convertPositionFrom(Vector3.ZERO,root)
        capsuleBottom.position(local(shellPull.displacement(0)))
        lid.position(Vector3(0f,0f,-.0235f)+local(shellPull.displacement(1)))
        if(shellPull.gap>=CAPSULE_PULL_DISTANCE){
            capsule.position(position(capsule)+(shellPull.displacement(0)+shellPull.displacement(1))*.5f)
            capsuleBottom.position(Vector3(0f,-.012f,0f));lid.position(Vector3(0f,.012f,-.0235f))
            vm.onEvent(CapsuleEvent.ShellsSeparated(shellPull.gap));shellPull.reset()
        }
    }
    fun endHalf(half:Int){shellPull.end(half);if(vm.state.value.game.phase==CapsulePhase.CAPSULE_TAKEN)resetShells(false)}
    private fun resetShells(resetGrip:Boolean=true){
        if(resetGrip)shellPull.reset()
        if(!::lid.isInitialized)return
        capsuleBottom.position(Vector3.ZERO);capsuleBottom.rotation(Quat.identity());capsuleBottom.scale(1f)
        lid.position(Vector3(0f,0f,-.0235f));lid.rotation(Quat.identity());lid.scale(1f)
    }
    fun endDrag(entity:Entity?)=finishDrag(entity,false)
    fun cancelDrag(entity:Entity?)=finishDrag(entity,true)
    private fun finishDrag(entity:Entity?,cancelled:Boolean) {
        // The SDK may no longer supply the hit entity after occlusion or cancellation.
        val kind=dragged?:target(entity)
        if(kind=="coin"&&vm.state.value.game.phase==CapsulePhase.COIN_READY){
            if(!cancelled)vm.onEvent(CapsuleEvent.CoinDropped(distance(position(coin),coinSlot)))
            if(vm.state.value.game.phase==CapsulePhase.CREDITED)coinAge=0f
            else coin.position(CapsuleLayout.coinHome)
        }
        if(toyId(entity)!=null||albumDragToy!=null)finishToyTransform()
        if(kind=="joystick")vm.clearClawJoystick()
        albumDragToy=null;dragged=null
    }
    fun transformToy(entity:Entity?,delta:Vector3,quaternion:Quat,scale:Float) {
        if(entity==null||!canTransform(entity)||!scale.isFinite()||scale<=0f)return
        if(!listOf(delta.x,delta.y,delta.z,quaternion.x,quaternion.y,quaternion.z,quaternion.w).all{it.isFinite()})return
        val id=toyId(entity)?:return;val e=toyEntities[id]?:return
        if(vm.state.value.game.activeToy==id)revealEffects.stop()
        vm.onEvent(CapsuleEvent.ToyPicked(id));movingToy=id
        e.position(position(e)+delta);e.rotation((quaternion*rotation(e)).normalize())
        val old=e.components[TransformComponent::class.java]?.scaleVector?.x?:TOY_DEFAULT_SCALE;e.scale((old*scale).coerceIn(TOY_MIN_SCALE,TOY_MAX_SCALE));volume.contain(e)
    }
    fun finishToyTransform() {
        val id=movingToy?:return;movingToy=null;val e=toyEntities[id]?:return
        val p=position(e);val q=rotation(e);val toy=vm.state.value.game.toys.firstOrNull{it.id==id}?:return
        vm.onEvent(CapsuleEvent.PlaceToy(toy.copy(x=p.x,y=p.y,z=p.z,qx=q.x,qy=q.y,qz=q.z,qw=q.w,scale=e.components[TransformComponent::class.java]?.scaleVector?.x?:TOY_DEFAULT_SCALE)))
        if(vm.state.value.game.albumOpen&&album.accepts(position(e),root))vm.onEvent(CapsuleEvent.StoreToy(id))
    }
    private fun syncToys() {
        val state=vm.state.value.game
        album.sync(state.toys.map{it.toy}.toSet(),albumVisible(),vm.state.value.definitions.indices.filter{vm.state.value.definitions[it].series==state.series})
        state.toys.forEach { toy->
            if(toy.id !in toyEntities){
                val entity=requireNotNull(templates[toy.toy].clone(Entity.CloneOptions(recursive=true,shouldShareMaterialInstance=true)));play.addChild(entity);entity.enabled=true;entity.setName("CollectedToy_${toy.id}")
                entity.position(Vector3(toy.x,toy.y,toy.z));entity.rotation(Quat(toy.qx,toy.qy,toy.qz,toy.qw));entity.scale(toy.scale)
                entity.components.set(CollisionComponent(collisionShape=listOf(ShapeResource.createSphere(.035f).offsetByTranslation(Vector3(0f,.025f,0f))),physicsMaterial=PhysicsMaterialResource()))
                entity.components.set(InteractableComponent());entity.components.set(HoverEffectComponent());toyEntities[toy.id]=entity;toyIds[entity]=toy.id
                Log.i(TAG,"TOY_REVEALED id=${toy.id} kind=${toy.toy} collectible=true")
            }
            val entity=toyEntities.getValue(toy.id)
            if(toy.id in state.storedIds)entity.enabled=false
            else {
                if(!entity.enabled){entity.position(Vector3(toy.x,toy.y,toy.z));entity.rotation(Quat(toy.qx,toy.qy,toy.qz,toy.qw));entity.scale(toy.scale)}
                entity.enabled=true
            }
        }
    }
    fun showTitleOptions(open:Boolean){
        titleOptionsOpen=open
        syncTitle()
    }
    private fun syncTitle(){
        val home=vm.state.value.flow.screen==ExperienceScreen.HOME
        titleScene?.root?.enabled=home&&!titleOptionsOpen
        root.findEntity("UI_TitleLogo")?.enabled=home
        root.findEntity("UI_TitleActions")?.enabled=home&&!titleOptionsOpen
        root.findEntity("UI_TitleOptions")?.enabled=home&&titleOptionsOpen
    }
    private fun applyMode(){
        val state=vm.state.value;val s=state.game;val flow=state.flow
        val home=flow.screen==ExperienceScreen.HOME
        val freePlay=flow.screen==ExperienceScreen.PLAY
        val tutorial=flow.screen==ExperienceScreen.TUTORIAL
        val isStudy=s.mode==ExperienceMode.STUDY
        play.enabled=!home&&!isStudy;study.enabled=!home&&isStudy
        syncTitle()
        root.findEntity("UI_Tutorial")?.enabled=tutorial||flow.screen==ExperienceScreen.COMPLETE
        root.findEntity("UI_Toolbar")?.enabled=freePlay||tutorial
        root.findEntity("UI_Status")?.enabled=freePlay||tutorial
        root.findEntity("UI_Bubble")?.enabled=freePlay
        root.findEntity("SceneMoveGrip")?.enabled=canPlaceScene
        root.findEntity("UI_ClawControls")?.enabled=freePlay&&s.isClaw&&!isStudy
        root.findEntity("UI_Series")?.enabled=freePlay&&!isStudy
        root.findEntity("UI_StudyGuide")?.enabled=freePlay&&isStudy
        album.sync(s.toys.map{it.toy}.toSet(),albumVisible(),state.definitions.indices.filter{state.definitions[it].series==s.series})
        seriesPreview.sync(s.series,freePlay&&!isStudy)
        syncToys()
    }
    private fun syncTutorialTarget(){
        val state=vm.state.value
        val target=if(state.flow.screen!=ExperienceScreen.TUTORIAL)null else when(state.flow.step){
            TutorialStep.POSITION->Vector3(0f,.535f,.16f)
            TutorialStep.COIN->position(coin)
            TutorialStep.TURN->if(state.game.phase==CapsulePhase.CREDITED)CapsuleLayout.machinePosition+machineMotion.anchors.getValue("crank")*CapsuleLayout.MACHINE_SCALE else null
            TutorialStep.TAKE,TutorialStep.OPEN->if(state.game.phase==CapsulePhase.OPENING)null else position(capsule)
            TutorialStep.STORE->null
        }
        tutorialMarker.sync(target,if(state.flow.step in listOf(TutorialStep.TAKE,TutorialStep.OPEN)).075f else .055f)
    }
    private fun syncSeries(series:Int){
        if(series==lastSeries)return
        housings.forEachIndexed{index,entity->entity.enabled=index==series}
        studyHousings.forEachIndexed{index,entity->entity.enabled=index==series}
        bodies.forEachIndexed{index,entity->entity.enabled=index==series}
        studyBodies.forEachIndexed{index,entity->entity.enabled=index==series}
        cranks.forEachIndexed{index,entity->entity.enabled=index==series}
        studyCranks.forEachIndexed{index,entity->entity.enabled=index==series}
        standardHopper.enabled=series!=2
        root.findEntity("UI_ClawControls")?.enabled=series==2&&vm.state.value.game.mode==ExperienceMode.PLAY&&vm.state.value.flow.screen==ExperienceScreen.PLAY
        nodes.clear();nodes.putAll(hopperNodes);nodes.putAll(bodyNodes[series])
        if(series<2)nodes["Crank"]=requireNotNull(cranks[series].findEntity("Crank"))
        cachedClipTime=Float.NaN
        lastSeries=series
    }
    private fun paintCapsule(entity:Entity,color:String){
        entity.components[ModelComponent::class.java]?.materials?.forEach{material->
            when(material){
                is PhysicallyBasedMaterial->if(material.getName().contains("CapsuleTint"))material.setBaseColor(Color4.fromSRGBHex(color))
                is UnlitMaterial->if(material.getName().contains("CapsuleTint"))material.setBaseColor(Color4.fromSRGBHex(color))
            }
        }
        entity.getChildren().forEach{paintCapsule(it,color)}
    }
    private fun captureHopper(){hopperEnd.clear();hopperStart.keys.forEach{name->nodes[name]?.let{hopperEnd[name]=position(it) to rotation(it)}}}
    private fun applyDispense(time:Float) {
        if(time==cachedClipTime)return;cachedClipTime=time
        for(track in machineMotion.clips.getValue("Capsule_Dispense")){
            val name=track.name.substringBefore('.');val property=track.name.substringAfter('.');val v=track.sample(time)
            if(name=="DeliveryCapsule"){
                when(property){"position"->capsule.position(CapsuleLayout.machinePosition+Vector3(v[0],v[1],v[2])*(.1f*CapsuleLayout.MACHINE_SCALE));"scale"->capsule.scale(v[0]*CapsuleLayout.MACHINE_SCALE);"quaternion"->capsule.rotation(Quat(v[0],v[1],v[2],v[3]).normalize())};continue
            }
            val e=nodes[name]?:continue
            if(name.startsWith("HopperBall")){
                val start=hopperStart.getValue(name);val base=hopperEnd.getValue(name)
                if(property=="position"){
                    val offset=atan2(base.first.z,base.first.x)-atan2(start.first.z,start.first.x)
                    e.position(Vector3(v[0]*cos(offset)-v[2]*sin(offset),v[1]-start.first.y+base.first.y,v[0]*sin(offset)+v[2]*cos(offset)))
                }else if(property=="quaternion")e.rotation((Quat(v[0],v[1],v[2],v[3])*start.second.conjugate()*base.second).normalize())
            }else if(property=="quaternion")e.rotation(Quat(v[0],v[1],v[2],v[3]).normalize())
        }
    }
    private fun applyClawDelivery(s:CapsuleState) {
        fun step(start:Float,end:Float):Float{val t=((s.elapsed-start)/(end-start)).coerceIn(0f,1f);return t*t*(3f-2f*t)}
        val opening=if(s.phase==CapsulePhase.CLAWING&&s.clawTarget!=null)step(4.55f,4.75f)*(1f-step(5.35f,5.6f)) else 0f
        skyFlap.components[TransformComponent::class.java]?.setEulerAngles(EulerAngles((-.24f-1.11f*opening)*180f/PI.toFloat(),0f,0f))
        if(s.phase!=CapsulePhase.CLAWING||s.clawTarget==null||s.elapsed<4.4f)return
        val t=((s.elapsed-4.4f)/1.2f).coerceIn(0f,1f)
        val drop=t*t*(3f-2f*t)
        val delivery=if(drop<.35f){val u=drop/.35f;Vector3(-.86f+.86f*u,2.42f+(.63f-2.42f)*u,.56f+.14f*u)}
            else{val u=(drop-.35f)/.65f;Vector3(.72f*u,.63f+(.255f-.63f)*u,.70f+(1.56f-.70f)*u)}
        capsule.position(CapsuleLayout.machinePosition+delivery*(.1f*CapsuleLayout.MACHINE_SCALE));capsule.scale(CapsuleLayout.MACHINE_SCALE);capsule.rotation(Quat.identity())
    }
    fun update() {
        if(!active)return
        volume.containVisibleToys(toyEntities.values)
        val now=java.lang.System.nanoTime();val dt=if(lastNanos==0L)0f else ((now-lastNanos)/1e9f).coerceAtLeast(0f);lastNanos=now
        if(!ready)return
        var s=vm.state.value.game
        syncSeries(s.series)
        val flow=vm.state.value.flow
        if(s.mode!=lastMode||flow.screen!=lastScreen||flow.step!=lastTutorialStep){
            applyMode();lastMode=s.mode;lastScreen=flow.screen;lastTutorialStep=flow.step
            if(!acceptsGameInput||s.mode==ExperienceMode.STUDY){
                if(s.phase==CapsulePhase.CAPSULE_TAKEN)resetShells()
                if(dragged=="coin")coin.position(CapsuleLayout.coinHome)
                dragged=null;finishToyTransform();revealEffects.stop()
            }
        }
        seriesPreview.sync(s.series,flow.screen==ExperienceScreen.PLAY&&s.mode==ExperienceMode.PLAY)
        syncTutorialTarget()
        if(flow.screen==ExperienceScreen.HOME){titleScene?.update(dt);return}
        if(!acceptsGameInput)return
        if(s.mode==ExperienceMode.STUDY){revealEffects.stop();clawMachine.sync(s,true);studyClaw.sync(s,true);return}
        if(s.capsuleColor!=lastCapsuleColor){
            val color=CapsuleColor.entries[s.capsuleColor]
            paintCapsule(capsuleBottom,color.bottom);paintCapsule(capsuleTop,color.top)
            lastCapsuleColor=s.capsuleColor
        }
        if(s.phase==CapsulePhase.CREDITED&&lastPhase!=CapsulePhase.CREDITED){captureHopper();cachedClipTime=Float.NaN;coinAge=0f}
        val cp=position(capsule)
        val wasOpening=s.phase==CapsulePhase.OPENING
        vm.onEvent(CapsuleEvent.Tick(dt,cp.x,cp.y+.035f,cp.z));s=vm.state.value.game
        syncToys()
        when {
            s.phase==CapsulePhase.CAPSULE_TAKEN&&shellPull.both -> revealEffects.opening(cp,shellPull.gap/CAPSULE_PULL_DISTANCE*1.4f)
            s.phase==CapsulePhase.OPENING -> revealEffects.opening(cp,s.elapsed)
            s.phase==CapsulePhase.REVEALED && (wasOpening || lastPhase==CapsulePhase.OPENING) -> {
                shellFade=0f
                val toy=s.toys.first{it.id==s.activeToy}
                revealEffects.reveal(cp,toyEntities.getValue(toy.id),s.toys.count{it.toy==toy.toy}==1)
            }
            s.phase==CapsulePhase.REVEALED -> revealEffects.update(dt)
            else -> revealEffects.stop()
        }
        val controls=vm.state.value.clawControls
        val hand=if(dragged=="joystick")1f else 0f
        val tiltX=if(hand>0f)if(controls.front)18f else if(controls.back)-18f else 0f else controls.z*18f
        val tiltZ=if(hand>0f)if(controls.right)-18f else if(controls.left)18f else 0f else -controls.x*18f
        joystick.components[TransformComponent::class.java]?.setEulerAngles(EulerAngles(tiltX,0f,tiltZ))
        grabButton.position(grabButtonRest+Vector3(0f,if(controls.primaryPressed)-.025f else 0f,0f))
        if(s.phase==CapsulePhase.CAPSULE_TAKEN&&lastPhase==CapsulePhase.CAPSULE_READY&&dragged!="capsule")capsule.position(CapsuleLayout.capsuleRest+Vector3(0f,.05f,.07f))
        clawMachine.sync(s);studyClaw.sync(s,true)
        if(s.phase==CapsulePhase.CREDITED){
            if(!s.isClaw)applyDispense(s.crank/360f*1.1f)
            if(coinAge<CoinMotion.INSERT_SECONDS){
                coinAge+=dt
                val t=(coinAge/CoinMotion.INSERT_SECONDS).coerceIn(0f,1f)
                coin.position(coinSlot+Vector3(0f,0f,.025f*(1f-t)))
                coin.scale(maxOf(.001f,1.5f*(1f-t)))
            }
        }
        if(s.phase==CapsulePhase.DISPENSING)applyDispense(1.1f+s.elapsed)
        if(s.isClaw)applyClawDelivery(s)
        if(s.phase==CapsulePhase.CAPSULE_READY&&lastPhase!=CapsulePhase.CAPSULE_READY){
            if(s.isClaw){capsule.position(CapsuleLayout.capsuleRest);capsule.scale(CapsuleLayout.MACHINE_SCALE)}else applyDispense(3.2f)
            Log.i(TAG,"DISPENSED stock=${s.stock} claw=${s.isClaw}")
        }
        if(s.phase==CapsulePhase.OPENING||s.phase==CapsulePhase.REVEALED){
            val t=if(s.phase==CapsulePhase.REVEALED)1f else (s.elapsed/1.4f).coerceIn(0f,1f)
            val u=t*t*(3f-2f*t)
            capsuleBottom.position(Vector3(-.065f*u,-.012f-.04f*u,0f))
            lid.position(Vector3(.065f*u,.012f-.025f*u,-.0235f))
            capsuleBottom.components[TransformComponent::class.java]?.setEulerAngles(EulerAngles(0f,0f,35f*u))
            lid.components[TransformComponent::class.java]?.setEulerAngles(EulerAngles(0f,0f,-35f*u))
            if(s.phase==CapsulePhase.REVEALED){
                shellFade+=dt
                val size=(1f-shellFade/.6f).coerceIn(.001f,1f)
                capsuleBottom.scale(size);lid.scale(size)
            }
        }
        if(s.phase==CapsulePhase.COIN_READY&&lastPhase!=CapsulePhase.COIN_READY){
            coin.position(CapsuleLayout.coinHome);coin.scale(1.5f);capsule.position(CapsuleLayout.capsuleRest);capsule.scale(CapsuleLayout.MACHINE_SCALE);resetShells()
        }
        coin.enabled=(s.phase==CapsulePhase.COIN_READY&&s.stock>0)||(s.phase==CapsulePhase.CREDITED&&coinAge<CoinMotion.INSERT_SECONDS)
        capsule.enabled=(s.phase==CapsulePhase.CLAWING&&s.clawTarget!=null&&s.elapsed>=4.4f)||s.phase in listOf(CapsulePhase.DISPENSING,CapsulePhase.CAPSULE_READY,CapsulePhase.CAPSULE_TAKEN,CapsulePhase.OPENING)||(s.phase==CapsulePhase.REVEALED&&shellFade<.6f)
        for(index in 0..23)nodes["HopperBall$index"]?.enabled=index<s.stock
        lastPhase=s.phase
    }
    fun close(){pause();templates.forEach{it.destroy()};volume.close();loaded=false}
    fun debugEntity(kind:String):Entity?=if(kind=="crank")cranks.getOrNull(vm.state.value.game.series) else if(kind=="lid")lid else handles.entries.firstOrNull{it.value==kind}?.key
    companion object {
        const val TAG="PaperCapsuleAR"
    }
}
class CapsuleSystem:com.pico.spatial.core.ecs.System() {
    override fun update(context:SceneUpdateContext){scene?.takeIf{it.root.scene==context.scene}?.update()}
    companion object {var scene:CapsuleScene?=null}
}
