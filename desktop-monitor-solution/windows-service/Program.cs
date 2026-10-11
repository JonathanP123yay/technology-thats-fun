using System.Buffers.Binary;
using System.Collections.Concurrent;
using System.Drawing;
using System.Drawing.Imaging;
using System.Net;
using System.Net.Sockets;
using System.Runtime.InteropServices;
using System.Text;
using System.Text.Json;

public static class Program
{
    public static async Task Main()
    {
        const string authToken = "desktop-monitor-demo";
        var port = ReadPort("DESKTOP_MONITOR_PORT", 43122);
        var discoveryPort = ReadPort("DESKTOP_MONITOR_DISCOVERY_PORT", 43123);
        Console.WriteLine("Desktop Monitor Windows service starting...");

        await Task.WhenAll(
            RunTcpServiceAsync(port, authToken),
            RunWindowDiscoveryAsync(discoveryPort)
        );
    }

    private static int ReadPort(string variableName, int defaultPort)
    {
        var value = Environment.GetEnvironmentVariable(variableName);
        if (string.IsNullOrWhiteSpace(value))
        {
            return defaultPort;
        }

        if (int.TryParse(value, out var port) && port is >= 1 and <= 65535)
        {
            return port;
        }

        Console.WriteLine($"Ignoring invalid {variableName} value '{value}'; using port {defaultPort}.");
        return defaultPort;
    }

    private static async Task RunTcpServiceAsync(int port, string authToken)
    {
        var listener = new TcpListener(IPAddress.Loopback, port);
        try
        {
            listener.Start();
        }
        catch (SocketException exception)
        {
            Console.WriteLine($"Could not listen on {IPAddress.Loopback}:{port}: {exception.Message}");
            return;
        }

        Console.WriteLine($"Listening on loopback {IPAddress.Loopback}:{port}");
        using var clientSlots = new SemaphoreSlim(8, 8);

        while (true)
        {
            try
            {
                var client = await listener.AcceptTcpClientAsync();
                if (!clientSlots.Wait(0))
                {
                    client.Dispose();
                    continue;
                }

                _ = HandleClientSafelyAsync(client, authToken, clientSlots);
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Service loop error: {ex.Message}");
            }
        }
    }

    private static async Task HandleClientSafelyAsync(TcpClient client, string authToken, SemaphoreSlim clientSlots)
    {
        try
        {
            using (client)
            {
                await DesktopMonitorService.HandleClientAsync(client, authToken);
            }
        }
        catch (Exception exception) when (exception is IOException or SocketException or OperationCanceledException)
        {
            Console.WriteLine($"Client disconnected: {exception.Message}");
        }
        catch (Exception exception)
        {
            Console.WriteLine($"Client session failed: {exception.Message}");
        }
        finally
        {
            clientSlots.Release();
        }
    }

    private static async Task RunWindowDiscoveryAsync(int discoveryPort)
    {
        UdpClient listener;
        try
        {
            listener = new UdpClient(new IPEndPoint(IPAddress.Loopback, discoveryPort));
        }
        catch (SocketException exception)
        {
            Console.WriteLine($"Could not start window discovery on {IPAddress.Loopback}:{discoveryPort}: {exception.Message}");
            return;
        }

        using (listener)
        {
            Console.WriteLine($"Window discovery listening on loopback {IPAddress.Loopback}:{discoveryPort}");

            while (true)
            {
                try
                {
                    var request = await listener.ReceiveAsync();
                    if (!request.Buffer.AsSpan().SequenceEqual("desktop-monitor:list-windows"u8))
                    {
                        continue;
                    }

                    var response = Encoding.UTF8.GetBytes(string.Join('\n', WindowDiscovery.GetVisibleWindowTitles()));
                    await listener.SendAsync(response, request.RemoteEndPoint);
                }
                catch (SocketException exception)
                {
                    Console.WriteLine($"Window discovery socket error: {exception.Message}");
                }
            }
        }
    }
}

internal static class WindowDiscovery
{
    private const int GwlExStyle = -20;
    private const long WsExToolWindow = 0x00000080L;
    private static readonly NativeMethods.EnumWindowsCallback EnumerateCallback = EnumerateWindow;
    private static readonly ConcurrentBag<string> Titles = new();

