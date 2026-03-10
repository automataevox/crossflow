import AppKit
import Network
import Foundation

// MARK: - Protocol
struct ClipMessage: Codable {
    let type: String
    let content: String
    let source: String
}

enum Protocol {
    static let port: UInt16 = 35647
    static let serviceType = "_crossflow._tcp."
    
    static func encode(_ msg: ClipMessage) -> Data {
        let json = (try? JSONEncoder().encode(msg)).flatMap { String(data: $0, encoding: .utf8) } ?? "{}"
        return (json + "\n").data(using: .utf8) ?? Data()
    }
    
    static func decode(_ data: Data) -> ClipMessage? {
        guard let str = String(data: data, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines) else { return nil }
        return try? JSONDecoder().decode(ClipMessage.self, from: Data(str.utf8))
    }
}

// MARK: - Clipboard Monitor
class ClipboardMonitor {
    private var lastContent = ""
    private var timer: Timer?
    private let onChange: (String) -> Void
    private let pasteboard = NSPasteboard.general
    
    init(onChange: @escaping (String) -> Void) {
        self.onChange = onChange
    }
    
    func start() {
        timer = Timer.scheduledTimer(withTimeInterval: 0.4, repeats: true) { [weak self] _ in
            self?.check()
        }
        print("[Clipboard] ✓ Monitoring started (polling every 400ms)")
    }
    
    func stop() {
        timer?.invalidate()
        print("[Clipboard] Stopped")
    }
    
    private func check() {
        guard let text = pasteboard.string(forType: .string), !text.isEmpty else { return }
        if text != lastContent {
            lastContent = text
            print("[Clipboard] 📋 Local change detected: '\(text.prefix(40))...'")
            onChange(text)
        }
    }
    
    func write(_ text: String) {
        guard text != lastContent else {
            print("[Clipboard] Skipped write (same as current)")
            return
        }
        lastContent = text
        pasteboard.clearContents()
        pasteboard.setString(text, forType: .string)
        print("[Clipboard] ✓ Written from remote: '\(text.prefix(40))...'")
    }
}

// MARK: - TCP Server
class TCPServer {
    private var listener: NWListener?
    private let queue = DispatchQueue(label: "tcp.server")
    var onMessage: ((ClipMessage) -> Void)?
    
    var onDebugLog: ((String) -> Void)?
    
    func start() {
        do {
            let parameters = NWParameters.tcp
            parameters.allowLocalEndpointReuse = true
            parameters.acceptLocalOnly = false
            
            listener = try NWListener(using: parameters, on: NWEndpoint.Port(rawValue: Protocol.port)!)
            listener?.stateUpdateHandler = { [weak self] state in
                switch state {
                case .ready:
                    if let port = self?.listener?.port {
                        print("[TCP] ✓ Server listening on port \(port)")
                        self?.onDebugLog?("✅ TCP server on :\(port)")
                    }
                case .failed(let error):
                    print("[TCP] ✗ Server failed: \(error)")
                    self?.onDebugLog?("❌ TCP failed: \(error)")
                case .cancelled:
                    print("[TCP] Server cancelled")
                default:
                    print("[TCP] Server state: \(state)")
                }
            }
            listener?.newConnectionHandler = { [weak self] conn in
                self?.handle(conn)
            }
            listener?.start(queue: queue)
        } catch {
            print("[TCP] ✗ Failed to start: \(error)")
        }
    }
    
    func stop() {
        listener?.cancel()
    }
    
