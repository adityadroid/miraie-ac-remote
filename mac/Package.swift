// swift-tools-version:5.10
import PackageDescription

let package = Package(
    name: "ACRemote",
    platforms: [.macOS(.v14)],
    targets: [
        .target(name: "MirAIe", path: "Sources/MirAIe"),
        .executableTarget(
            name: "ACRemote",
            dependencies: ["MirAIe"],
            path: "Sources/ACRemote"
        ),
        .executableTarget(
            name: "miraie-probe",
            dependencies: ["MirAIe"],
            path: "Sources/miraie-probe"
        ),
        // Plain executable instead of XCTest: Command Line Tools ship without XCTest.
        .executableTarget(
            name: "MirAIeChecks",
            dependencies: ["MirAIe"],
            path: "Tests/MirAIeChecks"
        ),
    ]
)
