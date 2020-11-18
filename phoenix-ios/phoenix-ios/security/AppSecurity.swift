import Foundation
import CommonCrypto
import CryptoKit
import LocalAuthentication

/// Represents the availability of Biometrics on the current device.
/// Devices either support TouchID or FaceID,
/// but the user needs to have enabled and enrolled in the service.
///
enum BiometricStatus {
	
	case touchID_available
	case touchID_notAvailable
	case touchID_notEnrolled
	
	case faceID_available
	case faceID_notAvailable
	case faceID_notEnrolled
	
	case notAvailable
	
	func isAvailable() -> Bool {
		return (self == .touchID_available) || (self == .faceID_available)
	}
}

/// Represents the "security.json" file, where we store the wrapped credentials needed to decrypt the databaseKey.
///
private struct SecurityFile: Codable {
	
	let keychain: KeyInfo?
	let passphrase: KeyInfo?
	
	init(keychain: KeyInfo?) {
		self.keychain = keychain
		self.passphrase = nil
	}
	
	private enum CodingKeys: String, CodingKey {
		case keychain
		case passphrase
	}

	init(from decoder: Decoder) throws {
		let container = try decoder.container(keyedBy: CodingKeys.self)
		
		self.keychain = try container.decodeIfPresent(KeyInfo_ChaChaPoly.self, forKey: .keychain)
		self.passphrase = try container.decodeIfPresent(KeyInfo_ChaChaPoly.self, forKey: .passphrase)
	}
	
	func encode(to encoder: Encoder) throws {
		
		var container = encoder.container(keyedBy: CodingKeys.self)
		
		if let keychain = self.keychain as? KeyInfo_ChaChaPoly {
			try container.encode(keychain, forKey: .keychain)
		}
		if let passphrase = self.passphrase as? KeyInfo_ChaChaPoly {
			try container.encode(passphrase, forKey: .passphrase)
		}
	}
}

/// Generic typed container.
/// Allows us to switch to alternative encryption schemes in the future, if needed.
///
private protocol KeyInfo: Codable {
	var type: String { get }
}

/// ChaCha20-Poly1305
/// Via Apple's CryptoKit using ChaChaPoly.
/// 
private struct KeyInfo_ChaChaPoly: KeyInfo, Codable {
	let type: String // "ChaCha20-Poly1305"
	let nonce: Data
	let ciphertext: Data
	let tag: Data
	
	init(sealedBox: ChaChaPoly.SealedBox) {
		type = "ChaCha20-Poly1305"
		nonce = sealedBox.nonce.dataRepresentation
		ciphertext = sealedBox.ciphertext
		tag = sealedBox.tag
	}
	
	func toSealedBox() throws -> ChaChaPoly.SealedBox {
		return try ChaChaPoly.SealedBox(
			nonce      : ChaChaPoly.Nonce(data: self.nonce),
			ciphertext : self.ciphertext,
			tag        : self.tag
		)
	}
}

class AppSecurity {
	
	/// Singleton instance
	///
	public static let shared = AppSecurity()
	
	private init() {
		// reserved...
	}
	
	public func biometricStatus() -> BiometricStatus {
		
		let context = LAContext()
		
		var error : NSError?
		let result = context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error)
		
		if context.biometryType == .touchID {
			if result && (error == nil) {
				return .touchID_available
			} else {
				if let error = error as? LAError, error.code == .biometryNotEnrolled {
					return .touchID_notEnrolled
				} else {
					return .touchID_notAvailable
				}
			}
		}
		if context.biometryType == .faceID {
			if result && (error == nil) {
				return .faceID_available
			} else {
				if let error = error as? LAError, error.code == .biometryNotEnrolled {
					return .faceID_notEnrolled
				} else {
					return .faceID_notAvailable
				}
			}
		}
		
