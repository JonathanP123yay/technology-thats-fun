# Desktop Monitor for Minecraft 26.3

This project was created outside the stock Fabric example mod so the example template remains untouched.

Minecraft 26.3 is the target. Fabric publishes Loom 1.18-SNAPSHOT, loader 0.19.5, and Fabric API 0.162.0+26.3 for this release. Minecraft 26.1 and newer are unobfuscated; the Fabric 26.1 porting guide says to remove the mappings dependency, use the new Loom plugin ID, and package with the standard `jar` task. Minecraft's version metadata specifies Java 25.

References: [Fabric 26.1 porting guide](https://github.com/FabricMC/fabric-docs/blob/main/versions/26.1.2/develop/porting/index.md), [Fabric 26.3 example mod](https://github.com/FabricMC/fabric-example-mod/tree/26.3), [Fabric development version catalogue](https://fabricmc.net/develop/), [Minecraft 26.3 version metadata](https://piston-meta.mojang.com/v1/packages/702fe59163c6ee6578607daa85811d9bc9c7cc40/26.3.json), and [Gradle Java compatibility](https://docs.gradle.org/current/userguide/compatibility.html).

## Components

- `fabric-monitor`: Java/Fabric client mod for Minecraft 26.3
- `windows-service`: .NET 10 Windows desktop capture and input injection service

## Quick start

1. Install JDK 25 or newer and configure JAVA_HOME.
2. From the project folder (desktop-monitor-solution), run `build.bat` to build both components.
   - The Fabric project includes its own Gradle wrapper.
   - A system `gradle` command is used only if the wrapper is missing.
3. To build only the Windows service, run `dotnet build` from `windows-service`.
4. Launch the Windows service on the host PC.
5. Start Minecraft with the Fabric mod installed.
6. Place the `monitor` block in the world and right-click it.
7. In the settings menu, choose the target window name, host, and port.
8. Right-click the monitor to open its settings; use the Desktop Monitor keybinds to open settings or toggle desktop control.

## Build outputs

- Mod jar: `fabric-monitor\build\libs\desktop-monitor-mod-1.0.0.jar`
- Windows service: `windows-service\bin\Debug\net10.0-windows\DesktopMonitorWindowsService.dll`

The `monitor` block has settings for the selected window title, host, and port. Settings are saved in `desktop-monitor.properties`.
