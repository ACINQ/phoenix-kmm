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
	
	/// Returns the short version of the label.
	/// For example: "AUD", "BRL"
	///
	var shortLabel: String {
		
		if let idx0 = label.firstIndex(of: "("),
		   let idx1 = label.firstIndex(of: ")"),
		   idx0 < idx1
		{
			let range = label.index(after: idx0)..<idx1
			return String(label[range])
		}
		else {
			return label
		}
	}
	
	/// Returns the long version of the label.
	/// For example: "Australian Dollar", "Brazilian Real"
	///
	var longLabel: String {
		
		if let idx = label.firstIndex(of: ")") {
			
			let range = label.index(after: idx)..<label.endIndex
			let substr = String(label[range])
			
			return substr.trimmingCharacters(in: .whitespaces)
		} else {
			return label
		}
	}
	
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
		
		for fiat in FiatCurrency.default().values {
			
			let fiatCode = fiat.shortLabel // e.g. "AUD", "BRL"
			
			if currencyCode.caseInsensitiveCompare(fiatCode) == .orderedSame {
				return fiat
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
	
	@Published var fiatExchangeRates: [BitcoinPriceRate] = []
	
	private var cancellables = Set<AnyCancellable>()
	private var unsubscribe: (() -> Void)? = nil

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
		
		let business = PhoenixApplicationDelegate.get().business
		let refreshFiatExchangeRates = {[weak self] in
			print("refreshFiatExchangeRates")

			let rates = business.currencyManager.getBitcoinRates()
			self?.fiatExchangeRates = rates
		}

		refreshFiatExchangeRates()
		unsubscribe = business.eventBus.subscribe {(event: Event) in
			print("EventBus notification: \(event)")

			if let _ = event as? FiatExchangeRatesUpdated {
				print("FiatExchangeRatesUpdated")
				refreshFiatExchangeRates()
			}
		}
	}
	
	private init(
		currencyType: CurrencyType,
		fiatCurrency: FiatCurrency,
		bitcoinUnit: BitcoinUnit,
		exchangeRate: Double
	) {
		self.currencyType = currencyType
		self.fiatCurrency = fiatCurrency
		self.bitcoinUnit = bitcoinUnit
		
		let exchangeRate = BitcoinPriceRate(fiatCurrency: fiatCurrency, price: exchangeRate)
		fiatExchangeRates.append(exchangeRate)
	}
	
	deinit {
		unsubscribe?()
	}
	
	func toggleCurrencyType() -> Void {
		
		if currencyType == .fiat {
			currencyType = .bitcoin
		} else {
			currencyType = .fiat
		}
	}
	
	static func mockUSD() -> CurrencyPrefs {
		return CurrencyPrefs(currencyType: .bitcoin, fiatCurrency: .usd, bitcoinUnit: .satoshi, exchangeRate: 20_000.00)
	}
	
	static func mockEUR() -> CurrencyPrefs {
		return CurrencyPrefs(currencyType: .bitcoin, fiatCurrency: .usd, bitcoinUnit: .satoshi, exchangeRate: 17_000.00)
	}
}
