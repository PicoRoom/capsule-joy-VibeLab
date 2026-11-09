package com.bujiaban.niuqu.pico.ui.capsule.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bujiaban.niuqu.pico.R
import com.bujiaban.niuqu.pico.domain.model.*
import com.bujiaban.niuqu.pico.scene.CapsuleLayout
import com.bujiaban.niuqu.pico.platform.AudioSettings
import com.bujiaban.niuqu.pico.ui.capsule.*
import com.pico.spatial.ui.design.*
import kotlinx.coroutines.delay

val ComicInk=Color(0xff30392f) // design-style: fixed-figma-color user-requested illustration ink; docs/DESIGN.md
val ComicPaper=Color(0xfff5eed9) // design-style: fixed-figma-color user-requested paper palette; docs/DESIGN.md
val ComicMint=Color(0xffc4dab5) // design-style: fixed-figma-color user-requested illustration selection paint; docs/DESIGN.md
private val ComicMuted=Color(0xff737964)
private val ComicLine=Color(0xffd6d2bd)
private val PanelShape=RoundedCornerShape(22.dp)

/** One catalogue card; its open centre is occupied by the native 3D miniatures. */
@Composable
fun SeriesCard(state:CapsuleUiState,onPrevious:()->Unit,onNext:()->Unit){
    val game=state.game
    val canSwitch=game.ready&&game.error==null&&game.mode==ExperienceMode.PLAY&&game.phase==CapsulePhase.COIN_READY
    val tint=listOf(Color(0xffdce6c9),Color(0xffecd6c7),Color(0xffd7e2ec))[game.series]
    val kind=listOf("泡泡糖机","经典扭蛋机","抓娃娃机")[game.series]
    Column(Modifier.fillMaxSize().background(ComicPaper,PanelShape).border(2.dp,ComicInk,PanelShape).padding(22.dp)){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
            Text("系列 ${(game.series+1).toString().padStart(2,'0')} / ${state.series.size.toString().padStart(2,'0')}",fontSize=18.sp,fontFamily=FontFamily.Monospace,color=ComicMuted)
            Text(kind,modifier=Modifier.background(tint,RoundedCornerShape(8.dp)).padding(horizontal=10.dp,vertical=5.dp),fontSize=17.sp,color=ComicInk)
        }
        // The scene aligns SeriesPreview to the clear area at y=72..228 px.
        Spacer(Modifier.height(174.dp))
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
            CatalogueArrow(true,canSwitch,onPrevious)
            Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text(state.series[game.series].name,fontSize=29.sp,fontWeight=FontWeight.Bold,fontFamily=FontFamily.Serif,color=ComicInk)
                Row(horizontalArrangement=Arrangement.spacedBy(7.dp),verticalAlignment=Alignment.CenterVertically){
                    repeat(state.series.size){index->Box(Modifier.size(if(index==game.series)8.dp else 6.dp).background(if(index==game.series)ComicInk else ComicLine,CircleShape))}
                }
            }
            CatalogueArrow(false,canSwitch,onNext)
        }
        Spacer(Modifier.weight(1f))
        Text(if(game.phase!=CapsulePhase.COIN_READY)"本轮进行中 · 完成后可换仓" else "左右换仓 · 每仓六款动物伙伴",modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Center,fontSize=17.sp,color=ComicMuted)
    }
}

@Composable
private fun CatalogueArrow(previous:Boolean,enabled:Boolean,onClick:()->Unit){
    Button(onClick=onClick,enabled=enabled,shape=CircleShape,contentPadding=PaddingValues(12.dp),
        colors=ButtonDefaults.buttonColors(containerColor=Color(0xfffffaed),contentColor=ComicInk),
        modifier=Modifier.size(52.dp).alpha(if(enabled)1f else .4f).border(1.5.dp,ComicLine,CircleShape)){
        Icon(painterResource(if(previous)R.drawable.ic_chevron_left else R.drawable.ic_chevron_right),contentDescription=if(previous)"上一系列" else "下一系列",tint=ComicInk,modifier=Modifier.size(26.dp))
    }
}

