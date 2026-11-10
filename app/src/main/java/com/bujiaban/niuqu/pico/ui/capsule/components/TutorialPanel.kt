package com.bujiaban.niuqu.pico.ui.capsule.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bujiaban.niuqu.pico.domain.model.CapsulePhase
import com.bujiaban.niuqu.pico.ui.capsule.CapsuleUiState
import com.bujiaban.niuqu.pico.ui.capsule.ExperienceScreen
import com.bujiaban.niuqu.pico.ui.capsule.TutorialStep
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.Text
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private val TutorialCream=Color(0xfffffaef)
private val TutorialMuted=Color(0xff6d786d)
private val TutorialLine=Color(0xffdcded0)
private val TutorialSea=Color(0xff438c85)
private val TutorialCoral=Color(0xffe49d87)
private val TutorialBlue=Color(0xffa9badb)
private val TutorialShape=RoundedCornerShape(32.dp)

/** Each instruction stays visible until the corresponding gameplay event succeeds. */
@Composable
fun TutorialPanel(state:CapsuleUiState,onPositioned:()->Unit,onSkip:()->Unit,onFinish:()->Unit,onRecenter:()->Unit){
    val step=state.flow.step
    val complete=state.flow.screen==ExperienceScreen.COMPLETE
    val waiting=state.game.phase in listOf(CapsulePhase.DISPENSING,CapsulePhase.OPENING)
    val title=if(complete)"第一份好运，收好啦！" else when(step){
        TutorialStep.POSITION->"先找个舒服的位置"
        TutorialStep.COIN->"投入一枚金币"
        TutorialStep.TURN->"把期待转一圈"
        TutorialStep.TAKE->"拿起你的扭蛋"
        TutorialStep.OPEN->"双手打开惊喜"
        TutorialStep.STORE->"给伙伴一个小家"
    }
    val instruction=if(complete)"你已经学会投币、开蛋和收藏。\n现在去发现更多玩具伙伴吧。" else when{
        state.game.phase==CapsulePhase.DISPENSING->"咕噜……扭蛋正在滑落。\n等它停在底部托盘，再伸手拿。"
        state.game.phase==CapsulePhase.OPENING->"这一颗，会是什么呢？\n稍等一下，伙伴就要出现了。"
        else->when(step){
            TutorialStep.POSITION->"让机器在舒服的位置，\n伸出双手，准备第一颗惊喜。"
            TutorialStep.COIN->"从左侧零钱盒捏起金币，\n移近机器右侧的投币口。"
            TutorialStep.TURN->"捏住投币口旁的小摇柄，\n顺着圆周转满一圈。"
            TutorialStep.TAKE->"捏住底部托盘里的扭蛋，\n向自己拉近一点。"
            TutorialStep.OPEN->"双手分别捏住上下半壳，\n轻轻向两边拉开。"
            TutorialStep.STORE->"把玩具拖进右侧珍藏册，\n或点册子里的「全部收好」。"
        }
    }
    val tip=if(complete)"左侧切换机器 · 右侧打开珍藏册\n抓娃娃机投币后，用瞄准板移动落点。" else when(step){
        TutorialStep.POSITION->"用系统窗口控件移动整套场景。\n薄荷色横杆可在窗口内微调。"
        TutorialStep.COIN->"拇指与食指捏合拿取；\n靠近投币口时，金币会自动吸入。"
        TutorialStep.TURN->if(waiting)"不用再转了，等好运到站。" else "转动进度 ${state.game.crank.toInt()}° / 360°\n可以松手，再捏住继续转。"
        TutorialStep.TAKE->"取出来后，再用双手打开。"
        TutorialStep.OPEN->if(waiting)"这位小伙伴会自动加入你的收藏。" else "左手${if(state.leftGrip)" ✓" else " 待抓稳"} · 右手${if(state.rightGrip)" ✓" else " 待抓稳"}\n${if(state.trackingPaused)"保持位置，正在恢复追踪" else "拉开进度 ${(state.openingProgress*100).toInt()}% · 松手合回"}"
        TutorialStep.STORE->"玩具也能拿在手里旋转、缩放。\n这次先把它收进册子，完成教程。"
    }
    Column(Modifier.fillMaxSize().background(TutorialCream,TutorialShape)
        .border(1.5.dp,TutorialLine,TutorialShape).padding(28.dp)){
        Row(Modifier.fillMaxWidth().height(34.dp),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.SpaceBetween){
            Text(if(complete)"新手教程 · 已完成" else "新手教程  ${(step.ordinal+1).toString().padStart(2,'0')} / 06",
                fontSize=18.sp,fontFamily=FontFamily.Monospace,color=TutorialSea)
            TutorialAction(if(complete)"完成" else "跳过",if(complete)onFinish else onSkip,
                modifier=Modifier.width(82.dp).fillMaxHeight(),small=true)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().height(6.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)){
            repeat(TutorialStep.entries.size){index->
                Box(Modifier.weight(1f).fillMaxHeight().background(
                    if(complete||index<=step.ordinal)TutorialSea else TutorialLine,RoundedCornerShape(3.dp)))
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(title,modifier=Modifier.height(36.dp),fontSize=28.sp,fontWeight=FontWeight.Bold,color=ComicInk)
        Spacer(Modifier.height(10.dp))
        Text(instruction,modifier=Modifier.fillMaxWidth().height(64.dp),fontSize=23.sp,lineHeight=32.sp,color=ComicInk)
        Spacer(Modifier.height(10.dp))
        TutorialArtwork(if(complete)TutorialStep.STORE else step,complete,Modifier.fillMaxWidth().height(140.dp))
        Spacer(Modifier.height(14.dp))
        Text(tip,modifier=Modifier.fillMaxWidth().height(64.dp),fontSize=20.sp,lineHeight=28.sp,color=TutorialMuted)
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(60.dp),contentAlignment=Alignment.Center){
            when{
                complete->TutorialAction("自由游玩",onFinish,primary=true,modifier=Modifier.fillMaxSize())
                step==TutorialStep.POSITION->Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(20.dp)){
                    TutorialAction("重置摆放",onRecenter,modifier=Modifier.weight(1f).fillMaxHeight(),small=true)
                    TutorialAction("摆好了，开始",onPositioned,primary=true,modifier=Modifier.weight(1f).fillMaxHeight(),small=true)
                }
                else->Text(if(waiting)"惊喜正在路上……" else "完成动作后，自动进入下一步",fontSize=19.sp,color=TutorialSea)
            }
        }
    }
}

