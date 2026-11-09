package com.bujiaban.niuqu.pico.domain.usecase

import com.bujiaban.niuqu.pico.data.repository.CapsuleRepository
import com.bujiaban.niuqu.pico.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class MemoryCapsuleRepository:CapsuleRepository {
    override val definitions=listOf(ToyDefinition("flower","花",0,30),ToyDefinition("rabbit","兔",1,25),ToyDefinition("mushroom","蘑菇",0,18),ToyDefinition("cat","猫",1,14),ToyDefinition("cloud","云",2,9),ToyDefinition("star","星",2,4))
    override val series=listOf(ToySeries("garden","花园来信",""),ToySeries("friends","午后伙伴",""),ToySeries("sky","天空口袋",""))
    var toys:List<ToyPlacement> = emptyList()
    override fun read()=toys
    var storedIds:Set<Long> = emptySet()
    override fun readStored()=storedIds
    override fun save(toys:List<ToyPlacement>,storedIds:Set<Long>){this.toys=toys;this.storedIds=storedIds}
}
fun completeRound(use:CapsuleUseCase,start:CapsuleState):CapsuleState {
    var s=use.reduce(start,CapsuleEvent.CoinDropped(.01f))
    if(s.isClaw)s=use.reduce(s,CapsuleEvent.ClawGrab) else repeat(4){s=use.reduce(s,CapsuleEvent.CrankMoved(90f))}
    repeat(if(s.isClaw)100 else 40){s=use.reduce(s,CapsuleEvent.Tick(.06f))}
    s=use.reduce(s,CapsuleEvent.CapsulePulled(.06f));s=use.reduce(s,CapsuleEvent.ShellsSeparated(.04f))
    repeat(26){s=use.reduce(s,CapsuleEvent.Tick(.06f,.3f,.9f,.2f))}
    return s
}
class CapsuleUseCaseTest {
    @Test fun clawRequiresCoinAimingAndTheGrabButtonInsteadOfTheCrank() {
        val use=CapsuleUseCase(MemoryCapsuleRepository()){0f}
        var s=use.reduce(use.reduce(use.initial(),CapsuleEvent.Loaded),CapsuleEvent.Series(2))
        assertEquals(s,use.reduce(s,CapsuleEvent.ClawGrab))
        assertEquals(s,use.reduce(s,CapsuleEvent.ClawMoved(.1f,.1f)))
        s=use.reduce(s,CapsuleEvent.CoinDropped(.01f))
        assertTrue(s.message.contains("瞄准板"))
        assertEquals(s,use.reduce(s,CapsuleEvent.CrankMoved(360f)))
        s=use.reduce(s,CapsuleEvent.ClawMoved(-.1f,-.08f))
        assertEquals(.4f,s.clawX,.00001f);assertEquals(.4f,s.clawZ,.00001f)
        s=use.reduce(s,CapsuleEvent.ClawGrab)
        assertEquals(CapsulePhase.CLAWING,s.phase);assertEquals(23,s.clawTarget)
        assertEquals(23%CapsuleColor.entries.size,s.capsuleColor)
        assertEquals(2,use.definitions[requireNotNull(s.pending)].series)
        assertEquals(24,s.stock);assertEquals(24,s.clawAvailable.size)
        assertEquals(s,use.reduce(s,CapsuleEvent.ClawGrab))
        assertEquals(s,use.reduce(s,CapsuleEvent.ClawMoved(.2f,.2f)))
        assertEquals(s,use.reduce(s,CapsuleEvent.Series(0)))
        s=use.reduce(s,CapsuleEvent.Tick(CLAW_DURATION))
        assertEquals(CapsulePhase.CAPSULE_READY,s.phase);assertEquals(23,s.stock)
        assertEquals(23%CapsuleColor.entries.size,s.capsuleColor)
        assertEquals(23,s.clawAvailable.size);assertFalse(23 in s.clawAvailable)
        assertEquals(s,use.reduce(s,CapsuleEvent.Tick(CLAW_DURATION)))
        assertEquals(s,use.reduce(s,CapsuleEvent.ClawGrab))
    }
    @Test fun aMissConsumesTheCoinWithoutCreatingAPrizeOrReducingStock() {
        val use=CapsuleUseCase(MemoryCapsuleRepository()){0f}
        var s=use.reduce(use.reduce(use.initial(),CapsuleEvent.Loaded),CapsuleEvent.Series(2))
        s=use.reduce(s,CapsuleEvent.CoinDropped(.01f))
        s=use.reduce(s,CapsuleEvent.ClawMoved(-s.clawX,-s.clawZ))
        val color=s.capsuleColor
        s=use.reduce(s,CapsuleEvent.ClawGrab)
        assertEquals(CapsulePhase.CLAWING,s.phase);assertNull(s.clawTarget);assertNull(s.pending)
        s=use.reduce(s,CapsuleEvent.Tick(CLAW_DURATION))
        assertEquals(CapsulePhase.COIN_READY,s.phase);assertEquals(24,s.stock)
        assertEquals(24,s.clawAvailable.size);assertTrue(s.toys.isEmpty());assertEquals(color,s.capsuleColor)
        assertTrue(s.message.contains("夹空"));assertEquals(.50f,s.clawX);assertEquals(.48f,s.clawZ)
        assertEquals(s,use.reduce(s,CapsuleEvent.ClawGrab))
    }
    @Test fun clawTargetsTheTopLayerAndNeverPicksAnAlreadyRemovedCapsule() {
        val use=CapsuleUseCase(MemoryCapsuleRepository()){0f}
        var s=use.reduce(use.reduce(use.initial(),CapsuleEvent.Loaded),CapsuleEvent.Series(2))
        s=use.reduce(s,CapsuleEvent.CoinDropped(.01f))
        s=use.reduce(s,CapsuleEvent.ClawMoved(.4f-s.clawX,.2f-s.clawZ))
        s=use.reduce(s,CapsuleEvent.ClawGrab)
        assertEquals(3.16f,CLAW_SLOTS[requireNotNull(s.clawTarget)].y)
        s=use.reduce(s,CapsuleEvent.Tick(CLAW_DURATION))
        val removed=requireNotNull(s.clawTarget)
        s=s.copy(phase=CapsulePhase.COIN_READY,pending=null)
        s=use.reduce(s,CapsuleEvent.CoinDropped(.01f))
        val slot=CLAW_SLOTS[removed]
        s=use.reduce(s,CapsuleEvent.ClawMoved(slot.x-s.clawX,slot.z-s.clawZ))
        s=use.reduce(s,CapsuleEvent.ClawGrab)
        assertNotEquals(removed,s.clawTarget)
        assertFalse(removed in s.clawAvailable)
    }
    @Test fun lowerCapsulesMustBeUncoveredBeforeTheyCanBeLifted() {
        val use=CapsuleUseCase(MemoryCapsuleRepository()){0f}
        var s=use.reduce(use.reduce(use.initial(),CapsuleEvent.Loaded),CapsuleEvent.Series(2))
        s=use.reduce(s,CapsuleEvent.CoinDropped(.01f))
        s=use.reduce(s,CapsuleEvent.ClawMoved(.2f-s.clawX,.2f-s.clawZ))
        val blocked=use.reduce(s,CapsuleEvent.ClawGrab)
        assertNull(blocked.clawTarget);assertNull(blocked.pending)
        val uncovered=s.copy(clawAvailable=(0 until 16).toSet(),stock=16)
        val caught=use.reduce(uncovered,CapsuleEvent.ClawGrab)
        assertEquals(10,caught.clawTarget)
        assertEquals(2.82f,CLAW_SLOTS[requireNotNull(caught.clawTarget)].y)
    }
    @Test fun clawRejectsInvalidMovementAndPausesDuringStudy() {
        val use=CapsuleUseCase(MemoryCapsuleRepository()){0f}
        var s=use.reduce(use.reduce(use.initial(),CapsuleEvent.Loaded),CapsuleEvent.Series(2))
        s=use.reduce(s,CapsuleEvent.CoinDropped(.01f))
        assertEquals(s,use.reduce(s,CapsuleEvent.ClawMoved(Float.NaN,0f)))
        assertEquals(s,use.reduce(s,CapsuleEvent.ClawMoved(0f,Float.POSITIVE_INFINITY)))
        s=use.reduce(s,CapsuleEvent.ClawMoved(100f,-100f))
        assertEquals(.70f,s.clawX);assertEquals(-.65f,s.clawZ)
        s=use.reduce(s,CapsuleEvent.ClawMoved(.4f-s.clawX,.4f-s.clawZ))
        s=use.reduce(s,CapsuleEvent.Mode(ExperienceMode.STUDY))
        assertEquals(s,use.reduce(s,CapsuleEvent.ClawGrab))
        assertEquals(s,use.reduce(s,CapsuleEvent.ClawMoved(-1f,1f)))
        s=use.reduce(s,CapsuleEvent.Mode(ExperienceMode.PLAY))
        assertTrue(s.message.contains("瞄准板"))
        s=use.reduce(s,CapsuleEvent.ClawGrab)
        s=use.reduce(s,CapsuleEvent.Mode(ExperienceMode.STUDY))
        assertEquals(s,use.reduce(s,CapsuleEvent.Tick(100f)))
        s=use.reduce(s,CapsuleEvent.Mode(ExperienceMode.PLAY))
        s=use.reduce(s,CapsuleEvent.Tick(CLAW_DURATION))
        assertEquals(CapsulePhase.CAPSULE_READY,s.phase)
        assertEquals(23,s.stock)
    }
    @Test fun clawStockStaysFiniteAndRefillRestoresTheActualSlots() {
        val use=CapsuleUseCase(MemoryCapsuleRepository()){0f}
        var s=use.reduce(use.reduce(use.initial(),CapsuleEvent.Loaded),CapsuleEvent.Series(2))
        repeat(24){
            s=use.reduce(s,CapsuleEvent.CoinDropped(.01f))
            val slot=CLAW_SLOTS[s.clawAvailable.maxBy{CLAW_SLOTS[it].y}]
            s=use.reduce(s,CapsuleEvent.ClawMoved(slot.x-s.clawX,slot.z-s.clawZ))
            s=use.reduce(s,CapsuleEvent.ClawGrab)
            assertNotNull(s.clawTarget)
            s=use.reduce(s,CapsuleEvent.Tick(CLAW_DURATION))
            s=use.reduce(s,CapsuleEvent.CapsulePulled(.06f));s=use.reduce(s,CapsuleEvent.ShellsSeparated(.04f))
            s=use.reduce(s,CapsuleEvent.Tick(1.4f))
            s=use.reduce(s,CapsuleEvent.StoreToy(requireNotNull(s.activeToy)))
        }
        assertEquals(0,s.stock);assertTrue(s.clawAvailable.isEmpty());assertEquals(24,s.toys.size)
        s=use.reduce(s,CapsuleEvent.CoinDropped(.01f));assertEquals(CapsulePhase.COIN_READY,s.phase)
        assertEquals(s,use.reduce(s,CapsuleEvent.ClawGrab))
        s=use.reduce(s,CapsuleEvent.Refill)
        assertEquals(24,s.stock);assertEquals(CLAW_SLOTS.indices.toSet(),s.clawAvailable);assertEquals(24,s.toys.size)
    }
    @Test fun skyCabinetShowsOnlyTheRemainingSharedInventoryAfterChangingSeries() {
        val use=CapsuleUseCase(MemoryCapsuleRepository()){0f}
        var s=completeRound(use,use.reduce(use.initial(),CapsuleEvent.Loaded))
        s=use.reduce(s,CapsuleEvent.ToyPicked(requireNotNull(s.activeToy)))
        s=use.reduce(s,CapsuleEvent.Series(2))
        assertEquals(23,s.stock);assertEquals(23,s.clawAvailable.size)
        val other=s.copy(series=0,phase=CapsulePhase.CREDITED)
        assertEquals(other,use.reduce(other,CapsuleEvent.ClawGrab))
        assertEquals(other,use.reduce(other,CapsuleEvent.ClawMoved(.2f,.2f)))
    }
    @Test fun requiresCoinAndACompletePhysicalTurn() {
        val use=CapsuleUseCase(MemoryCapsuleRepository()){0f};var s=use.reduce(use.initial(),CapsuleEvent.Loaded)
        assertEquals(s,use.reduce(s,CapsuleEvent.CrankMoved(90f)))
        s=use.reduce(s,CapsuleEvent.CoinDropped(.2f));assertEquals(CapsulePhase.COIN_READY,s.phase)
        s=use.reduce(s,CapsuleEvent.CoinDropped(.10f));repeat(3){s=use.reduce(s,CapsuleEvent.CrankMoved(90f))}
        assertEquals(270f,s.crank);assertEquals(24,s.stock);assertNull(s.pending)
        s=use.reduce(s,CapsuleEvent.CrankMoved(90f));assertEquals(CapsulePhase.DISPENSING,s.phase);assertEquals(23,s.stock)
        assertEquals(s,use.reduce(s,CapsuleEvent.CrankMoved(90f)))
    }
    @Test fun extractionAndOpeningAreRequiredBeforeRevelation() {
        val repo=MemoryCapsuleRepository();val use=CapsuleUseCase(repo){0f};var s=use.reduce(use.initial(),CapsuleEvent.Loaded)
        s=use.reduce(s,CapsuleEvent.CoinDropped(.02f));repeat(4){s=use.reduce(s,CapsuleEvent.CrankMoved(90f))};repeat(40){s=use.reduce(s,CapsuleEvent.Tick(.06f))}
        assertEquals(CapsulePhase.CAPSULE_READY,s.phase);assertTrue(s.toys.isEmpty())
        assertEquals(s,use.reduce(s,CapsuleEvent.ShellsSeparated(.1f)))
        s=use.reduce(s,CapsuleEvent.CapsulePulled(.06f));s=use.reduce(s,CapsuleEvent.ShellsSeparated(.04f));assertTrue(repo.toys.isEmpty())
        repeat(26){s=use.reduce(s,CapsuleEvent.Tick(.06f))};assertEquals(CapsulePhase.REVEALED,s.phase);assertEquals(1,repo.toys.size)
    }
    @Test fun seriesIsLockedDuringAPurchaseAndDrawsOnlyItsMembers() {
        for(index in 0..2)for(random in listOf(0f,.99f)) {
            val use=CapsuleUseCase(MemoryCapsuleRepository()){random};var s=use.reduce(use.initial(),CapsuleEvent.Loaded)
            s=use.reduce(s,CapsuleEvent.Series(index));val revealed=completeRound(use,s)
            assertEquals(index,use.definitions[revealed.toys.single().toy].series)
            assertEquals(index,use.reduce(revealed,CapsuleEvent.Series((index+1)%3)).series)
        }
    }
    @Test fun studyModePausesPurchaseAndCannotRevealOrSpend() {
        val use=CapsuleUseCase(MemoryCapsuleRepository());var s=use.reduce(use.initial(),CapsuleEvent.Loaded)
        s=use.reduce(s,CapsuleEvent.CoinDropped(.01f));repeat(4){s=use.reduce(s,CapsuleEvent.CrankMoved(90f))}
        s=use.reduce(s,CapsuleEvent.Mode(ExperienceMode.STUDY));repeat(100){s=use.reduce(s,CapsuleEvent.Tick(.06f))}
        assertEquals(CapsulePhase.DISPENSING,s.phase);assertEquals(0f,s.elapsed);assertEquals(23,s.stock)
        s=use.reduce(s,CapsuleEvent.Mode(ExperienceMode.PLAY));repeat(40){s=use.reduce(s,CapsuleEvent.Tick(.06f))};assertEquals(CapsulePhase.CAPSULE_READY,s.phase)
    }
    @Test fun collectedToysRetainIndividualPosesAndRejectInvalidInput() {
        val repo=MemoryCapsuleRepository();val use=CapsuleUseCase(repo){0f};var s=completeRound(use,use.reduce(use.initial(),CapsuleEvent.Loaded))
        val first=s.toys.single();assertEquals(TOY_DEFAULT_SCALE,first.scale);s=use.reduce(s,CapsuleEvent.ToyPicked(first.id));s=use.reduce(s,CapsuleEvent.PlaceToy(first.copy(x=.4f,y=.725f,z=0f,scale=12f)))
        assertEquals(.4f,repo.toys.single().x);assertEquals(12f,repo.toys.single().scale)
        assertEquals(s,use.reduce(s,CapsuleEvent.PlaceToy(first.copy(scale=TOY_MAX_SCALE+.1f))))
        assertEquals(s,use.reduce(s,CapsuleEvent.PlaceToy(first.copy(x=Float.NaN))))
        s=completeRound(use,s);assertEquals(2,s.toys.size);assertNotEquals(s.toys[0].id,s.toys[1].id)
        assertEquals(.4f,s.toys[0].x)
    }
    @Test fun revealUsesRealElapsedTimeWhenFramesAreSlow() {
        val use=CapsuleUseCase(MemoryCapsuleRepository()){0f}
        var s=use.reduce(use.initial(),CapsuleEvent.Loaded)
        s=use.reduce(s,CapsuleEvent.CoinDropped(.01f))
        repeat(4){s=use.reduce(s,CapsuleEvent.CrankMoved(90f))}
        repeat(5){s=use.reduce(s,CapsuleEvent.Tick(.5f))}
        assertEquals(CapsulePhase.CAPSULE_READY,s.phase)
        s=use.reduce(s,CapsuleEvent.CapsulePulled(.06f))
        s=use.reduce(s,CapsuleEvent.ShellsSeparated(.04f))
        repeat(3){s=use.reduce(s,CapsuleEvent.Tick(.5f))}
        assertEquals(CapsulePhase.REVEALED,s.phase)
        assertEquals(1,s.toys.size)
    }
    @Test fun capsuleColoursChangeEachRoundAndCoverThePalette() {
        val use=CapsuleUseCase(MemoryCapsuleRepository()){0f}
        var s=use.reduce(use.initial(),CapsuleEvent.Loaded)
        val seen=mutableSetOf<Int>()
        repeat(12){
            val before=s.capsuleColor
            s=completeRound(use,s)
            assertNotEquals(before,s.capsuleColor)
            seen+=s.capsuleColor
            s=use.reduce(s,CapsuleEvent.ToyPicked(requireNotNull(s.activeToy)))
        }
        assertEquals(CapsuleColor.entries.indices.toSet(),seen)
    }
    @Test fun albumWithdrawsOnlyOwnedStoredInstancesAndReturnsRestoreAvailability() {
        val repo=MemoryCapsuleRepository();val use=CapsuleUseCase(repo){0f}
        var s=use.reduce(use.initial(),CapsuleEvent.Loaded)
        assertFalse("The initial play view keeps the album closed",s.albumOpen)
        s=use.reduce(s,CapsuleEvent.ToggleAlbum)
        s=use.reduce(s,CapsuleEvent.TakeFromAlbum(0));assertTrue(s.toys.isEmpty())
        repeat(2){s=completeRound(use,s);s=use.reduce(s,CapsuleEvent.ToyPicked(requireNotNull(s.activeToy)))}
        assertEquals(2,s.total(0));assertEquals(0,s.available(0))
        s=use.reduce(s,CapsuleEvent.StoreAll)
        assertEquals(2,s.available(0));val ids=s.toys.map{it.id}.toSet()
        s=use.reduce(s,CapsuleEvent.TakeFromAlbum(0));assertEquals(1,s.available(0))
        s=use.reduce(s,CapsuleEvent.TakeFromAlbum(0));assertEquals(0,s.available(0))
        s=use.reduce(s,CapsuleEvent.TakeFromAlbum(0));assertEquals(0,s.available(0))
        assertEquals(ids,s.toys.map{it.id}.toSet());assertEquals(2,s.toys.size)
        s=use.reduce(s,CapsuleEvent.StoreToy(ids.first()));assertEquals(1,s.available(0))
        s=use.reduce(s,CapsuleEvent.StoreToy(ids.first()));assertEquals(1,s.available(0))
        val restored=use.initial();assertEquals(1,restored.available(0));assertEquals(2,restored.total(0))
        s=use.reduce(s,CapsuleEvent.ToggleAlbum)
        assertEquals(s,use.reduce(s,CapsuleEvent.TakeFromAlbum(0)))
        s=use.reduce(s,CapsuleEvent.Mode(ExperienceMode.STUDY))
        assertEquals(s,use.reduce(s,CapsuleEvent.StoreAll))
    }
    @Test fun storingARevealedToyCompletesTheRoundWithoutMintingAnotherInstance() {
        val use=CapsuleUseCase(MemoryCapsuleRepository()){0f}
        var s=completeRound(use,use.reduce(use.initial(),CapsuleEvent.Loaded))
        val id=requireNotNull(s.activeToy)
        s=use.reduce(s,CapsuleEvent.StoreToy(id))
        assertEquals(CapsulePhase.COIN_READY,s.phase);assertEquals(1,s.available(0));assertNull(s.activeToy)
        assertEquals("A closed album cannot release a stored toy",s,use.reduce(s,CapsuleEvent.TakeFromAlbum(0)))
        s=use.reduce(s,CapsuleEvent.ToggleAlbum)
        s=use.reduce(s,CapsuleEvent.TakeFromAlbum(0))
        assertEquals(0,s.available(0));assertEquals(id,s.toys.single().id);assertEquals(23,s.stock)
    }
    @Test fun soldOutCannotTakeMoreCoinsAndRefillDoesNotEraseToys() {
        val use=CapsuleUseCase(MemoryCapsuleRepository()){0f};var s=use.reduce(use.initial(),CapsuleEvent.Loaded)
        repeat(24){s=completeRound(use,s);s=use.reduce(s,CapsuleEvent.ToyPicked(requireNotNull(s.activeToy)))}
        assertEquals(0,s.stock);assertEquals(24,s.toys.size)
        s=use.reduce(s,CapsuleEvent.CoinDropped(.01f));assertEquals(CapsulePhase.COIN_READY,s.phase)
        s=use.reduce(s,CapsuleEvent.Refill);assertEquals(24,s.stock);assertEquals(24,s.toys.size)
    }
}
