import SwiftUI
import PhoenixShared
import os

protocol MVIView : View {
    associatedtype Model: MVI.Model
    associatedtype Intent: MVI.Intent

    typealias IntentReceiver = (Intent) -> Void
}

extension MVIView {
    func mvi<Content: View>(
            _ getController: @escaping (ControllerFactory) -> MVIController<Model, Intent>,
            background: Bool = false,
            onModel: ((MVIContent<Model, Intent, Content>.ModelChange) -> Void)? = nil,
            @ViewBuilder content: @escaping (Model, @escaping IntentReceiver) -> Content
    ) -> MVIContent<Model, Intent, Content> {
        MVIContent(getController, background: background, onModel: onModel, content: content)
    }
}
