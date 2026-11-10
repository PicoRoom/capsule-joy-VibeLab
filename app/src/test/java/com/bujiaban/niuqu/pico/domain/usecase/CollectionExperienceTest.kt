package com.bujiaban.niuqu.pico.domain.usecase
import com.bujiaban.niuqu.pico.domain.model.*
import org.junit.Assert.*
import org.junit.Test
class CollectionExperienceTest {
    @Test fun protectionSurvivesReloadAndGuaranteesAMissingToyAfterThreeDuplicates(){
        val repo=MemoryCapsuleRepository();var use=CapsuleUseCase(repo){0f};var s=use.reduce(use.initial(),CapsuleEvent.Loaded)
        repeat(4){s=completeRound(use,s);s=use.reduce(s,CapsuleEvent.StoreRevealed)}
        assertEquals(1,s.toys.map{it.toy}.distinct().size)
        use=CapsuleUseCase(repo){0f};s=use.reduce(use.initial(),CapsuleEvent.Loaded)
        s=completeRound(use,s)
        assertEquals(2,s.toys.map{it.toy}.distinct().size)
        assertTrue(s.message.contains("集齐"))
    }
    @Test fun albumPageIsIndependentEvenDuringPaidRound(){
        val use=CapsuleUseCase(MemoryCapsuleRepository());var s=use.reduce(use.initial(),CapsuleEvent.Loaded)
        s=use.reduce(s,CapsuleEvent.CoinDropped(0f));s=use.reduce(s,CapsuleEvent.AlbumPage(2))
        assertEquals(0,s.series);assertEquals(2,s.albumSeries);assertEquals(CapsulePhase.CREDITED,s.phase)
        assertEquals(s,use.reduce(s,CapsuleEvent.AlbumPage(9)))
    }
    @Test fun displayUsesOwnedInstancesAndPersistsTheirPoses(){
        val repo=MemoryCapsuleRepository();val use=CapsuleUseCase(repo){0f};var s=use.reduce(use.initial(),CapsuleEvent.Loaded)
        s=completeRound(use,s);s=use.reduce(s,CapsuleEvent.StoreRevealed)
        val ids=s.toys.map{it.id};s=use.reduce(s,CapsuleEvent.DisplaySeries)
        assertEquals(ids,s.toys.map{it.id});assertEquals(0,s.storedIds.size)
        assertEquals(CollectionDisplay.position(0)[0],s.toys.single().x,0f)
        assertEquals(s.toys,use.initial().toys)
    }
    @Test fun missAllowsQuickRetryWithoutLosingAimOrInventory(){
        val use=CapsuleUseCase(MemoryCapsuleRepository());var s=use.reduce(use.initial(),CapsuleEvent.Loaded)
        s=use.reduce(s,CapsuleEvent.Series(2));s=use.reduce(s,CapsuleEvent.CoinDropped(0f))
        s=use.reduce(s,CapsuleEvent.ClawMoved(-s.clawX,-s.clawZ));s=use.reduce(s,CapsuleEvent.ClawGrab)
        s=use.reduce(s,CapsuleEvent.Tick(2.7f));assertTrue(s.clawMissed)
        s=use.reduce(s,CapsuleEvent.RetryClaw);assertEquals(CapsulePhase.CREDITED,s.phase);assertEquals(24,s.stock);assertEquals(0f,s.clawX,0f)
    }
}