    public static IReadOnlyList<string> GetVisibleWindowTitles()
    {
        while (Titles.TryTake(out _))
        {
        }

        var serviceProcessId = new IntPtr(Environment.ProcessId);
        NativeMethods.EnumWindows(EnumerateCallback, serviceProcessId);
        return Titles
            .Distinct(StringComparer.OrdinalIgnoreCase)
            .OrderBy(title => title, StringComparer.OrdinalIgnoreCase)
            .Take(200)
            .Select(title => title.Length > 240 ? title[..240] : title)
            .ToArray();
    }

    public static bool TryGetVisibleWindowBounds(string targetTitle, out Rectangle bounds, out IntPtr windowHandle)
    {
        Rectangle foundBounds = Rectangle.Empty;
        IntPtr foundHandle = IntPtr.Zero;
        var matched = false;
        NativeMethods.EnumWindows((handle, _) =>
        {
            if (!NativeMethods.IsWindowVisible(handle) || NativeMethods.IsIconic(handle))
            {
                return true;
            }

            var length = NativeMethods.GetWindowTextLength(handle);
            if (length <= 0)
            {
                return true;
            }

            var title = new StringBuilder(length + 1);
            if (NativeMethods.GetWindowText(handle, title, title.Capacity) <= 0
                || !title.ToString().Contains(targetTitle, StringComparison.OrdinalIgnoreCase))
            {
                return true;
            }

            if (NativeMethods.GetWindowRect(handle, out var rect) && rect.Right > rect.Left && rect.Bottom > rect.Top)
            {
                foundBounds = Rectangle.FromLTRB(rect.Left, rect.Top, rect.Right, rect.Bottom);
                foundHandle = handle;
                matched = true;
                return false;
            }

            return true;
        }, IntPtr.Zero);

        bounds = foundBounds;
        windowHandle = foundHandle;
        return matched;
    }

    private static bool EnumerateWindow(IntPtr handle, IntPtr serviceProcessId)
    {
        if (!NativeMethods.IsWindowVisible(handle))
        {
            return true;
        }

        var extendedStyle = NativeMethods.GetWindowLongPtr(handle, GwlExStyle).ToInt64();
        if ((extendedStyle & WsExToolWindow) != 0)
        {
            return true;
        }

        NativeMethods.GetWindowThreadProcessId(handle, out var processId);
        if (processId == serviceProcessId.ToInt32())
        {
            return true;
        }

        var length = NativeMethods.GetWindowTextLength(handle);
        if (length <= 0)
        {
            return true;
        }

        var title = new StringBuilder(length + 1);
        if (NativeMethods.GetWindowText(handle, title, title.Capacity) > 0)
        {
            var normalizedTitle = title.ToString().Replace('\r', ' ').Replace('\n', ' ').Trim();
            if (!string.IsNullOrWhiteSpace(normalizedTitle))
            {
                Titles.Add(normalizedTitle);
            }
        }

        return true;
    }
}

public static class DesktopMonitorService
{
    private static readonly SemaphoreSlim ActiveSession = new(1, 1);

    public static async Task HandleClientAsync(TcpClient client, string authToken)
    {
        client.NoDelay = true;
        using var stream = client.GetStream();
        using var handshakeTimeout = new CancellationTokenSource(TimeSpan.FromSeconds(5));
        var auth = await ReadLineAsync(stream, 128, handshakeTimeout.Token);
        if (!string.Equals(auth, authToken, StringComparison.Ordinal))
        {
            Console.WriteLine("Rejected unauthenticated client.");
            return;
        }

        var selectedWindow = await ReadLineAsync(stream, 512, handshakeTimeout.Token);

        if (!ActiveSession.Wait(0))
        {
            Console.WriteLine("Rejected client because a desktop session is already active.");
            return;
        }

        if (string.IsNullOrWhiteSpace(selectedWindow))
        {
            selectedWindow = "Notepad";
        }

        try
        {
            Console.WriteLine($"Client authenticated. Streaming window: {selectedWindow}");
            using var frameCancellation = new CancellationTokenSource();
            var frameTask = StreamFramesAsync(stream, selectedWindow, frameCancellation.Token);
            var inputTask = ReadInputAsync(stream, selectedWindow, frameCancellation.Token);
            await Task.WhenAny(frameTask, inputTask);
            frameCancellation.Cancel();
            await Task.WhenAll(frameTask, inputTask);
        }
        finally
        {
            ActiveSession.Release();
        }
    }

