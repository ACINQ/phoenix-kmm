import SwiftUI
import PhoenixShared
import Network
import os.log

#if DEBUG && false
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "HomeView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

struct HomeView : MVIView {

	@StateObject var mvi = MVIState({ $0.home() })

	@Environment(\.controllerFactory) var factoryEnv
	var factory: ControllerFactory { return factoryEnv }

	@State var lastPayment: PhoenixShared.Eclair_kmpWalletPayment? = nil
	@State var showConnections = false

	@State var selectedPayment: PhoenixShared.Eclair_kmpWalletPayment? = nil
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	
	let lastIncomingPaymentPublisher = KotlinPassthroughSubject<Eclair_kmpWalletPayment>(
		AppDelegate.get().business.paymentsManager.lastIncomingPayment
	)

	@ViewBuilder
	var view: some View {

		main
			.navigationBarTitle("", displayMode: .inline)
			.navigationBarHidden(true)
			.onReceive(lastIncomingPaymentPublisher) { (payment: Eclair_kmpWalletPayment) in
				
				if lastPayment != payment {
					lastPayment = payment
					selectedPayment = payment
				}
			}
	}
	
	@ViewBuilder
	var main: some View {
		
		ZStack {
			
			if AppDelegate.get().business.chain.isTestnet() {
				Image("testnet_bg")
					.resizable(resizingMode: .tile)
			}
			
			VStack(alignment: HorizontalAlignment.center, spacing: 0) {
				
				// === Top-row buttons ===
				HStack {
					ConnectionStatusButton()
					Spacer()
					FaqButton()
				}
				.padding()

				// === Total Balance ====
				VStack(alignment: HorizontalAlignment.center, spacing: 0) {
				
					HStack(alignment: VerticalAlignment.bottom) {
						
						let amount = Utils.format(currencyPrefs, msat: mvi.model.balance.msat)
						Text(amount.digits)
							.font(.largeTitle)
							.onTapGesture { toggleCurrencyType() }
						
						Text(amount.type)
							.font(.title2)
							.padding(.bottom, 4)
							.onTapGesture { toggleCurrencyType() }
						
					} // </HStack>
					
					if let incoming = incomingAmount() {
						
						HStack(alignment: VerticalAlignment.center, spacing: 0) {
							
							Image(systemName: "link")
								.padding(.trailing, 2)
							
							Text("+\(incoming.string) incoming".lowercased())
								.onTapGesture { toggleCurrencyType() }
						}
						.font(.callout)
						.foregroundColor(.secondary)
						.padding(.top, 7)
						.padding(.bottom, 2)
					}
				}
				.padding([.top, .leading, .trailing])
				.padding(.bottom, 25)
				.background(
					VStack {
						Spacer()
						Line()
							.stroke(Color.appHorizon, style: StrokeStyle(lineWidth: 4, lineCap: .round))
							.frame(height: 4)
					}
				)
				.padding(.bottom)

				// === Payments List ====
				ScrollView {
					LazyVStack {
						ForEach(mvi.model.payments.indices, id: \.self) { index in
							Button {
								selectedPayment = mvi.model.payments[index]
							} label: {
								PaymentCell(payment: mvi.model.payments[index])
							}
						}
					}
					.sheet(isPresented: .constant(selectedPayment != nil)) {
						selectedPayment = nil
					} content: {
						PaymentView(
							payment: selectedPayment!,
							close: { selectedPayment = nil }
						)
						.modifier(GlobalEnvironment()) // SwiftUI bug (prevent crash)
					}
				}

				BottomBar(model: mvi.model)
			
			} // </VStack>
			.padding(.top, keyWindow?.safeAreaInsets.top ?? 0) // bottom handled in BottomBar
			.padding(.top)
		
		} // </ZStack>
		.frame(maxHeight: .infinity)
		.background(Color.primaryBackground)
		.edgesIgnoringSafeArea(.all)
	}
	
	func toggleCurrencyType() -> Void {
		currencyPrefs.toggleCurrencyType()
	}
	
	func incomingAmount() -> FormattedAmount? {
		
		if let incomingMsat = mvi.model.incomingBalance, incomingMsat.toLong() > 0 {
			return Utils.format(currencyPrefs, msat: incomingMsat)
		}
		return nil
	}
}

struct PaymentCell : View {

	let payment: PhoenixShared.Eclair_kmpWalletPayment
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs

	var body: some View {
		HStack {
			switch payment.state() {
			case .success:
				Image("payment_holder_def_success")
					.padding(4)
					.background(
						RoundedRectangle(cornerRadius: .infinity)
							.fill(Color.appHorizon)
					)
			case .pending:
				Image("payment_holder_def_pending")
					.padding(4)
			case .failure:
				Image("payment_holder_def_failed")
					.padding(4)
			default: EmptyView()
			}
			
			VStack(alignment: .leading) {
				Text(payment.desc() ?? NSLocalizedString("No description", comment: "placeholder text"))
					.lineLimit(1)
					.truncationMode(.tail)
					.foregroundColor(.primaryForeground)
				
				let timestamp = payment.timestamp()
				let timestampStr = timestamp > 0
					? timestamp.formatDateMS()
					: NSLocalizedString("pending", comment: "timestamp string for pending transaction")
				
				Text(timestampStr)
					.font(.caption)
					.foregroundColor(.secondary)
			}
			.frame(maxWidth: .infinity, alignment: .leading)
			.padding([.leading, .trailing], 6)
			
			if payment.state() != .failure {
				HStack(spacing: 0) {
					
					let amount = Utils.format(currencyPrefs, msat: payment.amountMsat())
					let isNegative = payment.amountMsat() < 0
					
					Text(isNegative ? "" : "+")
						.foregroundColor(isNegative ? .appRed : .appGreen)
						.padding(.trailing, 1)
					
					Text(amount.digits)
						.foregroundColor(isNegative ? .appRed : .appGreen)
						
					Text(" " + amount.type)
						.font(.caption)
						.foregroundColor(.gray)
				}
			}
		}
		.padding([.top, .bottom], 14)
		.padding([.leading, .trailing], 12)
	}
}

