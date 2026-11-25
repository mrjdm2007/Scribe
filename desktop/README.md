# Scribe for macOS

This directory contains the first native SwiftUI desktop application target.

## Build

Requires macOS 13+ and Swift 5.9+:

```sh
cd desktop
swift build
swift run
```

## Current status

The current target is a foundation, not feature parity. It provides a native window, course list, and atomic local JSON persistence under Application Support/Scribe. Chapter/topic/page editing, attachments, PDFKit annotations, Drive OAuth/sync, AI, import/export parity and release signing remain to be implemented and tested before this can be called a complete desktop app.
