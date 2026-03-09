import Foundation

// Shared message format — identical across all platforms
struct ClipMessage: Codable {
    var type: String = "clipboard"
    var content: String
    var source: String
}

enum Protocol {
    static let port: UInt16 = 35647
    static let serviceType  = "_crossflow._tcp."

    static func encode(_ msg: ClipMessage) -> Data {
        let line = (try? JSONEncoder().encode(msg)).flatMap { String(data: $0, encoding: .utf8) } ?? "{}"
        return (line + "\n").data(using: .utf8) ?? Data()
    }

    static func decode(_ data: Data) -> ClipMessage? {
        guard let str = String(data: data, encoding: .utf8)?
                .trimmingCharacters(in: .whitespacesAndNewlines),
              !str.isEmpty else { return nil }
        return try? JSONDecoder().decode(ClipMessage.self, from: Data(str.utf8))
    }
}
