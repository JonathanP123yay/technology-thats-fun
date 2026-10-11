# Desktop Monitor for Minecraft 26.3

This project was created outside the stock Fabric example mod so the example template remains untouched.

## Components

- `fabric-monitor`: Java/Fabric client mod for Minecraft 26.3
- `windows-service`: .NET 10 Windows desktop capture and input injection service

## Quick start

1. Install JDK 25 or newer and configure JAVA_HOME.
2. In `fabric-monitor`, run `gradlew build`.
3. Build `windows-service` with `dotnet build`.
4. Launch the Windows service on the host PC.
5. Start Minecraft with the Fabric mod installed.
6. Use the configured keybind to toggle desktop control.

## Notes

The current implementation is a working scaffold that uses a local loopback TCP connection and Win32 desktop capture with SendInput injection. It is designed to be extended with a full 3D monitor renderer and authenticated client/server handshakes.