    private static async Task ReadInputAsync(NetworkStream stream, string selectedWindow, CancellationToken cancellation)
    {
        while (!cancellation.IsCancellationRequested)
        {
            try
            {
                var lengthBuffer = new byte[4];
                await stream.ReadExactlyAsync(lengthBuffer.AsMemory(0, 4), cancellation);
                var length = BinaryPrimitives.ReadInt32BigEndian(lengthBuffer);
                if (length <= 0 || length > 65536)
                {
                    return;
                }

                var payloadBuffer = new byte[length];
                await stream.ReadExactlyAsync(payloadBuffer.AsMemory(0, length), cancellation);
                var payload = Encoding.UTF8.GetString(payloadBuffer);
                HandleInputMessage(payload, selectedWindow);
            }
            catch (IOException)
            {
                return;
            }
            catch (OperationCanceledException)
            {
                return;
            }
        }
    }

    private static async Task StreamFramesAsync(NetworkStream stream, string selectedWindow, CancellationToken cancellation)
    {
        while (!cancellation.IsCancellationRequested)
        {
            try
            {
                using var bitmap = CaptureScreen(selectedWindow);
                using var jpeg = new MemoryStream();
                bitmap.Save(jpeg, ImageFormat.Jpeg);
                var frame = jpeg.ToArray();

                var lengthBuffer = new byte[sizeof(int)];
                BinaryPrimitives.WriteInt32BigEndian(lengthBuffer, frame.Length);
                await stream.WriteAsync(lengthBuffer, cancellation);
                await stream.WriteAsync(frame, cancellation);
                await stream.FlushAsync(cancellation);

                await Task.Delay(33, cancellation);
            }
            catch (OperationCanceledException)
            {
                return;
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Capture error: {ex.Message}");
                return;
            }
        }
    }

    private static async Task<string> ReadLineAsync(NetworkStream stream, int maximumLength, CancellationToken cancellation)
    {
        var data = new List<byte>();
        var buffer = new byte[1];

        while (true)
        {
            var read = await stream.ReadAsync(buffer.AsMemory(0, 1), cancellation);
            if (read <= 0)
            {
                return string.Empty;
            }

            if (buffer[0] == '\n')
            {
                break;
            }

            if (buffer[0] != '\r')
            {
                if (data.Count >= maximumLength)
                {
                    throw new InvalidDataException("Connection handshake line was too long.");
                }

                data.Add(buffer[0]);
            }
        }

        return Encoding.UTF8.GetString(data.ToArray());
    }

    private static Bitmap CaptureScreen(string selectedWindow)
    {
        Rectangle bounds;
        if (!WindowDiscovery.TryGetVisibleWindowBounds(selectedWindow, out bounds, out _))
        {
            bounds = Screen.PrimaryScreen?.Bounds ?? throw new InvalidOperationException("No primary screen detected.");
        }

        using var captured = new Bitmap(bounds.Width, bounds.Height, PixelFormat.Format32bppArgb);
        using (var graphics = Graphics.FromImage(captured))
        {
            graphics.CopyFromScreen(bounds.Location, Point.Empty, bounds.Size, CopyPixelOperation.SourceCopy);
        }

        const int maximumWidth = 960;
        const int maximumHeight = 540;
        var scale = Math.Min(1.0, Math.Min((double)maximumWidth / captured.Width, (double)maximumHeight / captured.Height));
        var width = Math.Max(1, (int)Math.Round(captured.Width * scale));
        var height = Math.Max(1, (int)Math.Round(captured.Height * scale));
        var output = new Bitmap(width, height, PixelFormat.Format32bppArgb);
        using var outputGraphics = Graphics.FromImage(output);
        outputGraphics.InterpolationMode = System.Drawing.Drawing2D.InterpolationMode.HighQualityBicubic;
        outputGraphics.DrawImage(captured, new Rectangle(0, 0, width, height));
        return output;
    }

