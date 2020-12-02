import SwiftUI
import PhoenixShared

struct RestoreWalletView: View {

	@State var acceptedWarning = false
	@State var mnemonics = [String]()
	
    var body: some View {
        MVIView({ $0.restoreWallet() },
			onModel: { change in
			
				if change.newModel is RestoreWallet.ModelValidMnemonics {
					finishAndRestoreWallet()
				}
				
			}) { model, postIntent in
			
			main(model: model, postIntent: postIntent)
				.padding(.top, keyWindow?.safeAreaInsets.bottom)
				.padding(.bottom, keyWindow?.safeAreaInsets.top)
				.padding([.leading, .trailing], 10)
				.edgesIgnoringSafeArea([.bottom, .leading, .trailing])
		}
		.navigationBarTitle("Restore my wallet", displayMode: .inline)
    }

	@ViewBuilder func main(
		model: RestoreWallet.Model,
		postIntent: @escaping (RestoreWallet.Intent) -> Void
	) -> some View {
		
		if !acceptedWarning {
			WarningView(acceptedWarning: $acceptedWarning)
				.zIndex(1)
				.transition(.move(edge: .bottom))
		} else {
			RestoreView(model: model, postIntent: postIntent, mnemonics: $mnemonics)
				.zIndex(0)
		}
	}
	
	func finishAndRestoreWallet() -> Void {
		print("finishAndRestoreWallet()")
		
		let _mnemonics = mnemonics // snapshot
		
		AppSecurity.shared.addKeychainEntry(mnemonics: _mnemonics) { (error: Error?) in
			if error == nil {
				PhoenixApplicationDelegate.get().importWallet(mnemonics: _mnemonics)
			}
		}
	}
}

struct WarningView: View {
	
	@Binding var acceptedWarning: Bool
	@State var iUnderstand: Bool = false
	
	var body: some View {
		VStack {
			
			Text(
				"Do not import a seed that was NOT created by this application.\n\n" +
				"Also, make sure that you don't have another Phoenix wallet running with the same seed."
			)
			.font(.title3)
			.padding(.top, 20)

			Toggle(isOn: $iUnderstand) {
				Text("I understand.").font(.title3)
			}
			.padding([.top, .bottom], 16)
			.padding([.leading, .trailing], 88)

			Button {
				withAnimation {
					acceptedWarning = true
				}
				
			} label: {
				HStack {
					Image("ic_arrow_next")
						.resizable()
						.frame(width: 16, height: 16)
					Text("Next")
						.font(.title2)
				}
			}
			.disabled(!iUnderstand)
			.buttonStyle(PlainButtonStyle())
			.padding([.top, .bottom], 8)
			.padding([.leading, .trailing], 16)
			.background(Color(UIColor.systemFill))
			.cornerRadius(16)
			.overlay(
				RoundedRectangle(cornerRadius: 16)
					.stroke(Color.appHorizon, lineWidth: 2)
			)
			
			Spacer()
			
		} // </VStack>
		.background(Color(UIColor.systemBackground))
	}
}

struct RestoreView: View {
	
	let model: RestoreWallet.Model
	let postIntent: (RestoreWallet.Intent) -> Void

	@Binding var mnemonics: [String]
	@State var wordInput: String = ""
	
