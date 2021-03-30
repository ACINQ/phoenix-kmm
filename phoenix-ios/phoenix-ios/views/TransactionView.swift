import SwiftUI
import PhoenixShared
import DYPopoverView
import os.log

#if DEBUG && false
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "TransactionView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

fileprivate let explainFeesButtonViewId = "explainFeesButtonViewId"


struct PaymentView : View {

	let payment: PhoenixShared.Eclair_kmpWalletPayment
	let close: () -> Void
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	
	@State var explainFeesText: String = ""
	@State var explainFeesPopoverVisible = false
	@State var explainFeesPopoverFrame = CGRect(x: 0, y: 0, width: 200, height:500)
	
	var body: some View {
		
		ZStack {
			
			main()
			
			// Close button in upper right-hand corner
			VStack {
				HStack {
					Spacer()
					Button {
						close()
					} label: {
						Image("ic_cross")
							.resizable()
							.frame(width: 30, height: 30)
					}
					.padding(32)
				}
				Spacer()
			}
			
			// An invisible layer used to detect taps, and dismiss the DYPopoverView.
			if explainFeesPopoverVisible {
				Color.clear
					.contentShape(Rectangle()) // required: https://stackoverflow.com/a/60151771/43522
					.onTapGesture {
						explainFeesPopoverVisible = false
					}
			}
		}
		.popoverView( // DYPopoverView
			content: { ExplainFeesPopover(explanationText: $explainFeesText) },
			background: { Color(.secondarySystemBackground) },
			isPresented: $explainFeesPopoverVisible,
			frame: $explainFeesPopoverFrame,
			anchorFrame: nil,
			popoverType: .popover,
			position: .top,
			viewId: explainFeesButtonViewId
		)
		.onPreferenceChange(ExplainFeesPopoverHeight.self) {
			if let height = $0 {
				explainFeesPopoverFrame = CGRect(x: 0, y: 0, width: explainFeesPopoverFrame.width, height: height)
			}
		}
		.onAppear { onAppear() }
	}
	
	@ViewBuilder
	func main() -> some View {
		
		VStack {
			switch payment.state() {
			case .success:
				Image(systemName: "checkmark.circle")
					.renderingMode(.template)
					.resizable()
					.aspectRatio(contentMode: .fit)
					.frame(width: 100, height: 100)
					.foregroundColor(.appGreen)
				VStack {
					Text(payment.amountMsat() < 0 ? "SENT" : "RECEIVED")
						.font(Font.title2.bold())
						.padding(.bottom, 2)
					Text(payment.timestamp().formatDateMS())
						.font(.subheadline)
						.foregroundColor(.secondary)
				}
				.padding()
				
			case .pending:
			//	Image("ic_send") // image is blurry for some reason
				Image(systemName: "paperplane")
					.renderingMode(.template)
					.resizable()
					.foregroundColor(Color(UIColor.systemGray))
					.frame(width: 100, height: 100)
				Text("PENDING")
					.font(Font.title2.bold())
					.padding()
				
			case .failure:
				Image(systemName: "xmark.circle")
					.renderingMode(.template)
					.resizable()
					.frame(width: 100, height: 100)
					.foregroundColor(.appRed)
				VStack {
					Text("FAILED")
						.font(Font.title2.bold())
						.padding(.bottom, 2)
					
					Text("NO FUNDS HAVE BEEN SENT")
						.font(Font.title2.uppercaseSmallCaps())
						.padding(.bottom, 6)
					
					Text(payment.timestamp().formatDateMS())
						.font(Font.subheadline)
						.foregroundColor(.secondary)
					
				}
				.padding()
				
			default:
				EmptyView()
			}

			HStack(alignment: .bottom) {
				let amount = Utils.format(currencyPrefs, msat: payment.amountMsat(), hideMsats: false)

				Text(amount.digits)
					.font(.largeTitle)
					.onTapGesture { toggleCurrencyType() }
				Text(amount.type)
					.font(.title3)
					.foregroundColor(.gray)
					.padding(.bottom, 4)
					.onTapGesture { toggleCurrencyType() }
			}
			.padding()
			.background(
				VStack {
					Spacer()
					Line().stroke(Color.appHorizon, style: StrokeStyle(lineWidth: 4, lineCap: .round))
						.frame(height: 4)
				}
			)
			.padding(.bottom, 4)
			
			InfoGrid(
				payment: payment,
				explainFeesPopoverVisible: $explainFeesPopoverVisible
			)
		}
	}
	
