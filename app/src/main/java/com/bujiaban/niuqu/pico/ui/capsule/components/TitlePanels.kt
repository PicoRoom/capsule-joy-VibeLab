package com.bujiaban.niuqu.pico.ui.capsule.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bujiaban.niuqu.pico.domain.model.CapsulePhase
import com.bujiaban.niuqu.pico.platform.AudioSettings
import com.bujiaban.niuqu.pico.ui.capsule.CapsuleUiState
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.Text

private val TitleCream=Color(0xfffffaef)
private val TitleInk=Color(0xff264841)
private val TitleSea=Color(0xff367e78)

/** Transparent spatial lettering, separate from both the capsule and its controls. */
@Composable
fun TitleLogo(){
    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){
        Text("扭 趣",fontSize=112.sp,fontWeight=FontWeight.Bold,color=TitleCream,
            style=TextStyle(shadow=Shadow(TitleInk,Offset(0f,4f),6f)))
    }
}

@Composable
fun TitleActions(state:CapsuleUiState,onStart:()->Unit,onOptions:()->Unit,onExit:()->Unit){
    val game=state.game
    val label=when{
        game.error!=null->"准备失败"
        !game.ready->"准备中…"
        game.phase!=CapsulePhase.COIN_READY->"继续"
        else->"开始"
    }
    Column(Modifier.fillMaxSize().padding(top=12.dp),horizontalAlignment=Alignment.CenterHorizontally){
        TitleAction(label,onStart,Modifier.size(280.dp,88.dp),game.ready&&game.error==null,primary=true)
        Spacer(Modifier.height(26.dp))
        Row(Modifier.fillMaxWidth().height(64.dp),horizontalArrangement=Arrangement.spacedBy(24.dp)){
            TitleAction("设置",onOptions,Modifier.weight(1f).fillMaxHeight())
            TitleAction("退出",onExit,Modifier.weight(1f).fillMaxHeight())
        }
        if(game.error!=null)Text("请退出后重新进入",fontSize=18.sp,color=TitleCream)
    }
}

@Composable
fun TitleOptions(state:CapsuleUiState,sound:AudioSettings,onMusic:()->Unit,onEffects:()->Unit,
    onTutorial:()->Unit,onBack:()->Unit){
    val canLearn=state.game.ready&&state.game.error==null&&state.game.phase==CapsulePhase.COIN_READY
    Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally){
        Text("设置",Modifier.height(65.dp),fontSize=32.sp,color=TitleCream,fontWeight=FontWeight.Bold,
            style=TextStyle(shadow=Shadow(TitleInk,Offset(0f,2f),4f)))
        TitleAction("音乐　${if(sound.music)"开" else "关"}",onMusic,Modifier.fillMaxWidth().height(80.dp))
        Spacer(Modifier.height(12.dp))
        TitleAction("音效　${if(sound.effects)"开" else "关"}",onEffects,Modifier.fillMaxWidth().height(80.dp))
        Spacer(Modifier.height(12.dp))
        TitleAction("重看教程",onTutorial,Modifier.fillMaxWidth().height(80.dp),canLearn)
        Spacer(Modifier.height(12.dp))
        TitleAction("返回",onBack,Modifier.fillMaxWidth().height(80.dp),primary=true)
        if(state.game.phase!=CapsulePhase.COIN_READY){
            Spacer(Modifier.height(16.dp))
            Text("完成当前这一局后，可重看教程",fontSize=22.sp,color=TitleCream,textAlign=TextAlign.Center)
        }
    }
}

@Composable
private fun TitleAction(label:String,onClick:()->Unit,modifier:Modifier,enabled:Boolean=true,primary:Boolean=false){
    Button(onClick=onClick,enabled=enabled,modifier=modifier.alpha(if(enabled)1f else .45f),
        shape=RoundedCornerShape(44.dp),contentPadding=PaddingValues(0.dp),
        colors=ButtonDefaults.buttonColors(containerColor=if(primary)TitleSea else TitleCream,
            contentColor=if(primary)Color.White else TitleInk)){
        Text(label,fontSize=if(primary)34.sp else 28.sp,fontWeight=FontWeight.Medium,
            color=if(primary)Color.White else TitleInk)
    }
}
