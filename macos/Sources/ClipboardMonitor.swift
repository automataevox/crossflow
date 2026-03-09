import AppKit
import Foundation

/// Polls NSPasteboard every 400 ms. Calls `onChange` when text changes.
/// Use `write(_:)` to push text to the clipboard without triggering `onChange`.
final class ClipboardMonitor {

    private let pasteboard = NSPasteboard.general
    private var changeCount: Int
    private var lastContent = ""
    private var timer: Timer?
    let onChange: (String) -> Void

    init(onChange: @escaping (String) -> Void) {
        self.onChange    = onChange
        self.changeCount = NSPasteboard.general.changeCount
    }

    func start() {
        timer = Timer.scheduledTimer(withTimeInterval: 0.4, repeats: true) { [weak self] _ in
            self?.poll()
        }
    }

    func stop() {
        timer?.invalidate()
        timer = nil
    }

    func write(_ text: String) {
        guard text != lastContent else { return }
        lastContent  = text
        changeCount  = pasteboard.changeCount + 1   // pre-bump so poll ignores it
        pasteboard.clearContents()
        pasteboard.setString(text, forType: .string)
        changeCount = pasteboard.changeCount        // sync after write
    }

    private func poll() {
        let current = pasteboard.changeCount
        guard current != changeCount else { return }
        changeCount = current
        guard let text = pasteboard.string(forType: .string),
              !text.isEmpty, text != lastContent else { return }
        lastContent = text
        onChange(text)
    }
}