	func toggleCurrencyType() -> Void {
		currencyPrefs.toggleCurrencyType()
	}
	
	func explainFeesPopoverText() -> String {
		
		let feesInfo = payment.paymentFees(currencyPrefs: currencyPrefs)
		return feesInfo?.1 ?? ""
	}
	
	func onAppear() -> Void {
		log.trace("onAppear()")
		
		// Update text in explainFeesPopover
		explainFeesText = payment.paymentFees(currencyPrefs: currencyPrefs)?.1 ?? ""
		
		if let outgoingPayment = payment as? PhoenixShared.Eclair_kmpOutgoingPayment {
			
			// If this is an outgoingPayment, then we don't have the proper parts list.
			// That is, the outgoingPayment was fetched via listPayments(),
			// which gives us a fake parts list.
			//
			// Let's fetch the full outgoingPayment (with parts list),
			// so we can improve our fees description.
			
			let paymentId = outgoingPayment.component1()
			AppDelegate.get().business.paymentsManager.getOutgoingPayment(id: paymentId) {
				(fullOutgoingPayment: Eclair_kmpOutgoingPayment?, error: Error?) in
				
				if let fullOutgoingPayment = fullOutgoingPayment {
					let feesInfo = fullOutgoingPayment.paymentFees(currencyPrefs: currencyPrefs)
					explainFeesText = feesInfo?.1 ?? ""
				}
			}
		}
	}
}

