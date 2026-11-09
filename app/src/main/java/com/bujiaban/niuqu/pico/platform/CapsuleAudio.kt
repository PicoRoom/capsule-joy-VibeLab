package com.bujiaban.niuqu.pico.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.bujiaban.niuqu.pico.domain.model.CapsuleSound
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AudioSettings(val music:Boolean=true,val effects:Boolean=true)

/** Local, preloaded effects and a single streamed music loop. Called on the UI thread. */
class CapsuleAudio(context:Context):AutoCloseable {
    private val preferences=context.getSharedPreferences("paper-capsule-audio",Context.MODE_PRIVATE)
    private val mutableSettings=MutableStateFlow(AudioSettings(preferences.getBoolean("music",true),preferences.getBoolean("effects",true)))
    val settings=mutableSettings.asStateFlow()
    private val handler=Handler(Looper.getMainLooper())
    private val attributes=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val audioManager=context.getSystemService(AudioManager::class.java)
    private val pool=SoundPool.Builder().setMaxStreams(8).setAudioAttributes(attributes).build()
    private val samples=mutableMapOf<String,Int>()
    private val loaded=mutableSetOf<Int>()
    private val streams=ArrayDeque<Int>()
    private var player:MediaPlayer?=null
    private var prepared=false
    private var resumed=false
    private var closed=false
    private var focused=false
    private var focusRequested=false
    private var persistentLoop:String?=null
    private var gestureLoop:String?=null
    private var playingLoop:String?=null
    private var loopStream=0
    private val expireGesture=Runnable{gestureLoop=null;syncLoop()}
    private val focusRequest=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes)
        .setAcceptsDelayedFocusGain(true)
        .setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener({change->
            if(!closed){
                focused=change==AudioManager.AUDIOFOCUS_GAIN
                if(change==AudioManager.AUDIOFOCUS_LOSS)focusRequested=false
                if(focused)syncPlayback() else silence()
            }
        },handler).build()

    init {
        pool.setOnLoadCompleteListener{_,sample,status->
            if(!closed&&status==0){loaded+=sample;syncLoop()}
            else if(status!=0)Log.w(TAG,"Effect failed to load: $sample ($status)")
        }
        for(name in listOf("click","coin","crank","claw","drop","open","reveal","collect","refill","miss")){
            try{context.assets.openFd("audio/$name.wav").use{samples[name]=pool.load(it,1)}}
            catch(error:Exception){Log.w(TAG,"Cannot load $name",error)}
        }
        val music=MediaPlayer()
        player=music
        try{
            music.setAudioAttributes(attributes)
            context.assets.openFd("audio/bgm.ogg").use{music.setDataSource(it.fileDescriptor,it.startOffset,it.length)}
            music.isLooping=true
            music.setVolume(.32f,.32f)
            music.setOnPreparedListener{if(!closed){prepared=true;syncPlayback()}}
            music.setOnErrorListener{failed,what,extra->
                Log.w(TAG,"Music playback failed: $what/$extra")
                prepared=false;player=null;failed.release();true
            }
            music.prepareAsync()
        }catch(error:Exception){
            Log.w(TAG,"Cannot load background music",error)
            player=null;music.release()
        }
    }

    fun setMusic(enabled:Boolean){
        if(closed)return
        mutableSettings.value=mutableSettings.value.copy(music=enabled)
        preferences.edit().putBoolean("music",enabled).apply()
        refreshFocus();syncPlayback()
    }
    fun setEffects(enabled:Boolean){
        if(closed)return
        mutableSettings.value=mutableSettings.value.copy(effects=enabled)
        preferences.edit().putBoolean("effects",enabled).apply()
        if(!enabled)stopEffects()
        refreshFocus();syncPlayback()
    }
    fun resume(){if(!closed){resumed=true;refreshFocus();syncPlayback()}}
    fun pause(){
        resumed=false
        silence()
        abandonFocus()
    }
    private fun refreshFocus(){
        if(!resumed||(!settings.value.music&&!settings.value.effects)){abandonFocus();return}
        if(!focusRequested){
            val result=audioManager.requestAudioFocus(focusRequest)
            focusRequested=result!=AudioManager.AUDIOFOCUS_REQUEST_FAILED
            focused=result==AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }
    private fun abandonFocus(){
        if(focusRequested)audioManager.abandonAudioFocusRequest(focusRequest)
        focusRequested=false;focused=false
    }
    private val audible get()=resumed&&focused&&!closed

    fun play(cue:CapsuleSound){
        if(closed)return
        when(cue){
            CapsuleSound.CLAW_LOOP->{persistentLoop="claw";gestureLoop=null;syncLoop()}
            CapsuleSound.STOP_MECHANISM->{persistentLoop=null;gestureLoop=null;syncLoop()}
            CapsuleSound.CRANK_MOVE->nudge("crank")
            CapsuleSound.CLAW_MOVE->nudge("claw")
            else->{
                if(!audible||!settings.value.effects)return
                val sample=samples[cue.name.lowercase()]?:return
                if(sample !in loaded)return // Never replay stale interactions after asynchronous loading.
                val stream=pool.play(sample,.70f,.70f,1,0,1f)
                if(stream!=0){
                    streams.addLast(stream)
                    if(streams.size>24)pool.stop(streams.removeFirst())
                }
            }
        }
    }
    private fun nudge(name:String){
        if(!audible||!settings.value.effects)return
        gestureLoop=name
        handler.removeCallbacks(expireGesture)
        handler.postDelayed(expireGesture,160)
        syncLoop()
    }
    private fun syncLoop(){
        val desired=if(audible&&settings.value.effects)persistentLoop?:gestureLoop else null
        if(desired==playingLoop)return
        if(loopStream!=0)pool.stop(loopStream)
        loopStream=0;playingLoop=null
        val sample=samples[desired]?:return
        if(sample !in loaded)return
        loopStream=pool.play(sample,.48f,.48f,2,-1,1f)
        if(loopStream!=0)playingLoop=desired
    }
    private fun syncPlayback(){
        if(prepared)player?.let{music->
            if(audible&&settings.value.music){if(!music.isPlaying)music.start()}
            else if(music.isPlaying)music.pause()
        }
        syncLoop()
    }
    private fun stopEffects(){
        handler.removeCallbacks(expireGesture)
        gestureLoop=null
        streams.forEach(pool::stop);streams.clear()
        if(loopStream!=0)pool.stop(loopStream)
        loopStream=0;playingLoop=null
    }
    private fun silence(){
        if(prepared)player?.let{if(it.isPlaying)it.pause()}
        stopEffects()
    }
    override fun close(){
        if(closed)return
        pause();closed=true
        handler.removeCallbacksAndMessages(null)
        pool.release();player?.release();player=null;prepared=false
    }
    companion object {private const val TAG="CapsuleAudio"}
}
