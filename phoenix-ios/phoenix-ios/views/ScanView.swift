import SwiftUI
import AVFoundation
import PhoenixShared

import UIKit

struct ScanView: View {

	@Binding var isShowing: Bool

	@State var paymentRequest: String? = nil
	@State var isWarningDisplayed: Bool = false
	
    @StateObject var toast = Toast()

	var body: some View {
		ZStack {
			MVIView({ $0.scan() }, onModel: { change in
				
				if change.newModel is Scan.ModelBadRequest {
					toast.toast(text: "Unexpected request format!")
				}
				else if let model = change.newModel as? Scan.ModelRequestWithoutAmount {
					paymentRequest = model.request
					isWarningDisplayed = true
				}
				else if let model = change.newModel as? Scan.ModelValidate {
					paymentRequest = model.request
				}
				else if change.newModel is Scan.ModelSending {
					isShowing = false
				}
				change.animateIfModelTypeChanged()
				
			}) { model, intent in
				view(model: model, postIntent: intent)
			}
			toast.view()
		}
		.frame(maxWidth: .infinity, maxHeight: .infinity)
	}

	@ViewBuilder
	func view(model: Scan.Model, postIntent: @escaping (Scan.Intent) -> Void) -> some View {
		switch model {
		case _ as Scan.ModelReady,
		     _ as Scan.ModelBadRequest,
			 _ as Scan.ModelDangerousRequest:
			
			ReadyView(
				model: model,
				postIntent: postIntent,
				paymentRequest: $paymentRequest,
				isWarningDisplayed: $isWarningDisplayed
			)
			
        case let m as Scan.ModelValidate:
			ValidateView(model: m, postIntent: postIntent)
			
        case let m as Scan.ModelSending:
			SendingView(model: m)
			
        default:
            fatalError("Unknown model \(model)")
        }
    }
}

struct ReadyView: View {
	
	let model: Scan.Model
	let postIntent: (Scan.Intent) -> Void
	
	@Binding var paymentRequest: String?
	@Binding var isWarningDisplayed: Bool
	
	@State var ignoreScanner: Bool = false // subtle timing bug described below
	
	@Environment(\.popoverState) var popoverState: PopoverState
	
	// Subtle timing bug:
	//
	// Steps to reproduce:
	// - scan payment without amount (and without trampoline support)
	// - warning popup is displayed
	// - keep QRcode within camera screen while tapping Confirm button
	//
	// What happens:
	// - the validate screen is not displayed as it should be
	//
	// Why:
	// - the warning popup is displayed
	// - user taps "confirm"
	// - we send IntentConfirmEmptyAmount to library
	// - QrCodeScannerView fires
	// - we send IntentParse to library
	// - library sends us ModelValidate
	// - library sends us ModelRequestWithoutAmount

	var body: some View {
		
		VStack {
			Spacer()
			Text("Scan a QR code")
				.padding()
				.font(.title2)

			QrCodeScannerView { request in
				if ignoreScanner {
					print("ignoring: ignoreScanner")
				} else if isWarningDisplayed {
					print("ignoring: isWarningDisplayed")
				}
				else {
					print("postIntent(Scan.Parse(...))")
					postIntent(Scan.IntentParse(request: request))
				}
			}
			.cornerRadius(10)
			.overlay(
				RoundedRectangle(cornerRadius: 10)
					.stroke(Color.gray, lineWidth: 4)
			)
			.padding()

			Divider()
				.padding([.leading, .trailing])

			Button {
				if let request = UIPasteboard.general.string {
					postIntent(Scan.IntentParse(request: request))
				}
			} label: {
				Image(systemName: "arrow.right.doc.on.clipboard")
				Text("Paste from clipboard")
					.font(.title2)
			}
			.disabled(!UIPasteboard.general.hasStrings)
			.padding()
			
			Spacer()
		}
		.navigationBarTitle("Scan", displayMode: .inline)
		.zIndex(2)
		.transition(
			.asymmetric(
				insertion: .identity,
				removal: .move(edge: .bottom)
			)
		)
		.onChange(of: isWarningDisplayed) { newValue in
			print("========== ReadyView.onChange(isWarningDisplayed)  ==========")
			if newValue {
				print("isWarningDisplayed = true")
				showWarning()
			} else {
				print("isWarningDisplayed = false")
			}
		}
		.onAppear() {
			print("========== ReadyView.onAppear()  ==========")
		}
	}
	
	func showWarning() -> Void {
		print("========== showWarning() ==========")
		ignoreScanner = true
		
		popoverState.dismissable.send(false)
		popoverState.displayContent.send(
			PopupAlert(
				postIntent: postIntent,
				paymentRequest: paymentRequest!,
				isShowing: $isWarningDisplayed,
				ignoreScanner: $ignoreScanner
			).anyView
		)
	}
}

struct PopupAlert : View {

	let postIntent: (Scan.Intent) -> Void
	let paymentRequest: String

	@Binding var isShowing: Bool
	@Binding var ignoreScanner: Bool
	
	@Environment(\.popoverState) var popoverState: PopoverState