    private func handle(_ conn: NWConnection) {
        let endpoint = conn.endpoint
        print("[TCP] 📥 New connection from \(endpoint)")
        onDebugLog?("📥 Connection from \(endpoint)")
        
        conn.stateUpdateHandler = { [weak self] state in
            if case .failed(let error) = state {
                print("[TCP] Connection error: \(error)")
                self?.onDebugLog?("❌ Conn error: \(error)")
            }
        }
        
        conn.start(queue: queue)
        conn.receive(minimumIncompleteLength: 1, maximumLength: 65536) { [weak self] data, _, isComplete, error in
            if let error = error {
                print("[TCP] ✗ Receive error: \(error)")
                conn.cancel()
                return
            }
            
            if let data = data, !data.isEmpty {
                print("[TCP] Received \(data.count) bytes from \(endpoint)")
                if let msg = Protocol.decode(data) {
                    print("[TCP] ✓ Decoded message from '\(msg.source)': \(msg.content.prefix(40))...")
                    self?.onMessage?(msg)
                } else {
                    print("[TCP] ✗ Failed to decode message")
                }
            }
            
            conn.cancel()
        }
    }
}

// MARK: - Bonjour Service
class BonjourService: NSObject, NetServiceDelegate, NetServiceBrowserDelegate {
    private let deviceName: String
    private var service: NetService?
    private var browser: NetServiceBrowser?
    private var peers: [String: (host: String, port: Int)] = [:]
    private var resolvingServices: [NetService] = []
    private var udpListener: NWListener?
    private var broadcastTimer: Timer?
    
    var onPeerFound: ((String) -> Void)?
    var onPeerLost: ((String) -> Void)?
    var onDebugLog: ((String) -> Void)?
    
    init(deviceName: String) {
        self.deviceName = deviceName
    }
    
    func start() {
        // Advertise
        service = NetService(domain: "", type: Protocol.serviceType, name: deviceName, port: Int32(Protocol.port))
        service?.delegate = self
        service?.publish(options: .listenForConnections)
        onDebugLog?("📢 Advertising as '\(deviceName)'")
        
        // Browse
        browser = NetServiceBrowser()
        browser?.delegate = self
        browser?.searchForServices(ofType: Protocol.serviceType, inDomain: "")
        onDebugLog?("🔍 Browsing for services...")
        
        // Start UDP broadcast discovery (fallback for JmDNS compatibility)
        startUdpDiscovery()
    }
    
    func stop() {
        service?.stop()
        browser?.stop()
        udpListener?.cancel()
        broadcastTimer?.invalidate()
    }
    
    private func startUdpDiscovery() {
        // Start broadcasting immediately
        startBroadcasting()
        onDebugLog?("📡 UDP discovery started")
    }
    

    
    private func startBroadcasting() {
        // Send broadcasts every 5 seconds
        broadcastTimer = Timer.scheduledTimer(withTimeInterval: 5.0, repeats: true) { [weak self] _ in
            self?.sendBroadcast()
        }
        sendBroadcast()  // Send immediately
    }
    
    private func sendBroadcast() {
        let message = "CROSSFLOW:\(deviceName):\(Protocol.port)"
        guard let data = message.data(using: .utf8) else { return }
        
        // Broadcast to 255.255.255.255
        let endpoint = NWEndpoint.hostPort(host: "255.255.255.255", port: NWEndpoint.Port(rawValue: Protocol.port)!)
        let connection = NWConnection(to: endpoint, using: .udp)
        
        connection.start(queue: .global())
        connection.send(content: data, completion: .contentProcessed { _ in
            connection.cancel()
        })
    }
    
    func broadcast(_ text: String, from source: String) {
        let msg = ClipMessage(type: "clipboard", content: text, source: source)
        let data = Protocol.encode(msg)
        print("[Bonjour] Broadcasting to \(peers.count) peer(s)")
        for (name, info) in peers {
            send(data: data, to: info.host, port: info.port, peer: name)
        }
    }
    
