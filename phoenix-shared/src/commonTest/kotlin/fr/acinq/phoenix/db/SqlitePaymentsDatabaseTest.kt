/*
 * Copyright 2020 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.acinq.phoenix.db

import com.squareup.sqldelight.db.SqlDriver
import fr.acinq.bitcoin.Block
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.Crypto
import fr.acinq.eclair.*
import fr.acinq.eclair.channel.TooManyAcceptedHtlcs
import fr.acinq.eclair.db.HopDesc
import fr.acinq.eclair.db.IncomingPayment
import fr.acinq.eclair.db.OutgoingPayment
import fr.acinq.eclair.payment.FinalFailure
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.eclair.utils.Either
import fr.acinq.eclair.utils.UUID
import fr.acinq.eclair.utils.msat
import fr.acinq.eclair.utils.toByteVector32
import fr.acinq.eclair.wire.TemporaryNodeFailure
import runTest
import kotlin.test.*

class SqlitePaymentsDatabaseTest {
    private val db = SqlitePaymentsDb(testPaymentsDriver())

    val preimage1 = Eclair.randomBytes32()
    val paymentHash1 = Crypto.sha256(preimage1).toByteVector32()
    val origin1 = IncomingPayment.Origin.Invoice(createInvoice(preimage1))
    val receivedWith1 = IncomingPayment.ReceivedWith.LightningPayment

    val preimage2 = Eclair.randomBytes32()
    val paymentHash2 = Crypto.sha256(preimage2).toByteVector32()
    val origin2 = IncomingPayment.Origin.KeySend
    val receivedWith2 = IncomingPayment.ReceivedWith.NewChannel(fees = MilliSatoshi(5000), channelId = Eclair.randomBytes32())

    @Test
    fun incoming__empty() = runTest {
        assertNull(db.getIncomingPayment(paymentHash1))
        assertEquals(listOf(), db.listReceivedPayments(10, 0))
        assertFailsWith(IncomingPaymentNotFound::class) { db.receivePayment(paymentHash1, MilliSatoshi(123), receivedWith1, 5) }
    }

    @Test
    fun incoming__receive() = runTest {
        db.addIncomingPayment(preimage1, origin1, 0)
        db.listReceivedPayments(10, 0)[0].let {
            assertEquals(paymentHash1, it.paymentHash)
            assertEquals(preimage1, it.preimage)
            assertEquals(origin1, it.origin)
            assertNull(it.received)
        }
        db.receivePayment(paymentHash1, MilliSatoshi(123), receivedWith1, 10)
        db.getIncomingPayment(paymentHash1)!!.let {
            assertEquals(paymentHash1, it.paymentHash)
            assertEquals(preimage1, it.preimage)
            assertEquals(origin1, it.origin)
            assertEquals(MilliSatoshi(123), it.received?.amount)
            assertEquals(10, it.received?.receivedAt)
            assertEquals(receivedWith1, it.received?.receivedWith)
        }
    }

    @Test
    fun incoming__list() = runTest {
        db.addIncomingPayment(preimage1, origin1, 0)
        db.addIncomingPayment(preimage2, origin2, 15)

        // -- test ordering
        db.listReceivedPayments(10, 0).let {
            // older payments are last
            assertEquals(db.getIncomingPayment(paymentHash2), it[0])
            assertEquals(db.getIncomingPayment(paymentHash1), it[1])
        }
        db.receivePayment(paymentHash1, MilliSatoshi(123), receivedWith1, 20)
        db.listReceivedPayments(10, 0).let {
            // reception date takes precedence over creation date
            assertEquals(db.getIncomingPayment(paymentHash1), it[0])
            assertTrue(it[0].received != null)
            assertEquals(db.getIncomingPayment(paymentHash2), it[1])
        }

        // -- test paging
        assertEquals(1, db.listReceivedPayments(10, 1).size)
        assertEquals(0, db.listReceivedPayments(10, 2).size)
    }

    @Test
    fun incoming__unique_payment_hash() = runTest {
        db.addIncomingPayment(preimage1, origin1, 0)
        assertFails { db.addIncomingPayment(preimage1, origin1, 0) } // payment hash is unique
    }

    @Test
    fun incoming__receive_should_sum() = runTest {
        db.addIncomingPayment(preimage1, origin1, 0)
        db.receivePayment(paymentHash1, MilliSatoshi(100), receivedWith1, 10)
        assertEquals(MilliSatoshi(100), db.getIncomingPayment(paymentHash1)?.received?.amount)
        db.receivePayment(paymentHash1, MilliSatoshi(50), receivedWith1, 20)
        assertEquals(MilliSatoshi(150), db.getIncomingPayment(paymentHash1)?.received?.amount)
    }

    @Test
    fun incoming__is_expired() = runTest {
        db.addIncomingPayment(preimage1, origin1, 0)
        db.receivePayment(paymentHash1, MilliSatoshi(123), receivedWith1, 10)
        assertTrue(db.getIncomingPayment(paymentHash1)!!.isExpired())
    }

    private fun createOutgoing(): OutgoingPayment {
        val (a, b, c) = listOf(Eclair.randomKey().publicKey(), Eclair.randomKey().publicKey(), Eclair.randomKey().publicKey())
       val pr = createInvoice(Eclair.randomBytes32())
        return OutgoingPayment(
            UUID.randomUUID(),
            50_000.msat,
            pr.nodeId,
            OutgoingPayment.Details.Normal(pr),
            listOf(
                OutgoingPayment.Part(UUID.randomUUID(), 20_000.msat, listOf(HopDesc(a, c, ShortChannelId(42))), OutgoingPayment.Part.Status.Pending, 100),
                OutgoingPayment.Part(UUID.randomUUID(), 30_000.msat, listOf(HopDesc(a, b), HopDesc(b, c)), OutgoingPayment.Part.Status.Pending, 105)
            ),
            OutgoingPayment.Status.Pending
        )
    }

    @Test
    fun outgoing__get() = runTest {
        val p = createOutgoing()
        db.addOutgoingPayment(p)
        assertEquals(p, db.getOutgoingPayment(p.id))
        assertNull(db.getOutgoingPayment(UUID.randomUUID()))
        p.parts.forEach { assertEquals(p, db.getOutgoingPart(it.id)) }
        assertNull(db.getOutgoingPart(UUID.randomUUID()))
    }

    @Test
    fun outgoing__get_status() = runTest {
        val p = createOutgoing()
        db.addOutgoingPayment(p)
        val onePartFailed = p.copy(
            parts = listOf(
                p.parts[0].copy(status = OutgoingPayment.Part.Status.Failed(Either.Right(TemporaryNodeFailure), 110)),
                p.parts[1]
            )
        )
        db.updateOutgoingPart(p.parts[0].id, Either.Right(TemporaryNodeFailure), 110)
        assertEquals(onePartFailed, db.getOutgoingPayment(p.id))
        p.parts.forEach { assertEquals(onePartFailed, db.getOutgoingPart(it.id)) }

        // We should never update non-existing parts.
        assertFails { db.updateOutgoingPart(UUID.randomUUID(), Either.Right(TemporaryNodeFailure)) }
        assertFails { db.updateOutgoingPart(UUID.randomUUID(), Eclair.randomBytes32()) }

        // Other payment parts are added.
        val newParts = listOf(
            OutgoingPayment.Part(UUID.randomUUID(), 5_000.msat, listOf(HopDesc(Eclair.randomKey().publicKey(), Eclair.randomKey().publicKey())), OutgoingPayment.Part.Status.Pending, 115),
            OutgoingPayment.Part(UUID.randomUUID(), 10_000.msat, listOf(HopDesc(Eclair.randomKey().publicKey(), Eclair.randomKey().publicKey())), OutgoingPayment.Part.Status.Pending, 120),
        )
        assertFails { db.addOutgoingParts(UUID.randomUUID(), newParts) }
        assertFails { db.addOutgoingParts(onePartFailed.id, newParts.map { it.copy(id = p.parts[0].id) }) }
        val withMoreParts = onePartFailed.copy(parts = onePartFailed.parts + newParts)
        db.addOutgoingParts(onePartFailed.id, newParts)
        assertEquals(withMoreParts, db.getOutgoingPayment(p.id))
        withMoreParts.parts.forEach { assertEquals(withMoreParts, db.getOutgoingPart(it.id)) }

        // Payment parts succeed.
        val preimage = Eclair.randomBytes32()
        val partsSettled = withMoreParts.copy(
            parts = listOf(
                withMoreParts.parts[0], // this one was failed
                withMoreParts.parts[1].copy(status = OutgoingPayment.Part.Status.Succeeded(preimage, 125)),
                withMoreParts.parts[2].copy(status = OutgoingPayment.Part.Status.Succeeded(preimage, 126)),
                withMoreParts.parts[3].copy(status = OutgoingPayment.Part.Status.Succeeded(preimage, 127)),
            )
        )
        assertEquals(OutgoingPayment.Status.Pending, partsSettled.status)
        db.updateOutgoingPart(withMoreParts.parts[1].id, preimage, 125)
        db.updateOutgoingPart(withMoreParts.parts[2].id, preimage, 126)
        db.updateOutgoingPart(withMoreParts.parts[3].id, preimage, 127)
        assertEquals(partsSettled, db.getOutgoingPayment(p.id))
        partsSettled.parts.forEach { assertEquals(partsSettled, db.getOutgoingPart(it.id)) }

        // Payment succeeds: failed parts are removed.
        val paymentSucceeded = partsSettled.copy(
            status = OutgoingPayment.Status.Succeeded(preimage, 130),
            parts = partsSettled.parts.drop(1)
        )
        db.updateOutgoingPayment(p.id, preimage, 130)
        assertFails { db.updateOutgoingPayment(UUID.randomUUID(), preimage, 130) }
        assertEquals(paymentSucceeded, db.getOutgoingPayment(p.id))
        assertNull(db.getOutgoingPart(partsSettled.parts[0].id))
        partsSettled.parts.drop(1).forEach { assertEquals(paymentSucceeded, db.getOutgoingPart(it.id)) }
    }

    @Test
    fun outgoing__do_not_reuse_ids() = runTest {
        val p = createOutgoing()
        db.addOutgoingPayment(p)
        assertFails { db.addOutgoingPayment(p) }
        p.copy(recipientAmount = 1000.msat).let {
            assertFails { db.addOutgoingPayment(it) }
        }
        p.copy(id = UUID.randomUUID(), parts = p.parts.map { it.copy(id = p.parts[0].id) }).let {
            assertFails { db.addOutgoingPayment(it) }
        }
    }

    @Test
    fun outgoing__fail_payment() = runTest{
        val p = createOutgoing()
        db.addOutgoingPayment(p)
        val channelId = Eclair.randomBytes32()
        val partsFailed = p.copy(
            parts = listOf(
                p.parts[0].copy(status = OutgoingPayment.Part.Status.Failed(Either.Right(TemporaryNodeFailure), 110)),
                p.parts[1].copy(status = OutgoingPayment.Part.Status.Failed(Either.Left(TooManyAcceptedHtlcs(channelId, 10)), 111)),
            )
        )
        db.updateOutgoingPart(p.parts[0].id, Either.Right(TemporaryNodeFailure), 110)
        db.updateOutgoingPart(p.parts[1].id, Either.Left(TooManyAcceptedHtlcs(channelId, 10)), 111)
        assertEquals(partsFailed, db.getOutgoingPayment(p.id))
        p.parts.forEach { assertEquals(partsFailed, db.getOutgoingPart(it.id)) }

        val paymentFailed = partsFailed.copy(status = OutgoingPayment.Status.Failed(FinalFailure.NoRouteToRecipient, 120))
        db.updateOutgoingPayment(p.id, FinalFailure.NoRouteToRecipient, 120)
        assertFails { db.updateOutgoingPayment(UUID.randomUUID(), FinalFailure.NoRouteToRecipient, 120) }
        assertEquals(paymentFailed, db.getOutgoingPayment(p.id))
        p.parts.forEach { assertEquals(paymentFailed, db.getOutgoingPart(it.id)) }
    }

    @Test
    fun outgoing__list_by_payment_hash() = runTest {

    }

    @Test
    fun outgoing__list_with_paging() = runTest {

    }

    @Test
    fun outgoing__list_all_payments() = runTest {
        val (preimage1, preimage2, preimage3, preimage4) = listOf(Eclair.randomBytes32(), Eclair.randomBytes32(), Eclair.randomBytes32(), Eclair.randomBytes32())
        val incoming1 = IncomingPayment(preimage1, IncomingPayment.Origin.Invoice(createInvoice(preimage1)), null, createdAt = 20)
        val incoming2 = IncomingPayment(preimage2, IncomingPayment.Origin.SwapIn(20_000.msat, "1PwLgmRdDjy5GAKWyp8eyAC4SFzWuboLLb", null), null, createdAt = 21)
        val incoming3 = IncomingPayment(preimage3, IncomingPayment.Origin.Invoice(createInvoice(preimage3)), null, createdAt = 22)
        val incoming4 = IncomingPayment(preimage4, IncomingPayment.Origin.Invoice(createInvoice(preimage4)), null, createdAt = 23)
        listOf(incoming1, incoming2, incoming3, incoming4).forEach { db.addIncomingPayment(it.preimage, it.origin, it.createdAt) }

        // Pending incoming payments should not be listed.
        assertTrue(db.listPayments(count = 10, skip = 0).isEmpty())

        val outgoing1 = OutgoingPayment(UUID.randomUUID(), 50_000.msat, Eclair.randomKey().publicKey(), OutgoingPayment.Details.Normal(createInvoice(Eclair.randomBytes32())))
        val outgoing2 = OutgoingPayment(UUID.randomUUID(), 55_000.msat, Eclair.randomKey().publicKey(), OutgoingPayment.Details.KeySend(Eclair.randomBytes32()))
        val outgoing3 = OutgoingPayment(UUID.randomUUID(), 60_000.msat, Eclair.randomKey().publicKey(), OutgoingPayment.Details.SwapOut("1PwLgmRdDjy5GAKWyp8eyAC4SFzWuboLLb", Eclair.randomBytes32()))
        val outgoing4 = OutgoingPayment(UUID.randomUUID(), 45_000.msat, Eclair.randomKey().publicKey(), OutgoingPayment.Details.Normal(createInvoice(Eclair.randomBytes32())))
        val outgoing5 = OutgoingPayment(UUID.randomUUID(), 35_000.msat, Eclair.randomKey().publicKey(), OutgoingPayment.Details.Normal(createInvoice(Eclair.randomBytes32())))
        listOf(outgoing1, outgoing2, outgoing3, outgoing4, outgoing5).forEach { db.addOutgoingPayment(it) }

        db.receivePayment(incoming1.paymentHash, 20_000.msat, IncomingPayment.ReceivedWith.LightningPayment, receivedAt = 100)
        val inFinal1 = incoming1.copy(received = IncomingPayment.Received(20_000.msat, IncomingPayment.ReceivedWith.LightningPayment, 100))
        db.updateOutgoingPayment(outgoing1.id, Eclair.randomBytes32(), completedAt = 102)
        val outFinal1 = db.getOutgoingPayment(outgoing1.id)!!
        db.updateOutgoingPayment(outgoing2.id, FinalFailure.UnknownError, completedAt = 103)
        val outFinal2 = db.getOutgoingPayment(outgoing2.id)!!
        db.receivePayment(incoming2.paymentHash, 25_000.msat, IncomingPayment.ReceivedWith.NewChannel(250.msat, channelId = null), receivedAt = 105)
        val inFinal2 = incoming2.copy(received = IncomingPayment.Received(25_000.msat, IncomingPayment.ReceivedWith.NewChannel(250.msat, channelId = null), 105))
        db.updateOutgoingPayment(outgoing3.id, Eclair.randomBytes32(), completedAt = 106)
        val outFinal3 = db.getOutgoingPayment(outgoing3.id)!!
        db.receivePayment(incoming4.paymentHash, 10_000.msat, IncomingPayment.ReceivedWith.LightningPayment, receivedAt = 110)
        val inFinal4 = incoming4.copy(received = IncomingPayment.Received(10_000.msat, IncomingPayment.ReceivedWith.LightningPayment, 110))
        db.updateOutgoingPayment(outgoing5.id, Eclair.randomBytes32(), completedAt = 112)
        val outFinal5 = db.getOutgoingPayment(outgoing5.id)!!
        // outgoing4 and incoming3 are still pending.

        assertEquals(listOf(outFinal5, inFinal4, outFinal3, inFinal2, outFinal2, outFinal1, inFinal1), db.listPayments(count = 10, skip = 0))
        assertEquals(listOf(outFinal1, inFinal1), db.listPayments(count = 5, skip = 5))
    }

    companion object {
        private val defaultFeatures = Features(
            setOf(
                ActivatedFeature(Feature.VariableLengthOnion, FeatureSupport.Optional),
                ActivatedFeature(Feature.PaymentSecret, FeatureSupport.Optional),
                ActivatedFeature(Feature.BasicMultiPartPayment, FeatureSupport.Optional)
            )
        )
        private fun createInvoice(preimage: ByteVector32): PaymentRequest {
            return PaymentRequest.create(Block.LivenetGenesisBlock.hash, 150_000.msat, Crypto.sha256(preimage).toByteVector32(), Eclair.randomKey(), "invoice", CltvExpiryDelta(16), defaultFeatures)
        }
    }
}

expect fun testPaymentsDriver(): SqlDriver