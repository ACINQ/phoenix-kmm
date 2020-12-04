import SwiftUI
import PhoenixShared

struct InitializationView: View {
	
	@State var mnemonics: [String]? = nil
	
	var body: some View {
		MVIView({ $0.initialization() }, onModel: { change in
			
			if let model = change.newModel as? Initialization.ModelGeneratedMnemonics {
				mnemonics = model.mnemonics
			}
			
		}) { model, postIntent in
			
			main(model, postIntent)
		}
	}
	
	@ViewBuilder func main(
		_ model: Initialization.Model,
		_ postIntent: @escaping (Initialization.Intent) -> Void
	) -> some View {
		
		ZStack {
			
			// ZStack: layer 0 (background)
			// Position the settings icon in top-right corner.
			HStack{
				Spacer()
				VStack {
					NavigationLink(destination: ConfigurationView()) {
						Image(systemName: "gearshape")
						.imageScale(.large)
					}
					.buttonStyle(PlainButtonStyle())
					.padding([.top, .bottom], 8)
					.padding([.leading, .trailing], 8)
					.background(Color.white)
					.cornerRadius(16)
					.overlay(
						RoundedRectangle(cornerRadius: 16)
						.stroke(Color.appHorizon, lineWidth: 2)
					)
					Spacer()
				}
				.padding(.all, 16)
			}
			
			// ZStack: layer 1 (foreground)
			VStack {
			
				Image("logo")
					.resizable()
					.frame(width: 96, height: 96)
				//	.overlay(Circle().stroke(Color.secondary, lineWidth: 1.5))
				//	.clipShape(Circle())
					.padding([.top, .bottom], 0)

				Text("Phoenix")
					.font(Font.title2)
					.padding(.top, -10)
					.padding(.bottom, 80)

				Button {
					createMnemonics(postIntent)
				} label: {
					HStack {
						Image(systemName: "flame")
							.imageScale(.small)

						Text("Create new wallet")
					}
					.font(.title2)
					.foregroundColor(Color(red: 0.99, green: 0.99, blue: 1.0))
				}
				.buttonStyle(PlainButtonStyle())
				.padding([.top, .bottom], 8)
				.padding([.leading, .trailing], 16)
				.background(Color.appHorizon)
				.cornerRadius(16)
				.padding(.bottom, 40)

				NavigationLink(destination: RestoreWalletView()) {
					HStack {
						Image(systemName: "arrow.down.circle")
							.imageScale(.small)

						Text("Restore my wallet")
					}
					.font(.title2)
				}
				.buttonStyle(PlainButtonStyle())
				.padding([.top, .bottom], 8)
				.padding([.leading, .trailing], 16)
				.background(Color(UIColor.systemFill))
				.cornerRadius(16)
				.overlay(
					RoundedRectangle(cornerRadius: 16)
					.stroke(Color.appHorizon, lineWidth: 2)
				)
				.padding([.top, .bottom], 0)

			} // </VStack>
			.padding(.top, keyWindow?.safeAreaInsets.top)
			.padding(.bottom, keyWindow?.safeAreaInsets.bottom)
			.frame(maxWidth: .infinity, maxHeight: .infinity)
			.offset(x: 0, y: -40) // move center upwards; focus is buttons, not logo
			.edgesIgnoringSafeArea(.all)
			.navigationBarTitle("", displayMode: .inline)
			.navigationBarHidden(true)
				
		} // </ZStack>
		.onChange(of: mnemonics) { _ in
			createWallet(postIntent)
		}
	}
	
	func createMnemonics(
		_ postIntent: @escaping (Initialization.Intent) -> Void
	) -> Void {
		
		let swiftEntropy = AppSecurity.shared.generateEntropy()
		let kotlinEntropy = KotlinByteArray.fromSwiftData(swiftEntropy)
		
		let intent = Initialization.IntentGenerateMnemonics(seed: kotlinEntropy)
		postIntent(intent)
	}
	
	func createWallet(
		_ postIntent: @escaping (Initialization.Intent) -> Void
	) -> Void {
		
		guard let mnemonics = mnemonics else {
			return
		}
		
		AppSecurity.shared.addKeychainEntry(mnemonics: mnemonics) { (error: Error?) in
			if error == nil {
				PhoenixApplicationDelegate.get().loadWallet(mnemonics: mnemonics)
			}
		}
	}
}

class InitView_Previews : PreviewProvider {
    static let mockModel = Initialization.ModelReady()

    static var previews: some View {
        mockView(InitializationView())
                .previewDevice("iPhone 11")
    }

    #if DEBUG
    @objc class func injected() {
        UIApplication.shared.windows.first?.rootViewController = UIHostingController(rootView: previews)
    }
    #endif
}