    private func send(data: Data, to host: String, port: Int, peer: String) {
        guard let portValue = NWEndpoint.Port(rawValue: UInt16(port)) else { return }
        let conn = NWConnection(host: NWEndpoint.Host(host), port: portValue, using: .tcp)
        conn.stateUpdateHandler = { state in
            if case .ready = state {
                print("[Bonjour] Connected to \(peer) at \(host):\(port)")
                conn.send(content: data, completion: .contentProcessed { error in
                    if let error = error {
                        print("[Bonjour] Send error to \(peer): \(error)")
                    } else {
                        print("[Bonjour] ✓ Sent to \(peer)")
                    }
                    conn.cancel()
                })
            } else if case .failed(let error) = state {
                print("[Bonjour] Connection failed to \(peer): \(error)")
                conn.cancel()
            }
        }
        conn.start(queue: DispatchQueue(label: "send.\(peer)"))
    }
    
    // NetService delegates
    func netServiceDidPublish(_ sender: NetService) {
        onDebugLog?("✅ Published: \(sender.name)")
    }
    
    func netService(_ sender: NetService, didNotPublish errorDict: [String: NSNumber]) {
        onDebugLog?("❌ Publish failed: \(errorDict)")
    }
    
    func netServiceBrowser(_ browser: NetServiceBrowser, didFind service: NetService, moreComing: Bool) {
        onDebugLog?("🔍 Found: '\(service.name)'")
        if service.name == deviceName {
            return
        }
        service.delegate = self
        resolvingServices.append(service)
        service.resolve(withTimeout: 10.0)
        onDebugLog?("⏳ Resolving '\(service.name)'...")
    }
    
    func netServiceDidResolveAddress(_ sender: NetService) {
        print("[Bonjour] ✓ Resolved '\(sender.name)'")
        resolvingServices.removeAll { $0 === sender }
        
        guard sender.name != deviceName else {
            print("[Bonjour] Skipping self address")
            return
        }
        
        // Try to extract IPv4 address
        var ipAddress: String?
        if let addresses = sender.addresses {
            print("[Bonjour] Found \(addresses.count) address(es) for '\(sender.name)'")
            for addr in addresses {
                let data = addr as Data
                var hostname = [CChar](repeating: 0, count: Int(NI_MAXHOST))
                let result = data.withUnsafeBytes { ptr -> Int32 in
                    getnameinfo(
                        ptr.baseAddress?.assumingMemoryBound(to: sockaddr.self),
                        socklen_t(data.count),
                        &hostname,
                        socklen_t(hostname.count),
                        nil, 0,
                        NI_NUMERICHOST
                    )
                }
                
                if result == 0 {
                    let host = String(cString: hostname)
                    print("[Bonjour]   - Address: \(host)")
                    // Prefer IPv4
                    if host.contains(".") && !host.contains(":") {
                        ipAddress = host
                        break
                    } else if ipAddress == nil {
                        ipAddress = host
                    }
                }
            }
        }
        
        if let host = ipAddress {
            peers[sender.name] = (host, sender.port)
            onPeerFound?(sender.name)
            onDebugLog?("✅ Added: '\(sender.name)'")
        } else {
            onDebugLog?("❌ No IP for: '\(sender.name)'")
        }
    }
    
    func netService(_ sender: NetService, didNotResolve errorDict: [String: NSNumber]) {
        onDebugLog?("❌ Resolve failed: '\(sender.name)'")
        resolvingServices.removeAll { $0 === sender }
    }
    
    func netServiceBrowser(_ browser: NetServiceBrowser, didRemove service: NetService, moreComing: Bool) {
        onDebugLog?("🔴 Lost: '\(service.name)'")
        peers.removeValue(forKey: service.name)
        onPeerLost?(service.name)
    }
    
    func netServiceBrowserDidStopSearch(_ browser: NetServiceBrowser) {
        onDebugLog?("⏸️ Browser stopped")
    }
    
    func netServiceBrowser(_ browser: NetServiceBrowser, didNotSearch errorDict: [String: NSNumber]) {
        onDebugLog?("❌ Browser error: \(errorDict)")
    }
    
