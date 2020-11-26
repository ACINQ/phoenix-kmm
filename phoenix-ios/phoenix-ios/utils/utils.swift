//
//  utils.swift
//  phoenix-ios
//
//  Created by Robbie Hanson on 11/26/20.
//  Copyright © 2020 Acinq. All rights reserved.
//

import Foundation
import PhoenixShared

class Utils {
	
	static func format(sat: Int64, currencyPrefs: CurrencyPrefs, includeSuffix: Bool = true) -> String {
		return format(sat: sat, target: currencyPrefs.bitcoinUnit, includeSuffix: includeSuffix)
	}
	
	static func format(sat: Int64, target: BitcoinUnit, includeSuffix: Bool = true) -> String {
		let msat = sat * 1_000
		return format(msat: msat, target: target, includeSuffix: includeSuffix)
	}
	
	static func format(msat: Int64, target: BitcoinUnit, includeSuffix: Bool = true) -> String {
		let msatNum = NSNumber(value: msat)
		return format(msat: msatNum, target: target, includeSuffix: includeSuffix)
	}
	
	static func format(
		msat: NSNumber,
		target: BitcoinUnit,
		includeSuffix: Bool = true // Whether or not to include the target (e.g. " sat")
	) -> String {
		
		let targetAmount: Double
		switch target {
		case .satoshi      : targetAmount = msat.doubleValue /           1_000.0
		case .bits         : targetAmount = msat.doubleValue /         100_000.0
		case .millibitcoin : targetAmount = msat.doubleValue /     100_000_000.0
		default/*.bitcoin*/: targetAmount = msat.doubleValue / 100_000_000_000.0
		}
		
		let formatter = NumberFormatter()
		formatter.numberStyle = .decimal
		formatter.usesGroupingSeparator = true // thousands separator (US="10,000", FR="10 000")
		
		switch target {
			case .satoshi      : formatter.maximumFractionDigits = 0
			case .bits         : formatter.maximumFractionDigits = 2
			case .millibitcoin : formatter.maximumFractionDigits = 5
			default/*.bitcoin*/: formatter.maximumFractionDigits = 8
		}
		
		formatter.roundingMode = .floor
		
		let amountStr = formatter.string(from: NSNumber(value: targetAmount)) ?? targetAmount.description
		if includeSuffix {
			return "\(amountStr) \(target.abbrev)"
		} else {
			return amountStr
		}
	}
}
