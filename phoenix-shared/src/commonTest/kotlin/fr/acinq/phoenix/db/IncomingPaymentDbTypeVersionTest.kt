/*
 * Copyright 2021 ACINQ SAS
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

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.eclair.db.IncomingPayment
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.eclair.utils.msat
import fr.acinq.phoenix.db.payments.*
import fr.acinq.phoenix.db.payments.DbTypesHelper.any2blob
import kotlin.test.Test
import kotlin.test.assertEquals

class IncomingPaymentDbTypeVersionTest {

    val channelId1 = ByteVector32.fromValidHex("3b6208285563c9adb009781acf1626f1c2a3b1a3492d5ec312ead8282c7ad6da")
    val address1 = "tb1q97tpc0y4rvdnu9wm7nu354lmmzdm8du228u3g4"
    val invoice1 =
        PaymentRequest.read("lntb1500n1ps9u963pp5llphsu6evgmzgk8g2e73su44wn6txmwywdzwvtdwzrt9pqxc9f5sdpzxysy2umswfjhxum0yppk76twypgxzmnwvycqp7xqrrss9qy9qsqsp5qa7092geq6ptp24uzlfw0vj3w4whh2zuc9rquwca69acwx5khckqvslyw2n6dallc868vxu3uueyhw6pe00cmluynv7ca4tknz7g274rp9ucwqpx5ydejsmzl4xpegqtemcq6vwvu8alpxttlj82e7j26gspfj06gn")

    @Test
    fun incoming_origin_invoice() {
        val origin = IncomingPayment.Origin.Invoice(invoice1)
        val dbType = origin.mapToDb()
        assertEquals(IncomingOriginTypeVersion.INVOICE_V0 to IncomingOriginData.Invoice.V0(invoice1.write()), dbType)

        val blob = any2blob(dbType.second)
        val deserialized = IncomingOriginData.deserialize(IncomingOriginTypeVersion.INVOICE_V0, blob)
        assertEquals(origin, deserialized)
    }

    @Test
    fun incoming_origin_keysend() {
        val origin = IncomingPayment.Origin.KeySend
        val dbType = origin.mapToDb()
        assertEquals(IncomingOriginTypeVersion.KEYSEND_V0 to IncomingOriginData.KeySend.V0, dbType)

        val blob = any2blob(dbType.second)
        val deserialized = IncomingOriginData.deserialize(IncomingOriginTypeVersion.KEYSEND_V0, blob)
        assertEquals(origin, deserialized)
    }

    @Test
    fun incoming_origin_swapin() {
        val origin1 = IncomingPayment.Origin.SwapIn(address1)
        val dbType1 = origin1.mapToDb()
        assertEquals(IncomingOriginTypeVersion.SWAPIN_V0 to IncomingOriginData.SwapIn.V0(address1), dbType1)

        val blob1 = any2blob(dbType1.second)
        val deserialized1 = IncomingOriginData.deserialize(IncomingOriginTypeVersion.SWAPIN_V0, blob1)
        assertEquals(origin1, deserialized1)
    }

    @Test
    fun incoming_origin_swapin_null() {
        val origin1 = IncomingPayment.Origin.SwapIn(null)
        val dbType1 = origin1.mapToDb()
        assertEquals(IncomingOriginTypeVersion.SWAPIN_V0 to IncomingOriginData.SwapIn.V0(null), dbType1)

        val blob1 = any2blob(dbType1.second)
        val deserialized1 = IncomingOriginData.deserialize(IncomingOriginTypeVersion.SWAPIN_V0, blob1)
        assertEquals(origin1, deserialized1)
    }

    @Test
    fun incoming_receivedwith_lightning() {
        val receivedWith = IncomingPayment.ReceivedWith.LightningPayment
        val dbType = receivedWith.mapToDb()
        assertEquals(IncomingReceivedWithTypeVersion.LIGHTNING_PAYMENT_V0 to IncomingReceivedWithData.LightningPayment.V0, dbType)

        val blob = any2blob(dbType.second)
        val deserialized = IncomingReceivedWithData.deserialize(IncomingReceivedWithTypeVersion.LIGHTNING_PAYMENT_V0, blob)
        assertEquals(receivedWith, deserialized)
    }

    @Test
    fun incoming_receivedwith_newchannel() {
        val receivedWith1 = IncomingPayment.ReceivedWith.NewChannel(123456789.msat, channelId1)
        val dbType1 = receivedWith1.mapToDb()
        assertEquals(IncomingReceivedWithTypeVersion.NEW_CHANNEL_V0 to IncomingReceivedWithData.NewChannel.V0(123456789.msat, channelId1), dbType1)

        val blob1 = any2blob(dbType1.second)
        val deserialized1 = IncomingReceivedWithData.deserialize(IncomingReceivedWithTypeVersion.NEW_CHANNEL_V0, blob1)
        assertEquals(receivedWith1, deserialized1)
    }

    @Test
    fun incoming_receivedwith_newchannel_null() {
        val receivedWith1 = IncomingPayment.ReceivedWith.NewChannel(111111111.msat, null)
        val dbType1 = receivedWith1.mapToDb()
        assertEquals(IncomingReceivedWithTypeVersion.NEW_CHANNEL_V0 to IncomingReceivedWithData.NewChannel.V0(111111111.msat, null), dbType1)

        val blob1 = any2blob(dbType1.second)
        val deserialized1 = IncomingReceivedWithData.deserialize(IncomingReceivedWithTypeVersion.NEW_CHANNEL_V0, blob1)
        assertEquals(receivedWith1, deserialized1)
    }
}