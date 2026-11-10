package com.bujiaban.niuqu.pico.scene

import kotlin.math.*

/** Signed angles around the shaft. Radial movement and backtracking earn no turns. */
internal class CrankGesture {
    private var x=0f;private var y=0f
    private var travel=0f;private var furthest=0f
    fun begin(x:Float,y:Float){this.x=x;this.y=y;travel=0f;furthest=0f}
    fun move(dx:Float,dy:Float):Float {
        if(!dx.isFinite()||!dy.isFinite())return 0f
        val nx=x+dx;val ny=y+dy
        if(x*x+y*y<.000064f||nx*nx+ny*ny<.000064f){x=nx;y=ny;return 0f}
        var angle=(atan2(y,x)-atan2(ny,nx))*180f/PI.toFloat()
        while(angle>180f)angle-=360f
        while(angle< -180f)angle+=360f
        x=nx;y=ny
        if(abs(angle)>90f)return 0f
        travel+=angle
        val advance=(travel-furthest).coerceAtLeast(0f)
        furthest=maxOf(furthest,travel)
        return advance
    }
}