/** Global navigation with independent music and effect controls below it. */
@Composable
fun SceneToolbar(state:CapsuleUiState,onMode:()->Unit,onAlbum:()->Unit,onHint:()->Unit,onRecenter:()->Unit,onExit:()->Unit,onHome:()->Unit,
    soundSettings:AudioSettings,onMusic:()->Unit,onEffects:()->Unit){
    val game=state.game
    val studying=game.mode==ExperienceMode.STUDY
    val freePlay=state.flow.screen==ExperienceScreen.PLAY
    val canAlbum=freePlay||state.flow.step==TutorialStep.STORE
    Column(Modifier.fillMaxSize().background(ComicPaper,PanelShape).border(1.5.dp,ComicInk,PanelShape).padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
      Row(Modifier.fillMaxWidth().height(72.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
        ToolbarAction(if(studying)"返回游玩" else "观察内部",if(studying)R.drawable.ic_undo_2 else R.drawable.ic_scan_eye,game.ready&&freePlay,studying,onMode,Modifier.weight(1f))
        ToolbarAction("珍藏册",if(game.albumOpen)R.drawable.ic_book_open else R.drawable.ic_book,game.ready&&!studying&&canAlbum,game.albumOpen&&!studying,onAlbum,Modifier.weight(1f))
        ToolbarAction(if(freePlay)"操作提示" else "新手引导中",R.drawable.ic_message_circle_question_mark,freePlay,!freePlay,onHint,Modifier.weight(1f))
      }
      Row(Modifier.fillMaxWidth().height(60.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
        ToolbarAction("重置摆放",R.drawable.ic_locate_fixed,game.ready,false,onRecenter,Modifier.weight(1f))
        ToolbarAction("主菜单",R.drawable.ic_undo_2,freePlay,false,onHome,Modifier.weight(1f))
        ToolbarAction("退出",R.drawable.ic_undo_2,true,false,onExit,Modifier.weight(1f))
      }
      Row(Modifier.fillMaxWidth().weight(1f),horizontalArrangement=Arrangement.spacedBy(8.dp)){
        AudioButton("背景音乐",soundSettings.music,onMusic,Modifier.weight(1f))
        AudioButton("音效",soundSettings.effects,onEffects,Modifier.weight(1f))
      }
    }
}

@Composable
private fun AudioButton(label:String,enabled:Boolean,onClick:()->Unit,modifier:Modifier){
    val shape=RoundedCornerShape(10.dp)
    Button(onClick=onClick,shape=shape,contentPadding=PaddingValues(horizontal=12.dp,vertical=5.dp),
        colors=ButtonDefaults.buttonColors(containerColor=if(enabled)ComicMint else ComicPaper,contentColor=ComicInk),
        modifier=modifier.fillMaxHeight().border(1.dp,ComicLine,shape)){
        Text("$label · ${if(enabled)"开" else "关"}",fontSize=21.sp,color=ComicInk)
    }
}

@Composable
private fun ToolbarAction(label:String,icon:Int,enabled:Boolean,selected:Boolean,onClick:()->Unit,modifier:Modifier){
    val shape=RoundedCornerShape(12.dp)
    Button(onClick=onClick,enabled=enabled,shape=shape,contentPadding=PaddingValues(horizontal=10.dp,vertical=12.dp),
        colors=ButtonDefaults.buttonColors(containerColor=if(selected)ComicMint else ComicPaper,contentColor=ComicInk),
        modifier=modifier.fillMaxHeight().alpha(if(enabled)1f else .4f).border(if(selected)1.5.dp else 1.dp,if(selected)ComicInk else ComicLine,shape)){
        Row(horizontalArrangement=Arrangement.spacedBy(9.dp),verticalAlignment=Alignment.CenterVertically){
            Icon(painterResource(icon),contentDescription=null,tint=ComicInk,modifier=Modifier.size(26.dp))
            Text(label,fontSize=22.sp,fontWeight=if(selected)FontWeight.Bold else FontWeight.Medium,color=ComicInk)
        }
    }
}

@Composable
fun MachineStatus(state:CapsuleUiState){
    val game=state.game
    val stage=when{
        game.error!=null->"准备遇到问题"
        !game.ready->"正在准备"
        game.mode==ExperienceMode.STUDY->"内部观察"
        game.phase==CapsulePhase.COIN_READY->if(game.stock>0)"等待投币" else "等待补货"
        game.phase==CapsulePhase.CREDITED->if(game.isClaw)"瞄准奖品" else "转动旋钮"
        game.phase in listOf(CapsulePhase.DISPENSING,CapsulePhase.CLAWING)->"好运在路上"
        game.phase==CapsulePhase.CAPSULE_READY->"取出扭蛋"
        game.phase==CapsulePhase.CAPSULE_TAKEN->"拉开上壳"
        game.phase==CapsulePhase.OPENING->"即将揭晓"
        else->"收下好运"
    }
    Row(Modifier.fillMaxSize().background(ComicPaper,RoundedCornerShape(14.dp)).border(1.dp,ComicLine,RoundedCornerShape(14.dp)).padding(horizontal=22.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically){
            Box(Modifier.size(8.dp).background(if(game.error!=null)Color(0xffb45443) else ComicMuted,CircleShape))
            Text(stage,fontSize=22.sp,fontWeight=FontWeight.Medium,color=ComicInk)
        }
        Text("余 ${game.stock} 颗",fontSize=20.sp,color=ComicMuted)
        Text("已发现 ${game.toys.map{it.toy}.distinct().size} / ${state.definitions.size} 款",fontSize=20.sp,color=ComicMuted)
    }
}

@Composable
fun ComicBubble(state:CapsuleUiState,hintVersion:Int){
    val game=state.game
    var visible by remember{mutableStateOf(true)}
    LaunchedEffect(game.message,game.phase,game.mode,hintVersion){
        visible=true
        if(game.ready&&game.error==null&&game.phase==CapsulePhase.COIN_READY){delay(9000);visible=false}
    }
    AnimatedVisibility(visible,enter=fadeIn(tween(180)),exit=fadeOut(tween(220))){
        Box(Modifier.fillMaxSize()){
            Canvas(Modifier.fillMaxSize()){
                val w=size.width;val h=size.height
                val tipX=CapsuleLayout.BUBBLE_TIP_X;val tipY=CapsuleLayout.BUBBLE_TIP_Y
                val body=androidx.compose.ui.geometry.Size(w-6f,h*.73f)
                val tail=Path().apply{moveTo(w*(tipX-.06f),h*.72f);lineTo(w*tipX,h*tipY);lineTo(w*(tipX+.06f),h*.72f);close()}
                drawRoundRect(ComicPaper,size=body,cornerRadius=androidx.compose.ui.geometry.CornerRadius(24f))
                drawRoundRect(ComicInk,size=body,cornerRadius=androidx.compose.ui.geometry.CornerRadius(24f),style=Stroke(2.5f))
                drawPath(tail,ComicPaper)
                drawLine(ComicInk,androidx.compose.ui.geometry.Offset(w*(tipX-.06f),h*.73f),androidx.compose.ui.geometry.Offset(w*tipX,h*tipY),2.5f)
                drawLine(ComicInk,androidx.compose.ui.geometry.Offset(w*tipX,h*tipY),androidx.compose.ui.geometry.Offset(w*(tipX+.06f),h*.73f),2.5f)
            }
            Column(Modifier.fillMaxSize().padding(start=30.dp,end=30.dp,top=21.dp,bottom=74.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
                Text(if(game.error!=null)"请稍候" else if(game.mode==ExperienceMode.STUDY)"沿着目录，认识机器" else if(game.isClaw)"抓一份云上的好运" else "转出今天的小惊喜",color=ComicMuted,fontSize=21.sp,fontFamily=FontFamily.Serif,fontWeight=FontWeight.Bold)
                Text(game.error?:if(!game.ready)"正在准备机器与小玩具……" else if(game.mode==ExperienceMode.STUDY)"玩法已暂停，观察结束后可以继续这一轮。" else game.message,color=ComicInk,fontSize=29.sp,lineHeight=38.sp)
            }
        }
    }
}

@Composable
fun StudyGuide(state:CapsuleUiState){
    val labels=if(state.game.isClaw)CLAW_PART_LABELS else PART_LABELS
    Column(Modifier.fillMaxSize().background(ComicPaper,PanelShape).border(1.5.dp,ComicInk,PanelShape).padding(24.dp)){
        Text("内部观察",fontSize=29.sp,fontFamily=FontFamily.Serif,fontWeight=FontWeight.Bold,color=ComicInk)
        Spacer(Modifier.height(10.dp))
        Text(if(state.game.isClaw)"从导轨到夹爪，看看奖品如何被送到出口。" else "从投币到出蛋，看看一颗惊喜经过哪些零件。",fontSize=20.sp,lineHeight=28.sp,color=ComicMuted)
        Spacer(Modifier.height(20.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(ComicLine))
        Spacer(Modifier.height(12.dp))
        labels.forEachIndexed{index,label->
            Row(Modifier.fillMaxWidth().height(47.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){
                Text((index+1).toString().padStart(2,'0'),fontSize=18.sp,fontFamily=FontFamily.Monospace,color=ComicMuted)
                Text(label.substringAfter(' '),fontSize=22.sp,color=ComicInk)
            }
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth().height(1.dp).background(ComicLine))
        Spacer(Modifier.height(16.dp))
        Text("移动视角，靠近观察\n点击下方「返回游玩」继续",fontSize=18.sp,lineHeight=27.sp,color=ComicMuted)
    }
}

@Composable
fun StoreAllButton(enabled:Boolean,onClick:()->Unit){
    Button(onClick=onClick,enabled=enabled,shape=RoundedCornerShape(10.dp),contentPadding=PaddingValues(5.dp),
        colors=ButtonDefaults.buttonColors(containerColor=ComicMint,contentColor=ComicInk),modifier=Modifier.fillMaxSize().padding(3.dp)){
        Text("全部收好",fontSize=24.sp,fontWeight=FontWeight.Medium,color=ComicInk)
    }
}

@Composable
fun AlbumCaption(state:CapsuleUiState,slot:Int){
    val index=state.definitions.indices.filter{state.definitions[it].series==state.game.series}.getOrNull(slot)?:return
    val total=state.game.total(index);val available=state.game.available(index)
    Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally){
        Text(if(total>0)state.definitions[index].name else "${(index+1).toString().padStart(2,'0')}  等待发现",textAlign=TextAlign.Center,color=ComicInk,fontSize=23.sp,fontWeight=FontWeight.Bold)
        if(total>0)Text("可取 $available  /  共 $total",fontSize=20.sp,color=ComicMuted)
    }
}

@Composable
fun AlbumHeading(state:CapsuleUiState){
    Row(Modifier.fillMaxSize(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)){
        Text("${state.series[state.game.series].name}",fontFamily=FontFamily.Serif,fontSize=31.sp,fontWeight=FontWeight.Bold,color=ComicInk)
        Text("${state.game.toys.map{it.toy}.distinct().size} / ${state.definitions.size}",fontSize=22.sp,fontFamily=FontFamily.Monospace,color=ComicMuted)
    }
}

val PART_LABELS=listOf("01 透明储蛋仓","02 搅拌拨杆","03 单孔计量转盘","04 投币识别器","05 重力出蛋滑道","06 手摇传动轴","07 防逆转棘爪","08 锥齿轮与立轴","09 硬币收纳箱","10 取物翻门")
val CLAW_PART_LABELS=listOf("01 透明奖品柜","02 横向移动导轨","03 前后移动滑台","04 投币识别器","05 奖品输送滑道","06 升降缆绳","07 三指夹爪","08 摇杆与下爪按钮","09 硬币收纳箱","10 取物翻门")
