import AppKit
import Network
import Foundation
import os.log

// MARK: - Logger
let logger = Logger(subsystem: "dev.crossflow.macos", category: "main")

// Debug file logger (since os_log isn't appearing)
func debugLog(_ message: String) {
    let timestamp = Date().timeIntervalSince1970
    let logMessage = "[\(timestamp)] \(message)\n"
    if let data = logMessage.data(using: .utf8) {
        let fileURL = FileManager.default.homeDirectoryForCurrentUser
            .appendingPathComponent("crossflow-debug.log")
        if FileManager.default.fileExists(atPath: fileURL.path) {
            if let fileHandle = try? FileHandle(forWritingTo: fileURL) {
                fileHandle.seekToEndOfFile()
                fileHandle.write(data)
                fileHandle.closeFile()
            }
        } else {
            try? data.write(to: fileURL)
        }
    }
}

// MARK: - Protocol
struct ClipMessage: Codable {
    let type: String
    let content: String
    let source: String
    
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.type = try container.decodeIfPresent(String.self, forKey: .type) ?? "clipboard"
        self.content = try container.decode(String.self, forKey: .content)
        self.source = try container.decode(String.self, forKey: .source)
    }
    
    init(type: String = "clipboard", content: String, source: String) {
        self.type = type
        self.content = content
        self.source = source
    }
}

enum Protocol {
    static let port: UInt16 = 35647
    static let serviceType = "_crossflow._tcp."
    
    static func encode(_ msg: ClipMessage) -> Data {
        let json = (try? JSONEncoder().encode(msg)).flatMap { String(data: $0, encoding: .utf8) } ?? "{}"
        return (json + "\n").data(using: .utf8) ?? Data()
    }
    
