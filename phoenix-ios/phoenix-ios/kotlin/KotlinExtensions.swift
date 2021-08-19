import Foundation
import PhoenixShared
import Combine
import CryptoKit

extension PhoenixBusiness {
	
	func getPeer() -> Lightning_kmpPeer? {
		self.peerManager.peerState.value as? Lightning_kmpPeer
	}
}

extension Lightning_kmpWalletPayment: Identifiable {
	
	var identifiable: String {
		// @see LightningExtensions.kt: `fun WalletPayment.id()`
		return self.id()
	}
}

extension WalletPaymentId: Identifiable {
	
	var identifiable: String {
		return self.identifier
	}
}

extension WalletPaymentOrderRow: Identifiable {
	
	var identifiable: String {
		return self.identifier
	}
}

extension PaymentsManager {
	
	func getCachedPayment(row: WalletPaymentOrderRow) -> PaymentsFetcher.Result {
		
		return fetcher.getCachedPayment(row: row)
	}
	
	func getCachedStalePayment(row: WalletPaymentOrderRow) -> PaymentsFetcher.Result {
		
		return fetcher.getCachedStalePayment(row: row)
	}
	
	func getPayment(
		row: WalletPaymentOrderRow,
		completion: @escaping (PaymentsFetcher.Result) -> Void
	) -> Void {
		
		fetcher.getPayment(row: row) { (result: PaymentsFetcher.Result?, _: Error?) in
			
			completion(result ?? PaymentsFetcher.Result(payment: nil))
		}
	}
}

struct FetchQueueBatchResult {
	let rowids: [Int64]
	let rowidMap: [Int64: PaymentRowId]
	let rowMap: [PaymentRowId : PaymentRow]
	let metadataMap: [PaymentRowId : KotlinByteArray]
	let incomingStats: CloudKitDb.MetadataStats
	let outgoingStats: CloudKitDb.MetadataStats
	
	func uniquePaymentRowIds() -> Set<PaymentRowId> {
		return Set<PaymentRowId>(rowidMap.values)
	}
	
	func rowidsMatching(_ query: PaymentRowId) -> [Int64] {
		var results = [Int64]()
		for (rowid, paymentRowId) in rowidMap {
			if paymentRowId == query {
				results.append(rowid)
			}
		}
		return results
	}
	
	static func empty() -> FetchQueueBatchResult {
		
		return FetchQueueBatchResult(
			rowids: [],
			rowidMap: [:],
			rowMap: [:],
			metadataMap: [:],
			incomingStats: CloudKitDb.MetadataStats(),
			outgoingStats: CloudKitDb.MetadataStats()
		)
	}
}

extension CloudKitDb.FetchQueueBatchResult {
	
	func convertToSwift() -> FetchQueueBatchResult {
		
		// We are experiencing crashes like this:
		//
		// for (rowid, paymentRowId) in batch.rowidMap {
		//      ^^^^^
		// Crash: Could not cast value of type '__NSCFNumber' to 'PhoenixSharedLong'.
		//
		// This appears to be some kind of bug in Kotlin.
		// So we're going to make a clean migration.
		// And we need to do so without swift-style enumeration in order to avoid crashing.
		
		var _rowids = [Int64]()
		var _rowidMap = [Int64: PaymentRowId]()
		
		for i in 0 ..< self.rowids.count { // cannot enumerate self.rowidMap
			
			let value_kotlin = rowids[i]
			let value_swift = value_kotlin.int64Value
			
			_rowids.append(value_swift)
			if let paymentRowId = self.rowidMap[value_kotlin] {
				_rowidMap[value_swift] = paymentRowId
			}
		}
		
		return FetchQueueBatchResult(
			rowids: _rowids,
			rowidMap: _rowidMap,
			rowMap: self.rowMap,
			metadataMap: self.metadataMap,
			incomingStats: self.incomingStats,
			outgoingStats: self.outgoingStats
		)
	}
}

extension Lightning_kmpIncomingPayment {
	
	var createdAtDate: Date {
		return Date(timeIntervalSince1970: (Double(createdAt) / Double(1_000)))
	}
}

extension Lightning_kmpIncomingPayment.Received {
	
	var receivedAtDate: Date {
		return Date(timeIntervalSince1970: (Double(receivedAt) / Double(1_000)))
	}
}

extension Lightning_kmpIncomingPayment.ReceivedWith: Identifiable {
	
	var identifiable: Int {
		return self.hash
	}
}

extension Lightning_kmpOutgoingPayment {
	
	var createdAtDate: Date {
		return Date(timeIntervalSince1970: (Double(createdAt) / Double(1_000)))
	}
}

extension Lightning_kmpOutgoingPayment.Part {
	
	var createdAtDate: Date {
		return Date(timeIntervalSince1970: (Double(createdAt) / Double(1_000)))
	}
}

extension Lightning_kmpOutgoingPayment.PartStatusSucceeded {
	
	var completedAtDate: Date {
		return Date(timeIntervalSince1970: (Double(completedAt) / Double(1_000)))
	}
}

extension Lightning_kmpOutgoingPayment.PartStatusFailed {
	
	var completedAtDate: Date {
		return Date(timeIntervalSince1970: (Double(completedAt) / Double(1_000)))
	}
}

extension Lightning_kmpOutgoingPayment.StatusCompleted {
	
	var completedAtDate: Date {
		return Date(timeIntervalSince1970: (Double(completedAt) / Double(1_000)))
	}
}

extension Lightning_kmpPaymentRequest {
	
	var timestampDate: Date {
		return Date(timeIntervalSince1970: Double(timestampSeconds))
	}
}

extension Lightning_kmpConnection {
	
