package com.bujiaban.niuqu.pico.domain.model

val CapsuleState.canAimClaw get()=isClaw&&ready&&error==null&&mode==ExperienceMode.PLAY&&phase==CapsulePhase.CREDITED

/** Preview and grab use exactly the same reach and occlusion rules. */
fun CapsuleState.clawAimTarget():Int? {
    fun distance(index:Int):Float {val slot=CLAW_SLOTS[index];return (slot.x-clawX)*(slot.x-clawX)+(slot.z-clawZ)*(slot.z-clawZ)}
    fun uncovered(index:Int):Boolean {
        val slot=CLAW_SLOTS[index]
        return clawAvailable.none{other->val above=CLAW_SLOTS[other];above.y>slot.y&&(above.x-slot.x)*(above.x-slot.x)+(above.z-slot.z)*(above.z-slot.z)<.355f*.355f}
    }
    return clawAvailable.filter{distance(it)<=.23f*.23f&&uncovered(it)}.minWithOrNull(compareByDescending<Int>{CLAW_SLOTS[it].y}.thenBy{distance(it)})
}
