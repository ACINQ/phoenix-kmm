import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "ReceiveView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

enum ReceiveViewSheet {
	case sharing(url: String)
	case editing(model: Receive.ModelGenerated)
}

struct ReceiveView: AltMviView {
	
	@StateObject var mvi = AltMVI({ $0.receive() })
	
	@StateObject var qrCode = QRCode()

	@State var unit: String = "sat"
	@State var sheet: ReceiveViewSheet? = nil
	
	@State var pushPermissionRequestedFromOS = true
	@State var bgAppRefreshDisabled = false
	@State var notificationsDisabled = false
	@State var alertsDisabled = false
	@State var badgesDisabled = false
	
	@Environment(\.horizontalSizeClass) var horizontalSizeClass
	@Environment(\.verticalSizeClass) var verticalSizeClass
	@Environment(\.presentationMode) var mode: Binding<PresentationMode>
	@Environment(\.popoverState) var popoverState: PopoverState
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	
	@StateObject var lastIncomingPayment = ObservableLastIncomingPayment()
	@StateObject var toast = Toast()
	
	let willEnterForegroundPublisher = NotificationCenter.default.publisher(for:
		UIApplication.willEnterForegroundNotification
	)
	
	@ViewBuilder var view: some View {
		
		ZStack {
			
			Image("testnet_bg")
				.resizable(resizingMode: .tile)
			
			if verticalSizeClass == UserInterfaceSizeClass.compact {
				mainLandscape()
			} else {
				mainPortrait()
			}
			
			toast.view()
			
		} // </ZStack>
		.frame(maxHeight: .infinity)
		.background(Color.primaryBackground)
		.edgesIgnoringSafeArea([.bottom, .leading, .trailing]) // top is nav bar
		.onAppear {
			onAppear()
		}
		.onChange(of: mvi.model, perform: { model in
			onModelChange(model: model)
		})
		.onChange(of: lastIncomingPayment.value) { (payment: Eclair_kmpWalletPayment?) in
			lastIncomingPaymentChanged(payment)
		}
		.onReceive(willEnterForegroundPublisher, perform: { _ in
			willEnterForeground()
		})
		.sheet(isPresented: Binding( // SwiftUI only allows for 1 ".sheet"
			get: { sheet != nil },
			set: { if !$0 { sheet = nil }}
		)) {
			switch sheet! {
			case .sharing(let sharingUrl):
				let items: [Any] = [sharingUrl]
				ActivityView(activityItems: items, applicationActivities: nil)

			case .editing(let model):
				ModifyInvoiceSheet(
					mvi: mvi,
					sheet: $sheet,
                    initialAmount: model.amount,
					desc: model.desc ?? ""
				)
				.modifier(GlobalEnvironment()) // SwiftUI bug (prevent crash)
			}
		}
		.navigationBarTitle("Receive ", displayMode: .inline)
	}
	
	@ViewBuilder
	func mainPortrait() -> some View {
		
		let model = mvi.model
		VStack {
			qrCodeView(model)
				.frame(width: 200, height: 200)
				.padding()
				.background(Color.white)
				.cornerRadius(20)
				.overlay(
					RoundedRectangle(cornerRadius: 20)
						.stroke(Color.appHorizon, lineWidth: 1)
				)
				.padding()
			
			VStack(alignment: .center) {
			
				Text(invoiceAmount(model))
					.font(.caption2)
					.foregroundColor(.secondary)
					.padding(.bottom, 2)
			
				Text(invoiceDescription(model))
					.lineLimit(1)
					.font(.caption2)
					.foregroundColor(.secondary)
					.padding(.bottom, 2)
			}
			.padding([.leading, .trailing], 20)

			HStack {
				actionButton(
					image: Image(systemName: "square.on.square"),
					width: 20, height: 20,
					xOffset: 0, yOffset: 0
				) {
					didTapCopyButton(model)
				}
				.disabled(!(model is Receive.ModelGenerated))
				
				actionButton(
					image: Image(systemName: "square.and.arrow.up"),
					width: 21, height: 21,
					xOffset: 0, yOffset: -1
				) {
					didTapShareButton(model)
				}
				.disabled(!(model is Receive.ModelGenerated))
				
				actionButton(
					image: Image(systemName: "square.and.pencil"),
					width: 19, height: 19,
					xOffset: 1, yOffset: -1
				) {
					didTapEditButton()
				}
				.disabled(!(model is Receive.ModelGenerated))
			}
			
			warningButton()
			
			Spacer()
			
		} // </VStack>
		.padding(.bottom, keyWindow?.safeAreaInsets.bottom) // top is nav bar
	}
	
