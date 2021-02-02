import SwiftUI
import PhoenixShared

struct TorConfigurationView: View {

    @State var isTorEnabled = Prefs.shared.isTorEnabled

    var body: some View {
        MVIView({ $0.torConfiguration() }) { model, postIntent in
            Form {
                Section(header: TorFormHeader(), content: {}).textCase(nil)

                Toggle(isOn: $isTorEnabled.animation()) {
                    if isTorEnabled {
                        Text("Tor is enabled")
                    } else {
                        Text("Tor is disabled")
                    }
                }.onChange(of: isTorEnabled) { newValue in
                    self.toggleTor(newValue)
                }

                if let info = model.info {
                    VStack {
                        HStack {
                            Text("Tor version: \(info.version)")
                            Spacer()
                        }
                        HStack {
                            Text("Network: \(info.networkLiveness)")
                            Spacer()
                        }
                    }
                            .padding(.top, 10)
                            .font(.caption)
                }
            }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .edgesIgnoringSafeArea(.bottom)
                    .navigationBarTitle("Tor Settings", displayMode: .inline)
        }
    }

    struct TorFormHeader: View {
        var body: some View {
            Text(
                    "You can improve your privacy by only using Tor when connecting to an Electrum server or" +
                            " to your Lightning peer. This will slightly slow down your transactions."
            )
                    .font(.body)
                    .foregroundColor(Color.primary)
                    .padding(.top, 10)
        }
    }

    func toggleTor(_ isEnabled: Bool) {
        Prefs.shared.isTorEnabled = isEnabled
    }
}

class TorConfigurationView_Previews: PreviewProvider {

    static var previews: some View {
        TorConfigurationView()
                .previewDevice("iPhone 11")
    }

    static let mockModel = TorConfiguration.Model(info: Tor_mobile_kmpTor.TorInfo(version: "1.0.0", networkLiveness: "UP"))
}
