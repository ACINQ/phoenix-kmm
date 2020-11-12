import SwiftUI
import AVFoundation
import PhoenixShared

import UIKit

struct ScanView: MVIView {
    typealias Model = Scan.Model
    typealias Intent = Scan.Intent

    @Binding var isShowing: Bool

    @StateObject var toast = Toast()

    var body: some View {
        ZStack {
            mvi({ $0.scan() }, onModel: { change in
                print("NEW MODEL: \(change.newModel)")
                if change.newModel is Scan.ModelSending {
                    isShowing = false
                } else if change.newModel is Scan.ModelBadRequest {
                    toast.toast(text: "Unexpected request format!")
                }
                change.animateIfModelTypeChanged()
            }) { model, intent in
                view(model: model, intent: intent)
            }
            toast.view()
        }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color.appBackground)
    }

    @ViewBuilder
    func view(model: Scan.Model, intent: @escaping IntentReceiver) -> some View {
        switch model {
        case _ as Scan.ModelReady, _ as Scan.ModelBadRequest: ReadyView(intent: intent)
        case let m as Scan.ModelValidate: ValidateView(model: m, intent: intent)
        case let m as Scan.ModelSending: SendingView(model: m)
        default:
            fatalError("Unknown model \(model)")
        }
    }

    struct ReadyView: View {
        let intent: IntentReceiver

        var body: some View {
            VStack {
                Spacer()
                Text("Scan a QR code")
                        .padding()
                        .font(.title2)

                QrCodeScannerView { request in
                    print(request)
                    intent(Scan.IntentParse(request: request))
                }
                        .cornerRadius(10)
                        .overlay(
                                RoundedRectangle(cornerRadius: 10)
                                        .stroke(Color.gray, lineWidth: 4)
                        )
                        .padding()

                Divider()
                        .padding([.leading, .trailing])

                Button {
                    if let request = UIPasteboard.general.string {
                        intent(Scan.IntentParse(request: request))
                    }
                } label: {
                    Image(systemName: "arrow.right.doc.on.clipboard")
                    Text("Paste from clipboard")
                            .font(.title2)
                }
                        .disabled(!UIPasteboard.general.hasStrings)
                        .padding()
                Spacer()
            }
                    .navigationBarTitle("Scan", displayMode: .inline)
                    .zIndex(2)
                    .transition(.asymmetric(insertion: .identity, removal: .move(edge: .bottom)))

        }
    }

    struct ValidateView: View {
        let model: Scan.ModelValidate

        let intent: IntentReceiver

        @State var illegal: Bool = false
        @State var amount: String
        @State var unit: BitcoinUnit = .satoshi

        @State private var textWidth: CGFloat?

        init(model: Scan.ModelValidate, intent: @escaping IntentReceiver) {
            self.model = model

            if let amountSat = model.amountSat {
                self._amount = State(initialValue: String(amountSat.int64Value))
            } else {
                self._amount = State(initialValue: "")
            }

            self.intent = intent
        }

        var body: some View {
            VStack {
                HStack(alignment: .bottom) {
                    TextField("123", text: $amount)
                            .keyboardType(.decimalPad)
                            .disableAutocorrection(true)
                            .fixedSize()
                            .font(.title)
                            .foregroundColor(Color.appHorizon)
                            .multilineTextAlignment(.center)
                            .onChange(of: amount) {
                                illegal = !$0.isEmpty && (Double($0) == nil || Double($0)! < 0)
                            }
                            .foregroundColor(illegal ? Color.red : Color.black)

                    Picker(selection: $unit, label: Text(unit.abbrev).frame(width: 45)) {
                        ForEach(0..<BitcoinUnit.default().values.count) {
                            let u = BitcoinUnit.default().values[$0]
                            Text(u.abbrev).tag(u)
                        }
                    }
                            .pickerStyle(MenuPickerStyle())
                            .padding(.bottom, 4)
                }
                        .padding([.leading, .trailing])
                        .background(
                                Line()
                                        .stroke(Color.appHorizon, style: StrokeStyle(lineWidth: 2, dash: [3]))
                                        .frame(height: 1)
                                        .padding(.top, 45)
                        )

                Text(model.requestDescription ?? "")
                        .padding()
                        .padding([.top, .bottom])

                Button {
                    intent(Scan.IntentSend(request: model.request, amount: Double(amount)!, unit: unit))
                } label: {
                    HStack {
                        Image("ic_send")
                                .renderingMode(.template)
                                .resizable()
                                .foregroundColor(Color.white)
                                .frame(width: 22, height: 22)
                        Text("Pay")
                                .font(.title2)
                                .foregroundColor(Color.white)
                    }
                            .padding(4)
                            .padding([.leading, .trailing], 12)
                            .background(Color.appHorizon)
                            .cornerRadius(100)
                            .opacity((amount.isEmpty || illegal) ? 0.4 : 1.0)
                }
                        .disabled(amount.isEmpty || illegal)
            }
                    .navigationBarTitle("Validate payment", displayMode: .inline)
                    .zIndex(1)
                    .transition(.asymmetric(insertion: .identity, removal: .opacity))
        }
    }

    struct SendingView: View {
        let model: Scan.ModelSending

        var body: some View {
            VStack {
                Text("Sending Payment...")
                        .font(.title)
                        .padding()
            }
                    .navigationBarTitle("Sending payment", displayMode: .inline)
                    .zIndex(0)
        }
    }
}


class ScanView_Previews: PreviewProvider {

    static let mockModel = Scan.ModelValidate(
            request: "lntb15u1p0hxs84pp5662ywy9px43632le69s5am03m6h8uddgln9cx9l8v524v90ylmesdq4xysyymr0vd4kzcmrd9hx7cqp2xqrrss9qy9qsqsp5xr4khzu3xter2z7dldnl3eqggut200vzth6cj8ppmqvx29hzm30q0as63ks9zddk3l5vf46lmkersynge3fy9nywwn8z8ttfdpak5ka9dvcnfrq95e6s06jacnsdryq8l8mrjkrfyd3vxgyv4axljvplmwsqae7yl9",
            amountSat: 1500,
            requestDescription: "1 Blockaccino"
    )

    static var previews: some View {
        mockView(ScanView(isShowing: .constant(true)))
                .previewDevice("iPhone 11")
    }

    #if DEBUG
    @objc class func injected() {
        UIApplication.shared.windows.first?.rootViewController = UIHostingController(rootView: previews)
    }
    #endif
}
