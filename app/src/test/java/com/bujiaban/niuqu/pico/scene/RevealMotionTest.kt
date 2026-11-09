package com.bujiaban.niuqu.pico.scene

import org.junit.Assert.*
import org.junit.Test

class RevealMotionTest {
    @Test fun toySettlesAtAuthoredSizeWithSmallOvershoot(){
        assertEquals(.35f,RevealMotion.toyScale(0f),.0001f)
        assertEquals(1f,RevealMotion.toyScale(1f),.0001f)
        val samples=(0..100).map{RevealMotion.toyScale(it/100f)}
        assertTrue(samples.max()>1f)
        assertTrue(samples.all{it in .349f..1.07f})
        assertEquals(1f,RevealMotion.toyScale(4f),0f)
    }
    @Test fun particlesEaseInAndDisappearCompletely(){
        assertEquals(0f,RevealMotion.particleScale(0f),0f)
        assertTrue(RevealMotion.particleScale(.1f)>.9f)
        assertEquals(0f,RevealMotion.particleScale(1f),0f)
        assertEquals(0f,RevealMotion.particleScale(2f),0f)
        assertTrue((0..100).all{RevealMotion.particleScale(it/100f) in 0f..1f})
    }
}
