using System;
using System.Runtime.InteropServices;
using System.Threading;

class Program {
    [DllImport("user32.dll")]
    private static extern short GetAsyncKeyState(int vKey);

    private const int VK_CONTROL = 0x11;
    private const int VK_MENU = 0x12; // ALT
    private const int VK_SPACE = 0x20;
    private const int VK_LEFT = 0x25;
    private const int VK_RIGHT = 0x27;
    private const int VK_MEDIA_NEXT = 0xB0;
    private const int VK_MEDIA_PREV = 0xB1;
    private const int VK_MEDIA_PLAY_PAUSE = 0xB3;

    static void Main(string[] args) {
        Console.WriteLine("HOTKEY_READY");
        Console.Out.Flush();

        bool playDown = false;
        bool nextDown = false;
        bool prevDown = false;

        // Exit if stdin closes
        Thread reader = new Thread(() => {
            try {
                while (Console.ReadLine() != null) {}
            } catch {}
            Environment.Exit(0);
        });
        reader.IsBackground = true;
        reader.Start();

        while (true) {
            bool ctrl = (GetAsyncKeyState(VK_CONTROL) & 0x8000) != 0;
            bool alt = (GetAsyncKeyState(VK_MENU) & 0x8000) != 0;

            // Play / Pause
            bool isPlay = ((GetAsyncKeyState(VK_MEDIA_PLAY_PAUSE) & 0x8000) != 0) ||
                          (ctrl && alt && ((GetAsyncKeyState(VK_SPACE) & 0x8000) != 0));
            if (isPlay && !playDown) {
                Console.WriteLine("PLAY_PAUSE");
                Console.Out.Flush();
            }
            playDown = isPlay;

            // Next
            bool isNext = ((GetAsyncKeyState(VK_MEDIA_NEXT) & 0x8000) != 0) ||
                          (ctrl && alt && ((GetAsyncKeyState(VK_RIGHT) & 0x8000) != 0));
            if (isNext && !nextDown) {
                Console.WriteLine("NEXT");
                Console.Out.Flush();
            }
            nextDown = isNext;

            // Prev
            bool isPrev = ((GetAsyncKeyState(VK_MEDIA_PREV) & 0x8000) != 0) ||
                          (ctrl && alt && ((GetAsyncKeyState(VK_LEFT) & 0x8000) != 0));
            if (isPrev && !prevDown) {
                Console.WriteLine("PREV");
                Console.Out.Flush();
            }
            prevDown = isPrev;

            Thread.Sleep(50);
        }
    }
}
