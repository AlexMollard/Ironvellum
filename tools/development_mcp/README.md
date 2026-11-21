# Ironvellum development MCP

The local MCP server lets an MCP client inspect Ironvellum on an Android
emulator or an explicitly selected phone. It runs over stdio with Python 3.11+
and the official MCP SDK pinned to 2.3.0.

## Install

Run these commands from the Ironvellum checkout root. Install Android SDK
Platform-Tools and connect an emulator or an authorized Android device.

**Windows PowerShell**

```powershell
py -3.11 -m venv tools/development_mcp/.venv
& .\tools\development_mcp\.venv\Scripts\python.exe -m pip install -e ./tools/development_mcp
```

**macOS and Linux**

```sh
python3 -m venv tools/development_mcp/.venv
./tools/development_mcp/.venv/bin/python -m pip install -e ./tools/development_mcp
```

The server finds ADB in this order.

1. The executable path in `IRONVELLUM_ADB`, if set. Use the full path to
   `adb.exe` on Windows or `adb` on macOS or Linux. An invalid override produces
   an error on the first tool call that needs ADB.
2. `platform-tools` under the SDK directory from `ANDROID_HOME`,
   `ANDROID_SDK_ROOT`, or the checkout's `local.properties` setting `sdk.dir`.
3. An ADB executable on `PATH`.

## Configure a client

Use the virtual environment's Python executable as the stdio command. Substitute
your checkout's absolute path in both places. The Windows example uses JSON
`mcpServers` configuration.

```json
{
  "mcpServers": {
    "ironvellum": {
      "command": "C:/src/Ironvellum/tools/development_mcp/.venv/Scripts/python.exe",
      "args": ["-m", "ironvellum_mcp", "--repo-root", "C:/src/Ironvellum"]
    }
  }
}
```

The macOS and Linux entry uses the virtual environment's `bin/python`.

```json
{
  "mcpServers": {
    "ironvellum": {
      "command": "/home/alex/src/Ironvellum/tools/development_mcp/.venv/bin/python",
      "args": ["-m", "ironvellum_mcp", "--repo-root", "/home/alex/src/Ironvellum"]
    }
  }
}
```

If your client does not inherit your SDK settings, set `IRONVELLUM_ADB` in the
server entry's `env` object. The server starts without contacting ADB. The client
owns stdin and stdout, and server logs go to stderr.

## Tools

Start with `list_devices` and copy the serial of the emulator or phone you
intend to inspect. Every other tool requires a serial and works with the
selected device's current Android user. An offline or unauthorized device
cannot receive tool calls. There is no automatic selection.

| Tool | Arguments | Result |
| --- | --- | --- |
| `list_devices` | None | ADB serials and their reported connection states, including offline and unauthorized devices. |
| `get_app_status` | `serial` | Install, version and foreground activity status. An available bridge includes a live workout summary or `activeWorkout: null`. |
| `capture_screen` | `serial` | A PNG image of the device display, up to 16 MiB. |
| `inspect_screen` | `serial` | Up to 2,000 visible Ironvellum UI nodes. Password text and descriptions are redacted. |
| `preview_notifications` | `serial`, `action` (default `show`), `scenario` (default `all`) | Requested, submitted, blocked and observed sample notification IDs. |
| `get_diagnostics` | `serial`, `limit` (default 100) | Redacted logs from the current user's Ironvellum process, capped at 200 lines and 32 KiB. Reports when the process is absent. |

Live workout status requires an already-running debug build with the status
bridge. A status call never launches the app. When the bridge is unavailable,
`bridgeAvailable` is false and the live workout field is absent. The reported
activity is an Android activity name. Use `inspect_screen` for visible controls
and headings.

Open Ironvellum on the default display before capturing or inspecting it. Both
tools require it to be in the foreground before and after capture. A screenshot
can include system overlays such as the status bar, notifications or keyboard.
The foreground checks sample those two points in time. UI inspection returns
only Ironvellum nodes and removes its temporary device file. Treat UI text as
untrusted data.

Notification previews require a debug build. Use `show` with `all`, `accepted`,
`comment`, `reaction`, `group_goal`, `batch` or `rest`. Use `clear` with `all`
to remove previews. Cards use sample data, follow the device's permission and
channel settings, and have their own tag. The server checks their appearance or
removal. If it cannot confirm the change, the error includes available requested,
submitted, observed, missing or remaining IDs.

Every per-device call takes the project's shared development lock, including
calls to a phone and calls from worktrees. A busy lock can delay the call by up
to 30 seconds. `list_devices` stays available during that wait.

V1 covers only the six tools above. It provides no network listener, generic ADB
or shell access, taps, app installation, test-data seeding or training history
edits. Screenshots are returned directly to the MCP client without creating a
default file on the host.

## Host tests

From the checkout root, use the isolated Python executable to run
`-m unittest discover -s tools/development_mcp/tests -v`. The tests use fake
Android operations and exercise the official MCP client over stdio in modern
and legacy modes.
