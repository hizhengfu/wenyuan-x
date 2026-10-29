package io.hizhengfu.cloud.collection.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class CollectionCategoryReasonBalancesTest {
    private fun item(id:String, category:String="ceramics", quantity:Long=1, unit:String="件", source:EnumSource?=EnumSource.DONATE, reason:EnumLedgerChangeReason?=null) =
        LedgerStatItemVo(id,id,id,EnumCollectionNature.RELIC,EnumRelicLevel.THIRD,category,source,quantity,unit,categoryName=category,changeReason=reason)
    private fun event(kind:EnumLedgerStatEvent,item:LedgerStatItemVo) = AnnualLedgerEventVo(item.collectionId,kind,LocalDateTime.of(2026,9,1,0,0),item,"source",LocalDateTime.of(2026,9,1,0,0))

    @Test fun `classified reasons and cross category unit corrections reconcile independently`() {
        val old=item("old",quantity=5)
        val after=old.copy(categoryId="books",categoryName="books",quantity=2,unit="册")
        val donated=item("donation",quantity=3)
        val exchanged=item("exchange",source=EnumSource.OLD,reason=EnumLedgerChangeReason.EXCHANGE)
        val damaged=item("damage",quantity=2,reason=EnumLedgerChangeReason.DAMAGE)
        val opening=listOf(old,damaged)
        val closing=listOf(after,donated,exchanged)
        val rows=CollectionCategoryReasonBalances.build(opening,closing,listOf(event(EnumLedgerStatEvent.REGISTER,donated),event(EnumLedgerStatEvent.TRANSFER_IN,exchanged),event(EnumLedgerStatEvent.DEREGISTER,damaged),event(EnumLedgerStatEvent.CHANGE,after)),listOf(old to after))
        val ceramics=rows.single {it.categoryId=="ceramics"}
        assertEquals(3L,ceramics.donated);assertEquals(1L,ceramics.exchanged);assertEquals(2L,ceramics.damaged)
        assertEquals(-5L,ceramics.adjustment);assertEquals(4L,ceramics.closing)
        assertEquals("册",rows.single {it.categoryId=="books"}.unit)
        rows.forEach {assertEquals(it.closing,it.opening+it.increaseTotal-it.decreaseTotal+it.adjustment)}
    }
    @Test fun `unknown causes remain separate and temporary warehouse operations are not ledger reasons`() {
        val unknown=item("unknown",source=null)
        val rows=CollectionCategoryReasonBalances.build(emptyList(),listOf(unknown),listOf(event(EnumLedgerStatEvent.REGISTER,unknown)),emptyList())
        assertEquals(1L,rows.single().unknownIn);assertEquals(0L,rows.single().otherIn)
        assertNull(CollectionCategoryReasonBalances.reason(EnumLedgerStatEvent.TRANSFER_IN,EnumSource.DONATE))
        assertNull(CollectionCategoryReasonBalances.deregistrationReason(null))
        assertEquals(EnumLedgerChangeReason.OTHER_OUT,CollectionCategoryReasonBalances.deregistrationReason(EnumDeregReason.RETURN))
    }
}