	@ViewBuilder
	func mainLandscape() -> some View {
		
		let model = mvi.model
		HStack {
			
			qrCodeView(model)
				.frame(width: 200, height: 200)
				.padding(.all, 20)
				.background(Color.white)
				.cornerRadius(20)
				.overlay(
					RoundedRectangle(cornerRadius: 20)
						.strokeBorder(Color.appHorizon, lineWidth: 1)
				)
				.padding([.top, .bottom])
			
			VStack {
				
				actionButton(image: Image(systemName: "square.on.square")) {
					didTapCopyButton(model)
				}
				.disabled(!(model is Receive.ModelGenerated))
				
				actionButton(image: Image(systemName: "square.and.arrow.up")) {
					didTapShareButton(model)
				}
				.disabled(!(model is Receive.ModelGenerated))
				
				actionButton(image: Image(systemName: "square.and.pencil")) {
					didTapEditButton()
				}
				.disabled(!(model is Receive.ModelGenerated))
			}
			
			VStack(alignment: .center) {
			
				Spacer()
				
				Text(invoiceAmount(model))
					.font(.caption2)
					.foregroundColor(.secondary)
					.padding(.bottom, 6)
			
				Text(invoiceDescription(model))
					.lineLimit(1)
					.font(.caption2)
					.foregroundColor(.secondary)
				
				warningButton()
				
				Spacer()
			}
			.frame(width: 240) // match width of QRcode box
			.padding([.top, .bottom])
			
		} // </HStack>
		.padding(.bottom, keyWindow?.safeAreaInsets.bottom) // top is nav bar
	}
	
	@ViewBuilder
	func qrCodeView(_ model: Receive.Model) -> some View {
		
		if let m = model as? Receive.ModelGenerated,
		   qrCode.value == m.request,
		   let image = qrCode.image
		{
			image.resizable()
		} else {
			VStack {
				// Remember: This view is on a white background. Even in dark mode.
				ProgressView()
					.progressViewStyle(CircularProgressViewStyle(tint: Color.appHorizon))
					.padding(.bottom, 10)
			
				Text("Generating QRCode...")
					.foregroundColor(Color(UIColor.darkGray))
					.font(.caption)
			}
		}
	}

	@ViewBuilder
	func actionButton(
		image: Image,
		width: CGFloat = 20,
		height: CGFloat = 20,
		xOffset: CGFloat = 0,
		yOffset: CGFloat = 0,
		action: @escaping () -> Void
	) -> some View {
		
		Button(action: action) {
			ZStack {
				Color.buttonFill
					.frame(width: 40, height: 40)
					.cornerRadius(50)
					.overlay(
						RoundedRectangle(cornerRadius: 50)
							.stroke(Color(UIColor.separator), lineWidth: 1)
					)
				
				image
					.renderingMode(.template)
					.resizable()
					.scaledToFit()
					.frame(width: width, height: height)
					.offset(x: xOffset, y: yOffset)
			}
		}
		.padding()
	}
	