// Architecture Design:
//
// We want to display a list of key/value pairs:
//
//     Desc: Pizza reimbursement
//     Fees: 2 sat
//  Elapsed: 2.4 seconds
//
// Requirements:
// 1. the elements must be vertically aligned
//   - all keys have same trailing edge
//   - all values have same leading edge
// 2. the list (as a whole) must be horizontally centered
//
//       1,042 sat
//       ---------
// Desc: Party
//
//      ^ Wrong! List not horizontally centered!
//
//       1,042 sat
//       ---------
//      Desc: Party
//
//      ^ Correct!
//
// Ultimately, we need to:
// - assign all keys the same width
// - ensure the assigned width is the minimum possible width
//
// This was super easy with UIKit.
// We could simply add constraints such that all keys are equal width.
//
// In SwiftUI, it's not that simple. But it's not that bad either.
//
// - we use InfoGrid_Column0 to measure the width of each key
// - we use InfoGrid_Column0_MeasuredWidth to communicate the width
//   up the hierarchy to the InfoGrid.
// - InfoGrid_Column0_MeasuredWidth.reduce is used to find the max width
// - InfoGrid assigns the maxWidth to each key frame
//
// Note that this occurs in 2 passes.
// - In the first pass, InfoGrid.widthColumn0 is nil
// - It then lays out all the elements, and they get measured
// - The width is passed up the hierarchy via InfoGrid_Column0_MeasuredWidth preference
// - This triggers InfoGrid.onPreferenceChange(InfoGrid_Column0_MeasuredWidth.self)
// - Which triggers a second layout pass
//
fileprivate struct InfoGrid: View {
	
	let payment: PhoenixShared.Eclair_kmpWalletPayment
	
	@Binding var explainFeesPopoverVisible: Bool
	
	@State private var widthColumn0: CGFloat? = nil
	
	private let cappedWidthColumn0: CGFloat = 200
	
	private let verticalSpacingBetweenRows: CGFloat = 12
	private let horizontalSpacingBetweenColumns: CGFloat = 8
	
	@Environment(\.openURL) var openURL
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	
	var body: some View {
		
		VStack(
			alignment : HorizontalAlignment.leading,
			spacing   : verticalSpacingBetweenRows
		) {
			
			if let pDescription = payment.paymentDescription() {
				
				HStack(
					alignment : VerticalAlignment.top,
					spacing   : horizontalSpacingBetweenColumns
				) {
					HStack(alignment: VerticalAlignment.top, spacing: 0) {
						Spacer(minLength: 0) // => HorizontalAlignment.trailing
						InfoGrid_Column0 {
							Text("Desc")
								.foregroundColor(.secondary)
						}
					}
					.frame(width: widthColumn0)
					
					Text(pDescription)
						.contextMenu {
							Button(action: {
								UIPasteboard.general.string = pDescription
							}) {
								Text("Copy")
							}
						}
				}
			} // </if let pDescription>
			
			if let pType = payment.paymentType() {
				
				HStack(
					alignment : VerticalAlignment.top,
					spacing   : horizontalSpacingBetweenColumns
				) {
					HStack(alignment: VerticalAlignment.top, spacing: 0) {
						Spacer(minLength: 0) // => HorizontalAlignment.trailing
						InfoGrid_Column0 {
							Text("Type")
								.foregroundColor(.secondary)
						}
					}
					.frame(width: widthColumn0)
					
					VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
						
						Text(pType.0)
						+ Text(" (\(pType.1))")
							.font(.footnote)
							.foregroundColor(.secondary)
						
						if let link = payment.paymentLink() {
							Button {
								openURL(link)
							} label: {
								Text("blockchain tx")
							}
						}
					}
				}
			} // </if let pType>
			
			if let pClosingInfo = payment.channelClosing() {
				
				HStack(
					alignment : VerticalAlignment.top,
					spacing   : horizontalSpacingBetweenColumns
				) {
					HStack(alignment: VerticalAlignment.top, spacing: 0) {
						Spacer(minLength: 0) // => HorizontalAlignment.trailing
						InfoGrid_Column0 {
							Text("Output")
								.foregroundColor(.secondary)
						}
					}
					.frame(width: widthColumn0)

					VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
						
						// Bitcoin address (copyable)
						Text(pClosingInfo.closingAddress)
							.contextMenu {
								Button(action: {
									UIPasteboard.general.string = pClosingInfo.closingAddress
								}) {
									Text("Copy")
								}
							}
						
						if pClosingInfo.isSentToDefaultAddress {
							Text("(This is your address - derived from your seed. You alone possess your seed.)")
								.font(.footnote)
								.foregroundColor(.secondary)
								.padding(.top, 4)
						}
					}
				}
			} // </if let channelClosing>
			
			if let pFees = payment.paymentFees(currencyPrefs: currencyPrefs) {

				HStack(
					alignment : VerticalAlignment.top,
					spacing   : horizontalSpacingBetweenColumns
				) {
					HStack(alignment: VerticalAlignment.top, spacing: 0) {
						Spacer(minLength: 0) // => HorizontalAlignment.trailing
						InfoGrid_Column0 {
							Text("Fees")
								.foregroundColor(.secondary)
						}
					}
					.frame(width: widthColumn0)

					HStack(alignment: VerticalAlignment.center, spacing: 0) {
						
						Text(pFees.0.string) // pFees.0 => FormattedAmount
							.onTapGesture { toggleCurrencyType() }
						
						if pFees.1.count > 0 {
							
							Button {
								explainFeesPopoverVisible = true
							} label: {
								Image(systemName: "questionmark.circle")
									.renderingMode(.template)
									.foregroundColor(.secondary)
									.font(.body)
							}
							.anchorView(viewId: explainFeesButtonViewId)
							.padding(.leading, 4)
						}
					}

				}
			} // </if let pFees>
			
			if let pElapsed = payment.paymentTimeElapsed() {
				
				HStack(
					alignment : VerticalAlignment.top,
					spacing   : horizontalSpacingBetweenColumns
				) {
					HStack(alignment: VerticalAlignment.top, spacing: 0) {
						Spacer(minLength: 0) // => HorizontalAlignment.trailing
						InfoGrid_Column0 {
							Text("Elapsed")
								.foregroundColor(.secondary)
						}
					}
					.frame(width: widthColumn0)

					if pElapsed < 1_000 {
						Text("\(pElapsed) milliseconds")
					} else {
						let seconds = pElapsed / 1_000
						let millis = pElapsed % 1_000
						
						Text("\(seconds).\(millis) seconds")
					}
				}
			} // </if let pElapsed>
			
			if let pError = payment.paymentFinalError() {
				
				HStack(
					alignment : VerticalAlignment.top,
					spacing   : horizontalSpacingBetweenColumns
				) {
					HStack(alignment: VerticalAlignment.top, spacing: 0) {
						Spacer(minLength: 0) // => HorizontalAlignment.trailing
						InfoGrid_Column0 {
							Text("Error")
								.foregroundColor(.secondary)
						}
					}
					.frame(width: widthColumn0)

					Text(pError)
				}
				
			} // </if let pError>
			
		}
		.padding([.leading, .trailing])
		.onPreferenceChange(InfoGrid_Column0_MeasuredWidth.self) {
			log.debug("InfoGrid_Column0_MeasuredWidth => \($0 ?? CGFloat(-1))")
			if let width = $0 {
				self.widthColumn0 = min(width, cappedWidthColumn0)
			} else {
				self.widthColumn0 = $0
			}
		}
	}
	
	func toggleCurrencyType() -> Void {
		currencyPrefs.toggleCurrencyType()
	}
}

