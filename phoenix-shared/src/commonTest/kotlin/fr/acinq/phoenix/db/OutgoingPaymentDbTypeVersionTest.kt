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

import fr.acinq.bitcoin.Satoshi
import fr.acinq.eclair.Eclair.randomBytes32
import fr.acinq.eclair.channel.InvalidFinalScript
import fr.acinq.eclair.db.ChannelClosingType
import fr.acinq.eclair.db.IncomingPayment
import fr.acinq.eclair.db.OutgoingPayment
import fr.acinq.eclair.payment.FinalFailure
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.eclair.wire.PermanentNodeFailure
import fr.acinq.phoenix.db.payments.*
import kotlin.test.Test
import kotlin.test.assertEquals

class OutgoingPaymentDbTypeVersionTest {

    val txs1 = listOf(randomBytes32(), randomBytes32(), randomBytes32(), randomBytes32())
    val channelId1 = randomBytes32()
    val address1 = "tb1q97tpc0y4rvdnu9wm7nu354lmmzdm8du228u3g4"
    val preimage1 = randomBytes32()
    val paymentHash1 = preimage1.sha256()
    val paymentRequest1 = PaymentRequest.read("lntb1500n1ps9utezpp5xjfvpvgg3zykv2kdd9yws86xw5ww2kr60h9yphth2h6fly87a9gqdpzxysy2umswfjhxum0yppk76twypgxzmnwvycqp7xqrrss9qy9qsqsp5vm25lch9spq2m9fxqrgcxq0mxrgaehstd9javflyadsle5d97p9qmu9zsjn7l59lmps3568tz9ppla4xhawjptjyrw32jed84fe75z0ka0kmnntc9la95acvc0mjav6rdv5037y6zq9e0eqhenlt8y0yh8cpj467cl")

    @Test
    fun outgoing_details_normal() {
        val details = OutgoingPayment.Details.Normal(paymentRequest1)
        val dbType = details.mapToDb()
        assertEquals(OutgoingDetailsTypeVersion.NORMAL_V0 to OutgoingDetailsData.Normal.V0(paymentRequest1.write()), dbType)

        val blob = DbTypesHelper.any2blob(dbType.second)
        val deserialized = OutgoingDetailsData.deserialize(OutgoingDetailsTypeVersion.NORMAL_V0, blob)
        assertEquals(details, deserialized)
    }

    @Test
    fun outgoing_keysend_normal() {
        val details = OutgoingPayment.Details.KeySend(preimage1)
        val dbType = details.mapToDb()
        assertEquals(OutgoingDetailsTypeVersion.KEYSEND_V0 to OutgoingDetailsData.KeySend.V0(preimage1), dbType)

        val blob = DbTypesHelper.any2blob(dbType.second)
        val deserialized = OutgoingDetailsData.deserialize(OutgoingDetailsTypeVersion.KEYSEND_V0, blob)
        assertEquals(details, deserialized)
    }

    @Test
    fun outgoing_details_swapout() {
        val details = OutgoingPayment.Details.SwapOut(address1, paymentHash1)
        val dbType = details.mapToDb()
        assertEquals(OutgoingDetailsTypeVersion.SWAPOUT_V0 to OutgoingDetailsData.SwapOut.V0(address1, paymentHash1), dbType)

        val blob = DbTypesHelper.any2blob(dbType.second)
        val deserialized = OutgoingDetailsData.deserialize(OutgoingDetailsTypeVersion.SWAPOUT_V0, blob)
        assertEquals(details, deserialized)
    }

    @Test
    fun outgoing_details_closing() {
        val details = OutgoingPayment.Details.ChannelClosing(channelId1, address1, false)
        val dbType = details.mapToDb()
        assertEquals(OutgoingDetailsTypeVersion.CLOSING_V0 to OutgoingDetailsData.Closing.V0(channelId1, address1, false), dbType)

        val blob = DbTypesHelper.any2blob(dbType.second)
        val deserialized = OutgoingDetailsData.deserialize(OutgoingDetailsTypeVersion.CLOSING_V0, blob)
        assertEquals(details, deserialized)
    }