	@ViewBuilder
	func warningButton() -> some View {
		
		// There are warnings we may want to display:
		//
		// 1. The user has disabled Background App Refresh.
		//    In this case, we won't be able to receive payments
		//    while the app is in the background.
		//
		// 2. When we first prompted them to enable push notifications,
		//    the user said "no". Thus we have never tried to enable push notifications.
		//
		// 3. The user has totally disabled notifications for our app.
		//    So if a payment is received while the app is in the background,
		//    we won't be able to notify them (in any way, shape or form).
		//
		// 4. Similarly to above, the user didn't totally disable notifications,
		//    but they effectively did. Because they disabled all alerts & badges.
		
		if bgAppRefreshDisabled {
			Button {
				showBgAppRefreshDisabledWarning()
			} label: {
				Image(systemName: "exclamationmark.octagon.fill")
					.renderingMode(.template)
					.resizable()
					.aspectRatio(contentMode: .fit)
					.foregroundColor(Color.appRed)
					.frame(width: 32, height: 32)
			}
			.padding(.top, 2)
		}
		else if !pushPermissionRequestedFromOS {
			Button {
				showRequestPushPermissionPopup()
			} label: {
				Image(systemName: "exclamationmark.bubble.fill")
					.renderingMode(.template)
					.resizable()
					.aspectRatio(contentMode: .fit)
					.frame(width: 32, height: 32)
			}
			.padding(.top, 2)
		}
		else if notificationsDisabled || (alertsDisabled && badgesDisabled) {
			Button {
				showNotificationsDisabledWarning()
			} label: {
				Image(systemName: "exclamationmark.triangle.fill")
					.renderingMode(.template)
					.resizable()
					.aspectRatio(contentMode: .fit)
					.foregroundColor(Color.appYellow)
					.frame(width: 32, height: 32)
			}
			.padding(.top, 2)
		}
	}
	
	func invoiceAmount(_ model: Receive.Model) -> String {
		
		if let m = model as? Receive.ModelGenerated {
			if let msat = m.amount?.msat {
				let btcAmt = Utils.formatBitcoin(msat: msat, bitcoinUnit: currencyPrefs.bitcoinUnit)
				
				if let exchangeRate = currencyPrefs.fiatExchangeRate() {
					let fiatAmt = Utils.formatFiat(msat: msat, exchangeRate: exchangeRate)
					return "\(btcAmt.string)  /  \(fiatAmt.string)"
				} else {
					return btcAmt.string
				}
				
			} else {
				return NSLocalizedString("any amount",
					comment: "placeholder: invoice is amount-less"
				)
			}
		} else {
			return ""
		}
	}
	
	func invoiceDescription(_ model: Receive.Model) -> String {
		
		if let m = model as? Receive.ModelGenerated {
			if let desc = m.desc, desc.count > 0 {
				return desc
			} else {
				return NSLocalizedString("no description",
					comment: "placeholder: invoice is description-less"
				)
			}
		} else {
			return ""
		}
	}
	