@Composable
private fun TutorialAction(label:String,onClick:()->Unit,modifier:Modifier,enabled:Boolean=true,primary:Boolean=false,small:Boolean=false){
    val shape=RoundedCornerShape(if(small)12.dp else 18.dp)
    Button(onClick=onClick,enabled=enabled,shape=shape,
        contentPadding=PaddingValues(horizontal=10.dp,vertical=3.dp),
        colors=ButtonDefaults.buttonColors(containerColor=if(primary)TutorialSea else TutorialCream,
            contentColor=if(primary)Color.White else ComicInk),
        modifier=modifier.alpha(if(enabled)1f else .4f).border(1.dp,if(primary)TutorialSea else TutorialLine,shape)){
        Text(label,fontSize=if(small)20.sp else 26.sp,fontWeight=if(primary)FontWeight.Bold else FontWeight.Medium,
            color=if(primary)Color.White else ComicInk)
    }
}

@Composable
private fun TutorialArtwork(step:TutorialStep,complete:Boolean,modifier:Modifier){
    Canvas(modifier.background(Color(0xffedf3e9),RoundedCornerShape(20.dp))){
        val c=Offset(size.width/2f,size.height/2f)
        when(step){
            TutorialStep.POSITION->{
                drawRoundRect(TutorialSea,c+Offset(-34f,-4f),Size(68f,52f),CornerRadius(10f))
                drawCircle(TutorialCream,37f,c+Offset(0f,-22f))
                capsule(c+Offset(-12f,-21f),10f,TutorialCoral)
                capsule(c+Offset(11f,-32f),10f,TutorialBlue)
                capsule(c+Offset(12f,-10f),10f,ComicMint)
                drawRoundRect(ComicMint,c+Offset(-62f,53f),Size(124f,8f),CornerRadius(4f))
                arrow(c+Offset(-75f,5f),c+Offset(-124f,5f),TutorialSea)
                arrow(c+Offset(75f,5f),c+Offset(124f,5f),TutorialSea)
            }
            TutorialStep.COIN->{
                drawCircle(Color(0xffedc76b),32f,c+Offset(-92f,0f))
                drawCircle(Color(0xffc39735),23f,c+Offset(-92f,0f),style=Stroke(2.5f))
                drawLine(TutorialCream,c+Offset(-92f,-12f),c+Offset(-92f,12f),5f,StrokeCap.Round)
                arrow(c+Offset(-39f,0f),c+Offset(27f,0f),TutorialSea)
                drawRoundRect(TutorialSea,c+Offset(54f,-48f),Size(80f,96f),CornerRadius(15f))
                drawRoundRect(TutorialCream,c+Offset(88f,-25f),Size(12f,50f),CornerRadius(6f))
            }
            TutorialStep.TURN->{
                drawCircle(TutorialSea,39f,c)
                drawRoundRect(TutorialCream,c+Offset(-47f,-9f),Size(94f,18f),CornerRadius(9f))
                drawCircle(TutorialCoral,12f,c)
                drawArc(TutorialSea,205f,268f,false,c+Offset(-57f,-57f),Size(114f,114f),style=Stroke(4f,cap=StrokeCap.Round))
                arrow(c+Offset(-23f,53f),c+Offset(-39f,45f),TutorialSea)
            }
            TutorialStep.TAKE->{
                drawRoundRect(TutorialSea,c+Offset(-112f,32f),Size(104f,17f),CornerRadius(7f))
                capsule(c+Offset(-59f,3f),32f,TutorialCoral)
                arrow(c+Offset(1f,18f),c+Offset(70f,-21f),TutorialSea)
                capsule(c+Offset(109f,-27f),25f,TutorialCoral)
            }
            TutorialStep.OPEN->{
                capsule(c,36f,TutorialSea,open=true)
                sparkle(c,12f,TutorialCoral)
                arrow(c+Offset(-61f,0f),c+Offset(-116f,0f),TutorialSea)
                arrow(c+Offset(61f,0f),c+Offset(116f,0f),TutorialSea)
            }
            TutorialStep.STORE->{
                drawRoundRect(TutorialCream,c+Offset(-91f,-51f),Size(182f,102f),CornerRadius(12f))
                drawRoundRect(TutorialSea,c+Offset(-91f,-51f),Size(182f,102f),CornerRadius(12f),style=Stroke(3f))
                drawLine(TutorialLine,c+Offset(0f,-44f),c+Offset(0f,44f),2f)
                repeat(6){i->
                    val p=c+Offset(-61f+(i%3)*61f,if(i<3)-25f else 25f)
                    drawCircle(ComicMint.copy(alpha=.65f),18f,p)
                    if(i==0||complete)capsule(p,13f,listOf(TutorialSea,TutorialCoral,TutorialBlue)[i%3])
                }
                sparkle(c+Offset(125f,-32f),13f,TutorialCoral)
                if(complete)sparkle(c+Offset(-124f,18f),10f,TutorialSea)
            }
        }
    }
}