fileprivate struct InfoGrid_Column0<Content>: View where Content: View {
	let content: Content
	
	init(@ViewBuilder builder: () -> Content) {
		content = builder()
	}
	
	var body: some View {
		content
			.background(GeometryReader { proxy in
				Color.clear.preference(
					key: InfoGrid_Column0_MeasuredWidth.self,
					value: proxy.size.width
				)
			})
	}
}

fileprivate struct InfoGrid_Column0_MeasuredWidth: PreferenceKey {
	static let defaultValue: CGFloat? = nil
	
	static func reduce(value: inout CGFloat?, nextValue: () -> CGFloat?) {
		
		// This function is called with the measured width of each individual column0 item.
		// We want to determine the maximum measured width here.
		if let prv = value {
			if let nxt = nextValue() {
				value = prv >= nxt ? prv : nxt
			} else {
				value = prv
			}
		} else {
			value = nextValue()
		}
	}
}

fileprivate struct ExplainFeesPopover: View {
	
	@Binding var explanationText: String
	
	var body: some View {
		
		VStack {
			Text(explanationText)
				.padding()
				.background(GeometryReader { proxy in
					Color.clear.preference(
						key: ExplainFeesPopoverHeight.self,
						value: proxy.size.height
					)
				})
		}.frame(minHeight: 500)
		// ^^^ Why? ^^^
		//
		// We're trying to accomplish 2 things:
		// - allow the explanationText to be dynamically changed
		// - calculate the appropriate height for the text
		//
		// But there's a problem, which occurs like so:
		// - explainFeesPopoverFrame starts with hard-coded frame of (150, 500)
		// - SwiftUI performs layout of our body with inherited frame of (150, 500)
		// - We calculate appropriate height (X) for our text,
		//   and it gets reported via ExplainFeesPopoverHeight
		// - explainFeesPopoverFrame is resized to (150, X)
		// - explanationText is changed, triggering a view update
		// - SwiftUI performs layout of our body with previous frame of (150, X)
		// - Text cannot report appropriate height, as it's restricted to X
		//
		// So we force the height of our VStack to 500,
		// giving the Text room to size itself appropriately.
	}
}

fileprivate struct ExplainFeesPopoverHeight: PreferenceKey {
	static let defaultValue: CGFloat? = nil
	
	static func reduce(value: inout CGFloat?, nextValue: () -> CGFloat?) {
		value = value ?? nextValue()
	}
}

extension Eclair_kmpWalletPayment {
	
