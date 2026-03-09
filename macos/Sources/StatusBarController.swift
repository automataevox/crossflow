import AppKit

/// Controls the menu bar item and popup menu.
final class StatusBarController {

    private var statusItem: NSStatusItem!
    private var menu: NSMenu!
    private var deviceItems: [NSMenuItem] = []
    private var statusItem_label: NSMenuItem!

    var onQuit: (() -> Void)?
    var onShowWindow: (() -> Void)?

    func setup() {
        statusItem = NSStatusBar.system.statusItem(withLength: NSStatusItem.squareLength)

        if let button = statusItem.button {
            button.image = NSImage(systemSymbolName: "doc.on.clipboard",
                                   accessibilityDescription: "CrossFlow")
            button.image?.isTemplate = true
        }

        menu = NSMenu()

        statusItem_label = NSMenuItem(title: "CrossFlow — Active", action: nil, keyEquivalent: "")
        statusItem_label.isEnabled = false
        menu.addItem(statusItem_label)

        menu.addItem(.separator())

        let showWindowItem = NSMenuItem(title: "Show Window",
                                        action: #selector(showWindowAction),
                                        keyEquivalent: "")
        showWindowItem.target = self
        menu.addItem(showWindowItem)

        menu.addItem(.separator())

        let devicesHeader = NSMenuItem(title: "Devices", action: nil, keyEquivalent: "")
        devicesHeader.isEnabled = false
        menu.addItem(devicesHeader)

        menu.addItem(.separator())
        menu.addItem(NSMenuItem(title: "Quit CrossFlow",
                                action: #selector(quitAction),
                                keyEquivalent: "q"))
        for item in menu.items where item.action == #selector(quitAction) {
            item.target = self
        }

        statusItem.menu = menu
    }

    func updateDevices(_ names: [String]) {
        // Remove old device items
        deviceItems.forEach { menu.removeItem($0) }
        deviceItems.removeAll()

        let insertIndex = menu.items.firstIndex(where: { $0.isSeparatorItem && menu.items.firstIndex(of: $0)! > 2 }) ?? 3

        if names.isEmpty {
            let none = NSMenuItem(title: "  No devices found", action: nil, keyEquivalent: "")
            none.isEnabled = false
            menu.insertItem(none, at: insertIndex)
            deviceItems.append(none)
        } else {
            for (i, name) in names.enumerated() {
                let item = NSMenuItem(title: "  ● \(name.replacingOccurrences(of: "_", with: " "))",
                                      action: nil, keyEquivalent: "")
                item.isEnabled = false
                menu.insertItem(item, at: insertIndex + i)
                deviceItems.append(item)
            }
        }
    }

    @objc private func quitAction() {
        onQuit?()
    }

    @objc private func showWindowAction() {
        onShowWindow?()
    }
}
