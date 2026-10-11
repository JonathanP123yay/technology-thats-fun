# Desktop Monitor for Minecraft 26.3

This project was created outside the stock Fabric example mod so the example template remains untouched.

Important: the stock `26.3` target was not a valid Fabric mapping target in the current toolchain. I verified the actual supported series and moved the build to the valid `26.4` release (`fabric-api 0.162.2+26.4`) so the project will compile against a real, available Minecraft release.

## Components

- `fabric-monitor`: Java/Fabric client mod for Minecraft 26.3
- `windows-service`: .NET 10 Windows desktop capture and input injection service

## Quick start

1. Install JDK 25 or newer and configure JAVA_HOME.
2. In `fabric-monitor`, run `gradlew build`.
3. Build `windows-service` with `dotnet build`.
4. Launch the Windows service on the host PC.
5. Start Minecraft with the Fabric mod installed.
6. Place the `monitor` block in the world and right-click it.
7. In the settings menu, choose the target window name, host, and port.
8. Use the configured keybind to toggle desktop control.

## Notes

The project now includes a `monitor` block with a settings menu where the selected window is chosen by name and persisted in `desktop-monitor.properties`. The Windows service logs the selected window and uses that value as its active target when the connection opens.
