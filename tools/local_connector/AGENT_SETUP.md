# Connect a desktop AI to Ironvellum

On the phone, open **Train → AI tools → Using AI on your PC → Copy setup instructions**. Give that request to an agent on the computer that runs your AI client. You can also copy it here.

> Set up my Ironvellum personal connector using https://github.com/AlexMollard/Ironvellum/blob/main/tools/local_connector/AGENT_SETUP.md. Use the verified source and an isolated Python environment. Ask which local folders and AI client to use. Preserve my other settings. Verify MCP initialization, tool listing and read-only training status. Tell me if I need to reload my client or transfer a file.

## Choose the files and client

Ask the owner to choose a local folder for incoming training exports and a separate private folder for workout proposals. Use a third private directory for connector state. Give each directory its own absolute path under an existing personal directory. Keep settings in a private directory outside the input folder and outbox. Setup creates the chosen leaf directories as needed. Use a fixed local disk and direct paths. A chosen cloud AI provider has its own data terms.

Use the [official source repository](https://github.com/AlexMollard/Ironvellum) and confirm the checkout remote and release tag or commit against the source the owner trusts. In that checkout, change to `tools/local_connector`. Install with CPython 3.11 or later. Installation can download public dependencies. Normal training and proposal transfers use local files and stdio and require no Ironvellum server or account.

## Install and set up

These examples use folders under your home directory. Substitute your chosen paths and client before running them. Add `--watch` to enable refresh during client requests. With `--no-watch` (the default), the connector holds its loaded export until a restart or an explicit dataset change.

**Windows PowerShell**

```powershell
py -3.11 -m venv .venv
$python = (Resolve-Path .venv/Scripts/python.exe).Path
& $python -m pip install -r requirements.lock .
& $python -m pip check
$settings = Join-Path $env:USERPROFILE 'Ironvellum settings/settings.json'
& $python -I -m ironvellum_local_connector setup --settings $settings --input-directory "$env:USERPROFILE/Ironvellum exports" --outbox-directory "$env:USERPROFILE/Ironvellum drafts" --state-directory "$env:USERPROFILE/Ironvellum state" --client config-only --watch --json
& $python -I -m ironvellum_local_connector status --settings $settings --json
```

**macOS or Linux**

```sh
python3 -m venv .venv
python="$PWD/.venv/bin/python"
"$python" -m pip install -r requirements.lock .
"$python" -m pip check
settings="$HOME/Ironvellum settings/settings.json"
"$python" -I -m ironvellum_local_connector setup --settings "$settings" --input-directory "$HOME/Ironvellum exports" --outbox-directory "$HOME/Ironvellum drafts" --state-directory "$HOME/Ironvellum state" --client config-only --watch --json
"$python" -I -m ironvellum_local_connector status --settings "$settings" --json
```

For a compatible stdio MCP client, use the returned `fragment.mcpServers.ironvellum-personal` entry in that client's local settings. Preserve the command and individual arguments, including paths with spaces. For Claude Desktop, select `--client claude-desktop` in the setup command to install that entry. Use `--client-config FILE` to choose a different config explicitly. The default locations are documented in the [MCP guide to local servers](https://modelcontextprotocol.io/docs/2026-07-28/develop/connect-local-servers).

| Platform | Claude Desktop config |
| --- | --- |
| Windows | `%APPDATA%\Claude\claude_desktop_config.json` |
| macOS | `~/Library/Application Support/Claude/claude_desktop_config.json` |
| Linux | `~/.config/Claude/claude_desktop_config.json` |

Setup preserves unrelated servers and preferences. A different existing `ironvellum-personal` entry returns `client_entry_conflict`. Inspect it with the owner before explicitly rerunning setup with `--replace-existing`. Setup keeps the original config in a private sibling file ending in `.bak` before replacing it. An identical entry needs no change. A previously configured single-file server with another name remains available. If the client directory does not meet private storage requirements, use `config-only` and the client's own editor.

## Verify from the chosen client

1. Load the entry in the chosen client. If the client requires a reload, tell the owner. Claude Desktop reads its file on restart.
2. Open an MCP session, initialize it, list tools, then call `get_training_status` with `{}`. The call only reads status. Use the connection in the intended client. A separate diagnostic client's handshake verifies only that diagnostic session.
3. On the phone, use **Share training → Sharing details · Save a copy**. Transfer the original JSON bytes to the selected input folder. A fresh setup may report `awaiting_training` until this transfer. For a fixed single-file setup, replace its file and restart the connector.
4. If a dataset choice is pending, show the owner the available export dates and coverage. Run `select-source --settings FILE --dataset-id UUID --json` with the chosen UUID using the same installed module. Call `get_training_status` again and check its export date and coverage.
5. Report setup, running sessions, each session's actual loaded source, and any remaining transfer, dataset choice or reload. The `status` command works without a desktop window. It reports a session as verified after MCP initialization, tool listing and a successful read-only status call. Session heartbeats expire after 15 seconds. A changed folder configuration requires the owning client to reload.

The phone can import a proposed workout after you transfer its original JSON from the outbox. Review the workout in Ironvellum and choose **Save workout**.

If the owner wants the optional status window, run the same installed Python module
with `desktop --settings FILE`. This needs Tk and a display. Keep the AI client's
own stdio entry and SDK verification in place. The window can observe its sessions,
select a dataset and expose the drafts folder. Headless setup and verification use
the commands above.