    private static void HandleInputMessage(string payload, string selectedWindow)
    {
        try
        {
            using var message = JsonDocument.Parse(payload);
            var root = message.RootElement;
            if (root.ValueKind != JsonValueKind.Object)
            {
                return;
            }

            var action = GetString(root, "type") ?? GetString(root, "action");
            if (string.IsNullOrWhiteSpace(action))
            {
                return;
            }

            switch (action.Trim().ToLowerInvariant())
            {
                case "click":
                case "left_click":
                case "right_click":
                case "middle_click":
                    FocusWindow(selectedWindow);
                    if (TryGetNormalizedPosition(root, out var x, out var y))
                    {
                        MoveToWindowPosition(selectedWindow, x, y);
                    }

                    var button = action.Equals("right_click", StringComparison.OrdinalIgnoreCase)
                        ? MouseButton.Right
                        : action.Equals("middle_click", StringComparison.OrdinalIgnoreCase)
                            ? MouseButton.Middle
                            : GetString(root, "button")?.ToLowerInvariant() switch
                            {
                                "right" => MouseButton.Right,
                                "middle" => MouseButton.Middle,
                                _ => MouseButton.Left
                            };
                    SendMouseButton(button, true);
                    SendMouseButton(button, false);
                    break;
                case "scroll":
                    FocusWindow(selectedWindow);
                    if (TryGetNormalizedPosition(root, out x, out y))
                    {
                        MoveToWindowPosition(selectedWindow, x, y);
                    }

                    var delta = root.TryGetProperty("delta", out var deltaValue) && deltaValue.TryGetInt32(out var requestedDelta)
                        ? Math.Clamp(requestedDelta, -1200, 1200)
                        : 120;
                    if (delta != 0)
                    {
                        SendMouseWheel(delta);
                    }
                    break;
                case "type":
                case "text":
                    FocusWindow(selectedWindow);
                    var text = GetString(root, "text");
                    if (text is { Length: > 0 and <= 4096 })
                    {
                        SendText(text);
                    }
                    break;
                case "key":
                    var key = GetString(root, "key");
                    if (TryGetVirtualKey(key, out var virtualKey))
                    {
                        FocusWindow(selectedWindow);
                        NativeMethods.SendVirtualKey(virtualKey);
                    }
                    break;
            }
        }
        catch (JsonException)
        {
            // Ignore malformed input messages; never interpret arbitrary text as an action.
        }
        catch (ArgumentException)
        {
            // Ignore malformed coordinates or values without interrupting the frame stream.
        }
    }

    private static string? GetString(JsonElement value, string name)
    {
        return value.TryGetProperty(name, out var property) && property.ValueKind == JsonValueKind.String
            ? property.GetString()
            : null;
    }

    private static bool TryGetNormalizedPosition(JsonElement value, out double x, out double y)
    {
        x = y = 0;
        return value.TryGetProperty("x", out var xValue)
            && value.TryGetProperty("y", out var yValue)
            && xValue.TryGetDouble(out x)
            && yValue.TryGetDouble(out y)
            && double.IsFinite(x)
            && double.IsFinite(y)
            && x is >= 0 and <= 1
            && y is >= 0 and <= 1;
    }

    private static void MoveToWindowPosition(string selectedWindow, double x, double y)
    {
        if (!WindowDiscovery.TryGetVisibleWindowBounds(selectedWindow, out var bounds, out _))
        {
            return;
        }

        var targetX = bounds.Left + (int)Math.Round(x * Math.Max(0, bounds.Width - 1));
        var targetY = bounds.Top + (int)Math.Round(y * Math.Max(0, bounds.Height - 1));
        NativeMethods.SetCursorPos(targetX, targetY);
    }

    private static void FocusWindow(string selectedWindow)
    {
        if (WindowDiscovery.TryGetVisibleWindowBounds(selectedWindow, out _, out var windowHandle))
        {
            NativeMethods.ShowWindowAsync(windowHandle, 9);
            NativeMethods.SetForegroundWindow(windowHandle);
        }
    }

    private static void SendText(string text)
    {
        foreach (var ch in text)
        {
            SendKeyboardInput(ch, true);
            SendKeyboardInput(ch, false);
        }
    }

    private static void SendMouseButton(MouseButton button, bool isDown)
    {
        var flags = button switch
        {
            MouseButton.Left => isDown ? 0x0002u : 0x0004u,
            MouseButton.Right => isDown ? 0x0008u : 0x0010u,
            _ => isDown ? 0x0020u : 0x0040u
        };

        NativeMethods.mouse_event(flags, 0u, 0u, 0, IntPtr.Zero);
    }

    private static void SendMouseWheel(int delta)
    {
        NativeMethods.mouse_event(0x0800u, 0u, 0u, delta, IntPtr.Zero);
    }

