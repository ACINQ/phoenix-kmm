import SwiftUI
import PhoenixShared
import os


//extension View {
//    func mvi<Model: MVI.Model, Intent: MVI.Intent, Content: View>(
//            _ getController: @escaping (ControllerFactory) -> MVIController<Model, Intent>,
//            background: Bool = false,
//            onModel: ((MVIContent<Model, Intent, Content>.ModelChange) -> Void)? = nil,
//            @ViewBuilder content: @escaping (Model, @escaping (Intent) -> Void) -> Content
//    ) -> MVIContent<Model, Intent, Content> {
//        MVIContent(getController, background: background, onModel: onModel, content: content)
//    }
//}
