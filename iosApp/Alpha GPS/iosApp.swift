import SwiftUI

@main
struct iosApp: App {
    /// Ensures the CoreBluetooth central manager is created early on every
    /// launch — including background launches for BLE state restoration.
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea()
        }
    }
}