    private static void SendKeyboardInput(char ch, bool isDown)
    {
        NativeMethods.SendUnicodeCharacter(ch, isDown);
    }

    private static bool TryGetVirtualKey(string? key, out ushort virtualKey)
    {
        virtualKey = key?.ToUpperInvariant() switch
        {
            "BACKSPACE" => 0x08,
            "TAB" => 0x09,
            "ENTER" => 0x0D,
            "PAGEUP" => 0x21,
            "PAGEDOWN" => 0x22,
            "END" => 0x23,
            "HOME" => 0x24,
            "LEFT" => 0x25,
            "UP" => 0x26,
            "RIGHT" => 0x27,
            "DOWN" => 0x28,
            "DELETE" => 0x2E,
            _ => 0
        };
        return virtualKey != 0;
    }
}

public enum MouseButton
{
    Left,
    Right,
    Middle
}

internal static class NativeMethods
{
    public delegate bool EnumWindowsCallback(IntPtr windowHandle, IntPtr parameter);

    [DllImport("user32.dll")]
    public static extern bool EnumWindows(EnumWindowsCallback callback, IntPtr parameter);

    [DllImport("user32.dll")]
    public static extern bool IsWindowVisible(IntPtr windowHandle);

    [DllImport("user32.dll")]
    public static extern bool IsIconic(IntPtr windowHandle);

    [DllImport("user32.dll")]
    public static extern bool ShowWindowAsync(IntPtr windowHandle, int command);

    [DllImport("user32.dll")]
    public static extern bool SetForegroundWindow(IntPtr windowHandle);

    [DllImport("user32.dll")]
    public static extern bool GetWindowRect(IntPtr windowHandle, out WindowRect rectangle);

    [StructLayout(LayoutKind.Sequential)]
    public struct WindowRect
    {
        public int Left;
        public int Top;
        public int Right;
        public int Bottom;
    }

    [DllImport("user32.dll", EntryPoint = "GetWindowLongPtrW")]
    public static extern IntPtr GetWindowLongPtr(IntPtr windowHandle, int index);

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    public static extern int GetWindowText(IntPtr windowHandle, StringBuilder text, int maximumCount);

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    public static extern int GetWindowTextLength(IntPtr windowHandle);

    [DllImport("user32.dll")]
    public static extern uint GetWindowThreadProcessId(IntPtr windowHandle, out uint processId);

    [DllImport("user32.dll")]
    public static extern void mouse_event(uint dwFlags, uint dx, uint dy, int dwData, IntPtr dwExtraInfo);

    [DllImport("user32.dll")]
    public static extern bool SetCursorPos(int x, int y);

    public static void SendUnicodeCharacter(char character, bool isDown)
    {
        var input = new Input
        {
            Type = 1,
            Data = new InputUnion
            {
                Keyboard = new KeyboardInput
                {
                    Scan = character,
                    Flags = 0x0004u | (isDown ? 0u : 0x0002u)
                }
            }
        };

        SendInput(1, [input], Marshal.SizeOf<Input>());
    }

    public static void SendVirtualKey(ushort virtualKey)
    {
        var inputs = new[]
        {
            new Input
            {
                Type = 1,
                Data = new InputUnion { Keyboard = new KeyboardInput { VirtualKey = virtualKey } }
            },
            new Input
            {
                Type = 1,
                Data = new InputUnion { Keyboard = new KeyboardInput { VirtualKey = virtualKey, Flags = 0x0002u } }
            }
        };
        SendInput((uint)inputs.Length, inputs, Marshal.SizeOf<Input>());
    }

    [DllImport("user32.dll", SetLastError = true)]
    private static extern uint SendInput(uint inputCount, [In] Input[] inputs, int inputSize);

    [StructLayout(LayoutKind.Sequential)]
    private struct Input
    {
        public uint Type;
        public InputUnion Data;
    }

    [StructLayout(LayoutKind.Explicit)]
    private struct InputUnion
    {
        [FieldOffset(0)]
        public MouseInput Mouse;

        [FieldOffset(0)]
        public KeyboardInput Keyboard;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct KeyboardInput
    {
        public ushort VirtualKey;
        public ushort Scan;
        public uint Flags;
        public uint Time;
        public UIntPtr ExtraInfo;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct MouseInput
    {
        public int X;
        public int Y;
        public uint MouseData;
        public uint Flags;
        public uint Time;
        public UIntPtr ExtraInfo;
    }
}