	fileprivate func paymentDescription() -> String? {
		
		if let incomingPayment = self as? Eclair_kmpIncomingPayment {
			
			if let invoice = incomingPayment.origin.asInvoice() {
				return invoice.paymentRequest.desc()
			}
			
		} else if let outgoingPayment = self as? Eclair_kmpOutgoingPayment {
			
			if let normal = outgoingPayment.details.asNormal() {
				return normal.paymentRequest.desc()
			}
		}
		
		return nil
	}
	
	fileprivate func paymentType() -> (String, String)? {
		
		// Will be displayed in the UI as:
		//
		// Type : value (explanation)
		//
		// where return value is: (value, explanation)
		
		if let incomingPayment = self as? Eclair_kmpIncomingPayment {
			
			if let _ = incomingPayment.origin.asSwapIn() {
				let val = NSLocalizedString("Swap-In", comment: "Transaction Info: Value")
				let exp = NSLocalizedString("layer 1 -> 2", comment: "Transaction Info: Explanation")
				return (val, exp)
			}
			if let _ = incomingPayment.origin.asKeySend() {
				let val = NSLocalizedString("KeySend", comment: "Transaction Info: Value")
				let exp = NSLocalizedString("non-invoice payment", comment: "Transaction Info: Explanation")
				return (val, exp)
			}
			
		} else if let outgoingPayment = self as? Eclair_kmpOutgoingPayment {
			
			if let _ = outgoingPayment.details.asSwapOut() {
				let val = NSLocalizedString("Swap-Out", comment: "Transaction Info: Value")
				let exp = NSLocalizedString("layer 2 -> 1", comment: "Transaction Info: Explanation")
				return (val, exp)
			}
			if let _ = outgoingPayment.details.asKeySend() {
				let val = NSLocalizedString("KeySend", comment: "Transaction Info: Value")
				let exp = NSLocalizedString("non-invoice payment", comment: "Transaction Info: Explanation")
				return (val, exp)
			}
			if let _ = outgoingPayment.details.asChannelClosing() {
				let val = NSLocalizedString("Channel Closing", comment: "Transaction Info: Value")
				let exp = NSLocalizedString("layer 2 -> 1", comment: "Transaction Info: Explanation")
				return (val, exp)
			}
		}
		
		return nil
	}
	
	fileprivate func paymentLink() -> URL? {
		
		var address: String? = nil
		if let incomingPayment = self as? Eclair_kmpIncomingPayment {
		
			if let swapIn = incomingPayment.origin.asSwapIn() {
				address = swapIn.address
			}
			
		} else if let outgoingPayment = self as? Eclair_kmpOutgoingPayment {
		
			if let swapOut = outgoingPayment.details.asSwapOut() {
				address = swapOut.address
			}
		}
		
		if let address = address {
			let str = "https://mempool.space/testnet/address/\(address)"
			return URL(string: str)
		}
		
		return nil
	}
	
	fileprivate func channelClosing() -> Eclair_kmpOutgoingPayment.DetailsChannelClosing? {
		
		if let outgoingPayment = self as? Eclair_kmpOutgoingPayment {
			if let result = outgoingPayment.details.asChannelClosing() {
				return result
			}
		}
		
		return nil
	}
	
