import SwiftUI
import PhoenixShared

struct ContentView: View {

    static func UIKitAppearance() {
        let appearance = UINavigationBarAppearance()
        appearance.configureWithOpaqueBackground()
        
        UINavigationBar.appearance().scrollEdgeAppearance = appearance
        UINavigationBar.appearance().compactAppearance = appearance
        UINavigationBar.appearance().standardAppearance = appearance
    }
	
	@State var isUnlocked = false
	@State var enabledSecurity = EnabledSecurity()

	var body: some View {
	
		if !enabledSecurity.isEmpty {
			LockView(enabledSecurity: enabledSecurity)
			
		} else if isUnlocked {
			primaryView()
			
		} else {
			loadingView()
			.onAppear {
				onAppLaunch()
			}
		}
	}
	
	@ViewBuilder func primaryView() -> some View {
		
		appView(MVIView({ $0.content() }) { model, intent in
			
			NavigationView {
				
				if model is Content.ModelIsInitialized {
					HomeView()
				} else if model is Content.ModelNeedInitialization {
					InitializationView()
				} else {
					loadingView()
				}
				
			} // </NavigationView>
		})
    }
	
	@ViewBuilder func loadingView() -> some View {
		
		VStack {
			Image(systemName: "arrow.triangle.2.circlepath")
				.imageScale(.large)
				.rotationEffect(Angle(degrees: 360.0))
				.animation(.easeIn)
		}
		.edgesIgnoringSafeArea(.all)
		.navigationBarTitle("", displayMode: .inline)
		.navigationBarHidden(true)
	}
	
	private func onAppLaunch() -> Void {
		print("onAppLaunch()")
		
		AppSecurity.shared.tryUnlockWithKeychain {(databaseKey: Data?, enabledSecurity: EnabledSecurity) in
			
			if databaseKey == nil && enabledSecurity.isEmpty {
				return handleFirstAppLaunch()
			}
			
			if databaseKey != nil {
				self.isUnlocked = true
			} else {
				self.enabledSecurity = enabledSecurity
			}
		}
	}
	
	private func handleFirstAppLaunch() -> Void {
		print("handleFirstAppLaunch()")
		
		let databaseKey = AppSecurity.shared.generateDatabaseKey()
		AppSecurity.shared.addKeychainEntry(databaseKey: databaseKey) {(error: Error?) in
			
			self.onAppLaunch()
		}
	}
}


class ContentView_Previews: PreviewProvider {
    static let mockModel = Content.ModelNeedInitialization()

    static var previews: some View {
        mockView(ContentView())
                .previewDevice("iPhone 11")
    }

    #if DEBUG
    @objc class func injected() {
        UIApplication.shared.windows.first?.rootViewController = UIHostingController(rootView: previews)
    }
    #endif
}
