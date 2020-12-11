import CryptoKit
import XCTest
@testable import Phoenix

class AppSecurityTests: XCTestCase {
    
    let testMnemonics = [
        "witch", "collapse", "practice", "feed", "shame", "open",
        "despair", "creek", "road", "again", "ice", "least"
    ]
    
    // account names are copasted from `AppSecurity` to keep them private
    let keyChainAccount = "securityFile_keychain"
    let biometricsAccount = "securityFile_biometrics"
    
    var store: GenericPasswordStore!

    override func setUp() {
        store = GenericPasswordStore()
    }

    override func tearDown() {
        try? store.deleteKey(account: keyChainAccount)
        try? store.deleteKey(account: biometricsAccount)
    }
    
    func test_Keychain_insertTestMnemonics_AppFirstLaunch_ShouldBeAbleToReadThem() throws{
        let addExpectation = XCTestExpectation(description: "add entry expectation")
        AppSecurity.shared.addKeychainEntry(mnemonics: testMnemonics){ error in
            XCTAssertNil(error)
            let key: SymmetricKey? = try? self.store.readKey(account: self.keyChainAccount)
            XCTAssertNotNil(key)
            addExpectation.fulfill()
        }
        let unlockExpectation = XCTestExpectation(description: "unlock expectation")
        AppSecurity.shared.tryUnlockWithKeychain { mnemonics, security in
            XCTAssertEqual(security, .none)
            XCTAssertEqual(mnemonics, self.testMnemonics)
            unlockExpectation.fulfill()
            
        }
        wait(for: [addExpectation, unlockExpectation], timeout: 10)
    }

}
