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

struct HomeView : View {

    @State var lastPayment: PhoenixShared.Eclair_kmpWalletPayment? = nil
    @State var showConnections = false

    @State var selectedPayment: PhoenixShared.Eclair_kmpWalletPayment? = nil
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs

    var body: some View {
        MVIView({ $0.home() },
			background: true,
			onModel: { change in
				if lastPayment != change.newModel.lastPayment {
                    lastPayment = change.newModel.lastPayment
					selectedPayment = lastPayment
				}
			}
        ) { model, postIntent in
            
			mainView(model, postIntent)
				.navigationBarTitle("", displayMode: .inline)
				.navigationBarHidden(true)
    	}
	}
	
	@ViewBuilder func mainView(
		_ model: Home.Model,
		_ postIntent: @escaping (Home.Intent) -> Void
	) -> some View {
		
		ZStack {
			
			Image("testnet_bg")
				.resizable(resizingMode: .tile)
			
			VStack {
				
				// === Top-row buttons ===
				HStack {
					ConnectionStatusButton()
					Spacer()
					FaqButton()
				}
				.padding()

				// === Total Balance ====
				HStack(alignment: .bottom) {
					
					let amount = Utils.format(currencyPrefs, sat: model.balanceSat)
					Text(amount.digits)
						.font(.largeTitle)
						.onTapGesture { toggleCurrencyType() }
					
					Text(amount.type)
						.font(.title2)
						.padding(.bottom, 4)
						.onTapGesture { toggleCurrencyType() }
					
				} // </HStack>

				// === Payments List ====
				ScrollView {
					LazyVStack {
						ForEach(model.payments.indices, id: \.self) { index in
							Button {
								selectedPayment = model.payments[index]
							} label: {
								PaymentCell(payment: model.payments[index])
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
					}
				}

				BottomBar(model: model)
			
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
}

struct PaymentCell : View {

	let payment: PhoenixShared.Eclair_kmpWalletPayment
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs

	var body: some View {
		HStack {
			switch (payment.status()) {
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
				Text(payment.timestamp().formatDateMS())
					.font(.caption)
					.foregroundColor(.secondary)
			}
			.frame(maxWidth: .infinity, alignment: .leading)
			.padding([.leading, .trailing], 6)
			
			if payment.status() != .failure {
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
		let tor = connectionsMonitor.connections.tor
		let status = connectionsMonitor.connections.global
		let connectedWithTor = status == Eclair_kmpConnection.established && tor != nil
		let connectedWithoutTor = status == Eclair_kmpConnection.established && tor == nil

		Group {
			if connectedWithTor {
				NavigationLink(destination: TorConfigurationView()) {
					HStack {
						Image(systemName: "checkmark.shield")
								.frame(width: 16, height: 16)
								.foregroundColor(.appGreen)
						Text("Tor enabled")
								.font(.caption2)
					}
				}
			} else {
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
				.opacity((dimStatus && !connectedWithTor) ? 0.2 : 1.0)
				.isHidden(connectedWithoutTor)
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
		
		popoverState.dismissable.send(true)
		popoverState.displayContent.send(
			ConnectionsPopover().anyView
		)
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
		tor      : .established,
		peer     : .established,
		electrum : .closed
	)
	
	static let mockModel = Home.Model(
		balanceSat: 123500,
		payments: [
		],
		lastPayment: nil
	)

	static var previews: some View {
        mockView(HomeView())
			.preferredColorScheme(.dark)
			.previewDevice("iPhone 8")
			.environmentObject(CurrencyPrefs.mockEUR())
		
		mockView(HomeView())
			.preferredColorScheme(.light)
			.previewDevice("iPhone 8")
			.environmentObject(CurrencyPrefs.mockEUR())
		
	//	mockView(HomeView())
	//		.preferredColorScheme(.light)
	//		.previewDevice("iPhone 11")
	//		.environmentObject(CurrencyPrefs.mockEUR())
	}

    #if DEBUG
    @objc class func injected() {
        UIApplication.shared.windows.first?.rootViewController = UIHostingController(rootView: previews)
    }
    #endif
}