	var body: some View {
		
		VStack(alignment: .leading) {

			Text("Warning")
				.font(.title2)
				.padding(.bottom)

			Group {
				Text(
					"This invoice doesn't include an amount. This may be dangerous:" +
					" malicious nodes may be able to steal your payment. To be safe, "
				)
				+ Text("ask the payee to specify an amount").fontWeight(.bold)
				+ Text(" in the payment request.")
			}
			.padding(.bottom)

			Text("Are you sure you want to pay this invoice?")
				.padding(.bottom)

			HStack {
				Button("Cancel") {
					isShowing = false
					ignoreScanner = false
					popoverState.close.send()
				}
				.font(.title3)
					
				Spacer()
					
				Button("Confirm") {
					isShowing = false
					postIntent(Scan.IntentConfirmDangerousRequest(request: paymentRequest))
					popoverState.close.send()
				}
				.font(.title3)
			}
		}
	}
}

struct CurrencyUnit: Hashable {
	let bitcoinUnit: BitcoinUnit?
	let fiatCurrency: FiatCurrency?
	
	init(bitcoinUnit: BitcoinUnit) {
		self.bitcoinUnit = bitcoinUnit
		self.fiatCurrency = nil
	}
	init(fiatCurrency: FiatCurrency) {
		self.bitcoinUnit = nil
		self.fiatCurrency = fiatCurrency
	}
	
	var abbrev: String {
		if let bitcoinUnit = bitcoinUnit {
			return bitcoinUnit.abbrev
		} else {
			return fiatCurrency!.shortLabel
		}
	}
	
	static func all(currencyPrefs: CurrencyPrefs) -> [CurrencyUnit] {
		
		var all = [CurrencyUnit]()
		
		for bitcoinUnit in BitcoinUnit.default().values {
			all.append(CurrencyUnit(bitcoinUnit: bitcoinUnit))
		}
		
		if let _ = currencyPrefs.fiatExchangeRate() {
			all.append(CurrencyUnit(fiatCurrency: currencyPrefs.fiatCurrency))
		} else {
			// We don't have the exchange rate for the user's selected fiat currency.
			// So we won't be able to perform conversion to millisatoshi.
		}
		
		return all
	}
}

struct ValidateView: View {
	
	let model: Scan.ModelValidate
	let postIntent: (Scan.Intent) -> Void

	@State var unit: CurrencyUnit = CurrencyUnit(bitcoinUnit: BitcoinUnit.satoshi)
	@State var amount: String = ""
	@State var altAmount: String = ""
	
	@State var isInvalidAmount: Bool = false
	@State var exceedsWalletCapacity: Bool = false
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	
	var body: some View {
	
		VStack {
			
			HStack(alignment: .firstTextBaseline) {
				TextField("123", text: $amount)
					.keyboardType(.decimalPad)
					.disableAutocorrection(true)
					.fixedSize()
					.font(.title)
					.foregroundColor(Color.appHorizon)
					.multilineTextAlignment(.trailing)
					.foregroundColor(isInvalidAmount ? Color.appRed : Color.primaryForeground)

				Picker(selection: $unit, label: Text(unit.abbrev).frame(minWidth: 40)) {
					let options = CurrencyUnit.all(currencyPrefs: currencyPrefs)
					ForEach(0 ..< options.count) {
						let option = options[$0]
						Text(option.abbrev).tag(option)
					}
				}
				.pickerStyle(MenuPickerStyle())
				
			} // </HStack>
			.padding([.leading, .trailing])
			.background(
				VStack {
					Spacer()
					Line().stroke(Color.appHorizon, style: StrokeStyle(lineWidth: 2, dash: [3]))
						.frame(height: 1)
				}
			)
			
			Text(altAmount)
				.font(.caption)
				.foregroundColor(isInvalidAmount ? Color.appRed : .secondary)
				.padding(.top, 4)

			Text(model.requestDescription ?? "")
				.padding()
				.padding([.top, .bottom])

			Button {
				sendPayment()
			} label: {
				HStack {
					Image("ic_send")
						.renderingMode(.template)
						.resizable()
						.aspectRatio(contentMode: .fit)
						.foregroundColor(Color.white)
						.frame(width: 22, height: 22)
					Text("Pay")
						.font(.title2)
						.foregroundColor(Color.white)
				}
				.padding(.top, 4)
				.padding(.bottom, 5)
				.padding([.leading, .trailing], 24)
			}
			.buttonStyle(ScaleButtonStyle(
				backgroundFill: Color.appHorizon,
				disabledBackgroundFill: Color.gray
			))
			.disabled(isInvalidAmount || exceedsWalletCapacity)
		}
		.navigationBarTitle("Validate payment", displayMode: .inline)
		.zIndex(1)
		.transition(.asymmetric(insertion: .identity, removal: .opacity))
		.onAppear() {
			onAppear()
		}
		.onChange(of: amount) { _ in
			checkAmount()
		}
		.onChange(of: unit) { _  in
			checkAmount()
		}
	}
	
