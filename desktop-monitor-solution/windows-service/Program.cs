using System.Buffers.Binary;
using System.Collections.Concurrent;
using System.Drawing;
using System.Drawing.Imaging;
using System.Net;
using System.Net.Sockets;
using System.Runtime.InteropServices;
using System.Text;

public static class Program
{
    public static async Task Main()
    {
        const string authToken = "desktop-monitor-demo";
        const int port = 43122;

        Console.WriteLine("Desktop Monitor Windows service starting...");
        Console.WriteLine($"Listening on loopback {IPAddress.Loopback}:{port}");

        await Task.WhenAll(
            RunTcpServiceAsync(port, authToken),
            RunWindowDiscoveryAsync()
        );
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

        while (true)
        {
            try
            {
                using var client = await listener.AcceptTcpClientAsync();
                await DesktopMonitorService.HandleClientAsync(client, authToken);
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Service loop error: {ex.Message}");
            }
        }
    }

    private static async Task RunWindowDiscoveryAsync()
    {
        const int discoveryPort = 43123;
        using var listener = new UdpClient(new IPEndPoint(IPAddress.Loopback, discoveryPort));
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
    public static async Task HandleClientAsync(TcpClient client, string authToken)
    {
        using var stream = client.GetStream();
        var auth = await ReadLineAsync(stream, CancellationToken.None);
        var selectedWindow = await ReadLineAsync(stream, CancellationToken.None);
        if (!string.Equals(auth, authToken, StringComparison.Ordinal))
        {
            Console.WriteLine("Rejected unauthenticated client.");
            return;
        }

        if (string.IsNullOrWhiteSpace(selectedWindow))
        {
            selectedWindow = "Notepad";
        }

        Console.WriteLine($"Client authenticated. Streaming desktop frames for window: {selectedWindow}");

        using var frameCancellation = new CancellationTokenSource();

        var frameTask = Task.Run(() => StreamFramesAsync(stream, frameCancellation.Token), frameCancellation.Token);
        var inputTask = Task.Run(() => ReadInputAsync(stream, frameCancellation.Token), frameCancellation.Token);

        var completed = await Task.WhenAny(frameTask, inputTask);
        frameCancellation.Cancel();
        await Task.WhenAll(frameTask, inputTask);
    }

    private static async Task ReadInputAsync(NetworkStream stream, CancellationToken cancellation)
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
                HandleInputMessage(payload);
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

    private static async Task StreamFramesAsync(NetworkStream stream, CancellationToken cancellation)
    {
        while (!cancellation.IsCancellationRequested)
        {
            try
            {
                using var bitmap = CaptureScreen();
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

    private static async Task<string> ReadLineAsync(NetworkStream stream, CancellationToken cancellation)
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
                data.Add(buffer[0]);
            }
        }

        return Encoding.UTF8.GetString(data.ToArray());
    }

    private static Bitmap CaptureScreen()
    {
        var screen = Screen.PrimaryScreen ?? throw new InvalidOperationException("No primary screen detected.");
        var bounds = screen.Bounds;
        var bitmap = new Bitmap(bounds.Width, bounds.Height, PixelFormat.Format32bppArgb);
        using var graphics = Graphics.FromImage(bitmap);
        graphics.CopyFromScreen(bounds.Location, Point.Empty, bounds.Size, CopyPixelOperation.SourceCopy);
        return bitmap;
    }

    private static void HandleInputMessage(string payload)
    {
        Console.WriteLine($"Input payload: {payload}");

        if (payload.Contains("left_click", StringComparison.OrdinalIgnoreCase))
        {
            SendMouseButton(MouseButton.Left, true);
            SendMouseButton(MouseButton.Left, false);
        }
        else if (payload.Contains("scroll", StringComparison.OrdinalIgnoreCase))
        {
            SendMouseWheel(120);
        }
        else if (payload.Contains("type", StringComparison.OrdinalIgnoreCase))
        {
            SendText("X");
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
        var scan = char.ToUpperInvariant(ch);
        var vk = (byte)scan;
        NativeMethods.keybd_event(vk, 0, isDown ? 0x0000u : 0x0002u, UIntPtr.Zero);
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
    public static extern void keybd_event(byte bVk, byte bScan, uint dwFlags, UIntPtr dwExtraInfo);
}
