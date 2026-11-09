package com.bujiaban.niuqu.pico.scene

import android.content.Context
import com.pico.spatial.core.math.Vector3
import org.json.JSONObject

class MotionTrack(val name:String,val size:Int,val times:FloatArray,val values:FloatArray) {
    fun sample(time:Float):FloatArray {
        var index=times.binarySearch(time)
        if(index<0)index=(-index-2).coerceAtLeast(0)
        val next=(index+1).coerceAtMost(times.lastIndex)
        val factor=if(index==next)0f else ((time-times[index])/(times[next]-times[index])).coerceIn(0f,1f)
        var sign=1f
        if(size==4&&(0..3).sumOf{(values[index*size+it]*values[next*size+it]).toDouble()}<0)sign=-1f
        return FloatArray(size){axis->values[index*size+axis]*(1-factor)+values[next*size+axis]*factor*sign}
    }
}
class MachineMotion(val clips:Map<String,List<MotionTrack>>,val anchors:Map<String,Vector3>)
class MotionLibrary(context:Context) {
    val machines:Map<String,MachineMotion>
    init {
        val json=JSONObject(context.assets.open("art/motion.json").bufferedReader().use{it.readText()})
        val source=json.getJSONObject("machines")
        machines=source.keys().asSequence().associate { id->
            val machine=source.getJSONObject(id);val clips=machine.getJSONArray("clips")
            val tracks=(0 until clips.length()).associate {i->
                val clip=clips.getJSONObject(i);val entries=clip.getJSONArray("tracks")
                clip.getString("name") to List(entries.length()){j->
                    val t=entries.getJSONObject(j);val times=t.getJSONArray("times");val values=t.getJSONArray("values")
                    MotionTrack(t.getString("name"),t.getInt("size"),FloatArray(times.length()){times.getDouble(it).toFloat()},FloatArray(values.length()){values.getDouble(it).toFloat()})
                }
            }
            val anchors=machine.getJSONObject("anchors")
            id to MachineMotion(tracks,anchors.keys().asSequence().associate {name->
                val point=anchors.getJSONArray(name)
                name to Vector3(point.getDouble(0).toFloat(),point.getDouble(1).toFloat(),point.getDouble(2).toFloat())
            })
        }
    }
}