struct ConnectionStatusButton : View {
	
	@State var dimStatus = false
	@StateObject var connectionsMonitor = ObservableConnectionsMonitor()
	
	@Environment(\.popoverState) var popoverState: PopoverState

	var body: some View {
		let status = connectionsMonitor.connections.global
		
		Group {
			Button {
				showConnectionsPopover()
			} label: {
				HStack {
					Image("ic_connection_lost")
						.resizable()
						.frame(width: 16, height: 16)
					Text(status.localizedText())
						.font(.caption2)
				}
			}
			.buttonStyle(PlainButtonStyle())
			.padding([.leading, .top, .bottom], 4)
			.padding([.trailing], 6)
			.background(Color.buttonFill)
			.cornerRadius(10)
			.overlay(
				RoundedRectangle(cornerRadius: 10)
					.stroke(Color.gray, lineWidth: 1)
			)
			.opacity(dimStatus ? 0.2 : 1.0)
			.isHidden(status == Eclair_kmpConnection.established)
		}
		.onAppear {
			DispatchQueue.main.async {
				withAnimation(Animation.linear(duration: 1.0).repeatForever()) {
					self.dimStatus.toggle()
				}
			}
		}
	}
	
	func showConnectionsPopover() -> Void {
		log.trace("(ConnectionStatusButton) showConnectionsPopover()")
		
		popoverState.display.send(PopoverItem(
		
			ConnectionsPopover().anyView,
			dismissable: true
		))
	}
}

struct FaqButton: View {
	
	@Environment(\.openURL) var openURL
	
	var body: some View {
		
		Button {
			openURL(URL(string: "https://phoenix.acinq.co/faq")!)
		} label: {
			HStack {
				Image(systemName: "questionmark.circle")
					.renderingMode(.template)
					.resizable()
					.aspectRatio(contentMode: .fit)
					.frame(width: 16, height: 16)
				Text("FAQ")
					.font(.caption2)
			}
		}
		.buttonStyle(PlainButtonStyle())
		.padding([.top, .bottom], 4)
		.padding([.leading, .trailing], 6)
		.background(Color.buttonFill)
		.cornerRadius(10)
		.overlay(
			RoundedRectangle(cornerRadius: 10)
				.stroke(Color(UIColor.systemGray), lineWidth: 1)
		)
	}
}

struct BottomBar: View {

	let model: Home.Model

	@Environment(\.colorScheme) var colorScheme
	
	@State var isShowingScan: Bool = false

	var body: some View {
		
		HStack {

			NavigationLink(
				destination: ConfigurationView()
			) {
				Image("ic_settings")
					.resizable()
					.frame(width: 22, height: 22)
			}
			.padding()
			.padding(.leading, 8)

			Divider().frame(height: 40)
			Spacer()
			
			NavigationLink(
				destination: ReceiveView()
			) {
				HStack {
					Image("ic_receive")
						.resizable()
						.frame(width: 22, height: 22)
					Text("Receive")
						.foregroundColor(.primaryForeground)
				}
			}

			Spacer()
			Divider().frame(height: 40)
			Spacer()

			NavigationLink(
				destination: ScanView(isShowing: $isShowingScan),
				isActive: $isShowingScan
			) {
				HStack {
					Image("ic_scan")
						.resizable()
						.frame(width: 22, height: 22)
					Text("Send")
						.foregroundColor(.primaryForeground)
				}
			}

			Spacer()
		}
		.padding(.top, 10)
		.padding(.bottom, keyWindow?.safeAreaInsets.bottom)
		.background(colorScheme == .dark ? Color(UIColor.secondarySystemBackground) : Color.white)
		.cornerRadius(15, corners: [.topLeft, .topRight])
	}
}

// MARK: -

class HomeView_Previews: PreviewProvider {
	
	static let connections = Connections(
		internet : .established,
		peer     : .established,
		electrum : .closed
	)

	static var previews: some View {

		HomeView().mock(Home.Model(
			balance: Eclair_kmpMilliSatoshi(msat: 123500),
			incomingBalance: Eclair_kmpMilliSatoshi(msat: 0),
			payments: []
		))
		.preferredColorScheme(.dark)
		.previewDevice("iPhone 8")
		.environmentObject(CurrencyPrefs.mockEUR())
		
		HomeView().mock(Home.Model(
			balance: Eclair_kmpMilliSatoshi(msat: 1000000),
			incomingBalance: Eclair_kmpMilliSatoshi(msat: 12000000),
			payments: []
		))
		.preferredColorScheme(.light)
		.previewDevice("iPhone 8")
		.environmentObject(CurrencyPrefs.mockEUR())
	}
}
