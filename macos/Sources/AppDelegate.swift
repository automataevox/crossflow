import AppKit
import Foundation

@main
final class AppDelegate: NSObject, NSApplicationDelegate {

    private let deviceName: String = {
        (Host.current().localizedName ?? ProcessInfo.processInfo.hostName)
            .replacingOccurrences(of: " ", with: "_")
            .prefix(32)
            .description
    }()

    private var tcpServer: TCPServer!
    private var bonjourService: BonjourService!
    private var clipboardMonitor: ClipboardMonitor!
    private var statusBarController: StatusBarController!
    private var windowManager: WindowManager!
    private var knownDevices: [String] = []

    // ── App launch ────────────────────────────────────────────────────────────

    func applicationDidFinishLaunching(_ notification: Notification) {
        // Hide from Dock — menu bar only
        // (Also set LSUIElement = YES in Info.plist)
        NSApp.setActivationPolicy(.accessory)

        setupWindowManager()
        setupTCPServer()
        setupBonjour()
        setupClipboardMonitor()
        setupStatusBar()

        print("[AppDelegate] CrossFlow started as \(deviceName)")
        print("[AppDelegate] ✓ Syncing in background, click menu bar icon to manage")
    }

    func applicationWillTerminate(_ notification: Notification) {
        clipboardMonitor.stop()
        bonjourService.stop()
        tcpServer.stop()
    }

    // ── Keep running in background when window closes ────────────────────────

    func applicationShouldTerminateWhenLastWindowClosed(_ sender: NSApplication) -> Bool {
        // Return false to keep the app running even when the last window is closed
        // The app continues syncing in the menu bar
        print("[AppDelegate] Last window closed, keeping app running in menu bar...")
        return false
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private func setupWindowManager() {
        windowManager = WindowManager(deviceName: deviceName)
        windowManager.onWindowClosed = { [weak self] in
            self?.statusBarController.updateDevices(self?.knownDevices ?? [])
        }
    }

    private func setupTCPServer() {
        tcpServer = TCPServer()
        tcpServer.onMessage = { [weak self] msg in
            guard let self = self else { return }
            if msg.source == self.deviceName { return }   // echo
            DispatchQueue.main.async {
                self.clipboardMonitor.write(msg.content)
                print("[AppDelegate] Clipboard set from \(msg.source)")
            }
        }
        tcpServer.start()
    }

    private func setupBonjour() {
        bonjourService = BonjourService(deviceName: deviceName)
        bonjourService.onPeerFound = { [weak self] name in
            DispatchQueue.main.async {
                guard let self = self else { return }
                if !self.knownDevices.contains(name) {
                    self.knownDevices.append(name)
                    self.statusBarController.updateDevices(self.knownDevices)
                }
            }
        }
        bonjourService.onPeerLost = { [weak self] name in
            DispatchQueue.main.async {
                guard let self = self else { return }
                self.knownDevices.removeAll { $0 == name }
                self.statusBarController.updateDevices(self.knownDevices)
            }
        }
        bonjourService.startAdvertising()
        bonjourService.startBrowsing()
    }

    private func setupClipboardMonitor() {
        clipboardMonitor = ClipboardMonitor { [weak self] text in
            guard let self = self else { return }
            print("[AppDelegate] Local clipboard changed, broadcasting…")
            self.bonjourService.broadcast(text, from: self.deviceName)
        }
        clipboardMonitor.start()
    }

    private func setupStatusBar() {
        statusBarController = StatusBarController()
        statusBarController.setup()
        statusBarController.onQuit = {
            NSApplication.shared.terminate(nil)
        }
        statusBarController.onShowWindow = { [weak self] in
            self?.windowManager.showWindow()
        }
        statusBarController.updateDevices([])
    }
}