	func onAppear() -> Void {
		print("ValidateView.onAppear()")
		
		unit = CurrencyUnit(bitcoinUnit: currencyPrefs.bitcoinUnit)
		
		if let msat_kotlin = model.amountMsat {
			let msat = Int64(truncating: msat_kotlin)
			
			// Todo: Replace this hack with a proper TextField formatter
			
			let formatted = Utils.formatBitcoin(msat: msat, bitcoinUnit: currencyPrefs.bitcoinUnit)
			let digits = "0123456789"
			
			let unformattedIntegerDigits = formatted.integerDigits.filter { digits.contains($0) }
			let unformattedFractionDigits = formatted.fractionDigits.filter { digits.contains($0) }
			
			var unformattedAmount = unformattedIntegerDigits
			if unformattedFractionDigits.count > 0 {
				unformattedAmount += formatted.decimalSeparator
				unformattedAmount += unformattedFractionDigits
			}
			
			amount = unformattedAmount
			checkAmount()
		}
	}
	
	func checkAmount() -> Void {
		
		if amount.count == 0 {
			isInvalidAmount = true
			altAmount = NSLocalizedString("Enter an amount", comment: "error message")
			return
		}
		
		guard let amt = Double(amount), amt > 0 else {
			isInvalidAmount = true
			altAmount = NSLocalizedString("Enter a valid amount", comment: "error message")
			return
		}
		
		isInvalidAmount = false
		
		if let bitcoinUnit = unit.bitcoinUnit {
			// amt    => bitcoinUnit
			// altAmt => fiatCurrency
			
			if let exchangeRate = currencyPrefs.fiatExchangeRate() {
				
				let msat = Utils.toMsat(from: amt, bitcoinUnit: bitcoinUnit)
				let alt = Utils.formatFiat(msat: msat, exchangeRate: exchangeRate)
				
				altAmount = "≈ \(alt.string)"
				
			} else {
				// We don't know the exchange rate, so we can't display fiat value.
				altAmount = ""
			}
			
		} else if let fiatCurrency = unit.fiatCurrency {
			// amt    => fiatCurrency
			// altAmt => bitcoinUnit
			
			if let exchangeRate = currencyPrefs.fiatExchangeRate(fiatCurrency: fiatCurrency) {
				
				let msat = Utils.toMsat(fromFiat: amt, exchangeRate: exchangeRate)
				let alt = Utils.formatBitcoin(msat: msat, bitcoinUnit: currencyPrefs.bitcoinUnit)
				
				altAmount = "≈ \(alt.string)"
				
			} else {
				// We don't know the exchange rate !
				// We shouldn't get into this state since CurrencyUnit.all() already filters for this.
				altAmount = ""
			}
		}
	}
	
	func sendPayment() -> Void {
		
		guard let amt = Double(amount), amt > 0 else {
			return
		}
		if let bitcoinUnit = unit.bitcoinUnit {
			postIntent(Scan.IntentSend(request: model.request, amount: amt, unit: bitcoinUnit))
			
		} else if let fiatCurrency = unit.fiatCurrency,
		          let exchangeRate = currencyPrefs.fiatExchangeRate(fiatCurrency: fiatCurrency)
		{
			let msat = Utils.toMsat(fromFiat: amt, exchangeRate: exchangeRate)
			let sat = Utils.convertBitcoin(msat: msat, bitcoinUnit: .satoshi)
			
			postIntent(Scan.IntentSend(request: model.request, amount: sat, unit: .satoshi))
		}
	}
}

struct SendingView: View {
	let model: Scan.ModelSending

	var body: some View {
		VStack {
			Text("Sending Payment...")
				.font(.title)
				.padding()
		}
		.navigationBarTitle("Sending payment", displayMode: .inline)
		.zIndex(0)
	}
}

// MARK:-

class ScanView_Previews: PreviewProvider {

	static let model_validate = Scan.ModelValidate(
		request: "lntb15u1p0hxs84pp5662ywy9px43632le69s5am03m6h8uddgln9cx9l8v524v90ylmesdq4xysyymr0vd4kzcmrd9hx7cqp2xqrrss9qy9qsqsp5xr4khzu3xter2z7dldnl3eqggut200vzth6cj8ppmqvx29hzm30q0as63ks9zddk3l5vf46lmkersynge3fy9nywwn8z8ttfdpak5ka9dvcnfrq95e6s06jacnsdryq8l8mrjkrfyd3vxgyv4axljvplmwsqae7yl9",
		amountMsat: 1500,
		requestDescription: "1 Blockaccino"
	)
	static let model_sending = Scan.ModelSending()
	
	
	static let mockModel = model_validate

	static var previews: some View {
		
	//	mockView(ScanView(isShowing: .constant(true)))
	//		.previewDevice("iPhone 11")
		
		NavigationView {
			SendingView(model: model_sending)
		}
		.preferredColorScheme(.light)
		.previewDevice("iPhone 8")
		
		NavigationView {
			SendingView(model: model_sending)
		}
		.preferredColorScheme(.dark)
		.previewDevice("iPhone 8")
	}

	#if DEBUG
	@objc class func injected() {
		UIApplication.shared.windows.first?.rootViewController = UIHostingController(rootView: previews)
	}
	#endif
}
