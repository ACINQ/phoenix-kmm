import SwiftUI
import PhoenixShared

struct LogsConfigurationViewerView: View {

    let filePath: String

    @State var share: NSURL? = nil

    @State var text: String? = nil

    var body: some View {
        VStack {
            if let text = text {
                ScrollView {
                    Text(text)
                            .font(.system(.callout, design: .monospaced))
                            .frame(maxWidth: .infinity, alignment: .leading)
                }
            } else {
                EmptyView()
            }
        }
            .navigationBarItems(
                    trailing: Button {
                        share = NSURL(fileURLWithPath: filePath)
                    } label: {
                        Image(systemName: "square.and.arrow.up")
                    }
                    .sharing($share)
            )
            .onAppear {
                DispatchQueue.global(qos: .userInitiated).async {
                    do {
                        let logs = try String(contentsOfFile: filePath)
                        DispatchQueue.main.async { text = logs }
                    } catch {
                        DispatchQueue.main.async { text = "Could not load \(filePath)" }
                    }
                }
            }
    }
}

class LogsConfigurationViewerView_Previews: PreviewProvider {

    static var previews: some View {
        mockView(LogsConfigurationViewerView(filePath: "fake"), nav: .inline)
                .previewDevice("iPhone 11")
    }

    #if DEBUG
    @objc class func injected() {
        UIApplication.shared.windows.first?.rootViewController = UIHostingController(rootView: previews)
    }
    #endif
}
