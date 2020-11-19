import SwiftUI


struct LockView : View {
	
	let enabledSecurity: EnabledSecurity
	
	@State var isTouchID = true
	@State var isFaceID = false
	@State var errorMsg: String? = nil
	
	// When the app returns from being in the background, the biometric status may have changed.
	// For example: .touchID_notEnrolled => .touchID_available
	//
	let pub = NotificationCenter.default.publisher(for: UIApplication.willEnterForegroundNotification)
	
	var body: some View {
		
		VStack {
			
			Image("logo")
			.resizable()
			.frame(width: 96, height: 96)
			.padding([.top, .bottom], 0)

			Text("Phoenix")
			.font(Font.title2)
			.padding(.top, -10)
			.padding(.bottom, 40)
			
			if isTouchID || isFaceID {
				
				Button {
					tryBiometricsLogin()
				} label: {
					if isTouchID {
						Image(systemName: "touchid")
							.resizable()
							.frame(width: 32, height: 32)
					} else {
						Image(systemName: "faceid")
							.resizable()
							.frame(width: 32, height: 32)
					}
				}
				
			} else {
				
				Button {
					tryBiometricsLogin()
				} label: {
					Image(systemName: "ant")
						.resizable()
						.frame(width: 32, height: 32)
				}
				.disabled(true)
			}
		}
		.onAppear {
			onAppLaunch()
		}
		.onReceive(pub, perform: { _ in
			updateBiometricsStatus(AppSecurity.shared.biometricStatus())
		})
	}
	
	func onAppLaunch() -> Void {
		
		let status = AppSecurity.shared.biometricStatus()
		updateBiometricsStatus(status)
		if status != .notAvailable {
			tryBiometricsLogin()
		}
	}
	
	func updateBiometricsStatus(_ status: BiometricStatus) -> Void {
		
		switch status {
			case .touchID_available    : fallthrough
			case .touchID_notEnrolled  : fallthrough
			case .touchID_notAvailable : isTouchID = true
			default                    : isTouchID = false
		}
		
		switch status {
			case .touchID_available    : errorMsg = nil
			case .touchID_notEnrolled  : fallthrough
			case .touchID_notAvailable : errorMsg = NSLocalizedString(
				"Please enable Touch ID", comment: "Error message in LockView"
			)
			
			case .faceID_available    : errorMsg = nil
			case .faceID_notEnrolled  : fallthrough
			case .faceID_notAvailable : errorMsg = NSLocalizedString(
				"Please enabled Face ID", comment: "Error message in LockView"
			)
			
			default: errorMsg = NSLocalizedString(
				"Unknown biometrics", comment: "Error message in LockView"
			)
		}
	}
	
	func tryBiometricsLogin() -> Void {
		
		AppSecurity.shared.tryUnlockWithBiometrics {(result: Result<Data?, Error>) in
			
			switch result {
				case .success(let databaseKey):
					print("databaseKey: \(databaseKey?.hexEncodedString() ?? "nil")")
				case .failure(let error):
					print("error: \(error)")
			}
		}
	}
}
