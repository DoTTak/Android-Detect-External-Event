"""Drive a real scrcpy-server (v4.x) control socket to inject the same events the
scrcpy desktop client sends: mouse hover, mouse left-click, and a finger touch.

Usage: python3 tools/scrcpy_inject.py [serial]
"""
import os, socket, struct, subprocess, sys, time

SERIAL = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("ANDROID_SERIAL", "emulator-5554")
SERVER = "/opt/homebrew/share/scrcpy/scrcpy-server"
VERSION = subprocess.check_output(["scrcpy", "--version"], text=True).split()[1]
SCID = "0000abcd"
PORT = 27199

TYPE_INJECT_TOUCH = 2
DOWN, UP, MOVE, HOVER_MOVE = 0, 1, 2, 7
POINTER_MOUSE, POINTER_FINGER = -1, -2
BUTTON_PRIMARY = 1


def adb(*args, **kw):
    return subprocess.run(["adb", "-s", SERIAL, *args], check=True, capture_output=True, text=True, **kw)


def screen_size():
    out = adb("shell", "wm", "size").stdout
    w, h = out.strip().splitlines()[-1].split(":")[1].strip().split("x")
    return int(w), int(h)


def touch(sock, action, pointer, x, y, w, h, pressure, action_button=0, buttons=0):
    sock.sendall(struct.pack(">BBqiiHHHii", TYPE_INJECT_TOUCH, action, pointer, x, y, w, h,
                             0xFFFF if pressure else 0, action_button, buttons))


def main():
    w, h = screen_size()
    adb("push", SERVER, "/data/local/tmp/scrcpy-server.jar")
    adb("forward", f"tcp:{PORT}", f"localabstract:scrcpy_{SCID}")
    server = subprocess.Popen(
        ["adb", "-s", SERIAL, "shell", f"CLASSPATH=/data/local/tmp/scrcpy-server.jar app_process / "
         f"com.genymobile.scrcpy.Server {VERSION} scid={SCID} tunnel_forward=true "
         "video=false audio=false control=true cleanup=false"])
    try:
        sock = None
        for _ in range(50):
            try:
                sock = socket.create_connection(("127.0.0.1", PORT))
                if sock.recv(1) == b"\x00":  # dummy byte => server accepted
                    break
                sock.close()
            except OSError:
                pass
            time.sleep(0.2)
        else:
            raise SystemExit("could not connect to scrcpy-server")

        cx, cy = w // 2, int(h * 0.7)
        print("[scrcpy] mouse hover")
        for i in range(5):
            touch(sock, HOVER_MOVE, POINTER_MOUSE, cx + i * 20, cy, w, h, 0)
            time.sleep(0.05)
        time.sleep(1)

        print("[scrcpy] mouse left click")
        touch(sock, DOWN, POINTER_MOUSE, cx, cy, w, h, 1, BUTTON_PRIMARY, BUTTON_PRIMARY)
        time.sleep(0.08)
        touch(sock, UP, POINTER_MOUSE, cx, cy, w, h, 0, BUTTON_PRIMARY, 0)
        time.sleep(1)

        print("[scrcpy] finger touch (generic finger pointer)")
        touch(sock, DOWN, POINTER_FINGER, cx, cy, w, h, 1)
        touch(sock, MOVE, POINTER_FINGER, cx + 30, cy + 30, w, h, 1)
        time.sleep(0.08)
        touch(sock, UP, POINTER_FINGER, cx + 30, cy + 30, w, h, 0)
        time.sleep(1)
        sock.close()
    finally:
        server.terminate()
        adb("forward", "--remove", f"tcp:{PORT}")


if __name__ == "__main__":
    main()
