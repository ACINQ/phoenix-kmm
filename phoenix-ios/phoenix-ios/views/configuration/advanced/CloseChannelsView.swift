import SwiftUI
import PhoenixShared

struct CloseChannelsView : View {
	
	var body: some View {
		
		MVIView({ $0.closeChannelsConfiguration() }) { model, postIntent in
			
			main(model, postIntent)
		}
		.padding(.top, 40)
		.padding([.leading, .trailing, .bottom])
		.navigationBarTitle("Close channels", displayMode: .inline)
	}
	
	@ViewBuilder func main(
		_ model: CloseChannelsConfiguration.Model,
		_ postIntent: @escaping (CloseChannelsConfiguration.Intent) -> Void
	) -> some View {
		
		if let model = model as? CloseChannelsConfiguration.ModelReady {
	
			if model.channelCount == 0 {
				EmptyWalletView()
			} else {
				StandardWalletView(model: model)
			}
	
		} else {
			
			LoadingWalletView()
		}
	}
}

fileprivate struct LoadingWalletView : View {
	
	var body: some View {
		
		VStack(alignment: .center) {
		
			ProgressView()
				.progressViewStyle(CircularProgressViewStyle())
				.padding(.bottom, 5)
			
			Text("Checking channel state...")
			
			Spacer()
		}
	}
}

fileprivate struct EmptyWalletView : View {
	
	var body: some View {
		
		VStack(alignment: .leading) {
			
			Text("You currently don't have any channels that can be closed.")
				.padding(.bottom, 20)
			
			Group {
				Text("Payment channels are automatically created when you receive payments. ") +
				Text("Use the ") +
				Text("Receive").bold() +
				Text(" screen to receive via the Lightning network.")
			}
			.padding(.bottom, 20)

			Text("You can also use the ") +
			Text("Payment Channels").bold() +
			Text(" screen to inspect the state of your channels.")

			Spacer()

			FooterView()
		}
	}
}

fileprivate struct StandardWalletView : View {
	
	let model: CloseChannelsConfiguration.ModelReady
	
	@State var bitcoinAddress: String = ""
	
	var body: some View {
		
		VStack(alignment: .leading) {
			
			let formattedSats = Utils.formatBitcoin(sat: model.sats, bitcoinUnit: .satoshi)
			
			if model.channelCount == 1 {
				Text(
					"You currenly have 1 Lightning channel" +
					" with a balance of \(formattedSats.string)."
				)
			} else {
				Text(
					"You currently have \(String(model.channelCount)) Lightning channels" +
					" with an aggragated balance of \(formattedSats.string)."
				)
			}
			
			Group {
				Text(
					"Funds can be sent to a Bitcoin wallet." +
					" Make sure the address is correct before sending."
				)
			}
			.padding(.top, 20)
			.padding(.bottom, 10)
			
			HStack {
				TextField("Bitcoin address)", text: $bitcoinAddress)
					.padding([.top, .bottom], 8)
					.padding([.leading, .trailing], 16)
			}
			.background(Capsule().stroke(Color(UIColor.separator)))
			.padding(.bottom, 10)
			
			Button {
				drainWallet()
			} label: {
				HStack {
					Image(systemName: "bitcoinsign.circle")
						.imageScale(.small)

					Text("Drain my wallet")
				}
			}
			.buttonStyle(ScaleButtonStyle())
			
			Spacer()
			
			FooterView()
		}
	}
	
	func drainWallet() -> Void {
		print("drainWallet()")
		
		// Todo...
	}
}

struct ScaleButtonStyle: ButtonStyle {

	var scaleAmount: CGFloat = 0.98
	
	@Environment(\.isEnabled) var isEnabled

	func makeBody(configuration: Self.Configuration) -> some View {
		return configuration.label
			.opacity(configuration.isPressed ? 0.65 : 1.0)
			.scaleEffect(configuration.isPressed ? scaleAmount : 1.0)
			.padding([.top, .bottom], 8)
			.padding([.leading, .trailing], 16)
			.cornerRadius(16)
			.background(
				Capsule().stroke(
					isEnabled ? Color.appHorizon : Color(UIColor.separator),
					lineWidth: 1.5
				)
			)
	}
}

fileprivate struct FooterView : View {
	
	var body: some View {
		
		// The "send to bitcoin address" functionality isn't available in eclair-kmp yet.
		// When added, and integrated into Send screen, the code below should be uncommented.
		// 
		Group {
			Text("Use this feature to transfer ") +
			Text("all").italic() +
			Text(" your funds to a Bitcoin address. ") // +
		//	Text("If you only want to send ") +
		//	Text("some").italic() +
		//	Text(" of your funds, then you can use the ") +
		//	Text("Send").bold().italic() +
		//	Text(" screen. Just scan/enter a Bitcoin address and Phoenix does the rest.")
		}
		.font(.footnote)
		.foregroundColor(.secondary)
	}
}

class CloseChannelsView_Previews: PreviewProvider {
	
	static let model_1 = CloseChannelsConfiguration.ModelLoading()
	static let model_2 = CloseChannelsConfiguration.ModelReady(channelCount: 0, sats: 0)
	static let model_3 = CloseChannelsConfiguration.ModelReady(channelCount: 1, sats: 500_000)
	static let model_4 = CloseChannelsConfiguration.ModelReady(channelCount: 3, sats: 1_500_000)
	
	static let mockModel = model_1
	
	static var previews: some View {
		
		NavigationView {
			CloseChannelsView()
		}
		.preferredColorScheme(.light)
		.previewDevice("iPhone 8")
		
//		NavigationView {
//			CloseChannelsView()
//		}
//		.preferredColorScheme(.dark)
//		.previewDevice("iPhone 11")
//
//		NavigationView {
//			CloseChannelsView()
//		}
//		.preferredColorScheme(.light)
//		.previewDevice("iPhone 8")
//		.environmentObject(CurrencyPrefs.mockEUR())
	}
}