		return .notAvailable
	}
	
	private lazy var securityJsonUrl: URL = {
		
		guard let appSupportDir = try?
			FileManager.default.url(for: .applicationSupportDirectory,
			                         in: .userDomainMask,
			             appropriateFor: nil,
			                     create: true)
		else {
			fatalError("FileManager returned nil applicationSupportDirectory !")
		}
		
		return appSupportDir.appendingPathComponent("security.json", isDirectory: false)
	}()
	
	private func writeToDisk(sealedBox: ChaChaPoly.SealedBox) throws {
		
		let keyInfo = KeyInfo_ChaChaPoly(sealedBox: sealedBox)
		let securityFile = SecurityFile(keychain: keyInfo)
		
		let jsonData = try JSONEncoder().encode(securityFile)
		
		try jsonData.write(to: self.securityJsonUrl, options: [.atomic])
	}
	
	private func readFromDisk() throws -> SecurityFile? {
		
		let data: Data
		do {
			data = try Data(contentsOf: self.securityJsonUrl)
		} catch {
			// Probably the file doesn't exist
			return nil
		}
		
		return try JSONDecoder().decode(SecurityFile.self, from: data)
	}
	
	private let keychainAccount = "generic_test"
	
	public func keychainRoundTrip() throws {
		
		let keychain = GenericPasswordStore()
		
		let key1 = SymmetricKey(size: .bits256)
		print("key1 : \(key1.rawRepresentation.hexEncodedString())")
		
		try keychain.deleteKey(account: keychainAccount)
		try keychain.storeKey(key1, account: keychainAccount)
		
		guard let key2: SymmetricKey = try keychain.readKey(account: keychainAccount) else {
			print("Nothing found in keychain")
			return
		}
		
		print("key2 : \(key2.rawRepresentation.hexEncodedString())")
	}
	
	public func testRoundTrip() throws {
		
		let databaseKey = SymmetricKey(size: .bits256)
		let externalKey = SymmetricKey(size: .bits256) // used to wrap databaseKey
		
		print("databaseKey : \(databaseKey.rawRepresentation.hexEncodedString())")
		print("externalKey : \(externalKey.rawRepresentation.hexEncodedString())")
		print("-----------------------------------------------------------------------------")
		
		let plaintext = databaseKey.dataRepresentation
		
		let sealedBoxA = try ChaChaPoly.seal(plaintext, using: externalKey)
		try writeToDisk(sealedBox: sealedBoxA)
		
		let securityFile = try readFromDisk()
		let keyInfo = securityFile?.keychain as? KeyInfo_ChaChaPoly
		
		if let sealedBoxB = try keyInfo?.toSealedBox() {
			
			let decrypted = try ChaChaPoly.open(sealedBoxB, using: externalKey)
			
			print("databaseKey : \(databaseKey.rawRepresentation.hexEncodedString())")
			print("decrypted   : \(decrypted.hexEncodedString())")
		
			let keychain = GenericPasswordStore()
			
			try keychain.deleteKey(account: keychainAccount)
			try keychain.storeKey(externalKey, account: keychainAccount)
		}
	}
	
	public func testReadDatabaseKey() throws {
		
		let keychain = GenericPasswordStore()
		
		guard let externalKey: SymmetricKey = try keychain.readKey(account: keychainAccount) else {
			print("Nothing found in keychain")
			return
		}
		
		print("externalKey : \(externalKey.rawRepresentation.hexEncodedString())")
		
	//	do {
	//		try FileManager.default.removeItem(at: self.securityJsonUrl)
	//	} catch {/* throws an error on FileNotFound, which is annoying */}
		
		
		guard let securityFile = try readFromDisk() else {
			print("Nothing found on disk")
			return
		}
		
		if let keychain = securityFile.keychain as? KeyInfo_ChaChaPoly,
		   let sealedBox = try? keychain.toSealedBox()
		{
			let decrypted = try ChaChaPoly.open(sealedBox, using: externalKey)
			
			print("databaseKey : \(decrypted.hexEncodedString())")
		}
	}
}
