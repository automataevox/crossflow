import Network
import Foundation

/// NWListener-based TCP server. Calls `onMessage` for each received ClipMessage.
final class TCPServer {

    private var listener: NWListener?
    private let queue = DispatchQueue(label: "crossflow.server")
    var onMessage: ((ClipMessage) -> Void)?

    func start() {
        do {
            let params = NWParameters.tcp
            listener = try NWListener(using: params, on: NWEndpoint.Port(rawValue: CrossFlowProtocol.port)!)
        } catch {
            print("[TCPServer] Failed to create listener: \(error)")
            return
        }

        listener?.newConnectionHandler = { [weak self] connection in
            self?.handle(connection)
        }

        listener?.stateUpdateHandler = { state in
            switch state {
            case .ready:
                print("[TCPServer] Listening on port \(CrossFlowProtocol.port)")
            case .failed(let error):
                print("[TCPServer] Failed: \(error)")
            default: break
            }
        }

        listener?.start(queue: queue)
    }

    func stop() {
        listener?.cancel()
        listener = nil
    }

    private func handle(_ connection: NWConnection) {
        connection.start(queue: queue)
        receiveData(from: connection)
    }

    private func receiveData(from connection: NWConnection) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 65536) {
            [weak self] data, _, isComplete, error in
            if let data = data, !data.isEmpty {
                if let msg = CrossFlowProtocol.decode(data) {
                    self?.onMessage?(msg)
                }
            }
            if isComplete || error != nil {
                connection.cancel()
            } else {
                self?.receiveData(from: connection)
            }
        }
    }
}

// Alias so Swift file can use the same name without clashing with Foundation
enum CrossFlowProtocol {
    static let port: UInt16 = Protocol.port
    static func decode(_ data: Data) -> ClipMessage? { Protocol.decode(data) }
    static func encode(_ msg: ClipMessage) -> Data   { Protocol.encode(msg) }
}
