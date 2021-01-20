import Foundation

/// Represents the security options enabled by the user.
///
struct EnabledSecurity: OptionSet {
	let rawValue: Int

	static let biometrics = EnabledSecurity(rawValue: 1 << 0)
	static let passphrase = EnabledSecurity(rawValue: 1 << 1)

	static let none: EnabledSecurity = []
	static let both: EnabledSecurity = [.biometrics, .passphrase]
}
