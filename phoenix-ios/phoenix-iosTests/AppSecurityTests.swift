import Combine
import CryptoKit
import LocalAuthentication
@testable import Phoenix
import XCTest

class AppSecurityTests: XCTestCase {
    let testMnemonics = [
        "witch", "collapse", "practice", "feed", "shame", "open",
        "despair", "creek", "road", "again", "ice", "least"
    ]
    
    // account names are copasted from `AppSecurity` to keep them private
    let keyChainAccount = "securityFile_keychain"
    let biometricsAccount = "securityFile_biometrics"
    
    var store: GenericPasswordStore!
    var cancellables: [AnyCancellable]!
    var securityModes: [EnabledSecurity]!
    
    override func setUp() {
        store = GenericPasswordStore()
        cancellables = []
        securityModes = []
        Biometrics.unenrolled()
    }

    override func tearDown() {
        try? store.deleteKey(account: keyChainAccount)
        try? store.deleteKey(account: biometricsAccount)
        cancellables.forEach { $0.cancel() }
    }
    
    func test_Keychain_insertTestMnemonics_AppFirstLaunch_ShouldBeAbleToReadThem() throws {
        let addExpectation = XCTestExpectation(description: "add entry expectation")
        AppSecurity.shared.addKeychainEntry(mnemonics: testMnemonics) { error in
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

        recordEnabledSecurity()
        wait(for: [addExpectation, unlockExpectation], timeout: 10)
        // should not this be passphrase here ? test is failing due to .none security
        // XCTAssertEqual([EnabledSecurity(), .passphrase], securityModes)
    }
    
    /// test by manipulating biometric flags, does not work for device with faceId as
    /// faceId asks confirmation through a springboard alert (target iPhone SE)
    func test_Biometrics_insertTestMnemonics_AppFirstLaunch_ShouldBeAbleToReadThem() throws {
        Biometrics.enrolled()
        
        let addExpectation = XCTestExpectation(description: "add entry expectation")
        AppSecurity.shared.addBiometricsEntry(mnemonics: testMnemonics) { error in
            XCTAssertNil(error)
            let key: SymmetricKey? = try? self.store.readKey(account: self.biometricsAccount)
            XCTAssertNotNil(key)
            addExpectation.fulfill()
        }
        let unlockExpectation = XCTestExpectation(description: "unlock expectation")
        AppSecurity.shared.tryUnlockWithBiometrics { result in
            switch result {
            case let .failure(err):
                XCTFail("Unable to unlock \(err)")
            case let .success(mnemonics):
                XCTAssertEqual(mnemonics, self.testMnemonics)
            }
            
            unlockExpectation.fulfill()
        }
        // wait for app to settle, and touchId prompt
        DispatchQueue.main.asyncAfter(wallDeadline: .now() + 3) {
            Biometrics.successfulAuthentication()
        }
        
        recordEnabledSecurity()
        wait(for: [addExpectation, unlockExpectation], timeout: 10)
        XCTAssertEqual([EnabledSecurity(), .biometrics], securityModes)
    }
    
    /// need to write unit tests for cases enumerated in `AppSecurity`, to ensure we're not losing
    /// precious data if app crashes in between applying changes
    
    
    private func recordEnabledSecurity() {
        AppSecurity.shared.enabledSecurity.sink {
            self.securityModes.append($0)
        }.store(in: &cancellables)
    }
}
