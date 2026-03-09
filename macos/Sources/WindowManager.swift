import AppKit

/// Manages the optional info/settings window for the macOS menu bar app.
/// The window can be closed and reopened from the menu bar without affecting the background sync.
final class WindowManager: NSObject, NSWindowDelegate {

    private var window: NSWindow?
    private let deviceName: String

    var onWindowClosed: (() -> Void)?

    init(deviceName: String) {
        self.deviceName = deviceName
        super.init()
    }

    func showWindow() {
        // Create window if it doesn't exist
        if window == nil {
            createWindow()
        }
        // Show and bring to front
        window?.makeKeyAndOrderFront(nil)
        NSApp.activate(ignoringOtherApps: true)
    }

    func hideWindow() {
        window?.orderOut(nil)
    }

    private func createWindow() {
        let frame = NSRect(x: 100, y: 100, width: 480, height: 600)
        window = NSWindow(contentRect: frame,
                          styleMask: [.titled, .closable, .miniaturizable, .resizable],
                          backing: .buffered,
                          defer: false)
        
        window?.title = "CrossFlow — Device Status"
        window?.delegate = self
        window?.isReleasedWhenClosed = false  // Keep window in memory when closed
        
        // Create content view with status info
        let contentView = NSView(frame: frame)
        contentView.wantsLayer = true
        
        let stackView = NSStackView(frame: NSRect(x: 20, y: 20, width: 440, height: 560))
        stackView.orientation = .vertical
        stackView.spacing = 16
        
        // Title
        let titleLabel = NSTextField(frame: NSRect(x: 0, y: 0, width: 440, height: 30))
        titleLabel.stringValue = "CrossFlow"
        titleLabel.font = NSFont.systemFont(ofSize: 18, weight: .semibold)
        titleLabel.isEditable = false
        titleLabel.isBezeled = false
        titleLabel.drawsBackground = false
        stackView.addArrangedSubview(titleLabel)
        
        // Device name
        let deviceLabel = NSTextField(frame: NSRect(x: 0, y: 0, width: 440, height: 16))
        deviceLabel.stringValue = "Device: \(deviceName.replacingOccurrences(of: "_", with: " "))"
        deviceLabel.font = NSFont.systemFont(ofSize: 12)
        deviceLabel.textColor = NSColor.secondaryLabelColor
        deviceLabel.isEditable = false
        deviceLabel.isBezeled = false
        deviceLabel.drawsBackground = false
        stackView.addArrangedSubview(deviceLabel)
        
        let separator = NSBox()
        separator.boxType = .separator
        stackView.addArrangedSubview(separator)
        
        // Status message
        let statusLabel = NSTextField(frame: NSRect(x: 0, y: 0, width: 440, height: 60))
        statusLabel.stringValue = "CrossFlow is running in the background and continuously syncing your clipboard across all connected devices on the local network.\n\nYou can close this window — the app will continue syncing."
        statusLabel.font = NSFont.systemFont(ofSize: 11)
        statusLabel.isEditable = false
        statusLabel.isBezeled = false
        statusLabel.drawsBackground = false
        statusLabel.lineBreakMode = .byWordWrapping
        statusLabel.preferredMaxLayoutWidth = 440
        stackView.addArrangedSubview(statusLabel)
        
        contentView.addSubview(stackView)
        window?.contentView = contentView
    }

    // MARK: NSWindowDelegate

    func windowWillClose(_ notification: Notification) {
        print("[WindowManager] Info window closed, app continues running in menu bar")
        onWindowClosed?()
    }
}
