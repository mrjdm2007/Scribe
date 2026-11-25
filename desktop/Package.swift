// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "ScribeDesktop",
    platforms: [.macOS(.v13)],
    products: [.executable(name: "Scribe", targets: ["Scribe"])],
    targets: [.executableTarget(name: "Scribe", path: "Sources/Scribe")]
)
