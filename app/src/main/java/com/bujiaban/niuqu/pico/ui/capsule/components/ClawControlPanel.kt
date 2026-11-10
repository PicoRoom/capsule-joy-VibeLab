package com.bujiaban.niuqu.pico.ui.capsule.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bujiaban.niuqu.pico.domain.model.*
import com.bujiaban.niuqu.pico.ui.capsule.CapsuleUiState
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.Text

@Composable
fun ClawControlPanel(
    state:CapsuleUiState,
    onAimStart:()->Unit,
    onAimDrag:(Float,Float)->Unit,
    onAimEnd:()->Unit,
    onDrop:()->Unit,
    onAuxiliary:()->Unit,
){
    val game=state.game
    val aiming=game.canAimClaw
    val target=if(aiming)game.clawAimTarget() else null
    val start by rememberUpdatedState(onAimStart)
    val drag by rememberUpdatedState(onAimDrag)
    val end by rememberUpdatedState(onAimEnd)
    DisposableEffect(Unit){onDispose{end()}}
    val available=game.isClaw&&game.ready&&game.error==null&&game.mode==ExperienceMode.PLAY
    val auxiliary=when(game.phase){
        CapsulePhase.COIN_READY->if(game.stock>0)if(game.clawMissed)"再试一次" else "投币" else "售罄"
        CapsulePhase.CAPSULE_READY->"取蛋"
        CapsulePhase.REVEALED->"收好"
        else->null
    }
    Box(Modifier.fillMaxSize().background(ComicPaper,RoundedCornerShape(30.dp)).border(2.dp,ComicInk,RoundedCornerShape(30.dp))){
        Column(Modifier.padding(22.dp).width(374.dp)){
            Text(if(!state.clawLearned)"初次抓取 · 试着完成三步" else "瞄准板",fontSize=24.sp,fontWeight=FontWeight.Bold,color=ComicInk)
            Text(when(game.phase){
                CapsulePhase.CREDITED->"捏住拖动 · 松手停住"
                CapsulePhase.CLAWING->"机械爪正在抓取"
                CapsulePhase.CAPSULE_READY->"点击取蛋，拿出胶囊"
                CapsulePhase.CAPSULE_TAKEN->"用双手捏住胶囊掰开"
                CapsulePhase.OPENING->"惊喜即将揭晓"
                CapsulePhase.REVEALED->"收好后可继续"
                else->"投币后开始瞄准"
            },fontSize=18.sp,color=ComicInk)
            Spacer(Modifier.height(12.dp))
            Box(Modifier.size(374.dp,244.dp)
                .background(if(aiming)Color(0xffe3eee5) else Color(0xffe6e3da),RoundedCornerShape(20.dp))
                .border(if(state.clawControls.aiming)3.dp else 1.dp,ComicInk,RoundedCornerShape(20.dp))
                .semantics{testTag="claw_aim_pad";contentDescription="瞄准板，左右拖动控制左右，上下拖动控制前后，松手停止";if(!aiming)disabled()}
                .pointerInput(aiming){
                    if(aiming)try{
                        detectDragGestures(onDragStart={start()},onDragEnd={end()},onDragCancel={end()}){change,amount->
                            change.consume()
                            // One full pad sweep spans the cabinet; lifting allows repositioning.
                            drag(amount.x/size.width*1.4f,amount.y/size.height*1.3f)
                        }
                    }finally{end()}
                }
            ){
                Canvas(Modifier.fillMaxSize().padding(22.dp)){
                    val grid=ComicInk.copy(alpha=.12f)
                    for(i in 1..3){
                        drawLine(grid,Offset(size.width*i/4,0f),Offset(size.width*i/4,size.height),1.dp.toPx())
                        drawLine(grid,Offset(0f,size.height*i/4),Offset(size.width,size.height*i/4),1.dp.toPx())
                    }
                    val p=Offset((game.clawX+.7f)/1.4f*size.width,(game.clawZ+.65f)/1.3f*size.height)
                    drawCircle(if(target!=null)Color(0xff257965) else ComicInk,12.dp.toPx(),p,style=Stroke(3.dp.toPx()))
                    drawCircle(ComicInk,3.dp.toPx(),p)
                }
                if(!state.clawLearned)Text(when(game.phase){
                    CapsulePhase.COIN_READY->"① 点击${if(game.clawMissed)"再试一次" else "投币"}\n② 拖动圆环到胶囊上\n③ 松手，点击下爪"
                    CapsulePhase.CREDITED->if(target==null)"② 拖动圆环，直到变绿" else "③ 对准了！松手后下爪"
                    else->"抓取中，等待伙伴到达"
                },Modifier.align(Alignment.Center).padding(18.dp),fontSize=23.sp,color=ComicInk)
                Text("前",Modifier.align(Alignment.TopCenter),fontSize=16.sp,color=ComicInk)
                Text("后",Modifier.align(Alignment.BottomCenter),fontSize=16.sp,color=ComicInk)
            }
        }
        Column(Modifier.offset(420.dp,24.dp).width(198.dp),horizontalAlignment=Alignment.CenterHorizontally){
            Text(if(!aiming)when(game.phase){CapsulePhase.CLAWING->"抓取中";CapsulePhase.CAPSULE_READY->"抓到了！";CapsulePhase.REVEALED->game.message.substringBefore(" 拿起");CapsulePhase.CAPSULE_TAKEN->"双手掰开";CapsulePhase.OPENING->"开蛋中";else->"准备好再开始"}
                else if(state.clawControls.aiming)"松手后可下爪" else if(target!=null)"已对准胶囊" else "移动圆环瞄准",fontSize=18.sp,color=ComicInk)
            Spacer(Modifier.height(18.dp))
            Button(onClick=onDrop,enabled=aiming&&!state.clawControls.aiming,
                modifier=Modifier.size(164.dp).semantics{testTag="claw_drop";contentDescription="下爪"},shape=CircleShape,
                colors=ButtonDefaults.buttonColors(containerColor=Color(0xffe95747),contentColor=ComicPaper)){
                Text("下爪",fontSize=32.sp,fontWeight=FontWeight.Bold)
            }
            Spacer(Modifier.height(20.dp))
            if(auxiliary!=null)Button(onClick=onAuxiliary,enabled=available&&(game.phase!=CapsulePhase.COIN_READY||game.stock>0),
                modifier=Modifier.size(164.dp,56.dp).semantics{testTag="claw_auxiliary"},
                colors=ButtonDefaults.buttonColors(containerColor=ComicMint,contentColor=ComicInk)){
                Text(auxiliary,fontSize=22.sp)
            }
        }
        Text(if(game.phase==CapsulePhase.CAPSULE_TAKEN)"左手${if(state.leftGrip)"✓" else "待抓稳"} · 右手${if(state.rightGrip)"✓" else "待抓稳"} · 拉开 ${(state.openingProgress*100).toInt()}%" else "左右拖动调左右 · 上下拖动调前后",Modifier.align(Alignment.BottomCenter).padding(bottom=12.dp),fontSize=18.sp,color=ComicInk)
    }
}