	func onAppear() -> Void {
		log.trace("[ReadyReceivePayment] onAppear()")
		
		mvi.intent(Receive.IntentAsk(amount: nil, desc: nil))
		
		let query = Prefs.shared.pushPermissionQuery
		if query == .neverAskedUser {
			
			// We want to ask the user:
			// "Do you want to be notified when you've received a payment?"
			//
			// But let's show the popup after a brief delay,
			// to allow the user to see what this view is about.
			
			DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
				showRequestPushPermissionPopup()
			}
			
		} else {
			
			checkPushPermissions()
		}
	}
	
	func onModelChange(model: Receive.Model) -> Void {
		
		if let m = model as? Receive.ModelGenerated {
			qrCode.generate(value: m.request)
		}
	}
	
	func willEnterForeground() -> Void {
		log.trace("[ReadyReceivePayment] willEnterForeground()")
		
		let query = Prefs.shared.pushPermissionQuery
		if query != .neverAskedUser {
			
			checkPushPermissions()
		}
	}
	
	func requestPushPermission() -> Void {
		log.trace("[ReadyReceivePayment] requestPushPermission()")
		
		AppDelegate.get().requestPermissionForLocalNotifications { (granted: Bool) in
			
			let block = { checkPushPermissions() }
			if Thread.isMainThread {
				block()
			} else {
				DispatchQueue.main.async { block() }
			}
		}
	}
	
	func checkPushPermissions() -> Void {
		log.trace("[ReadyReceivePayment] checkPushPermission()")
		assert(Thread.isMainThread, "invoked from non-main thread")
		
		let query = Prefs.shared.pushPermissionQuery
		if query == .userAccepted {
			pushPermissionRequestedFromOS = true
		} else {
			// user denied or ignored, which means we haven't asked the OS for permission yet
			pushPermissionRequestedFromOS = false
		}
		
		bgAppRefreshDisabled = (UIApplication.shared.backgroundRefreshStatus != .available)
		
		let center = UNUserNotificationCenter.current()
		center.getNotificationSettings { settings in
			
			notificationsDisabled = (settings.authorizationStatus != .authorized)
			
			// Are we able to display notifications to the user ?
			// That is, can we display a message that says, "Payment received" ?
			//
			// Within Settings -> Notifications, there are 3 options
			// that can be enabled/disabled independently of each other:
			//
			// - Lock Screen
			// - Notification Center
			// - Banners
			//
			// If the user has disabled ALL of them, then we consider notifications to be disabled.
			// If the user has only disabled one, such as the Lock Screen,
			// we consider that an understandable privacy choice,
			// and don't highlight it in the UI.
			
			var count = 0
			if settings.lockScreenSetting == .enabled {
				count += 1
			}
			if settings.notificationCenterSetting == .enabled {
				count += 1
			}
			if settings.alertSetting == .enabled {
				count += 1
			}
			
			alertsDisabled = (count == 0)
			badgesDisabled = (settings.badgeSetting != .enabled)
		}
		
	}
	
	func showBgAppRefreshDisabledWarning() -> Void {
		log.trace("showBgAppRefreshDisabledWarning()")
		
		popoverState.dismissable.send(false)
		popoverState.displayContent.send(
			BgAppRefreshDisabledWarning().anyView
		)
	}
	
	func showNotificationsDisabledWarning() -> Void {
		log.trace("showNotificationsDisabledWarning()")
		
		popoverState.dismissable.send(false)
		popoverState.displayContent.send(
			NotificationsDisabledWarning().anyView
		)
	}
	
	func showRequestPushPermissionPopup() -> Void {
		log.trace("showRequestPushPermissionPopup()")
		
		let callback = {(response: PushPermissionPopupResponse) -> Void in
			
			log.debug("PushPermissionPopupResponse: \(response.rawValue)")
			
			switch response {
			case .accepted :
				Prefs.shared.pushPermissionQuery = .userAccepted
				requestPushPermission()
				
			case .denied:
				Prefs.shared.pushPermissionQuery = .userDeclined
				checkPushPermissions()
				
			case .ignored:
				// Ask again next time
				checkPushPermissions()
			}
		}
		
		popoverState.dismissable.send(true)
		popoverState.displayContent.send(
			RequestPushPermissionPopup(callback: callback).anyView
		)
	}
	
	func didTapCopyButton(_ model: Receive.Model) -> Void {
		log.trace("didTapCopyButton()")
		
		if let m = model as? Receive.ModelGenerated {
			UIPasteboard.general.string = m.request
			toast.toast(text: "Copied in pasteboard!")
		}
	}
	
	func didTapShareButton(_ model: Receive.Model) -> Void {
		log.trace("didTapShareButton()")
		
		if let m = model as? Receive.ModelGenerated {
			withAnimation {
				let url = "lightning:\(m.request)"
				sheet = ReceiveViewSheet.sharing(url: url)
			}
		}
	}
	
	func didTapEditButton() -> Void {
		log.trace("didTapEditButton()")
		
		if let model = mvi.model as? Receive.ModelGenerated {
			withAnimation {
				sheet = ReceiveViewSheet.editing(model: model)
			}
		}
	}
	
	func lastIncomingPaymentChanged(_ payment: Eclair_kmpWalletPayment?) {
		log.trace("lastIncomingPaymentChanged()")
		
		guard
			let model = mvi.model as? Receive.ModelGenerated,
			let lastIncomingPayment = payment as? Eclair_kmpIncomingPayment
		else {
			return
		}
		
		if lastIncomingPayment.state() == WalletPaymentState.success {
			
			if lastIncomingPayment.paymentHash.toHex() == model.paymentHash {
				self.mode.wrappedValue.dismiss()
			}
		}
	}
}

