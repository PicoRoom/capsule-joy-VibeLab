package com.bujiaban.niuqu.pico.data.repository

import android.content.Context
import com.bujiaban.niuqu.pico.domain.model.*
import org.json.JSONArray
import org.json.JSONObject

interface CapsuleRepository {
    val definitions:List<ToyDefinition>
    val series:List<ToySeries>
    fun read():List<ToyPlacement>
    fun readStored():Set<Long>
    fun save(toys:List<ToyPlacement>,storedIds:Set<Long>)
}

class LocalCapsuleRepository(context:Context):CapsuleRepository {
    private val prefs=context.getSharedPreferences("paper_capsule_animals",Context.MODE_PRIVATE)
    private val catalog=JSONObject(context.assets.open("art/catalog.json").bufferedReader().use{it.readText()})
    override val series=List(catalog.getJSONArray("series").length()){i->catalog.getJSONArray("series").getJSONObject(i).let{ToySeries(it.getString("id"),it.getString("name"),it.getString("description"))}}
    override val definitions=List(catalog.getJSONArray("prizes").length()){i->
        val prize=catalog.getJSONArray("prizes").getJSONObject(i)
        val owner=(series.indices).first{idx->val members=catalog.getJSONArray("series").getJSONObject(idx).getJSONArray("prizes");(0 until members.length()).any{members.getInt(it)==i}}
        ToyDefinition(prize.getString("id"),prize.getString("name"),owner,prize.getInt("weight"))
    }
    override fun read():List<ToyPlacement> = try {
        val array=JSONArray(prefs.getString("toys","[]")); val ids=mutableSetOf<Long>()
        List(array.length()){array.getJSONObject(it)}.mapNotNull { o ->
            val toy=o.getInt("toy");val id=o.getLong("id")
            val p=ToyPlacement(id,toy,o.getDouble("x").toFloat(),o.getDouble("y").toFloat(),o.getDouble("z").toFloat(),o.optDouble("qx",0.0).toFloat(),o.optDouble("qy",0.0).toFloat(),o.optDouble("qz",0.0).toFloat(),o.optDouble("qw",1.0).toFloat(),o.getDouble("scale").toFloat())
            if(toy in definitions.indices&&id>0&&ids.add(id)&&listOf(p.x,p.y,p.z,p.qx,p.qy,p.qz,p.qw,p.scale).all{it.isFinite()}&&p.scale in TOY_MIN_SCALE..TOY_MAX_SCALE) p else null
        }
    } catch(_:Exception){emptyList()}
    override fun readStored():Set<Long> = prefs.getStringSet("stored_toy_ids",emptySet()).orEmpty().mapNotNull{it.toLongOrNull()?.takeIf{id->id>0}}.toSet()
    override fun save(toys:List<ToyPlacement>,storedIds:Set<Long>) {
        val array=JSONArray();toys.forEach { p->array.put(JSONObject().put("id",p.id).put("toy",p.toy).put("x",p.x).put("y",p.y).put("z",p.z).put("qx",p.qx).put("qy",p.qy).put("qz",p.qz).put("qw",p.qw).put("scale",p.scale)) }
        prefs.edit().putString("toys",array.toString()).putStringSet("stored_toy_ids",storedIds.map{it.toString()}.toSet()).apply()
    }
}
