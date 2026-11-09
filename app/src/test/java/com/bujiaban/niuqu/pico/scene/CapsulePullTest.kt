package com.bujiaban.niuqu.pico.scene
import com.pico.spatial.core.math.Vector3
import org.junit.Assert.*
import org.junit.Test
class CapsulePullTest {
    @Test fun onlyTwoGripsCanSeparate(){
        val p=CapsulePull();p.begin(1);p.move(1,Vector3(0f,1f,0f));assertEquals(0f,p.gap,0f)
        p.begin(0);p.move(0,Vector3(0f,-.02f,0f));p.move(1,Vector3(0f,.02f,0f));assertEquals(.04f,p.gap,.0001f)
    }
    @Test fun releaseResetsPartialPullAndAllowsRegrip(){
        val p=CapsulePull();p.begin(0);p.begin(1);p.move(1,Vector3(0f,.02f,0f));p.end(1)
        assertEquals(0f,p.gap,0f);assertFalse(p.both)
        p.begin(1);assertTrue(p.both);assertEquals(0f,p.gap,0f)
        p.reset();assertFalse(p.both)
    }
    @Test fun movingTogetherOrPushingClosedDoesNotOpen(){
        val p=CapsulePull();p.begin(0);p.begin(1)
        p.move(0,Vector3(.1f,0f,0f));p.move(1,Vector3(.1f,0f,0f));assertEquals(0f,p.gap,0f)
        p.move(1,Vector3(0f,-.02f,0f));assertEquals(0f,p.gap,0f)
    }
}
