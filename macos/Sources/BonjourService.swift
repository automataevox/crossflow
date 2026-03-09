import Foundation
import Network

/// Advertises this device via Bonjour and discovers peers on the LAN.
/// Sends clipboard text to all discovered peers via direct TCP.
final class BonjourService: NSObject {

    private let deviceName: String
    private var publisher: NetService?
    private var browser: NetServiceBrowser?
    private var peers: [String: (host: String, port: Int)] = [:]
    private let queue = DispatchQueue(label: "crossflow.bonjour")

    var onPeerFound: ((String) -> Void)?
    var onPeerLost:  ((String) -> Void)?

    init(deviceName: String) {
        self.deviceName = deviceName
        super.init()
    }

    // ── Advertising ───────────────────────────────────────────────────────────

    func startAdvertising() {
        publisher = NetService(
            domain: "local.",
            type:   Protocol.serviceType,
            name:   deviceName,
            port:   Int32(Protocol.port)
        )
        publisher?.delegate = self
        publisher?.publish()
        print("[Bonjour] Advertising as \(deviceName) on port \(Protocol.port)")
    }

    // ── Discovery ─────────────────────────────────────────────────────────────

    func startBrowsing() {
        browser = NetServiceBrowser()
        browser?.delegate = self
        browser?.searchForServices(ofType: Protocol.serviceType, inDomain: "local.")
        print("[Bonjour] Browsing for \(Protocol.serviceType)")
    }

    // ── Send clipboard to all peers ───────────────────────────────────────────

    func broadcast(_ text: String, from source: String) {
        let msg  = ClipMessage(content: text, source: source)
        let data = Protocol.encode(msg)
        queue.async { [weak self] in
            self?.peers.forEach { (name, info) in
                self?.send(data: data, to: info.host, port: info.port, peerName: name)
            }
        }
    }

    private func send(data: Data, to host: String, port: Int, peerName: String) {
        guard let portValue = NWEndpoint.Port(rawValue: UInt16(port)) else { return }
        let connection = NWConnection(
            host: NWEndpoint.Host(host),
            port: portValue,
            using: .tcp
        )
        let q = DispatchQueue(label: "crossflow.send.\(peerName)")
        connection.stateUpdateHandler = { state in
            if case .ready = state {
                connection.send(content: data, completion: .contentProcessed { _ in
                    connection.cancel()
                })
            } else if case .failed(let e) = state {
                print("[Bonjour] Send to \(peerName) failed: \(e)")
                connection.cancel()
            }
        }
        connection.start(queue: q)
    }

    func stop() {
        publisher?.stop()
        browser?.stop()
    }
}

// ── NetServiceDelegate ────────────────────────────────────────────────────────

extension BonjourService: NetServiceDelegate {
    func netServiceDidPublish(_ sender: NetService) {
        print("[Bonjour] Published \(sender.name)")
    }
    func netService(_ sender: NetService, didNotPublish errorDict: [String: NSNumber]) {
        print("[Bonjour] Publish failed: \(errorDict)")
    }
}

// ── NetServiceBrowserDelegate ─────────────────────────────────────────────────

extension BonjourService: NetServiceBrowserDelegate {
    func netServiceBrowser(_ browser: NetServiceBrowser,
                           didFind service: NetService,
                           moreComing: Bool) {
        guard service.name != deviceName else { return }
        print("[Bonjour] Found: \(service.name), resolving…")
        service.delegate = self
        service.resolve(withTimeout: 5)
    }

    func netServiceBrowser(_ browser: NetServiceBrowser,
                           didRemove service: NetService,
                           moreComing: Bool) {
        peers.removeValue(forKey: service.name)
        onPeerLost?(service.name)
    }
}

// ── NetServiceDelegate (resolution) ──────────────────────────────────────────

extension BonjourService {
    func netServiceDidResolveAddress(_ sender: NetService) {
        guard let host = sender.hostName else { return }
        let port = sender.port
        print("[Bonjour] Resolved \(sender.name) @ \(host):\(port)")
        peers[sender.name] = (host: host, port: port)
        onPeerFound?(sender.name)
    }
    func netService(_ sender: NetService, didNotResolve errorDict: [String: NSNumber]) {
        print("[Bonjour] Resolve failed for \(sender.name): \(errorDict)")
    }
}
