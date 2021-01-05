import UIKit
import PhoenixShared
import os.log
import Firebase

#if DEBUG && false
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "AppDelegate"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

@UIApplicationMain
class AppDelegate: UIResponder, UIApplicationDelegate, MessagingDelegate {

	static func get() -> AppDelegate {
		UIApplication.shared.delegate as! AppDelegate
	}
	
    let business: PhoenixBusiness
	
	private var walletLoaded = false
	private var fcmToken: String? = nil
	
	private var badgeCount = 0

    override init() {
        setenv("CFNETWORK_DIAGNOSTICS", "3", 1);

        business = PhoenixBusiness(ctx: PlatformContext())
        business.start()
    }
	
	// --------------------------------------------------
	// MARK: UIApplication Lifecycle
	// --------------------------------------------------

    func application(
		_ application: UIApplication,
		didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
	) -> Bool {

        UIKitAppearance()
		
		#if !targetEnvironment(simulator) // push notifications don't work on iOS simulator
			UIApplication.shared.registerForRemoteNotifications()
		#endif
		
		FirebaseApp.configure()
		Messaging.messaging().delegate = self

        #if DEBUG
            var injectionBundlePath = "/Applications/InjectionIII.app/Contents/Resources"
            #if targetEnvironment(macCatalyst)
                injectionBundlePath = "\(injectionBundlePath)/macOSInjection.bundle"
            #elseif os(iOS)
                injectionBundlePath = "\(injectionBundlePath)/iOSInjection.bundle"
            #elseif os(tvOS)
                injectionBundlePath = "\(injectionBundlePath)/tvOSInjection.bundle"
            #endif
            Bundle(path: injectionBundlePath)?.load()
        #endif

        return true
    }
	
	func applicationDidBecomeActive(_ application: UIApplication) {
		badgeCount = 0
	}
	
	// --------------------------------------------------
	// MARK: UISceneSession Lifecycle
	// --------------------------------------------------

	func application(
		_ application: UIApplication,
		configurationForConnecting connectingSceneSession: UISceneSession,
		options: UIScene.ConnectionOptions
	) -> UISceneConfiguration {
		// Called when a new scene session is being created.
		// Use this method to select a configuration to create the new scene with.
		return UISceneConfiguration(name: "Default Configuration", sessionRole: connectingSceneSession.role)
	}

	func application(_ application: UIApplication, didDiscardSceneSessions sceneSessions: Set<UISceneSession>) {
		// Called when the user discards a scene session.
		// If any sessions were discarded while the application was not running,
		// this will be called shortly after application:didFinishLaunchingWithOptions.
		// Use this method to release any resources that were specific to the discarded
		// scenes, as they will not return.
	}

	// --------------------------------------------------
	// MARK: Push Notifications
	// --------------------------------------------------
	
	func application(
		_ application: UIApplication,
		didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
	) -> Void
	{
		log.trace("application(didRegisterForRemoteNotificationsWithDeviceToken:)")
		
		let pushToken = deviceToken.map { String(format: "%02.2hhx", $0) }.joined()
		log.debug("pushToken: \(pushToken)")
		
		Messaging.messaging().apnsToken = deviceToken
		
	//	registerPushToken(pushToken)
	//	requestPermissionForLocalNotifications()
	}

	func application(
		_ application: UIApplication,
		didFailToRegisterForRemoteNotificationsWithError error: Error
	) -> Void
	{
		log.trace("application(didFailToRegisterForRemoteNotificationsWithError:)")
		
		log.error("Remote notification support is unavailable due to error: \(error.localizedDescription)")
	}

	func application(
		_ application: UIApplication,
		didReceiveRemoteNotification userInfo: [AnyHashable : Any],
		fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void
	) -> Void
	{
		// Handle incoming remote notification
		
		log.debug("Received remote notification: \(userInfo)")
	}
	
	func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
		assertMainThread()
		
		log.trace("messaging(:didReceiveRegistrationToken:)")
		log.debug("Firebase registration token: \(String(describing: fcmToken))")
		
		self.fcmToken = fcmToken
		maybeRegisterFcmToken()
	}
	
	// --------------------------------------------------
	// MARK: Local Notifications
	// --------------------------------------------------
	
	func requestPermissionForLocalNotifications() {
		
		let center = UNUserNotificationCenter.current()
		center.requestAuthorization(options: [.alert, .sound, .badge]) { granted, error in
			
			log.debug("UNUserNotificationCenter.requestAuthorization(): granted = \(granted)")
			if let error = error {
				// How can an error occur ?!? Apple doesn't tell us.
				log.debug("UNUserNotificationCenter.requestAuthorization(): \(String(describing: error))")
			}
		}
	}
	
	func displayLocalNotification() {
		
		UNUserNotificationCenter.current().getNotificationSettings { settings in
			guard settings.authorizationStatus == .authorized else {
				return
			}

			// The user can independently enabled/disable:
			// - alerts
			// - sounds
			// - badges
			//
			// So we may only be able to badge the app icon, and that's it.
			self.badgeCount += 1
			
			let content = UNMutableNotificationContent()
			content.title = "Store Changed"
			content.body = "20,000 sats for: Invoice name"
			
			let uuidString = UUID().uuidString // maybe replace with paymentId ?
			let request = UNNotificationRequest(identifier: uuidString, content: content, trigger: nil)
			
			UNUserNotificationCenter.current().add(request) { error in
				if let error = error {
					log.error("NotificationCenter.add(request): error: \(String(describing: error))")
				}
			}
		}
	}

	// --------------------------------------------------
	// MARK: PhoenixBusiness
	// --------------------------------------------------
	
	func loadWallet(mnemonics: [String]) -> Void {
		log.trace("loadWallet(mnemonics:)")
		
		let seed = business.prepWallet(mnemonics: mnemonics, passphrase: "")
		loadWallet(seed: seed)
	}
	
	func loadWallet(seed: KotlinByteArray) -> Void {
		log.trace("loadWallet(seed:)")
		assertMainThread()
		
		if !walletLoaded {
			business.loadWallet(seed: seed)
			walletLoaded = true
			maybeRegisterFcmToken()
		}
	}
	
	func maybeRegisterFcmToken() -> Void {
		log.trace("maybeRegisterFcmToken()")
		assertMainThread()
		
		if walletLoaded && fcmToken != nil {

			// Todo: What happens if the user disables "background app refresh" ?
		//	if UIApplication.shared.backgroundRefreshStatus == .available {}
			
			let newTuple = FcmTokenInfo(
				nodeID: business.nodeID(),
				fcmToken: fcmToken  ?? ""
			)
			var needsRegister = true
			
			if let oldTuple = Prefs.shared.fcmTokenInfo {
				needsRegister = oldTuple == newTuple
			}
			
			if needsRegister {
				business.registerFcmToken(token: self.fcmToken)
				
				// We cannot reliably do this here:
			//	Prefs.shared.fcmTokenInfo = newTuple
				//
				// Why not ?
				// We should wait until we know the server has received and processed our token.
				// But currently the server doesn't send an ack for our message ...
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Utilities
	// --------------------------------------------------
	
	func assertMainThread() -> Void {
		assert(Thread.isMainThread, "Improper thread: expected main thread; Thread-unsafe code ahead")
	}
}