    @Test
    fun outgoing_status_success_onchain() {
        val closingType = ChannelClosingType.Local
        val status = OutgoingPayment.Status.Completed.Succeeded.OnChain(txs1, Satoshi(42), closingType, completedAt = 123)
        val dbType = status.mapToDb()
        assertEquals(OutgoingStatusTypeVersion.SUCCEEDED_ONCHAIN_V0 to OutgoingStatusData.SucceededOnChain.V0(txs1, Satoshi(42), closingType), dbType)

        val blob = DbTypesHelper.any2blob(dbType.second)
        val deserialized = OutgoingStatusData.deserialize(OutgoingStatusTypeVersion.SUCCEEDED_ONCHAIN_V0, blob, completedAt = 123)
        assertEquals(status, deserialized)
    }

    @Test
    fun outgoing_status_success_offchain() {
        val status = OutgoingPayment.Status.Completed.Succeeded.OffChain(preimage1, 456)
        val dbType = status.mapToDb()
        assertEquals(OutgoingStatusTypeVersion.SUCCEEDED_OFFCHAIN_V0 to OutgoingStatusData.SucceededOffChain.V0(preimage1), dbType)

        val blob = DbTypesHelper.any2blob(dbType.second)
        val deserialized = OutgoingStatusData.deserialize(OutgoingStatusTypeVersion.SUCCEEDED_OFFCHAIN_V0, blob, completedAt = 456)
        assertEquals(status, deserialized)
    }

    @Test
    fun outgoing_status_failed() {
        val status = OutgoingPayment.Status.Completed.Failed(FinalFailure.UnknownError, 789)
        val dbType = status.mapToDb()
        assertEquals(OutgoingStatusTypeVersion.FAILED_V0 to OutgoingStatusData.Failed.V0(FinalFailure.UnknownError), dbType)

        val blob = DbTypesHelper.any2blob(dbType.second)
        val deserialized = OutgoingStatusData.deserialize(OutgoingStatusTypeVersion.FAILED_V0, blob, completedAt = 789)
        assertEquals(status, deserialized)
    }

    @Test
    fun outgoing_part_status_failed_channelexception() {
        val status = OutgoingPayment.Part.Status.Failed(null, InvalidFinalScript(channelId1).details(), completedAt = 123)
        val dbType = status.mapToDb()
        assertEquals(OutgoingPartStatusTypeVersion.FAILED_V0 to OutgoingPartStatusData.Failed.V0(null, InvalidFinalScript(channelId1).details()), dbType)

        val blob = DbTypesHelper.any2blob(dbType.second)
        val deserialized = OutgoingPartStatusData.deserialize(OutgoingPartStatusTypeVersion.FAILED_V0, blob, completedAt = 123)
        assertEquals(status, deserialized)
    }

    @Test
    fun outgoing_part_status_failed_remotefailure() {
        val status = OutgoingPayment.Part.Status.Failed(PermanentNodeFailure.code, PermanentNodeFailure.message, completedAt = 345)
        val dbType = status.mapToDb()
        assertEquals(OutgoingPartStatusTypeVersion.FAILED_V0 to OutgoingPartStatusData.Failed.V0(PermanentNodeFailure.code, PermanentNodeFailure.message), dbType)

        val blob = DbTypesHelper.any2blob(dbType.second)
        val deserialized = OutgoingPartStatusData.deserialize(OutgoingPartStatusTypeVersion.FAILED_V0, blob, completedAt = 345)
        assertEquals(status, deserialized)
    }

    @Test
    fun outgoing_part_status_succeeded() {
        val status = OutgoingPayment.Part.Status.Succeeded(preimage1, completedAt = 3456)
        val dbType = status.mapToDb()
        assertEquals(OutgoingPartStatusTypeVersion.SUCCEEDED_V0 to OutgoingPartStatusData.Succeeded.V0(preimage1), dbType)

        val blob = DbTypesHelper.any2blob(dbType.second)
        val deserialized = OutgoingPartStatusData.deserialize(OutgoingPartStatusTypeVersion.SUCCEEDED_V0, blob, completedAt = 3456)
        assertEquals(status, deserialized)
    }

}