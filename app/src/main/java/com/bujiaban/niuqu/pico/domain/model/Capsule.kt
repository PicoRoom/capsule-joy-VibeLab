package com.bujiaban.niuqu.pico.domain.model

enum class CapsuleColor(val bottom:String,val top:String) {
    ROSE("#d78d9d","#f2d3d9"), MINT("#85b69a","#d1e9db"),
    LEMON("#d9bd68","#f4e8b9"), LILAC("#b197c5","#e3d5ed"),
    SKY("#86adce","#d6e8f2"), PEACH("#dba17f","#f3ddcb")
}
enum class ExperienceMode { PLAY, STUDY }
enum class CapsulePhase { COIN_READY, CREDITED, DISPENSING, CLAWING, CAPSULE_READY, CAPSULE_TAKEN, OPENING, REVEALED }
const val CLAW_DURATION=5.6f
const val COIN_ACCEPT_RADIUS=.11f
const val CAPSULE_PULL_DISTANCE=.035f
data class ClawSlot(val x:Float,val y:Float,val z:Float)
val CLAW_SLOTS=buildList {
    for(z in listOf(-.6f,-.2f,.2f,.6f))for(x in listOf(-.6f,-.2f,.2f,.6f))add(ClawSlot(x,2.82f,z))
    for(z in listOf(-.4f,0f,.4f))for(x in listOf(-.4f,0f,.4f))if(x!=0f||z!=0f)add(ClawSlot(x,3.16f,z))
}
data class ToyDefinition(val id:String,val name:String,val series:Int,val weight:Int)
data class ToySeries(val id:String,val name:String,val description:String)
const val TOY_DEFAULT_SCALE=7.5f
const val TOY_MIN_SCALE=.8f
const val TOY_MAX_SCALE=16f
data class ToyPlacement(val id:Long,val toy:Int,val x:Float,val y:Float,val z:Float,val qx:Float=0f,val qy:Float=0f,val qz:Float=0f,val qw:Float=1f,val scale:Float=TOY_DEFAULT_SCALE)
data class CapsuleState(
    val mode:ExperienceMode=ExperienceMode.PLAY,
    val phase:CapsulePhase=CapsulePhase.COIN_READY,
    val series:Int=0,
    val capsuleColor:Int=0,
    val albumOpen:Boolean=false,
    val stock:Int=24,
    val crank:Float=0f,
    val clawX:Float=.50f,
    val clawZ:Float=.48f,
    val clawAvailable:Set<Int> = CLAW_SLOTS.indices.toSet(),
    val clawTarget:Int?=null,
    val elapsed:Float=0f,
    val pending:Int?=null,
    val activeToy:Long?=null,
    val toys:List<ToyPlacement> = emptyList(),
    val storedIds:Set<Long> = emptySet(),
    val message:String="从左侧零钱盒拿一枚金币，靠近投币口即可自动投入。",
    val ready:Boolean=false,
    val error:String?=null,
) {
    val isClaw get()=series==2
    fun available(toy:Int)=toys.count{it.toy==toy&&it.id in storedIds}
    fun total(toy:Int)=toys.count{it.toy==toy}
}

sealed interface CapsuleEvent {
    data object Loaded:CapsuleEvent
    data class Failure(val reason:String):CapsuleEvent
    data class Mode(val value:ExperienceMode):CapsuleEvent
    data class Series(val index:Int):CapsuleEvent
    data class CoinDropped(val distance:Float):CapsuleEvent
    data class CrankMoved(val degrees:Float):CapsuleEvent
    data class ClawMoved(val dx:Float,val dz:Float):CapsuleEvent
    data object ClawGrab:CapsuleEvent
    data class CapsulePulled(val distance:Float):CapsuleEvent
    data class ShellsSeparated(val distance:Float):CapsuleEvent
    data class Tick(val dt:Float,val revealX:Float=0f,val revealY:Float=0f,val revealZ:Float=0f):CapsuleEvent
    data class ToyPicked(val id:Long):CapsuleEvent
    data class PlaceToy(val pose:ToyPlacement):CapsuleEvent
    data class TakeFromAlbum(val toy:Int):CapsuleEvent
    data class StoreToy(val id:Long):CapsuleEvent
    data object StoreAll:CapsuleEvent
    data object ToggleAlbum:CapsuleEvent
    data object Refill:CapsuleEvent
}
