package com.bujiaban.niuqu.pico.scene
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*
class CrankGestureTest {
    @Test fun radialAndDepthIndependentMotionCannotBuyATurn(){val g=CrankGesture();g.begin(.04f,0f);repeat(20){assertEquals(0f,g.move(.01f,0f),.001f)}}
    @Test fun clockwiseCircleCompletesAndReverseRetracingCannotFarmProgress(){
        val g=CrankGesture();g.begin(.04f,0f)
        var x=.04f;var y=0f;var total=0f
        repeat(24){i->val a=-(i+1)*PI.toFloat()/12;val nx=.04f*cos(a);val ny=.04f*sin(a);total+=g.move(nx-x,ny-y);x=nx;y=ny}
        assertEquals(360f,total,.01f)
        g.begin(.04f,0f)
        assertEquals(90f,g.move(-.04f,-.04f),.01f)
        assertEquals(0f,g.move(.04f,.04f),.01f)
        assertEquals(0f,g.move(-.04f,-.04f),.01f)
    }
}