struct ModifyInvoiceSheet: View {

	@ObservedObject var mvi: AltMVI<Receive.Model, Receive.Intent>
	@Binding var sheet: ReceiveViewSheet?

	let initialAmount: Eclair_kmpMilliSatoshi?
	
	@State var unit: CurrencyUnit = CurrencyUnit(bitcoinUnit: BitcoinUnit.sat)
	@State var amount: String = ""
	@State var parsedAmount: Result<Double, TextFieldCurrencyStylerError> = Result.failure(.emptyInput)
	
	@State var altAmount: String = ""
	@State var isInvalidAmount: Bool = false
	@State var isEmptyAmount: Bool = false
	
	@State var desc: String
	
	@EnvironmentObject private var currencyPrefs: CurrencyPrefs
	
	func currencyStyler() -> TextFieldCurrencyStyler {
		return TextFieldCurrencyStyler(
			unit: $unit,
			amount: $amount,
			parsedAmount: $parsedAmount,
			hideMsats: false
		)
	}

	var body: some View {
		
		VStack(alignment: .leading) {
			Text("Edit payment request")
				.font(.title3)
				.padding([.top, .bottom])

			HStack {
				TextField("Amount (optional)", text: currencyStyler().amountProxy)
					.keyboardType(.decimalPad)
					.disableAutocorrection(true)
					.foregroundColor(isInvalidAmount ? Color.appRed : Color.primaryForeground)
					.padding([.top, .bottom], 8)
					.padding(.leading, 16)
					.padding(.trailing, 0)

				Picker(
					selection: $unit,
					label: Text(unit.abbrev).frame(minWidth: 40, alignment: Alignment.trailing)
				) {
					let options = CurrencyUnit.displayable(currencyPrefs: currencyPrefs)
					ForEach(0 ..< options.count) {
						let option = options[$0]
						Text(option.abbrev).tag(option)
					}
				}
				.pickerStyle(MenuPickerStyle())
				.padding(.trailing, 16)
			}
			.background(Capsule().stroke(Color(UIColor.separator)))

			Text(altAmount)
				.font(.caption)
				.foregroundColor(isInvalidAmount && !isEmptyAmount ? Color.appRed : .secondary)
				.padding(.top, 0)
				.padding(.leading, 16)
				.padding(.bottom, 4)

			HStack {
				TextField("Description (optional)", text: $desc)
					.padding([.top, .bottom], 8)
					.padding([.leading, .trailing], 16)
			}
			.background(Capsule().stroke(Color(UIColor.separator)))

			Spacer()
			HStack {
				Spacer()
				Button("Save") {
					saveChanges()
					withAnimation { sheet = nil }
				}
				.font(.title2)
				.disabled(isInvalidAmount && !isEmptyAmount)
			}
			.padding(.bottom)

		} // </VStack>
		.padding([.leading, .trailing])
		.onAppear() {
			onAppear()
		}
		.onChange(of: amount) { _ in
			amountDidChange()
		}
		.onChange(of: unit) { _  in
			unitDidChange()
		}
		
	} // </body>
	
