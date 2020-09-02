import SwiftUI
import PhoenixShared

protocol MVIView : View {
    associatedtype Model: MVI.Model
    associatedtype Intent: MVI.Intent

    typealias IntentReceiver = (Intent) -> Void
}

extension MVIView {
    func mvi<Content: View>(@ViewBuilder content: @escaping (Model, @escaping IntentReceiver) -> Content) -> MVIContext<Model, Intent, Content> {
        MVIContext(Model.self, Intent.self, content: content)
    }
}