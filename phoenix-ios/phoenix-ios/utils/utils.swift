import Foundation
import PhoenixShared

struct FormattedAmount {
	
	/// The currency amount, formatted for the current locale. E.g.:
	/// - "12,845.123456"
	/// - "12 845.123456"
	/// - "12.845,123456"
	///
	let digits: String
	
	/// The currency type. E.g.:
	/// - "USD"
	/// - "btc"
	///
	let type: String
	
	/// The locale-specific separator between the integerDigits & fractionDigits.
	/// If you're doing custom formatting between the two,
	/// be sure that you use this value. Don't assume it's a dot !
	///
	let decimalSeparator: String
	
	/// Returns the simple string value. E.g.:
	/// - "42,526 sat"
	///
	var string: String {
		return "\(digits) \(type)"
	}
	
	/// Returns only the integer portion of the digits. E.g.:
	/// - digits="12,845.123456" => "12,845"
	/// - digits="12 845.123456" => "12 845"
	/// - digits="12.845,123456" => "12.845"
	///
	var integerDigits: String {
	
		guard let sRange = digits.range(of: decimalSeparator) else {
			return digits
		}
		let range = digits.startIndex ..< sRange.lowerBound
		return String(digits[range])
	}
	
	/// Returns only the fraction portion of the digits. E.g.:
	/// - digits="12,845.123456" => "123456"
	/// - digits="12 845.123456" => "123456"
	/// - digits="12.845,123456" => "123456"
	///
	var fractionDigits: String {
		
		guard let sRange = digits.range(of: decimalSeparator) else {
			return ""
		}
		let range = sRange.upperBound ..< digits.endIndex
		return String(digits[range])
	}
}

class Utils {
	
	static func format(_ currencyPrefs: CurrencyPrefs, sat: Int64) -> FormattedAmount {
		return format(currencyPrefs, msat: (sat * 1_000))
	}
	
	static func format(_ currencyPrefs: CurrencyPrefs, msat: Int64) -> FormattedAmount {
		
		if currencyPrefs.currencyType == .bitcoin {
			return formatBitcoin(msat: msat, bitcoinUnit: currencyPrefs.bitcoinUnit)
		} else {
			return FormattedAmount(digits: "19.42", type: "USD", decimalSeparator: ".")
		}
	}
	
	static func formatBitcoin(msat: Int64, bitcoinUnit: BitcoinUnit) -> FormattedAmount {
		
		let targetAmount: Double
		switch bitcoinUnit {
			case .satoshi      : targetAmount = Double(msat) /           1_000.0
			case .bits         : targetAmount = Double(msat) /         100_000.0
			case .millibitcoin : targetAmount = Double(msat) /     100_000_000.0
			default/*.bitcoin*/: targetAmount = Double(msat) / 100_000_000_000.0
		}
		
		let formatter = NumberFormatter()
		formatter.numberStyle = .decimal
		formatter.usesGroupingSeparator = true // thousands separator (US="10,000", FR="10 000")
		
		switch bitcoinUnit {
			case .satoshi      : formatter.maximumFractionDigits = 0
			case .bits         : formatter.maximumFractionDigits = 2
			case .millibitcoin : formatter.maximumFractionDigits = 5
			default/*.bitcoin*/: formatter.maximumFractionDigits = 8
		}
		
		formatter.roundingMode = .floor
		
		let digits = formatter.string(from: NSNumber(value: targetAmount)) ?? targetAmount.description
		
		return FormattedAmount(
			digits: digits,
			type: bitcoinUnit.abbrev,
			decimalSeparator: formatter.decimalSeparator
		)
	}
}