	func onAppear() -> Void {
		log.trace("(ModifyInvoiceSheet) onAppear()")
		
        let msat: Int64? = initialAmount?.msat
		
		// Regardless of whether or not the invoice currently has an amount,
		// we should default to the user's preferred currency.
		// In other words, we should default to fiat, if that's what the user prefers.
		//
		if currencyPrefs.currencyType == .fiat, let exchangeRate = currencyPrefs.fiatExchangeRate() {
			
			let fiatCurrency = currencyPrefs.fiatCurrency
			unit = CurrencyUnit(fiatCurrency: fiatCurrency)
			
			if let msat = msat {
				let targetAmt = Utils.convertToFiat(msat: msat, exchangeRate: exchangeRate)
				parsedAmount = Result.success(targetAmt)
				
				let formattedAmt = Utils.formatFiat(msat: msat, exchangeRate: exchangeRate)
				amount = formattedAmt.digits
			} else {
				refreshAltAmount()
			}
			
		} else {
			
			let bitcoinUnit = currencyPrefs.bitcoinUnit
			unit = CurrencyUnit(bitcoinUnit: bitcoinUnit)
			
			if let msat = msat {
				let targetAmt = Utils.convertBitcoin(msat: msat, bitcoinUnit: bitcoinUnit)
				parsedAmount = Result.success(targetAmt)
				
				let formattedAmt = Utils.formatBitcoin(msat: msat, bitcoinUnit: bitcoinUnit, hideMsats: false)
				amount = formattedAmt.digits
			} else {
				refreshAltAmount()
			}
		}
	}
	
	func amountDidChange() -> Void {
		log.trace("(ModifyInvoiceSheet) amountDidChange()")
		
		refreshAltAmount()
	}
	
	func unitDidChange() -> Void {
		log.trace("(ModifyInvoiceSheet) unitDidChange()")
		
		// We might want to apply a different formatter
		let result = TextFieldCurrencyStyler.format(input: amount, unit: unit, hideMsats: false)
		parsedAmount = result.1
		amount = result.0
		
		refreshAltAmount()
	}
	
	func refreshAltAmount() -> Void {
		log.trace("(ModifyInvoiceSheet) refreshAltAmount()")
		
		switch parsedAmount {
		case .failure(let error):
			isInvalidAmount = true
			isEmptyAmount = error == .emptyInput
			
			switch error {
			case .emptyInput:
				altAmount = NSLocalizedString("Any amount", comment: "error message")
			case .invalidInput:
				altAmount = NSLocalizedString("Enter a valid amount", comment: "error message")
			}
			
		case .success(let amt):
			isInvalidAmount = false
			isEmptyAmount = false
			
			if let bitcoinUnit = unit.bitcoinUnit {
				// amt    => bitcoinUnit
				// altAmt => fiatCurrency
				
				if let exchangeRate = currencyPrefs.fiatExchangeRate() {
					
					let msat = Utils.toMsat(from: amt, bitcoinUnit: bitcoinUnit)
					altAmount = Utils.formatFiat(msat: msat, exchangeRate: exchangeRate).string
					
				} else {
					// We don't know the exchange rate, so we can't display fiat value.
					altAmount = "?.?? \(currencyPrefs.fiatCurrency.name)"
				}
				
			} else if let fiatCurrency = unit.fiatCurrency {
				// amt    => fiatCurrency
				// altAmt => bitcoinUnit
				
				if let exchangeRate = currencyPrefs.fiatExchangeRate(fiatCurrency: fiatCurrency) {
					
					let msat = Utils.toMsat(fromFiat: amt, exchangeRate: exchangeRate)
					altAmount = Utils.formatBitcoin(msat: msat, bitcoinUnit: currencyPrefs.bitcoinUnit).string
					
				} else {
					// We don't know the exchange rate !
					// We shouldn't get into this state since CurrencyUnit.displayable() already filters for this.
					altAmount = "?.?? \(currencyPrefs.fiatCurrency.name)"
				}
			}
		}
	}
	
