package com.bujiaban.niuqu.pico.scene

import com.pico.spatial.core.math.Vector3
import org.junit.Assert.*
import org.junit.Test

class CoinMotionTest {
    @Test fun fastPassAndStationaryCoinReachTheSlot(){
        assertEquals(0f,CoinMotion.distanceToPath(Vector3(-1f,0f,0f),Vector3(1f,0f,0f),Vector3.ZERO),.0001f)
        assertEquals(.08f,CoinMotion.distanceToPath(Vector3(.08f,0f,0f),Vector3(.08f,0f,0f),Vector3.ZERO),.0001f)
        assertEquals(.2f,CoinMotion.distanceToPath(Vector3(-1f,.2f,0f),Vector3(1f,.2f,0f),Vector3.ZERO),.0001f)
        assertEquals(1f,CoinMotion.distanceToPath(Vector3(1f,0f,0f),Vector3(2f,0f,0f),Vector3.ZERO),.0001f)
    }
    @Test fun CoinCannotTunnelThroughBodyButCanMoveBesideIt(){
        val min=Vector3(-.2f,0f,-.2f);val max=Vector3(.2f,1f,.2f)
        assertEquals(.225f,CoinMotion.outsideMachine(Vector3(0f,.5f,-5f),min,max).z,.0001f)
        assertEquals(.3f,CoinMotion.outsideMachine(Vector3(0f,.5f,.3f),min,max).z,0f)
        val side=Vector3(-.3f,.5f,-.3f)
        assertEquals(side,CoinMotion.outsideMachine(side,min,max))
    }
}
