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
    soundSettings:AudioSettings,onMusic:()->Unit,onEffects:()->Unit,onMenu:(Boolean)->Unit){
    val game=state.game
    val studying=game.mode==ExperienceMode.STUDY
    val freePlay=state.flow.screen==ExperienceScreen.PLAY
    val canAlbum=freePlay||state.flow.step==TutorialStep.STORE
    var expanded by remember{mutableStateOf(false)}
    Column(Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top).background(ComicPaper,PanelShape).border(1.5.dp,ComicInk,PanelShape).padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
      Row(Modifier.fillMaxWidth().height(64.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
        ToolbarAction(if(studying)"返回游玩" else "观察内部",R.drawable.ic_scan_eye,game.ready&&freePlay,studying,onMode,Modifier.weight(1f))
        ToolbarAction("珍藏册",R.drawable.ic_book,game.ready&&!studying&&canAlbum,game.albumOpen,onAlbum,Modifier.weight(1f))
        ToolbarAction(if(expanded)"收起菜单" else "菜单",R.drawable.ic_message_circle_question_mark,true,expanded,{expanded=!expanded;onMenu(expanded)},Modifier.weight(1f))
      }
      if(expanded){
        Row(Modifier.fillMaxWidth().height(60.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
          ToolbarAction("操作提示",R.drawable.ic_message_circle_question_mark,freePlay,false,onHint,Modifier.weight(1f))
          ToolbarAction("重置摆放",R.drawable.ic_locate_fixed,game.ready,false,onRecenter,Modifier.weight(1f))
          ToolbarAction("主菜单",R.drawable.ic_undo_2,freePlay,false,onHome,Modifier.weight(1f))
          ToolbarAction("退出",R.drawable.ic_undo_2,true,false,onExit,Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().height(60.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
          AudioButton("音乐",soundSettings.music,onMusic,Modifier.weight(1f))
          AudioButton("音效",soundSettings.effects,onEffects,Modifier.weight(1f))
        }
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
fun ComicBubble(state:CapsuleUiState,hintVersion:Int,onStore:()->Unit){
    val game=state.game
    var visible by remember{mutableStateOf(true)}
    LaunchedEffect(game.message,game.phase,hintVersion){visible=true;if(game.ready&&game.phase==CapsulePhase.COIN_READY){delay(9000);visible=false}}
    AnimatedVisibility(visible){
      Column(Modifier.fillMaxSize().background(ComicPaper,PanelShape).border(1.dp,ComicInk,PanelShape).padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Text(game.error?:game.message,fontSize=27.sp,lineHeight=33.sp,color=ComicInk)
        when(game.phase){
          CapsulePhase.CREDITED->if(!game.isClaw)Text("顺时针 ↻  ${game.crank.toInt()}° / 360° · 可松手续转",fontSize=24.sp,color=ComicInk)
          CapsulePhase.CAPSULE_TAKEN->GripFeedback(state)
          CapsulePhase.REVEALED->Button(onClick=onStore,modifier=Modifier.fillMaxWidth().height(54.dp),colors=ButtonDefaults.buttonColors(containerColor=ComicMint,contentColor=ComicInk)) {Text("收好，再来一颗",fontSize=26.sp)}
          else->Unit
        }
      }
    }
}

@Composable
fun GripFeedback(state:CapsuleUiState){
    Text(if(state.trackingPaused)"追踪暂时中断，保持双手位置…" else
        "左手${if(state.leftGrip)" ✓" else " 待抓稳"}  ·  右手${if(state.rightGrip)" ✓" else " 待抓稳"}  ·  拉开 ${(state.openingProgress*100).toInt()}%",
        fontSize=20.sp,color=ComicInk)
}

@Composable
fun StudyGuide(state:CapsuleUiState,onDemo:()->Unit,onStep:()->Unit){
    val labels=if(state.game.isClaw)CLAW_PART_LABELS else PART_LABELS
    Column(Modifier.fillMaxSize().background(ComicPaper,PanelShape).border(1.5.dp,ComicInk,PanelShape).padding(24.dp)){
        Text("内部观察",fontSize=29.sp,fontFamily=FontFamily.Serif,fontWeight=FontWeight.Bold,color=ComicInk)
        Spacer(Modifier.height(10.dp))
        Text(if(state.game.isClaw)"从导轨到夹爪，看看奖品如何被送到出口。" else "从投币到出蛋，看看一颗惊喜经过哪些零件。",fontSize=20.sp,lineHeight=28.sp,color=ComicMuted)
        Spacer(Modifier.height(20.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(ComicLine))
        Spacer(Modifier.height(12.dp))
        val stage=(state.studyTime/2f).toInt().coerceIn(0,3)
        val highlighted=listOf(3,if(state.game.isClaw)5 else 5,4,9)[stage]
        Text(listOf("① 投币识别","② 传动与抓取","③ 运送到出口","④ 取出胶囊")[stage],fontSize=22.sp,color=ComicInk)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            Button(onClick=onDemo){Text(if(state.studyRunning)"重新演示" else "演示一次",fontSize=18.sp)}
            Button(onClick=onStep){Text("下一步",fontSize=18.sp)}
        }
        labels.forEachIndexed{index,label->
            Row(Modifier.fillMaxWidth().height(38.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){
                Text((index+1).toString().padStart(2,'0'),fontSize=18.sp,fontFamily=FontFamily.Monospace,color=ComicMuted)
                Text(label.substringAfter(' '),fontSize=22.sp,fontWeight=if(index==highlighted)FontWeight.Bold else FontWeight.Normal,color=if(index==highlighted)Color(0xff257965) else ComicInk)
            }
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth().height(1.dp).background(ComicLine))
        Spacer(Modifier.height(16.dp))
        Text("移动视角，靠近观察\n点击下方「返回游玩」继续",fontSize=18.sp,lineHeight=27.sp,color=ComicMuted)
    }
}

@Composable
fun AlbumCaption(state:CapsuleUiState,slot:Int){
    val index=state.definitions.indices.filter{state.definitions[it].series==state.game.albumSeries}.getOrNull(slot)?:return
    val total=state.game.total(index);val available=state.game.available(index)
    Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally){
        Text(if(total>0)state.definitions[index].name else "${(index+1).toString().padStart(2,'0')}  等待发现",textAlign=TextAlign.Center,color=ComicInk,fontSize=23.sp,fontWeight=FontWeight.Bold)
        if(total>0)Text("可取 $available  /  共 $total",fontSize=20.sp,color=ComicMuted)
    }
}

@Composable
fun CollectionActions(enabled:Boolean,onStore:()->Unit,onDisplay:()->Unit){
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(6.dp)){
        Button(onClick=onStore,enabled=enabled,modifier=Modifier.fillMaxWidth().weight(1f)){Text("全部收好",fontSize=22.sp)}
        Button(onClick=onDisplay,enabled=enabled,modifier=Modifier.fillMaxWidth().weight(1f)){Text("摆上展台",fontSize=22.sp)}
    }
}

@Composable
fun AlbumHeading(state:CapsuleUiState,onPage:(Int)->Unit){
    val page=state.game.albumSeries
    val count=state.definitions.indices.count{state.definitions[it].series==page&&state.game.total(it)>0}
    Column(Modifier.fillMaxSize()){
      Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
        Button(onClick={onPage((page+state.series.size-1)%state.series.size)},contentPadding=PaddingValues(2.dp),modifier=Modifier.width(50.dp)){Text("‹",fontSize=28.sp)}
        Text("${state.series[page].name} $count/6",modifier=Modifier.weight(1f),fontSize=25.sp,color=ComicInk,textAlign=TextAlign.Center)
        Button(onClick={onPage((page+1)%state.series.size)},contentPadding=PaddingValues(2.dp),modifier=Modifier.width(50.dp)){Text("›",fontSize=28.sp)}
      }
      Text(if(count==6)"整组集齐！金色展台已点亮" else "全收藏 ${state.game.toys.map{it.toy}.distinct().size}/${state.definitions.size} · 连续重复3次后必得新款",fontSize=16.sp,color=ComicInk)
    }
}

val PART_LABELS=listOf("01 透明储蛋仓","02 搅拌拨杆","03 单孔计量转盘","04 投币识别器","05 重力出蛋滑道","06 手摇传动轴","07 防逆转棘爪","08 锥齿轮与立轴","09 硬币收纳箱","10 取物翻门")
val CLAW_PART_LABELS=listOf("01 透明奖品柜","02 横向移动导轨","03 前后移动滑台","04 投币识别器","05 奖品输送滑道","06 升降缆绳","07 三指夹爪","08 摇杆与下爪按钮","09 硬币收纳箱","10 取物翻门")

@Composable
fun DisplayCaption(state:CapsuleUiState,slot:Int){
    val index=state.definitions.indices.filter{state.definitions[it].series==state.game.displaySeries}.getOrNull(slot)?:return
    Text(state.definitions[index].name,modifier=Modifier.fillMaxSize().background(ComicPaper,RoundedCornerShape(8.dp)),
        fontSize=23.sp,color=ComicInk,textAlign=TextAlign.Center)
}
