//
// Created by Salomon BRYS on 20/08/2020.
// Copyright (c) 2020 Acinq. All rights reserved.
//

import Foundation
import PhoenixShared

let mockPendingTransaction = Transaction(
        id: "0",
        amountMsat: -1900,
        displayedAmount: -1.9,
        desc: "1 Scala Chip Frappuccino",
        status: Transaction.Status.pending,
        timestamp: 0
//        paymentHash: "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
//        paymentRequest: "lnwhatever...",
//        paymentPreimage: "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210",
//        creationTimestamp: Int64(NSDate().timeIntervalSince1970 - 60 * 10),
//        expirationTimestamp: Int64(NSDate().timeIntervalSince1970 - 60 * 5),
//        completionTimestamp: Int64(NSDate().timeIntervalSince1970 - 60 * 8)
)

let mockSpendTransaction = Transaction(
        id: "1",
        amountMsat: -1500000,
        displayedAmount: -1500.0,
        desc: "1 Blockaccino",
        status: Transaction.Status.success,
        timestamp: 0
//        paymentHash: "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
//        paymentRequest: "lnwhatever...",
//        paymentPreimage: "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210",
//        creationTimestamp: Int64(NSDate().timeIntervalSince1970 - 60 * 10),
//        expirationTimestamp: Int64(NSDate().timeIntervalSince1970 - 60 * 5),
//        completionTimestamp: Int64(NSDate().timeIntervalSince1970 - 60 * 8)
)

let mockReceiveTransaction = Transaction(
        id: "2",
        amountMsat: 125000000,
        displayedAmount: 125000.0,
        desc: "On-Chain payment to 8b44f33a8c86f1fe0c18935df9db961ff5a6edb4ee49d3cee666458745d676fd",
        status: Transaction.Status.success,
        timestamp: 0
//        paymentHash: "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
//        paymentRequest: "lnwhatever...",
//        paymentPreimage: "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210",
//        creationTimestamp: Int64(NSDate().timeIntervalSince1970 - 60 * 10),
//        expirationTimestamp: Int64(NSDate().timeIntervalSince1970 - 60 * 5),
//        completionTimestamp: Int64(NSDate().timeIntervalSince1970 - 60 * 8)
)

let mockSpendFailedTransaction = Transaction(
        id: "3",
        amountMsat: -1700,
        displayedAmount: -1.7,
        desc: "1 Espresso Coin Panna",
        status: Transaction.Status.failure,
        timestamp: 0
//        paymentHash: "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
//        paymentRequest: "lnwhatever...",
//        paymentPreimage: "",
//        creationTimestamp: Int64(NSDate().timeIntervalSince1970 - 60 * 10),
//        expirationTimestamp: Int64(NSDate().timeIntervalSince1970 - 60 * 5),
//        completionTimestamp: Int64(NSDate().timeIntervalSince1970 - 60 * 8)
)
