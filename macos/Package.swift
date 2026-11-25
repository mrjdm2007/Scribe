// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "ScribeMac",
    platforms: [.macOS(.v13)],
    products: [.executable(name: "ScribeMac", targets: ["ScribeMac"]), .library(name: "ScribeCore", targets: ["ScribeCore"])],
    targets: [.target(name: "ScribeCore"), .executableTarget(name: "ScribeMac", dependencies: ["ScribeCore"]), .testTarget(name: "ScribeMacTests", dependencies: ["ScribeCore"])]
)
