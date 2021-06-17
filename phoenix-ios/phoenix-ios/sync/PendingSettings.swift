import Foundation
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "PendingSettings"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


class PendingSettings: Equatable, CustomStringConvertible {
	
	enum EnableDisable{
	  case willEnable
	  case willDisable
	}
	
	private weak var parent: SyncManager?
	
	let paymentSyncing: EnableDisable
	let fireDate: Date
	
	init(_ parent: SyncManager, enableSyncing date: Date) {
		self.parent = parent
		self.paymentSyncing = .willEnable
		self.fireDate = date
		log.trace("init()")
		startTimer()
	}
	
	init(_ parent: SyncManager, disableSyncing date: Date) {
		self.parent = parent
		self.paymentSyncing = .willDisable
		self.fireDate = date
		log.trace("init()")
		startTimer()
	}
	
	deinit {
		log.trace("deinit()")
	}
	
	private func startTimer() {
		log.trace("startTimer()")
		
		let deadline: DispatchTime = DispatchTime.now() + fireDate.timeIntervalSinceNow
		DispatchQueue.global(qos: .utility).asyncAfter(deadline: deadline) {[weak self] in
			self?.timerFire()
		}
	}
	
	private func timerFire() {
		log.trace("timerFire()")
		
		if let parent = parent {
			parent.updateState(finishing: self)
		} else {
			log.debug("parent is nil")
		}
	}
	
	/// Allows the user to terminate the delay early.
	///
	func skip() {
		log.trace("skip()")
		timerFire()
	}
	
	var description: String {
		
		let dateStr = fireDate.description(with: Locale.current)
		switch paymentSyncing {
		case .willEnable:
			return "<PendingSettings: willEnable @ \(dateStr)>"
		case .willDisable:
			return "<PendingSettings: willDisable @ \(dateStr)>"
		}
	}
	
	static func == (lhs: PendingSettings, rhs: PendingSettings) -> Bool {
		
		return (lhs.paymentSyncing == rhs.paymentSyncing) && (lhs.fireDate == rhs.fireDate)
	}
}
