import SwiftUI

struct CloseChannelsView : View {
	
	@State var hasZeroChannels: Bool
	
	init() {
		_hasZeroChannels = State(initialValue: false)
	}
	
#if DEBUG
	// For PreviewProvider(s)
	init(hasZeroChannels: Bool) {
		_hasZeroChannels = State(initialValue: hasZeroChannels)
	}
#endif
	
	var body: some View {
		
		Group {
			if hasZeroChannels {
				EmptyWalletView()
			} else {
				StandardWalletView()
			}
		}
		.padding(.top, 40)
		.padding([.leading, .trailing, .bottom])
		.navigationBarTitle("Close channels", displayMode: .inline)
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
	
	@State var channelCount: Int
	@State var numChannels: Int
	@State var msat: Int64 = 199_800
	
	@State var bitcoinAddress: String = ""
	
	init() {
		_channelCount = State(initialValue: 0)
		_numChannels = State(initialValue: 0)
	}
	
#if DEBUG
	// For PreviewProvider(s)
	init(channelCount: Int, numChannels: Int) {
		_channelCount = State(initialValue: channelCount)
		_numChannels = State(initialValue: numChannels)
	}
#endif
	
	var body: some View {
		
		VStack(alignment: .leading) {
			
			let formattedSats = Utils.formatBitcoin(msat: msat, bitcoinUnit: .satoshi)
			
			if channelCount == 1 {
				Text(
					"You currenly have 1 Lightning channel" +
					" with a balance of \(formattedSats.string)."
				)
			} else {
				Text(
					"You currently have \(String(numChannels)) Lightning channels" +
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
				// Todo...
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
		
		Group {
			Text("Use this feature to transfer ") +
			Text("all").italic() +
			Text(" your funds to a Bitcoin address. ") +
			Text("If you only want to send ") +
			Text("some").italic() +
			Text(" of your funds, then you can use the ") +
			Text("Send").bold().italic() +
			Text(" screen. Just scan/enter a Bitcoin address and Phoenix does the rest.")
		}
		.font(.footnote)
		.foregroundColor(.secondary)
	}
}

class CloseChannelsView_Previews: PreviewProvider {
	
	static var previews: some View {
		
		NavigationView {
			CloseChannelsView(hasZeroChannels: true)
		}
		.preferredColorScheme(.light)
		.previewDevice("iPhone 8")
		
		NavigationView {
			CloseChannelsView(hasZeroChannels: true)
		}
		.preferredColorScheme(.dark)
		.previewDevice("iPhone 11")
		
		NavigationView {
			CloseChannelsView(hasZeroChannels: false)
		}
		.preferredColorScheme(.light)
		.previewDevice("iPhone 8")
		.environmentObject(CurrencyPrefs.mockEUR())
	}
}
