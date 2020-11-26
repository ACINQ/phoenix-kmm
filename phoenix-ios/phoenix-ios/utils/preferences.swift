import SwiftUI
import PhoenixShared
import Combine

enum CurrencyType: String, CaseIterable {
	case fiat
	case bitcoin
	
	static func parse(_ str: String) -> CurrencyType? {
		for value in CurrencyType.allCases {
			if str == value.rawValue {
				return value
			}
		}
		return nil
	}
}

extension FiatCurrency {
	
	static func parse(_ str: String) -> FiatCurrency? {
		for value in FiatCurrency.default().values {
			if str == value.label {
				return value
			}
		}
		return nil
	}
	
	static func localeDefault() -> FiatCurrency? {
		
		guard let currencyCode = NSLocale.current.currencyCode else {
			return nil
		}
		// currencyCode examples:
		// - "USD"
		// - "JPY"
		
		for value in FiatCurrency.default().values {
			
			let label = value.label
			// FiatCurrency.label examples:
			// - "(AUD) Australian Dollar"
			// - "(BRL) Brazilian Real"
			
			if let idx0 = label.firstIndex(of: "("),
			   let idx1 = label.firstIndex(of: ")"),
			   idx0 < idx1
			{
				let range = label.index(after: idx0)..<idx1
				let valueCode = String(label[range]) // e.g. "AUD", "BRL"
				
				if currencyCode.caseInsensitiveCompare(valueCode) == .orderedSame {
					return value
				}
			}
		}
		
		return nil
	}
}

extension BitcoinUnit {
	
	static func parse(_ str: String) -> BitcoinUnit? {
		for value in BitcoinUnit.default().values {
			if str == value.label {
				return value
			}
		}
		return nil
	}
}

class Prefs {
	
	public static let shared = Prefs()
	
	private init() {/* must use shared instance */}
	
	private enum UserDefaultsKey: String {
		case currencyType
		case fiatCurrency
		case bitcoinUnit
	}
	
	lazy private(set) var currencyTypePublisher: CurrentValueSubject<CurrencyType, Never> = {
		var value = self.currencyType
		return CurrentValueSubject<CurrencyType, Never>(value)
	}()
	
	var currencyType: CurrencyType {
		get {
			var saved: CurrencyType? = nil
			if let str = UserDefaults.standard.string(forKey: UserDefaultsKey.currencyType.rawValue) {
				saved = CurrencyType.parse(str)
			}
			return saved ?? CurrencyType.bitcoin
		}
		set {
			UserDefaults.standard.set( newValue.rawValue,
			                   forKey: UserDefaultsKey.currencyType.rawValue)
			currencyTypePublisher.send(newValue)
	  }
	}
	
	lazy private(set) var fiatCurrencyPublisher: CurrentValueSubject<FiatCurrency, Never> = {
		var value = self.fiatCurrency
		return CurrentValueSubject<FiatCurrency, Never>(value)
	}()
	
	var fiatCurrency: FiatCurrency {
		get {
			var saved: FiatCurrency? = nil
			if let str = UserDefaults.standard.string(forKey: UserDefaultsKey.fiatCurrency.rawValue) {
				saved = FiatCurrency.parse(str)
			}
			return saved ?? FiatCurrency.localeDefault() ?? FiatCurrency.usd
		}
		set {
			UserDefaults.standard.set( newValue.label, forKey: UserDefaultsKey.fiatCurrency.rawValue)
			fiatCurrencyPublisher.send(newValue)
	  }
	}
	
	lazy private(set) var bitcoinUnitPublisher: CurrentValueSubject<BitcoinUnit, Never> = {
		var value = self.bitcoinUnit
		return CurrentValueSubject<BitcoinUnit, Never>(value)
	}()
	
	var bitcoinUnit: BitcoinUnit {
		get {
			var saved: BitcoinUnit? = nil
			if let str = UserDefaults.standard.string(forKey: UserDefaultsKey.bitcoinUnit.rawValue) {
				saved = BitcoinUnit.parse(str)
			}
			return saved ?? BitcoinUnit.satoshi
		}
		set {
			UserDefaults.standard.set( newValue.label, forKey: UserDefaultsKey.bitcoinUnit.rawValue)
			bitcoinUnitPublisher.send(newValue)
		}
	}
}

class CurrencyPrefs: ObservableObject {
	
	@Published var currencyType: CurrencyType
	@Published var fiatCurrency: FiatCurrency
	@Published var bitcoinUnit: BitcoinUnit
	
	private var cancellables = Set<AnyCancellable>()

	init() {
		currencyType = Prefs.shared.currencyType
		fiatCurrency = Prefs.shared.fiatCurrency
		bitcoinUnit = Prefs.shared.bitcoinUnit
		
		Prefs.shared.currencyTypePublisher.sink {[weak self](newValue: CurrencyType) in
			self?.currencyType = newValue
		}.store(in: &cancellables)
		
		Prefs.shared.fiatCurrencyPublisher.sink {[weak self](newValue: FiatCurrency) in
			self?.fiatCurrency = newValue
		}.store(in: &cancellables)
		
		Prefs.shared.bitcoinUnitPublisher.sink {[weak self](newValue: BitcoinUnit) in
			self?.bitcoinUnit = newValue
		}.store(in: &cancellables)
	}
}