	fileprivate func paymentFees(currencyPrefs: CurrencyPrefs) -> (FormattedAmount, String)? {
		
		if let incomingPayment = self as? Eclair_kmpIncomingPayment {
		
			// An incomingPayment may have fees if a new channel was automatically opened
			if let received = incomingPayment.received {
				
				let msat = received.receivedWith.fees.msat
				if msat > 0 {
					
					let formattedAmt = Utils.format(currencyPrefs, msat: msat, hideMsats: false)
					
					let exp = NSLocalizedString(
						"In order to receive this payment, a new payment channel was opened." +
						" This is not always required.",
						comment: "Fees explanation"
					)
					
					return (formattedAmt, exp)
				}
				else {
					// I think it's nice to see "Fees: 0 sat" :)
					
					let formattedAmt = Utils.format(currencyPrefs, msat: 0, hideMsats: true)
					let exp = ""
					
					return (formattedAmt, exp)
				}
			}
			
		} else if let outgoingPayment = self as? Eclair_kmpOutgoingPayment {
			
			if let _ = outgoingPayment.status.asFailed() {
				
				// no fees for failed payments
				return nil
				
			} else if let onChain = outgoingPayment.status.asOnChain() {
				
				// for on-chain payments, the fees are extracted from the mined transaction(s)
				
				let channelDrain: Eclair_kmpMilliSatoshi = outgoingPayment.recipientAmount
				let claimed = Eclair_kmpMilliSatoshi(sat: onChain.claimed)
				let fees = channelDrain.minus(other: claimed)
				let formattedAmt = Utils.format(currencyPrefs, msat: fees, hideMsats: false)
				
				let txCount = onChain.component1().count
				let exp: String
				if txCount == 1 {
					exp = NSLocalizedString(
						"Bitcoin networks fees paid for on-chain transaction. Payment required 1 transaction.",
						comment: "Fees explanation"
					)
				} else {
					exp = NSLocalizedString(
						"Bitcoin networks fees paid for on-chain transactions. Payment required \(txCount) transactions.",
						comment: "Fees explanation"
					)
				}
				
				return (formattedAmt, exp)
				
			} else if let _ = outgoingPayment.status.asOffChain() {
				
				let msat = outgoingPayment.fees.msat
				let formattedAmt = Utils.format(currencyPrefs, msat: msat, hideMsats: false)
				
				var parts = 0
				var hops = 0
				for part in outgoingPayment.parts {
					
					parts += 1
					hops = part.route.count
				}
				
				let exp: String
				if parts == 1 {
					if hops == 1 {
						exp = NSLocalizedString(
							"Lightning fees for routing the payment. Payment required 1 hop.",
							comment: "Fees explanation"
						)
					} else {
						exp = NSLocalizedString(
							"Lightning fees for routing the payment. Payment required \(hops) hops.",
							comment: "Fees explanation"
						)
					}
					
				} else {
					exp = NSLocalizedString(
						"Lightning fees for routing the payment. Payment was divided into \(parts) parts, using \(hops) hops.",
						comment: "Fees explanation"
					)
				}
				
				return (formattedAmt, exp)
			}
		}
		
		return nil
	}
	
	/// If the OutgoingPayment succeeded or failed, reports the total elapsed time.
	/// The return value is in number of milliseconds.
	///
	fileprivate func paymentTimeElapsed() -> Int64? {
		
		
		if let outgoingPayment = self as? Eclair_kmpOutgoingPayment {
			
			let started = self.timestamp()
			var finished: Int64? = nil
			
			if let failed = outgoingPayment.status.asFailed() {
				finished = failed.completedAt
				
			} else if let succeeded = outgoingPayment.status.asSucceeded() {
				finished = succeeded.completedAt
			}
			
			if let finished = finished, finished > started {
				return finished - started
			}
		}
		
		return nil
	}
	
	fileprivate func paymentFinalError() -> String? {

		if let outgoingPayment = self as? Eclair_kmpOutgoingPayment {
			
			if let failed = outgoingPayment.status.asFailed() {
				
				return failed.reason.description
			}
		}
		
		return nil
	}
}


class PaymentView_Previews : PreviewProvider {
	
	static var previews: some View {
		let mock = PhoenixShared.Mock()
		
		PaymentView(payment: mock.outgoingPending(), close: {})
			.preferredColorScheme(.light)
			.environmentObject(CurrencyPrefs.mockEUR())

		PaymentView(payment: mock.outgoingSuccessful(), close: {})
			.preferredColorScheme(.dark)
			.environmentObject(CurrencyPrefs.mockEUR())

		PaymentView(payment: mock.outgoingFailed(), close: {})
			.preferredColorScheme(.dark)
			.environmentObject(CurrencyPrefs.mockEUR())

		PaymentView(payment: mock.incomingPaymentReceived(), close: {})
			.preferredColorScheme(.dark)
			.environmentObject(CurrencyPrefs.mockEUR())
	}
}
