package com.bujiaban.niuqu.pico.domain.model

/** Six authored positions shared by the shelf, snapping and saved toy poses. */
object CollectionDisplay {
    const val SCALE=3f
    fun contains(toy:ToyPlacement)=(0..5).any{slot->val p=position(slot)
        kotlin.math.abs(toy.x-p[0])<.03f&&kotlin.math.abs(toy.y-p[1])<.03f&&kotlin.math.abs(toy.z-p[2])<.03f}
    fun position(slot:Int)=floatArrayOf(.40f+(slot%3)*.17f,.57f+(1-slot/3)*.10f,.20f+(slot/3)*.14f)
}
