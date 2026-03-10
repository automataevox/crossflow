// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "CrossFlow",
    platforms: [
        .macOS(.v12)
    ],
    products: [
        .executable(name: "CrossFlow", targets: ["CrossFlow"])
    ],
    targets: [
        .executableTarget(
            name: "CrossFlow",
            path: "macos/Sources"
        )
    ]
)
