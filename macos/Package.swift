// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "ScribeMac",
    platforms: [.macOS(.v13)],
    products: [.executable(name: "ScribeMac", targets: ["ScribeMac"])],
    targets: [.executableTarget(name: "ScribeMac")]
)
