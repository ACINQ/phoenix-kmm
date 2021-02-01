import SwiftUI
import PhoenixShared

struct ConnectionsPopover: View {

	@StateObject var monitor = ObservableConnectionsMonitor()
	
	@Environment(\.popoverState) var popoverState: PopoverState

	var body: some View {
		
		VStack(alignment: .leading) {
			Text("Connection status")
				.font(.title3)
				.padding(.bottom)
			Divider()
			ConnectionCell(label: "Internet", connection: monitor.connections.internet)
			Divider()
			if let tor = monitor.connections.tor {
				ConnectionCell(label: "Tor", connection: tor)
				Divider()
			}
			ConnectionCell(label: "Lightning peer", connection: monitor.connections.peer)
			Divider()
			ConnectionCell(label: "Electrum server", connection: monitor.connections.electrum)
			Divider()
			HStack {
				Spacer()
				Button("OK") {
					withAnimation {
						popoverState.close.send()
					}
				}
				.font(.title2)
			}
			.padding(.top)
		}
		.padding()
	}
}

fileprivate struct ConnectionCell : View {
	let label: String
	let connection: Eclair_kmpConnection

	var body : some View {
		HStack {
			let bullet = Image("ic_bullet").resizable().frame(width: 10, height: 10)

			if connection == .established {
				bullet.foregroundColor(.appGreen)
			}
			else if connection == .establishing {
				bullet.foregroundColor(.appYellow)
			}
			else if connection == .closed {
				bullet.foregroundColor(.appRed)
			}

			Text("\(label):")
			Spacer()
			Text(connection.localizedText())
		}
		.padding([.top, .bottom], 8)
	}
}
