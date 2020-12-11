/// Base view doing nothing but used for unit testing
/// this way app initialization does not interfere with
/// what is being tested
import SwiftUI

#if DEBUG
struct TestContentView: View {
    var body: some View {
        Text("Running tests...")
    }
}

struct TestContentView_Previews: PreviewProvider {
    static var previews: some View {
        TestContentView()
    }
}
#endif
