# SmartShare Android

A Premium Cross-Platform P2P Local and Remote File Sharing Application.

This repository has been successfully ported from Flutter/Dart to a native Android application using Kotlin and Jetpack Compose.

## Features
- **Adaptive UI**: Built with Jetpack Compose, supporting responsive layouts for both mobile and tablet devices.
- **Identity & Connection**: Generates device-specific unique codes and QR codes for quick pairing.
- **File Transfer**: Uses a functional mock transfer manager to simulate WebRTC/LAN file transfers (which can be extended to true native TCP/WebSockets).
- **History Tracking**: Keeps a robust history of received files stored locally in `SharedPreferences`.

## Architecture
- **Language**: Kotlin 2.1.0
- **UI Framework**: Jetpack Compose (Material 3)
- **Local Persistence**: `SharedPreferences` (JSON via `kotlinx.serialization`)
- **Build System**: Gradle 9.3.1 (Kotlin DSL)