    static func decode(_ data: Data) -> ClipMessage? {
        guard let str = String(data: data, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines) else {
            debugLog("[Protocol] UTF-8 conversion failed")
            logger.error("[Protocol] ✗ Failed to convert data to UTF-8 string")
            return nil
        }
        
        debugLog("[Protocol] Decoding: '\(str)'")
        logger.info("[Protocol] Attempting to decode: '\(str)'")
        
        do {
            let msg = try JSONDecoder().decode(ClipMessage.self, from: Data(str.utf8))
            debugLog("[Protocol] Success: type=\(msg.type)")
            logger.info("[Protocol] ✓ Decode success: type=\(msg.type), source=\(msg.source)")
            return msg
        } catch {
            debugLog("[Protocol] JSON error: \(error)")
            logger.error("[Protocol] ✗ JSON decode error: \(error)")
            logger.error("[Protocol] ✗ Failed string: '\(str)'")
            return nil
        }
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
                        debugLog("[TCP] Server listening on port \(port)")
                        logger.info("[TCP] ✓ Server listening on port \(String(describing: port))")
                        self?.onDebugLog?("✅ TCP server on :\(port)")
                    }
                case .failed(let error):
                    logger.error("[TCP] ✗ Server failed: \(error)")
                    self?.onDebugLog?("❌ TCP failed: \(error)")
                case .cancelled:
                    logger.info("[TCP] Server cancelled")
                default:
                    logger.debug("[TCP] Server state: \(String(describing: state))")
                }
            }
            listener?.newConnectionHandler = { [weak self] conn in
                self?.handle(conn)
            }
            listener?.start(queue: queue)
        } catch {
            logger.error("[TCP] ✗ Failed to start: \(error)")
        }
    }
    
    func stop() {
        listener?.cancel()
    }
    
    private func handle(_ conn: NWConnection) {
        let endpoint = conn.endpoint
        let endpointStr = String(describing: endpoint)
        debugLog("[TCP] New connection from \(endpointStr)")
        logger.info("[TCP] 📥 New connection from \(endpointStr)")
        onDebugLog?("📥 Connection from \(endpoint)")
        
        conn.stateUpdateHandler = { [weak self] state in
            if case .failed(let error) = state {
                logger.error("[TCP] Connection error: \(error)")
                self?.onDebugLog?("❌ Conn error: \(error)")
            }
        }
        
        conn.start(queue: queue)
        conn.receive(minimumIncompleteLength: 1, maximumLength: 65536) { [weak self] data, _, isComplete, error in
            if let error = error {
                logger.error("[TCP] ✗ Receive error: \(error)")
                conn.cancel()
                return
            }
            
            if let data = data, !data.isEmpty {
                debugLog("[TCP] Received \(data.count) bytes")
                logger.info("[TCP] Received \(data.count) bytes from \(endpointStr)")
                let hexDump = data.map { String(format: "%02x", $0) }.joined(separator: " ")
                debugLog("[TCP] Hex: \(hexDump)")
                logger.info("[TCP] Raw hex: \(hexDump)")
                if let rawString = String(data: data, encoding: .utf8) {
                    debugLog("[TCP] String: '\(rawString)'")
                    logger.info("[TCP] Raw string: '\(rawString)'")
                    logger.info("[TCP] Raw string (escaped): \(rawString.debugDescription)")
                } else {
                    debugLog("[TCP] Not valid UTF-8")
                    logger.warning("[TCP] ⚠️ Not valid UTF-8 data")
                }
                if let msg = Protocol.decode(data) {
                    debugLog("[TCP] Decoded: type=\(msg.type) from=\(msg.source)")
                    logger.info("[TCP] ✓ Decoded message from '\(msg.source)': '\(msg.content)'")
                    self?.onMessage?(msg)
                } else {
                    debugLog("[TCP] Decode FAILED")
                    logger.error("[TCP] ✗ Failed to decode message")
                    self?.onDebugLog?("❌ Decode failed")
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
        
        // Start listening for UDP broadcasts
        DispatchQueue.global(qos: .background).async { [weak self] in
            self?.listenForUdpBroadcasts()
        }
        
        onDebugLog?("📡 UDP discovery started")
    }
    
    private func listenForUdpBroadcasts() {
        print("[UDP] Creating socket...")
        let sock = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP)
        guard sock >= 0 else {
            let err = errno
            let errMsg = String(cString: strerror(err))
            print("[UDP] ⚠️ Socket creation failed: errno \(err) - \(errMsg)")
            DispatchQueue.main.async { [weak self] in
                self?.onDebugLog?("⚠️ UDP socket failed: \(err) - \(errMsg)")
            }
            return
        }
        
        print("[UDP] Socket created successfully (fd \(sock))")
        
        // Set socket options
        var yes: Int32 = 1
        setsockopt(sock, SOL_SOCKET, SO_REUSEADDR, &yes, socklen_t(MemoryLayout<Int32>.size))
        setsockopt(sock, SOL_SOCKET, SO_REUSEPORT, &yes, socklen_t(MemoryLayout<Int32>.size))
        setsockopt(sock, SOL_SOCKET, SO_BROADCAST, &yes, socklen_t(MemoryLayout<Int32>.size))
        
        print("[UDP] Socket options set")
        
        // Bind to port 35647
        var addr = sockaddr_in()
        addr.sin_family = sa_family_t(AF_INET)
        addr.sin_port = UInt16(Protocol.port).bigEndian
        addr.sin_addr.s_addr = INADDR_ANY
        
        print("[UDP] Attempting to bind to 0.0.0.0:35647...")
        
        let bindResult = withUnsafePointer(to: &addr) {
            $0.withMemoryRebound(to: sockaddr.self, capacity: 1) {
                Darwin.bind(sock, $0, socklen_t(MemoryLayout<sockaddr_in>.size))
            }
        }
        
        guard bindResult == 0 else {
            let err = errno
            let errMsg = String(cString: strerror(err))
            print("[UDP] ⚠️ Bind failed: errno \(err) - \(errMsg)")
            close(sock)
            DispatchQueue.main.async { [weak self] in
                self?.onDebugLog?("⚠️ UDP bind failed: \(err) - \(errMsg)")
            }
            return
        }
        
        print("[UDP] ✅ Successfully bound to :35647, listening for broadcasts...")
        DispatchQueue.main.async { [weak self] in
            self?.onDebugLog?("✅ UDP listener ACTIVE on :35647")
        }
        
        // Listen for broadcasts
        var buffer = [UInt8](repeating: 0, count: 512)
        var messageCount = 0
        var loopCount = 0
        while true {
            loopCount += 1
            if loopCount % 100 == 1 {
                print("[UDP] recvfrom() loop iteration #\(loopCount), waiting for packet...")
            }
            
            var senderAddr = sockaddr_in()
            var senderLen = socklen_t(MemoryLayout<sockaddr_in>.size)
            
            print("[UDP] Calling recvfrom()...")
            let recvLen = withUnsafeMutablePointer(to: &senderAddr) {
                $0.withMemoryRebound(to: sockaddr.self, capacity: 1) {
                    recvfrom(sock, &buffer, buffer.count, 0, $0, &senderLen)
                }
            }
            print("[UDP] recvfrom() returned: \(recvLen)")
            
            guard recvLen > 0 else {
                let err = errno
                if err != EAGAIN && err != EWOULDBLOCK {
                    print("[UDP] recvfrom error: errno \(err) - \(String(cString: strerror(err)))")
                }
                continue
            }
            
            messageCount += 1
            
            guard let message = String(bytes: buffer[0..<recvLen], encoding: .utf8) else {
                print("[UDP] Failed to decode message #\(messageCount)")
                continue
            }
            
            print("[UDP] 📥 Message #\(messageCount): '\(message)'")
            
            // Parse: CROSSFLOW:DeviceName:Port
            let parts = message.components(separatedBy: ":")
            if parts.count >= 3 && parts[0] == "CROSSFLOW" {
                let peerName = parts[1]
                let portStr = parts[2]
                
                print("[UDP] Parsed: name='\(peerName)' port='\(portStr)'")
                
                guard peerName != self.deviceName, let port = Int(portStr) else {
                    print("[UDP] Ignoring (self or invalid port)")
                    continue
                }
                
                // Convert sender IP to string
                var hostBuffer = [CChar](repeating: 0, count: Int(INET_ADDRSTRLEN))
                inet_ntop(AF_INET, &senderAddr.sin_addr, &hostBuffer, socklen_t(INET_ADDRSTRLEN))
                let senderIP = String(cString: hostBuffer)
                
                print("[UDP] ✅ Valid peer: '\(peerName)' @ \(senderIP):\(port)")
                
                DispatchQueue.main.async { [weak self] in
                    guard let self = self else { return }
                    if self.peers[peerName] == nil {
                        print("[UDP] Adding new peer '\(peerName)' to dictionary")
                        self.peers[peerName] = (senderIP, port)
                        self.onPeerFound?(peerName)
                        self.onDebugLog?("✅ UDP: Found '\(peerName)' @ \(senderIP)")
                    } else {
                        print("[UDP] Peer '\(peerName)' already known")
                    }
                }
            } else {
                print("[UDP] Message doesn't match protocol: '\(message)'")
            }
        }
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
        
        // Broadcast to multiple addresses for better compatibility
        let broadcastAddresses = ["255.255.255.255", "10.0.1.255"]
        
        for address in broadcastAddresses {
            let endpoint = NWEndpoint.hostPort(host: NWEndpoint.Host(address), port: NWEndpoint.Port(rawValue: Protocol.port)!)
            let connection = NWConnection(to: endpoint, using: .udp)
            
            connection.start(queue: .global())
            connection.send(content: data, completion: .contentProcessed { error in
                if let error = error {
                    print("[UDP] Broadcast to \(address) failed: \(error)")
                } else {
                    print("[UDP] ✓ Broadcast sent to \(address)")
                }
                connection.cancel()
            })
        }
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
    var clipboardHistory: [String] = []
    
    func applicationDidFinishLaunching(_ notification: Notification) {
        deviceName = Host.current().localizedName?.replacingOccurrences(of: " ", with: "_") ?? "Mac"
        
        // Status bar setup
        statusItem = NSStatusBar.system.statusItem(withLength: NSStatusItem.squareLength)
        if let button = statusItem.button {
            button.image = NSImage(systemSymbolName: "arrow.triangle.2.circlepath", accessibilityDescription: "CrossFlow")
            button.action = #selector(togglePopover)
            button.target = self
        }
        
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
                // Filter out peer_announce messages (Windows keep-alive)
                if msg.type == "peer_announce" {
                    print("[App] 📡 Peer announce from '\(msg.source)': \(msg.content)")
                    return
                }
                
                // Only write clipboard messages to clipboard
                if msg.type == "clipboard" {
                    print("[App] 📥 Received clipboard from '\(msg.source)': '\(msg.content)'")
                    self.clipboard.write(msg.content)
                } else {
                    print("[App] ⚠️ Unknown message type '\(msg.type)' from '\(msg.source)'")
                }
            }
        }
        tcpServer.start()
        
        // Bonjour
        bonjour = BonjourService(deviceName: deviceName)
        bonjour.onPeerFound = { [weak self] name in
            DispatchQueue.main.async {
                self?.peers.append(name)
            }
        }
        bonjour.onPeerLost = { [weak self] name in
            DispatchQueue.main.async {
                self?.peers.removeAll { $0 == name }
            }
        }
        bonjour.start()
        
        // Clipboard
        clipboard = ClipboardMonitor { [weak self] text in
            guard let self = self else { return }
            print("[App] 📤 Local clipboard changed: '\(text)'")
            print("[App] 📤 Broadcasting to \(self.peers.count) peer(s)")
            self.bonjour.broadcast(text, from: self.deviceName)
        }
        clipboard.start()

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
        stack.addArrangedSubview(status)
        
        stack.addArrangedSubview(separator())
        
        // Peers
        stack.addArrangedSubview(label("Connected Devices (\(peers.count))", size: 10, color: .secondaryLabelColor))
        if peers.isEmpty {
            stack.addArrangedSubview(label("No devices found", size: 12, color: .tertiaryLabelColor))
        } else {
            for peer in peers {
                let peerLabel = NSTextField(labelWithString: "● \(peer.replacingOccurrences(of: "_", with: " "))")
                peerLabel.font = .systemFont(ofSize: 12)
                stack.addArrangedSubview(peerLabel)
            }
        }
        
        // Spacer
        let spacer = NSView()
        stack.addArrangedSubview(spacer)
        stack.setHuggingPriority(.defaultLow, for: .vertical)
        
        // Clipboard History
        if !clipboardHistory.isEmpty {
            stack.addArrangedSubview(separator())
            stack.addArrangedSubview(label("Clipboard History", size: 10, color: .secondaryLabelColor))
            let historyText = clipboardHistory.suffix(3).joined(separator: "\n")
            let historyLabel = NSTextField(wrappingLabelWithString: historyText)
            historyLabel.font = .systemFont(ofSize: 9)
            historyLabel.textColor = .systemBlue
            historyLabel.preferredMaxLayoutWidth = 268
            stack.addArrangedSubview(historyLabel)
        }
        
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
