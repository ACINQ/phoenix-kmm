import Foundation
import PhoenixShared
import Combine
import UIKit

/// An ObservableObject that monitors the currently stored values in UserDefaults.
/// Available as an EnvironmentObject:
///
/// @EnvironmentObject var currencyPrefs: CurrencyPrefs
///
class CurrencyPrefs: ObservableObject {
	
	@Published var currencyType: CurrencyType
	@Published var fiatCurrency: FiatCurrency
	@Published var bitcoinUnit: BitcoinUnit
	
	@Published var fiatExchangeRates: [BitcoinPriceRate] = []
	private var fiatExchangeRatesWatcher: Ktor_ioCloseable? = nil
	
	var currency: Currency {
		switch currencyType {
		case .bitcoin:
			return Currency.bitcoin(bitcoinUnit)
		case .fiat:
			return Currency.fiat(fiatCurrency)
		}
	}
	
	private var cancellables = Set<AnyCancellable>()
	private var unsubscribe: (() -> Void)? = nil
	
	private var currencyTypeTimer: Timer? = nil
	private var currencyTypeNeedsSave: Bool = false

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
		
		let business = AppDelegate.get().business
		let ratesFlow = SwiftFlow<NSArray>(origin: business.currencyManager.ratesFlow)
		fiatExchangeRatesWatcher = ratesFlow.watch {[weak self](rates: NSArray?) in
			if let rates = rates as? Array<BitcoinPriceRate> {
				self?.fiatExchangeRates = rates
			}
		}
		
		let nc = NotificationCenter.default
		nc.publisher(for: UIApplication.willResignActiveNotification).sink {[weak self] _ in
			self?.saveCurrencyTypeIfNeeded()
		}.store(in: &cancellables)
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
		
		let exchangeRate = BitcoinPriceRate(
			fiatCurrency: fiatCurrency,
			price: exchangeRate,
			source: "",
			timestampMillis: 0
		)
		fiatExchangeRates.append(exchangeRate)
	}
	
	deinit {
		unsubscribe?()
		currencyTypeTimer?.invalidate()
        fiatExchangeRatesWatcher?.close()
	}
	
	func toggleCurrencyType() -> Void {
		
		assert(Thread.isMainThread, "This function is restricted to the main-thread")
		
		// I don't really want to save the currencyType to disk everytime the user changes it.
		// Because users tend to toggle back and forth often.
		// So we're using a timer, plus a listener on applicationWillResignActive.
		
		currencyType = (currencyType == .fiat) ? .bitcoin : .fiat
		currencyTypeNeedsSave = true
		
		currencyTypeTimer?.invalidate()
		currencyTypeTimer = Timer.scheduledTimer(withTimeInterval: 10.0, repeats: false, block: {[weak self] _ in
			self?.saveCurrencyTypeIfNeeded()
		})
	}
	
	/// Returns the exchangeRate for the currently set fiatCurrency.
	///
	func fiatExchangeRate() -> BitcoinPriceRate? {
		
		return fiatExchangeRate(fiatCurrency: self.fiatCurrency)
	}
	
	/// Returns the exchangeRate for the given fiatCurrency.
	///
	func fiatExchangeRate(fiatCurrency: FiatCurrency) -> BitcoinPriceRate? {
		
		return self.fiatExchangeRates.first { rate -> Bool in
			return (rate.fiatCurrency == fiatCurrency)
		}
	}
	
	private func saveCurrencyTypeIfNeeded() -> Void {
		
		if currencyTypeNeedsSave {
			currencyTypeNeedsSave = false
			
			Prefs.shared.currencyType = self.currencyType
			
			currencyTypeTimer?.invalidate()
			currencyTypeTimer = nil
		}
	}
	
	static func mockUSD() -> CurrencyPrefs {
		return CurrencyPrefs(currencyType: .bitcoin, fiatCurrency: .usd, bitcoinUnit: .sat, exchangeRate: 20_000.00)
	}
	
	static func mockEUR() -> CurrencyPrefs {
		return CurrencyPrefs(currencyType: .bitcoin, fiatCurrency: .eur, bitcoinUnit: .sat, exchangeRate: 17_000.00)
	}
}
