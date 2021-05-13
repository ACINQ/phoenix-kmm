import Foundation
import PhoenixShared

/// The PaymentsFetcher provides a singleton instance for fetching & caching items from the database.
///
/// The keys used for fetching & caching are `WalletPaymentOrderRow` which include:
/// - paymentId: [IncomingPaymentId || OutgoingPaymentId]
/// - created: Date
/// - completed: Date
///
/// Thus, since the key changes when the row is updated, the fetcher (and built-in cache)
/// always provides an up-to-date WalletPayment instance in response to your query.
///
class PaymentsFetcher {
	
	struct Result {
		let payment: Lightning_kmpWalletPayment?
		
		var incomingPayment: Lightning_kmpIncomingPayment? {
			return payment as? Lightning_kmpIncomingPayment
		}
		var outgoingPayment: Lightning_kmpOutgoingPayment? {
			return payment as? Lightning_kmpOutgoingPayment
		}
	}
	
	/// Using a strict cache to ensure eviction based on actual usage by the UI
	private var cache = Cache<String, Result>(countLimit: 250)
	
	typealias CompletionHandler = (Result) -> Void
	private var pendingFetches = [String: [CompletionHandler]]()
	
	private let paymentsManager = AppDelegate.get().business.paymentsManager
	
	/// Singleton instance
	///
	public static let shared = PaymentsFetcher()
	
	private init() {/* MUST use singleton instance */}
	
	/// Returns the payment if it exists in the cache.
	/// A database fetch is not performed.
	///
	public func getCachedPayment(
		row: WalletPaymentOrderRow
	) -> Result {
		
		let key = row.identifiable
		return cache[key] ?? Result(payment: nil)
	}
	
	/// Searches the cache for a stale version of the payment.
	/// Sometimes a stale version of the object is better than nothing.
	///
	public func getCachedStalePayment(
		row: WalletPaymentOrderRow
	) -> Result {
		
		let prefix = row.identifiablePrefix
		let max = cache.filteredKeys { (key: String) in
			
			return key.hasPrefix(prefix)
			
		}.reduce(into: [String: Int64]()) { (result: inout [String: Int64], key: String) in
			
			let startIdx = key.index(key.startIndex, offsetBy: prefix.count)
			let endIdx = key.endIndex
			
			let suffix = key[startIdx..<endIdx]
			
			result[key] = Int64(String(suffix)) ?? 0
			
		}.max {
			// areInIncreasingOrder:
			// A predicate that returns true if its first argument should be ordered before its second argument.
			
			let num0 = $0.value
			let num1 = $1.value
			
			return num0 < num1
		}
		
		var mostRecentStaleValue: Result? = nil
		if let mostRecentStaleKey = max?.key {
			mostRecentStaleValue = cache[mostRecentStaleKey]
		}
		
		return mostRecentStaleValue ?? Result(payment: nil)
	}
	
	/// Fetches the payment, either via the cache or via a database fetch.
	/// The given completion is always invoked on the main thread, and always on a future runloop cycle.
	///
	/// If multiple queries for the same row arrive simultaneously,
	/// they will be automatically consolidated into a single database fetch.
	/// 
	public func getPayment(
		row: WalletPaymentOrderRow,
		completion inCompletion: @escaping (Result) -> Void
	) -> Void {
		
		let key = row.identifiable
		
		if let result = cache[key] {
			return DispatchQueue.main.async {
				inCompletion(result)
			}
		}
		
		if var pendingCompletions = pendingFetches[key] {
			pendingCompletions.append(inCompletion)
			pendingFetches[key] = pendingCompletions
			
			return // database fetch already in progress
		} else {
			
			pendingFetches[key] = [inCompletion]
		}
		
		let completion: CompletionHandler = {[/* strong */ self] (result: Result) in
			
			self.cache[key] = result
			
			if let pendingCompletionList = self.pendingFetches[key] {
				self.pendingFetches[key] = nil
				
				for pendingCompletion in pendingCompletionList {
					pendingCompletion(result)
				}
			}
		}
		
		if let incomingPaymentId = row.id as? WalletPaymentId.IncomingPaymentId {
			
			paymentsManager.getIncomingPayment(paymentHash: incomingPaymentId.paymentHash) {
				(fetchedPayment: Lightning_kmpIncomingPayment?, error: Error?) in
				
				completion(Result(payment: fetchedPayment))
			}
			
		} else if let outgoingPaymentId = row.id as? WalletPaymentId.OutgoingPaymentId {
			
			paymentsManager.getOutgoingPayment(id: outgoingPaymentId.id) {
				(fetchedPayment: Lightning_kmpOutgoingPayment?, error: Error?) in
				
				completion(Result(payment: fetchedPayment))
			}
			
		} else {
			fatalError("Unknown WalletPaymentId type")
		}
	}
}