private fun DrawScope.capsule(center:Offset,radius:Float,color:Color,open:Boolean=false){
    val gap=if(open)radius*.30f else 0f
    drawArc(color,0f,180f,true,center+Offset(-radius,-radius+gap),Size(radius*2,radius*2))
    drawArc(TutorialCream,180f,180f,true,center+Offset(-radius,-radius-gap),Size(radius*2,radius*2))
    drawArc(color.copy(alpha=.45f),180f,180f,false,center+Offset(-radius,-radius-gap),Size(radius*2,radius*2),style=Stroke(2f))
    drawLine(color.copy(alpha=.45f),center+Offset(-radius,-gap),center+Offset(radius,-gap),2f)
    drawArc(Color.White.copy(alpha=.65f),210f,48f,false,center+Offset(-radius*.74f,-radius*.74f-gap),
        Size(radius*1.48f,radius*1.48f),style=Stroke(4f,cap=StrokeCap.Round))
}

private fun DrawScope.sparkle(center:Offset,radius:Float,color:Color){
    val path=Path().apply{
        moveTo(center.x,center.y-radius)
        lineTo(center.x+radius*.28f,center.y-radius*.28f)
        lineTo(center.x+radius,center.y)
        lineTo(center.x+radius*.28f,center.y+radius*.28f)
        lineTo(center.x,center.y+radius)
        lineTo(center.x-radius*.28f,center.y+radius*.28f)
        lineTo(center.x-radius,center.y)
        lineTo(center.x-radius*.28f,center.y-radius*.28f)
        close()
    }
    drawPath(path,color)
}

private fun DrawScope.arrow(from:Offset,to:Offset,color:Color){
    drawLine(color,from,to,4f,StrokeCap.Round)
    val angle=atan2(to.y-from.y,to.x-from.x)
    for(side in listOf(-1f,1f)){
        val a=angle+side*.55f
        drawLine(color,to,to-Offset(cos(a)*13f,sin(a)*13f),4f,StrokeCap.Round)
    }
}
