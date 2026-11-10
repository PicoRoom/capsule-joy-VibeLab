package com.bujiaban.niuqu.pico.domain.usecase

import com.bujiaban.niuqu.pico.data.repository.CapsuleRepository
import com.bujiaban.niuqu.pico.domain.model.*
import kotlin.random.Random

class CapsuleUseCase(private val repository:CapsuleRepository,private val random:()->Float={Random.nextFloat()}) {
    val definitions get()=repository.definitions
    val series get()=repository.series
    fun initial():CapsuleState {
        val toys=repository.read()
        val stored=repository.readStored().intersect(toys.map{it.id}.toSet())
        val displayed=toys.firstOrNull{it.id !in stored&&CollectionDisplay.contains(it)}
        return CapsuleState(toys=toys,storedIds=stored,displaySeries=displayed?.let{definitions[it.toy].series}?:0)
    }
    private fun choose(s:CapsuleState):Int {
        val all=definitions.withIndex().filter{it.value.series==s.series}
        val seen=mutableSetOf<Int>();var repeats=0
        s.toys.filter{definitions[it.toy].series==s.series}.forEach{repeats=if(seen.add(it.toy))0 else repeats+1}
        val missing=all.filter{it.index !in seen}
        val entries=if(repeats>=3&&missing.isNotEmpty())missing else all
        var value=random().coerceIn(0f,.999999f)*entries.sumOf{it.value.weight}
        return entries.first {value-=it.value.weight;value<0}.index
    }
    fun reduce(s:CapsuleState,e:CapsuleEvent):CapsuleState {
        if(e is CapsuleEvent.Loaded)return s.copy(ready=true)
        if(e is CapsuleEvent.Failure)return s.copy(error=e.reason,message=e.reason)
        if(e is CapsuleEvent.Mode)return s.copy(mode=e.value,message=if(e.value==ExperienceMode.STUDY)"透视学习：观察内部机构与零件标签。" else hint(s.phase,s.isClaw))
        if(s.mode!=ExperienceMode.PLAY||!s.ready||s.error!=null)return s
        val next=when(e) {
            is CapsuleEvent.Series -> if(s.phase==CapsulePhase.COIN_READY&&e.index in series.indices)s.copy(series=e.index,clawMissed=false,clawAvailable=s.clawAvailable.sorted().take(s.stock).toSet(),message="已换到${series[e.index].name}，拿起硬币开始吧。") else s
            is CapsuleEvent.CoinDropped -> if(s.phase==CapsulePhase.COIN_READY&&s.stock>0&&e.distance.isFinite()&&e.distance>=0f&&e.distance<COIN_ACCEPT_RADIUS)s.copy(phase=CapsulePhase.CREDITED,clawMissed=false,crank=0f,elapsed=0f,clawTarget=null,message=hint(CapsulePhase.CREDITED,s.isClaw)) else if(s.phase==CapsulePhase.COIN_READY)s.copy(message=if(s.stock==0)"本仓售罄，轻触顶部盖子补充。" else "把硬币靠近投币口，松手投入。") else s
            is CapsuleEvent.CrankMoved -> if(!s.isClaw&&s.phase==CapsulePhase.CREDITED&&e.degrees.isFinite()) {
                val progress=(s.crank+e.degrees.coerceIn(0f,90f)).coerceAtMost(360f)
                if(progress>=359f)s.copy(phase=CapsulePhase.DISPENSING,crank=360f,elapsed=0f,stock=s.stock-1,pending=choose(s),capsuleColor=(s.capsuleColor+1+(random().coerceIn(0f,.999999f)*(CapsuleColor.entries.size-1)).toInt())%CapsuleColor.entries.size,message=hint(CapsulePhase.DISPENSING)) else s.copy(crank=progress,message="继续转动旋钮 · ${progress.toInt()}° / 360°")
            } else s
            is CapsuleEvent.ClawMoved -> if(s.isClaw&&s.phase==CapsulePhase.CREDITED&&e.dx.isFinite()&&e.dz.isFinite())
                s.copy(clawX=(s.clawX+e.dx).coerceIn(-.70f,.70f),clawZ=(s.clawZ+e.dz).coerceIn(-.65f,.65f)) else s
            CapsuleEvent.ClawGrab -> if(s.isClaw&&s.phase==CapsulePhase.CREDITED&&s.stock>0){
                val target=s.clawAimTarget()
                s.copy(phase=CapsulePhase.CLAWING,elapsed=0f,clawTarget=target,pending=target?.let{choose(s)},
                    capsuleColor=target?.rem(CapsuleColor.entries.size)?:s.capsuleColor,
                    message=hint(CapsulePhase.CLAWING,true))
            } else s
            is CapsuleEvent.CapsulePulled -> if(s.phase==CapsulePhase.CAPSULE_READY&&e.distance.isFinite()&&e.distance>.045f)s.copy(phase=CapsulePhase.CAPSULE_TAKEN,message=hint(CapsulePhase.CAPSULE_TAKEN)) else s
            is CapsuleEvent.ShellsSeparated -> if(s.phase==CapsulePhase.CAPSULE_TAKEN&&e.distance.isFinite()&&e.distance>=CAPSULE_PULL_DISTANCE)s.copy(phase=CapsulePhase.OPENING,elapsed=0f,message=hint(CapsulePhase.OPENING)) else s
            is CapsuleEvent.Tick -> {
                val dt=if(e.dt.isFinite())e.dt.coerceAtLeast(0f) else 0f
                when(s.phase) {
                    CapsulePhase.DISPENSING -> if(s.elapsed+dt>=2.1f)s.copy(phase=CapsulePhase.CAPSULE_READY,elapsed=0f,message=hint(CapsulePhase.CAPSULE_READY)) else s.copy(elapsed=s.elapsed+dt)
                    CapsulePhase.CLAWING -> if(s.elapsed+dt>=if(s.clawTarget==null)2.6f else CLAW_DURATION){
                        if(s.clawTarget==null)s.copy(phase=CapsulePhase.COIN_READY,clawMissed=true,elapsed=0f,pending=null,message="差一点！点击「再试一次」，保持落点重新瞄准。")
                        else s.copy(phase=CapsulePhase.CAPSULE_READY,elapsed=0f,clawX=.50f,clawZ=.48f,stock=s.stock-1,clawAvailable=s.clawAvailable-s.clawTarget,message="抓到了！从取物口拿出这颗胶囊。")
                    } else s.copy(elapsed=s.elapsed+dt)
                    CapsulePhase.OPENING -> if(s.elapsed+dt>=1.4f) {
                        val id=(s.toys.maxOfOrNull{it.id}?:0)+1
                        val toy=ToyPlacement(id,requireNotNull(s.pending),e.revealX,e.revealY,e.revealZ)
                        val fresh=s.total(toy.toy)==0
                        val complete=fresh&&definitions.indices.filter{definitions[it].series==s.series}.all{it==toy.toy||s.total(it)>0}
                        val title=when{complete->"${series[s.series].name}集齐了！";fresh->"新发现：${definitions[toy.toy].name}！";else->"${definitions[toy.toy].name} · 第 ${s.total(toy.toy)+1} 只"}
                        s.copy(phase=CapsulePhase.REVEALED,elapsed=0f,activeToy=id,toys=s.toys+toy,message="$title 拿起摆放，或点击「收好，再来一颗」。")
                    } else s.copy(elapsed=s.elapsed+dt)
                    else -> s
                }
            }
            is CapsuleEvent.ToyPicked -> if(s.phase==CapsulePhase.REVEALED&&s.activeToy==e.id)s.copy(phase=CapsulePhase.COIN_READY,activeToy=null,pending=null,clawTarget=null,crank=0f,message="玩具可以自由拿取、旋转和摆放；拖回册子即可收纳。") else s
            is CapsuleEvent.PlaceToy -> {
                val p=e.pose; val valid=listOf(p.x,p.y,p.z,p.qx,p.qy,p.qz,p.qw,p.scale).all{it.isFinite()} && p.scale in TOY_MIN_SCALE..TOY_MAX_SCALE && s.toys.any{it.id==p.id&&it.toy==p.toy}
                if(valid)s.copy(toys=s.toys.map{if(it.id==p.id)p else it}) else s
            }
            is CapsuleEvent.AlbumPage -> if(e.index in series.indices)s.copy(albumSeries=e.index) else s
            CapsuleEvent.RetryClaw -> if(s.isClaw&&s.clawMissed&&s.phase==CapsulePhase.COIN_READY&&s.stock>0)
                s.copy(phase=CapsulePhase.CREDITED,clawMissed=false,elapsed=0f,message=hint(CapsulePhase.CREDITED,true)) else s
            CapsuleEvent.StoreRevealed -> s.activeToy?.let{reduce(s,CapsuleEvent.StoreToy(it))}?:s
            CapsuleEvent.DisplaySeries -> {
                val ids=definitions.indices.filter{definitions[it].series==s.albumSeries}
                val selected=ids.mapNotNull{index->s.toys.firstOrNull{it.toy==index}?.id}.toSet()
                if(selected.isEmpty()||s.phase !in listOf(CapsulePhase.COIN_READY,CapsulePhase.REVEALED))s
                else s.copy(toys=s.toys.map{toy->if(toy.id in selected){val slot=ids.indexOf(toy.toy);val p=CollectionDisplay.position(slot)
                    toy.copy(x=p[0],y=p[1],z=p[2],qx=0f,qy=0f,qz=0f,qw=1f,scale=CollectionDisplay.SCALE)}else toy},
                    displaySeries=s.albumSeries,storedIds=(s.storedIds+s.toys.filter{CollectionDisplay.contains(it)}.map{it.id})-selected,phase=if(s.activeToy in selected)CapsulePhase.COIN_READY else s.phase,activeToy=s.activeToy?.takeUnless{it in selected},pending=if(s.activeToy in selected)null else s.pending,
                    message=if(selected.size==ids.size)"整组伙伴到齐！试着拿起一只，再放回它的小圆台。" else "已摆好 ${selected.size} 位伙伴，空圆台等待下一位。")
            }
            CapsuleEvent.ToggleAlbum -> s.copy(albumOpen=!s.albumOpen)
            is CapsuleEvent.TakeFromAlbum -> {
                val toy=s.toys.firstOrNull{it.toy==e.toy&&it.id in s.storedIds}
                if(!s.albumOpen||e.toy !in definitions.indices)s
                else if(toy==null)s.copy(message="这一款都拿在外面了，放回收纳册后才能再取。")
                else s.copy(storedIds=s.storedIds-toy.id,
                    toys=s.toys.map{if(it.id==toy.id)it.copy(x=.10f,y=.98f,z=.32f) else it},
                    message="取出${definitions[e.toy].name}。册内还剩 ${s.available(e.toy)-1} 个，放回册子可再次取用。")
            }
            is CapsuleEvent.StoreToy -> if(s.toys.any{it.id==e.id}&&e.id !in s.storedIds){
                s.copy(storedIds=s.storedIds+e.id,
                    phase=if(s.activeToy==e.id)CapsulePhase.COIN_READY else s.phase,
                    activeToy=if(s.activeToy==e.id)null else s.activeToy,
                    pending=if(s.activeToy==e.id)null else s.pending,
                    clawTarget=if(s.activeToy==e.id)null else s.clawTarget,
                    message="已经收好啦，要拿出来时轻触对应格子。")
            } else s
            CapsuleEvent.StoreAll -> if(s.phase in listOf(CapsulePhase.COIN_READY,CapsulePhase.REVEALED))
                s.copy(storedIds=s.toys.map{it.id}.toSet(),phase=CapsulePhase.COIN_READY,activeToy=null,pending=null,clawTarget=null,crank=0f,message="玩具都收进册子了。抽中过几个，就能取出几个。") else s
            CapsuleEvent.Refill -> if(s.phase==CapsulePhase.COIN_READY)s.copy(stock=24,clawAvailable=CLAW_SLOTS.indices.toSet(),clawTarget=null,message="新的 24 颗惊喜准备好了。") else s
            else -> s
        }
        if(next.toys!=s.toys||next.storedIds!=s.storedIds)repository.save(next.toys,next.storedIds)
        return next
    }
    private fun hint(phase:CapsulePhase,claw:Boolean=false)=when(phase){
        CapsulePhase.COIN_READY->"从左侧零钱盒拿一枚金币，靠近投币口即可自动投入。"
        CapsulePhase.CREDITED->if(claw)"捏住瞄准板拖动，松手停住，再点击下爪。" else "捏住旋钮，顺着圆周转满一圈。"
        CapsulePhase.DISPENSING->"咕噜……惊喜正在滑道里，稍等一下。"
        CapsulePhase.CLAWING->"夹爪正在下降、合拢，再把抓到的胶囊送到取物口。"
        CapsulePhase.CAPSULE_READY->"从取物口拿出胶囊。"
        CapsulePhase.CAPSULE_TAKEN->"双手分别捏住上下半壳，轻轻拉开。松手会合回去。"
        CapsulePhase.OPENING->"这一颗，会是什么呢？"
        CapsulePhase.REVEALED->"双手捏住玩具，拉开放大、合拢缩小；也可放回收纳册。"
    }
}
