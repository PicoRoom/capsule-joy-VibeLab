package com.bujiaban.niuqu.pico.domain.model

/** Direct manipulation only: no latched velocity survives a hand release. */
data class ClawControls(val x:Float=0f,val z:Float=0f,val primaryPressed:Boolean=false,val aiming:Boolean=false) {
    val left get()=x<-.1f
    val right get()=x>.1f
    val back get()=z<-.1f
    val front get()=z>.1f
}
class ClawInput {
    private var handX=0f;private var handZ=0f;private var age=0f;private var pulseAge=0f
    private var dragging=false
    private var context:List<Any?> = emptyList()
    fun sync(game:CapsuleState){
        val next=listOf(game.series,game.mode,game.ready,game.error,game.phase)
        if(next!=context){context=next;clearHand()}
    }
    fun begin(game:CapsuleState){sync(game);dragging=game.canAimClaw}
    fun isDragging(game:CapsuleState):Boolean {sync(game);return dragging&&game.canAimClaw}
    fun pulse(){pulseAge=.12f}
    fun hand(x:Float,z:Float){handX=if(x.isFinite())x.coerceIn(-1f,1f) else 0f;handZ=if(z.isFinite())z.coerceIn(-1f,1f) else 0f;age=.12f}
    fun clearHand(){handX=0f;handZ=0f;age=0f;dragging=false}
    fun clear(){clearHand();pulseAge=0f;context=emptyList()}
    fun tick(dt:Float){age=(age-dt).coerceAtLeast(0f);pulseAge=(pulseAge-dt).coerceAtLeast(0f);if(age==0f){handX=0f;handZ=0f}}
    fun state(game:CapsuleState):ClawControls {sync(game);return ClawControls(handX,handZ,pulseAge>0f,dragging)}
}