	func localizedText() -> String {
		switch self {
		case .closed       : return NSLocalizedString("Offline", comment: "Connection state")
		case .establishing : return NSLocalizedString("Connecting...", comment: "Connection state")
		case .established  : return NSLocalizedString("Connected", comment: "Connection state")
		default            : return NSLocalizedString("Unknown", comment: "Connection state")
		}
	}
}

extension Bitcoin_kmpByteVector32 {
	
	static func random() -> Bitcoin_kmpByteVector32 {
		
		let key = SymmetricKey(size: .bits256) // 256 / 8 = 32
		
		let data = key.withUnsafeBytes {(bytes: UnsafeRawBufferPointer) -> Data in
			return Data(bytes: bytes.baseAddress!, count: bytes.count)
		}
		
		return Bitcoin_kmpByteVector32(bytes: data.toKotlinByteArray())
	}
}

extension ConnectionsManager {
	
	var currentValue: Connections {
		return connections.value as! Connections
	}
	
	var publisher: CurrentValueSubject<Connections, Never> {

		let publisher = CurrentValueSubject<Connections, Never>(currentValue)

		let swiftFlow = SwiftFlow<Connections>(origin: connections)
		swiftFlow.watch {[weak publisher](connections: Connections?) in
			publisher?.send(connections!)
		}

		return publisher
	}
}

class ObservableConnectionsManager: ObservableObject {
	
	@Published var connections: Connections
	
	private var watcher: Ktor_ioCloseable? = nil
	
	init() {
		let manager = AppDelegate.get().business.connectionsManager
		connections = manager.currentValue
		
		let swiftFlow = SwiftFlow<Connections>(origin: manager.connections)
		
		watcher = swiftFlow.watch {[weak self](newConnections: Connections?) in
			self?.connections = newConnections!
		}
	}
	
	#if DEBUG // For debugging UI: Force connection state
	init(fakeConnections: Connections) {
		self.connections = fakeConnections
	}
	#endif
	
	deinit {
		watcher?.close()
	}
}

extension FiatCurrency {
	
	var shortName: String {
		return name.uppercased()
	}
	
	var longName: String {
		
		switch self {
			case FiatCurrency.aud : return NSLocalizedString("Australian Dollar",    comment: "Currency name: AUD")
			case FiatCurrency.brl : return NSLocalizedString("Brazilian Real",       comment: "Currency name: BRL")
			case FiatCurrency.cad : return NSLocalizedString("Canadian Dollar",      comment: "Currency name: CAD")
			case FiatCurrency.chf : return NSLocalizedString("Swiss Franc",          comment: "Currency name: CHF")
			case FiatCurrency.clp : return NSLocalizedString("Chilean Peso",         comment: "Currency name: CLP")
			case FiatCurrency.cny : return NSLocalizedString("Chinese Yuan",         comment: "Currency name: CNY")
			case FiatCurrency.czk : return NSLocalizedString("Czech Koruna", 		 comment: "Currency name: CZK")
			case FiatCurrency.dkk : return NSLocalizedString("Danish Krone",         comment: "Currency name: DKK")
			case FiatCurrency.eur : return NSLocalizedString("Euro",                 comment: "Currency name: EUR")
			case FiatCurrency.gbp : return NSLocalizedString("Great British Pound",  comment: "Currency name: GBP")
			case FiatCurrency.hkd : return NSLocalizedString("Hong Kong Dollar",     comment: "Currency name: HKD")
			case FiatCurrency.hrk : return NSLocalizedString("Croation Kuna",        comment: "Currency name: HRK")
			case FiatCurrency.huf : return NSLocalizedString("Hungarian Forint",     comment: "Currency name: HUF")
			case FiatCurrency.inr : return NSLocalizedString("Indian Rupee",         comment: "Currency name: INR")
			case FiatCurrency.isk : return NSLocalizedString("Icelandic Kròna",      comment: "Currency name: ISK")
			case FiatCurrency.jpy : return NSLocalizedString("Japanese Yen",         comment: "Currency name: JPY")
			case FiatCurrency.krw : return NSLocalizedString("Korean Won",           comment: "Currency name: KRW")
			case FiatCurrency.mxn : return NSLocalizedString("Mexican Peso",         comment: "Currency name: MXN")
			case FiatCurrency.nzd : return NSLocalizedString("New Zealand Dollar",   comment: "Currency name: NZD")
			case FiatCurrency.pln : return NSLocalizedString("Polish Zloty",         comment: "Currency name: PLN")
			case FiatCurrency.ron : return NSLocalizedString("Romanian Leu",         comment: "Currency name: RON")
			case FiatCurrency.rub : return NSLocalizedString("Russian Ruble",        comment: "Currency name: RUB")
			case FiatCurrency.sek : return NSLocalizedString("Swedish Krona",        comment: "Currency name: SEK")
			case FiatCurrency.sgd : return NSLocalizedString("Singapore Dollar",     comment: "Currency name: SGD")
			case FiatCurrency.thb : return NSLocalizedString("Thai Baht",            comment: "Currency name: THB")
			case FiatCurrency.twd : return NSLocalizedString("Taiwan New Dollar",    comment: "Currency name: TWD")
			case FiatCurrency.usd : return NSLocalizedString("United States Dollar", comment: "Currency name: USD")
			default               : break
		}
		
		return self.name
	}
}

extension BitcoinUnit {
	
	var shortName: String {
		return name.lowercased()
	}
	
	var explanation: String {
		
		let s = FormattedAmount.fractionGroupingSeparator // narrow no-break space
		switch (self) {
			case BitcoinUnit.sat  : return "0.000\(s)000\(s)01 BTC"
			case BitcoinUnit.bit  : return "0.000\(s)001 BTC"
			case BitcoinUnit.mbtc : return "0.001 BTC"
			case BitcoinUnit.btc  : return ""
			default               : break
		}
		
		return self.name
	}
}