	func saveChanges() -> Void {
		log.trace("(ModifyInvoiceSheet) saveChanges()")
		
		if let amt = try? parsedAmount.get(), amt > 0 {
			
			if let bitcoinUnit = unit.bitcoinUnit {
                let msat = Eclair_kmpMilliSatoshi(msat: Utils.toMsat(from: amt, bitcoinUnit: bitcoinUnit))
				mvi.intent(Receive.IntentAsk(
					amount: msat,
					desc: desc
				))
				
			} else if let fiatCurrency = unit.fiatCurrency,
					  let exchangeRate = currencyPrefs.fiatExchangeRate(fiatCurrency: fiatCurrency)
			{
                let msat = Eclair_kmpMilliSatoshi(msat: Utils.toMsat(fromFiat: amt, exchangeRate: exchangeRate))
				mvi.intent(Receive.IntentAsk(
					amount: msat,
					desc: desc
				))
			}
			
		} else {
			
			mvi.intent(Receive.IntentAsk(
				amount: nil,
				desc: desc
			))
		}
	}
	
} // </ModifyInvoiceSheet>


struct BgAppRefreshDisabledWarning: View {
	
	@Environment(\.popoverState) private var popoverState: PopoverState
	
	let didEnterBackgroundPublisher = NotificationCenter.default.publisher(for:
		UIApplication.didEnterBackgroundNotification
	)
	
	var body: some View {
		
		VStack(alignment: .trailing) {
			
			VStack(alignment: .leading) {
				Text("You have disabled Background App Refresh for this app.")
					.bold()
					.padding(.bottom, 4)
				
				Text(
					"This means you will not be able to receive payments when Phoenix is in the background.  To receive payments, Phoenix must be open and in the foreground."
				)
				.lineLimit(nil)
				.minimumScaleFactor(0.5) // problems with "foreground" being truncated
				.padding(.bottom, 4)
				
				Text(
					"To fix this re-enable Background App Refresh via: Settings -> General -> Background App Refresh"
				)
				
			}
			.padding(.bottom)
			
			HStack {
				Button {
					popoverState.close.send()
				} label : {
					Text("OK").font(.title3)
				}
			}
		}
		.padding()
		.onReceive(didEnterBackgroundPublisher, perform: { _ in
			popoverState.close.send()
		})
	}
}

struct NotificationsDisabledWarning: View {
	
	@Environment(\.popoverState) private var popoverState: PopoverState
	
	let didEnterBackgroundPublisher = NotificationCenter.default.publisher(for:
		UIApplication.didEnterBackgroundNotification
	)
	
	var body: some View {
		
		VStack(alignment: .trailing) {
			
			VStack(alignment: .leading) {
				Text("You have disabled notifications for this app.")
					.bold()
					.padding(.bottom, 4)
				
				Text(
					"This means you will not be notified if you receive a payment while" +
					" Phoenix is in the background."
				)
			}
			.padding(.bottom)
			
			HStack {
				Button {
					fixIt()
					popoverState.close.send()
				} label : {
					Text("Settings").font(.title3)
				}
				.padding(.trailing, 20)
				
				Button {
					popoverState.close.send()
				} label : {
					Text("OK").font(.title3)
				}
			}
		}
		.padding()
		.onReceive(didEnterBackgroundPublisher, perform: { _ in
			popoverState.close.send()
		})
	}
	
	func fixIt() -> Void {
		log.trace("[NotificationsDisabledWarning] fixIt()")
		
		if let bundleIdentifier = Bundle.main.bundleIdentifier,
		   let appSettings = URL(string: UIApplication.openSettingsURLString + bundleIdentifier)
		{
			if UIApplication.shared.canOpenURL(appSettings) {
				UIApplication.shared.open(appSettings)
			}
		}
	}
}