	var body: some View {
	
		VStack {
			
			Text("Your wallet's seed is a list of 12 english words.")
				.font(.title3)
				.padding(.top, 20)

			TextField("Enter keywords from your seed", text: $wordInput)
				.onChange(of: wordInput) { input in
					onInput(input)
				}
				
				.padding()
				.disableAutocorrection(true)
				.disabled(mnemonics.count == 12)

			// Autocomplete suggestions for mnemonics
			ScrollView(.horizontal) {
				LazyHStack {
					if let model = model as? RestoreWallet.ModelFilteredWordlist,
					   mnemonics.count < 12
					{
						ForEach(model.words, id: \.self) { word in
							
							Text(word)
								.underline()
								.frame(maxWidth: .infinity) // Hack to be able to tap ...
								.onTapGesture {
									selectMnemonic(word)
								}
						}
					}
				}
			}
			.frame(height: 32)
			.padding([.leading, .trailing])

			Divider()
				.padding()

			// List of mnemonics:
			// #1   #7
			// #2   #8
			// ...  ...
			HStack {
				
				VStack {
					ForEach(0..<6, id: \.self) { idx in
						Text("#\(idx + 1) ")
							.font(.headline)
							.foregroundColor(.secondary)
							.padding(.bottom, 2)
					}
				}
				.padding(.trailing, 2)
				
				VStack(alignment: .leading) {
					ForEach(0..<6, id: \.self) { idx in
						HStack {
							Text(mnemonic(idx))
								.font(.headline)
								.frame(maxWidth: .infinity, alignment: .leading)
								.padding(.bottom, 2)
							
							Button {
								mnemonics.removeSubrange(idx..<mnemonics.count)
							} label: {
								Image("ic_cross")
									.resizable()
									.frame(width: 24, height: 24)
									.foregroundColor(Color.appRed)
							}
							.isHidden(mnemonics.count <= idx)
						}
					}
				}
				.padding(.trailing, 4) // boost spacing a wee bit
				
				VStack {
					ForEach(6..<12, id: \.self) { idx in
						Text("#\(idx + 1) ")
							.font(.headline)
							.foregroundColor(.secondary)
							.padding(.bottom, 2)
					}
				}
				.padding(.trailing, 2)
				
				VStack(alignment: .leading) {
					ForEach(6..<12, id: \.self) { idx in
						HStack {
							Text(mnemonic(idx))
								.font(.headline)
								.frame(maxWidth: .infinity, alignment: .leading)
								.padding(.bottom, 2)
							
							Button {
								mnemonics.removeSubrange(idx..<mnemonics.count)
							} label: {
								Image("ic_cross")
									.resizable()
									.frame(width: 24, height: 24)
									.foregroundColor(Color.appRed)
							}
							.isHidden(mnemonics.count <= idx)
						}
					}
				}
				
			} // </HStack>
			.padding([.leading, .trailing], 16)

			if model is RestoreWallet.ModelInvalidMnemonics {
				Text(
					"This seed is invalid and cannot be imported.\n\n" +
					"Please try again"
				)
				.padding()
				.foregroundColor(Color.red)
			}

			Spacer()

			Button {
				postIntent(RestoreWallet.IntentValidate(mnemonics: self.mnemonics))
			} label: {
				HStack {
					Image(systemName: "checkmark.circle")
						.imageScale(.small)

					Text("Import")
				}
				.font(.title2)
			}
			.disabled(mnemonics.count != 12)
			.buttonStyle(PlainButtonStyle())
			.padding([.top, .bottom], 8)
			.padding([.leading, .trailing], 16)
			.background(Color(UIColor.systemFill))
			.cornerRadius(16)
			.overlay(
				RoundedRectangle(cornerRadius: 16)
					.stroke(Color.appHorizon, lineWidth: 2)
			)
			.padding(.bottom, 20)
		}
		.frame(maxHeight: .infinity)
	}
	
	func mnemonic(_ idx: Int) -> String {
		return (mnemonics.count > idx) ? mnemonics[idx] : " "
	}
	
	func onInput(_ input: String) -> Void {
		
		// When the user hits space, we auto-accept the first mnemonic in the autocomplete list
		if input.hasSuffix(" "),
		   let model = model as? RestoreWallet.ModelFilteredWordlist,
		   let acceptedWord = model.words.first
		{
			selectMnemonic(acceptedWord)
		}
		else {
			postIntent(RestoreWallet.IntentFilterWordList(predicate: input))
		}
	}
	
	func selectMnemonic(_ word: String) -> Void {
		mnemonics.append(word)
		wordInput = ""
	}
}

class RestoreWalletView_Previews: PreviewProvider {
//    static let mockModel = RestoreWallet.ModelReady()
    static let mockModel = RestoreWallet.ModelInvalidMnemonics()
//    static let mockModel = RestoreWallet.ModelFilteredWordlist(words: ["abc", "abandon", "ability", "able", "about", "above", "absent", "absorb", "abstract", "absurd", "abuse", "access"])
//    static let mockModel = RestoreWallet.ModelFilteredWordlist(words: ["abandon", "ability", "able", "about", "above", "absent", "absorb", "abstract", "absurd", "abuse", "access", "accident", "account"])
//    static let mockModel = RestoreWallet.ModelFilteredWordlist(words: ["abc", "abandon", "ability", "able", "about", "above", "absent", "absorb", "abstract", "absurd", "abuse", "access", "accident", "account", "accuse", "achieve", "acid"])

    static var previews: some View {
        mockView(RestoreWalletView())
                .previewDevice("iPhone 11")
    }

    #if DEBUG
    @objc class func injected() {
        UIApplication.shared.windows.first?.rootViewController = UIHostingController(rootView: previews)
    }
    #endif
}
