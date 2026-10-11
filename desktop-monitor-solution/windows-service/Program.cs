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

        var listener = new TcpListener(IPAddress.Loopback, port);
        listener.Start();

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
                var length = BitConverter.ToInt32(lengthBuffer, 0);
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

                var lengthBuffer = BitConverter.GetBytes(frame.Length);
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
    [DllImport("user32.dll")]
    public static extern void mouse_event(uint dwFlags, uint dx, uint dy, int dwData, IntPtr dwExtraInfo);

    [DllImport("user32.dll")]
    public static extern void keybd_event(byte bVk, byte bScan, uint dwFlags, UIntPtr dwExtraInfo);
}
