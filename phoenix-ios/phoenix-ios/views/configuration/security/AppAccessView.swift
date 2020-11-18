import Foundation
import Combine
import SwiftUI
import PhoenixShared

struct AppAccessView : View {
	
	var cancellables = Set<AnyCancellable>()
	
	@State var biometricStatus = AppSecurity.shared.biometricStatus()
	@State var shutupCompiler = false
	@State var biometricsEnabled = false
	
	// When the app returns from being in the background, the biometric status may have changed.
	// For example: .touchID_notEnrolled => .touchID_available
	//
	let pub = NotificationCenter.default.publisher(for: UIApplication.willEnterForegroundNotification)
	
	var body: some View {
		
		Form {
			
			Toggle(isOn: $biometricsEnabled) {
				
				switch biometricStatus {
					case .touchID_available:
						Text("Touch ID")
						
					case .touchID_notAvailable:
						Text("Touch ID") + Text(" (not available)").foregroundColor(.secondary)
					
					case .touchID_notEnrolled:
						Text("Touch ID") + Text(" (not enrolled)").foregroundColor(.secondary)
					
					case .faceID_available:
						Text("Face ID")
					
					case .faceID_notAvailable:
						Text("Face ID") + Text(" (not available)").foregroundColor(.secondary)
					
					case .faceID_notEnrolled:
						Text("Face ID") + Text(" (not enrolled)").foregroundColor(.secondary)
					
					default:
						Text("Biometrics") + Text(" (not available)").foregroundColor(.secondary)
				}
			}.disabled(!biometricStatus.isAvailable())
			
			
		}
		.onReceive(pub, perform: { _ in
			
			self.biometricStatus = AppSecurity.shared.biometricStatus()
		})
	}
}

