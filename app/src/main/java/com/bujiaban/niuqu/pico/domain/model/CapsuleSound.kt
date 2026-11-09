package com.bujiaban.niuqu.pico.domain.model

enum class CapsuleSound {
    CLICK, COIN, DROP, OPEN, REVEAL, COLLECT, REFILL, MISS,
    CRANK_MOVE, CLAW_MOVE, CLAW_LOOP, STOP_MECHANISM,
}

/** Compare committed game states so rejected gestures and recomposition remain silent. */
fun capsuleSounds(before:CapsuleState,after:CapsuleState):List<CapsuleSound> = buildList {
    val oldLoop=mechanismSound(before)
    val newLoop=mechanismSound(after)
    if(oldLoop!=newLoop||(before.mode!=after.mode&&after.mode==ExperienceMode.STUDY))add(newLoop?:CapsuleSound.STOP_MECHANISM)
    if(before.phase==CapsulePhase.CREDITED&&after.phase==CapsulePhase.DISPENSING)add(CapsuleSound.STOP_MECHANISM)
    if(!before.ready||!after.ready||after.error!=null)return@buildList
    if(before.mode!=after.mode||before.series!=after.series||before.albumOpen!=after.albumOpen)add(CapsuleSound.CLICK)
    if(after.mode!=ExperienceMode.PLAY)return@buildList
    if(before.phase!=after.phase)when(after.phase){
        CapsulePhase.CREDITED->add(CapsuleSound.COIN)
        CapsulePhase.OPENING->add(CapsuleSound.OPEN)
        CapsulePhase.REVEALED->add(CapsuleSound.REVEAL)
        CapsulePhase.COIN_READY->if(before.phase==CapsulePhase.CLAWING)add(CapsuleSound.MISS)
        else->Unit
    }
    // First visible tray impact, rather than the later end of the dispense clip.
    val impact=when(before.phase){
        CapsulePhase.DISPENSING->if(before.series==0)1.8f else 1.3f
        CapsulePhase.CLAWING->if(before.clawTarget!=null)5.12f else null
        else->null
    }
    if(before.mode==ExperienceMode.PLAY&&impact!=null&&before.elapsed<impact&&
        ((after.phase==before.phase&&after.elapsed>=impact)||after.phase==CapsulePhase.CAPSULE_READY))add(CapsuleSound.DROP)
    if(after.phase==CapsulePhase.CREDITED){
        if(after.crank>before.crank)add(CapsuleSound.CRANK_MOVE)
        if(after.isClaw&&(after.clawX!=before.clawX||after.clawZ!=before.clawZ))add(CapsuleSound.CLAW_MOVE)
    }
    if(after.storedIds.size>before.storedIds.size)add(CapsuleSound.COLLECT)
    else if(after.storedIds.size<before.storedIds.size)add(CapsuleSound.CLICK)
    if(after.series==before.series&&after.stock>before.stock)add(CapsuleSound.REFILL)
}

fun mechanismSound(state:CapsuleState):CapsuleSound? =
    if(!state.ready||state.error!=null||state.mode!=ExperienceMode.PLAY)null
    else when(state.phase){
        CapsulePhase.DISPENSING->null
        CapsulePhase.CLAWING->CapsuleSound.CLAW_LOOP
        else->null
    }