    func netServiceBrowserWillSearch(_ browser: NetServiceBrowser) {
        onDebugLog?("▶️ Browser started searching")
    }
}

// MARK: - Status Bar App
class AppDelegate: NSObject, NSApplicationDelegate {
    var statusItem: NSStatusItem!
    var popover: NSPopover!
    var deviceName: String!
    var clipboard: ClipboardMonitor!
    var tcpServer: TCPServer!
    var bonjour: BonjourService!
    var peers: [String] = []
    var debugLog: [String] = []
    
    func appendDebugLog(_ message: String) {
        let timestamp = DateFormatter.localizedString(from: Date(), dateStyle: .none, timeStyle: .medium)
        debugLog.append("[\(timestamp)] \(message)")
        if debugLog.count > 20 { debugLog.removeFirst() }
        if popover != nil && deviceName != nil {
            updatePopover()
        }
    }
    
    func applicationDidFinishLaunching(_ notification: Notification) {
        deviceName = Host.current().localizedName?.replacingOccurrences(of: " ", with: "_") ?? "Mac"
        
        appendDebugLog("🚀 CrossFlow started")
        appendDebugLog("📱 Device: \(deviceName)")
        
        print("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        print("CrossFlow macOS - Starting")
        print("Device Name: \(deviceName)")
        print("Protocol: \(Protocol.serviceType)")
        print("Port: \(Protocol.port)")
        print("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        
        // Status bar setup
        statusItem = NSStatusBar.system.statusItem(withLength: NSStatusItem.squareLength)
        if let button = statusItem.button {
            button.image = NSImage(systemSymbolName: "arrow.triangle.2.circlepath", accessibilityDescription: "CrossFlow")
            button.action = #selector(togglePopover)
            button.target = self
        }
        print("[UI] Status bar icon created")
        
        // Popover
        popover = NSPopover()
        popover.contentSize = NSSize(width: 300, height: 350)
        popover.behavior = .transient
        popover.contentViewController = createViewController()
        
        // TCP Server
        tcpServer = TCPServer()
        tcpServer.onMessage = { [weak self] msg in
            guard let self = self, msg.source != self.deviceName else { return }
            DispatchQueue.main.async {
                print("[App] 📥 Received clipboard from '\(msg.source)'")
                self.appendDebugLog("📥 Recv from '\(msg.source)'")
                self.clipboard.write(msg.content)
            }
        }
        tcpServer.onDebugLog = { [weak self] message in
            DispatchQueue.main.async {
                self?.appendDebugLog(message)
            }
        }
        tcpServer.start()
        
        // Bonjour
        bonjour = BonjourService(deviceName: deviceName)
        bonjour.onPeerFound = { [weak self] name in
            DispatchQueue.main.async {
                self?.peers.append(name)
                self?.appendDebugLog("🎉 Peer found: '\(name)'")
            }
        }
        bonjour.onPeerLost = { [weak self] name in
            DispatchQueue.main.async {
                self?.peers.removeAll { $0 == name }
                self?.appendDebugLog("👋 Peer lost: '\(name)'")
            }
        }
        bonjour.onDebugLog = { [weak self] message in
            DispatchQueue.main.async {
                self?.appendDebugLog(message)
            }
        }
        bonjour.start()
        
        // Clipboard
        clipboard = ClipboardMonitor { [weak self] text in
            guard let self = self else { return }
            print("[App] 📤 Local clipboard changed, broadcasting to \(self.peers.count) peer(s)")
            self.bonjour.broadcast(text, from: self.deviceName)
        }
        clipboard.start()
        
        print("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        print("✓ CrossFlow is running!")
        print("Look for the icon in your menu bar")
        print("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }
    
    @objc func togglePopover() {
        if popover.isShown {
            popover.performClose(nil)
        } else {
            if let button = statusItem.button {
                updatePopover()
                popover.show(relativeTo: button.bounds, of: button, preferredEdge: .minY)
            }
        }
    }
    
    func createViewController() -> NSViewController {
        let vc = NSViewController()
        vc.view = NSView(frame: NSRect(x: 0, y: 0, width: 300, height: 350))
        return vc
    }
    
    func updatePopover() {
        guard let view = popover?.contentViewController?.view, let deviceName = deviceName else { return }
        view.subviews.forEach { $0.removeFromSuperview() }
        
        let stack = NSStackView(frame: NSRect(x: 16, y: 16, width: 268, height: 318))
        stack.orientation = .vertical
        stack.alignment = .leading
        stack.spacing = 12
        
        // Title
        let title = NSTextField(labelWithString: "CrossFlow")
        title.font = .systemFont(ofSize: 18, weight: .semibold)
        stack.addArrangedSubview(title)
        
        // Status
        let status = NSTextField(labelWithString: "● Active")
        status.font = .systemFont(ofSize: 11)
        status.textColor = .systemGreen
        stack.addArrangedSubview(status)
        
        stack.addArrangedSubview(separator())
        
        // Device
        stack.addArrangedSubview(label("This Device", size: 10, color: .secondaryLabelColor))
        stack.addArrangedSubview(label(deviceName.replacingOccurrences(of: "_", with: " "), size: 13))
        
        stack.addArrangedSubview(separator())
        
        // Peers
        stack.addArrangedSubview(label("Connected Devices (\(peers.count))", size: 10, color: .secondaryLabelColor))
        if peers.isEmpty {
            stack.addArrangedSubview(label("No devices found", size: 12, color: .tertiaryLabelColor))
        } else {
            for peer in peers {
                let peerLabel = NSTextField(labelWithString: "● \(peer.replacingOccurrences(of: "_", with: " "))")
                peerLabel.font = .systemFont(ofSize: 12)
                peerLabel.textColor = .systemGreen
                stack.addArrangedSubview(peerLabel)
            }
        }
        
        // Spacer
        let spacer = NSView()
        stack.addArrangedSubview(spacer)
        stack.setHuggingPriority(.defaultLow, for: .vertical)
        
        // Debug Log
        if !debugLog.isEmpty {
            stack.addArrangedSubview(separator())
            stack.addArrangedSubview(label("Debug Log", size: 10, color: .secondaryLabelColor))
            let debugText = debugLog.suffix(5).joined(separator: "\n")
            let debugLabel = NSTextField(wrappingLabelWithString: debugText)
            debugLabel.font = .systemFont(ofSize: 9)
            debugLabel.textColor = .tertiaryLabelColor
            debugLabel.preferredMaxLayoutWidth = 268
            stack.addArrangedSubview(debugLabel)
        }
        
        // Info
        let info = NSTextField(wrappingLabelWithString: "Clipboard syncing active. Copy on any device, paste on another!")
        info.font = .systemFont(ofSize: 10)
        info.textColor = .secondaryLabelColor
        info.preferredMaxLayoutWidth = 268
        stack.addArrangedSubview(info)
        
        // Quit button
        let quit = NSButton(title: "Quit CrossFlow", target: self, action: #selector(quitApp))
        quit.bezelStyle = .rounded
        stack.addArrangedSubview(quit)
        
        view.addSubview(stack)
    }
    
    func label(_ text: String, size: CGFloat, color: NSColor = .labelColor) -> NSTextField {
        let label = NSTextField(labelWithString: text)
        label.font = .systemFont(ofSize: size)
        label.textColor = color
        return label
    }
    
    func separator() -> NSBox {
        let sep = NSBox()
        sep.boxType = .separator
        sep.setFrameSize(NSSize(width: 268, height: 1))
        return sep
    }
    
    @objc func quitApp() {
        NSApplication.shared.terminate(nil)
    }
}

// Entry point
let app = NSApplication.shared
let delegate = AppDelegate()
app.delegate = delegate
app.setActivationPolicy(.accessory)
app.run()
