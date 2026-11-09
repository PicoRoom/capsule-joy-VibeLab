package com.bujiaban.niuqu.pico.scene

import com.pico.spatial.core.math.Vector3
import kotlin.math.sqrt

/** Sweep the complete hand movement so a fast pass cannot skip the coin slot. */
internal object CoinMotion {
    const val INSERT_SECONDS=.24f
    fun distanceToPath(from:Vector3,to:Vector3,slot:Vector3):Float {
        val d=to-from
        val length=d.x*d.x+d.y*d.y+d.z*d.z
        val offset=slot-from
        val t=if(length>0f)((offset.x*d.x+offset.y*d.y+offset.z*d.z)/length).coerceIn(0f,1f) else 0f
        val nearest=from+d*t-slot
        return sqrt(nearest.x*nearest.x+nearest.y*nearest.y+nearest.z*nearest.z)
    }
    fun outsideMachine(point:Vector3,min:Vector3,max:Vector3):Vector3 {
        val clearance=.025f
        return if(point.x in min.x-clearance..max.x+clearance&&point.y in min.y-clearance..max.y+clearance)
            Vector3(point.x,point.y,maxOf(point.z,max.z+clearance)) else point
    }
}