enum PushPermissionPopupResponse: String {
	case ignored
	case denied
	case accepted
}

struct RequestPushPermissionPopup: View {
	
	let callback: (PushPermissionPopupResponse) -> Void
	
	@State private var userIsIgnoringPopover: Bool = true
	
	@Environment(\.popoverState) private var popoverState: PopoverState
	
	var body: some View {
		
		VStack(alignment: .trailing) {
			
			VStack(alignment: .leading) {
				Text("Would you like to be notified when somebody sends you a payment?")
			}
			.padding(.bottom)
			
			HStack {
				Button {
					didDeny()
				} label : {
					Text("No").font(.title3)
				}
				.padding(.trailing, 20)
				
				Button {
					didAccept()
				} label : {
					Text("Yes").font(.title3)
				}
			}
		}
		.padding()
		.onReceive(popoverState.close) {
			willClose()
		}
	}
	
	func didDeny() -> Void {
		log.trace("[RequestPushPermissionPopup] didDeny()")
		
		callback(.denied)
		userIsIgnoringPopover = false
		popoverState.close.send()
	}
	
	func didAccept() -> Void {
		log.trace("[RequestPushPermissionPopup] didAccept()")
		
		callback(.accepted)
		userIsIgnoringPopover = false
		popoverState.close.send()
	}
	
	func willClose() -> Void {
		log.trace("[RequestPushPermissionPopup] willClose()")
		
		if userIsIgnoringPopover {
			callback(.ignored)
		}
	}
}

struct FeePromptPopup : View {
	
	@Binding var show: Bool
	let postIntent: (Receive.Intent) -> Void
	
	var body: some View {
		VStack(alignment: .leading) {
			
			Text("Receive with a Bitcoin address")
				.font(.system(.title3, design: .serif))
				.lineLimit(nil)
				.padding(.bottom, 20)
			
			VStack(alignment: .leading, spacing: 14) {
			
				Text("A standard Bitcoin address will be displayed next.")
					
				Text("Funds sent to this address will be shown on your wallet after one confirmation.")
				
				Text("There is a small fee: ") +
				Text("0.10%").bold() +
				Text("\nFor example, to receive $100, the fee is 10 cents.")
			}
			
			HStack {
				Spacer()
				Button("Cancel") {
					withAnimation { show = false }
				}
				.font(.title3)
				.padding(.trailing, 8)
				
				Button("Proceed") {
					withAnimation { show = false }
				}
				.font(.title3)
			}
			.padding(.top, 20)
			
		} // </VStack>
		.padding()
	}
	
} // </FeePromptPopup>

// MARK:-

class ReceiveView_Previews: PreviewProvider {

//	static let mockModel = Receive.ModelAwaiting()
	static let mockModel = Receive.ModelGenerated(
		request: "lntb17u1p0475jgpp5f69ep0f2202rqegjeddjxa3mdre6ke6kchzhzrn4rxzhyqakzqwqdpzxysy2umswfjhxum0yppk76twypgxzmnwvycqp2xqrrss9qy9qsqsp5nhhdgpz3549mll70udxldkg48s36cj05epp2cjjv3rrvs5hptdfqlq6h3tkkaplq4au9tx2k49tcp3gx7azehseq68jums4p0gt6aeu3gprw3r7ewzl42luhc3gyexaq37h3d73wejr70nvcw036cde4ptgpckmmkm",
		paymentHash: "foobar",
		amount: Eclair_kmpMilliSatoshi(msat: 170000),
		desc: "1 Espresso Coin Panna"
	)

	static var previews: some View {
		mockView(ReceiveView())
			.previewDevice("iPhone 11")
	}

    #if DEBUG
    @objc class func injected() {
        UIApplication.shared.windows.first?.rootViewController = UIHostingController(rootView: previews)
    }
    #endif
}
