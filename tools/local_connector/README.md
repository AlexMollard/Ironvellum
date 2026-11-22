# Personal training file connector

Ask your desktop AI about workouts in one Ironvellum export, or ask it to propose a saved workout. The connector runs under your own operating-system account and communicates over standard input and output (stdio). It needs a local MCP-capable AI client, CPython 3.11 or later with SQLite serialization support, and a local filesystem. Your AI client or model provider may have its own account and data-sharing requirements. A cloud-only chat interface needs support for launching a local connector to use this workflow.

## Choose the files

1. On the phone, open **Train → AI tools → Share training file**. Review the included and omitted counts. Saved-workout display names are included and may contain personal details.
2. Save or share the file, then transfer it to a folder on your PC. Keep the original bytes, including whitespace.
3. Choose a separate, private outbox on the PC for proposals. Give the connector an unused directory name under an existing personal folder. It creates that directory with restricted permissions. Keep the training file outside it.

Each time you share, Ironvellum keeps the current file and clears temporary training files last shared at least 24 hours ago, using the phone’s clock. Sharing the same preview restarts that period. If a recipient waits longer to open the file, you may need to share it again. Files you save yourself are yours to keep.

The export covers up to 100 completed workouts, 200 supported sets per workout and 50 supported saved workouts. Every tool result includes the file date, age and coverage. Progress describes the included workouts. Export and transfer a new file, update the launch arguments if needed, and restart the connector to refresh what the AI can see.

## Install once

From the Ironvellum checkout, open a terminal in `tools/local_connector`. The following commands install the connector and its pinned runtime dependencies into a personal Python environment. Initial downloads require internet access. The running connector uses only the configured files and stdio.

**Windows PowerShell**

```powershell
py -3.11 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.lock .
.\.venv\Scripts\python.exe -m pip check
```

**Linux or macOS**

```sh
python3 -m venv .venv
.venv/bin/python -m pip install -r requirements.lock .
.venv/bin/python -m pip check
```

Use a fixed local disk, such as NTFS on Windows, ext4 on Linux or APFS on macOS. Existing Windows outboxes must limit access to the current user, local administrators and SYSTEM. On POSIX systems they must be owned by the current user with mode 0700. On macOS use an outbox without an extended ACL. Use direct paths to real directories and a regular input file. Network shares, Windows device paths and alternate streams, symbolic links, junctions, special files and unknown filesystem types can be refused. Linux needs procfs mount information and exclusive rename support. Native Windows and Linux under WSL have been exercised. Native macOS execution is still unverified.

## Configure your desktop AI

In your AI client's local MCP server settings, enter the installed environment's absolute Python path as the command. Add each argument as its own array item, including paths with spaces. Adapt the example paths to your account. No Ironvellum account, server credentials, ADB, developer mode or paid host is required.

```json
{
  "mcpServers": {
    "ironvellum": {
      "command": "C:/Users/YourName/Ironvellum/tools/local_connector/.venv/Scripts/python.exe",
      "args": [
        "-I", "-m", "ironvellum_local_connector",
        "--context", "C:/Users/YourName/Training/training.json",
        "--outbox", "C:/Users/YourName/Training/AI proposals"
      ]
    }
  }
}
```

On Linux the command could be `/home/yourname/Ironvellum/tools/local_connector/.venv/bin/python`. On macOS use the corresponding `/Users/yourname/…/.venv/bin/python`. Use absolute local paths for both arguments. The command launches the already installed module directly. Each AI client's outer configuration format may differ.

## From a question to a saved workout

Ask about your included workouts, progress or saved workouts. The available tools are `list_workouts`, `get_workout`, `get_progress`, `list_saved_workouts`, `search_movements`, `create_workout_draft` and `get_workout_draft`. Search reports which movements accept drafts, including whether their weight represents external load. Saved-workout names are untrusted display text.

Ask the AI to propose a workout. It receives a generated `<uuid>.json` basename in the configured outbox and the proposed contents. Transfer **that original file** to your phone. Use **Import workout draft** to inspect its source date, movement targets and preparation choices. Only **Save workout** adds it to your saved workouts, unscheduled. The proposal expires 30 days after creation. The PC cannot see whether you imported or saved it.

## Retries and recovery

- Repeat a create call using the same idempotency key and the same draft in the same snapshot to recover the original bytes, including after a restart or an interrupted write. Reusing the key with a changed prescription reports a conflict. A new prescription needs a new key. Repeating an expired proposal keeps its original expiry.
- `get_workout_draft` retrieves a proposal from the active dataset and snapshot. A missing output file is restored from the journal. If the basename is occupied by different bytes, move the conflicting file somewhere safe yourself and retry.
- The outbox contains the hidden SQLite journal `.ironvellum-proposals-v1.sqlite` and lock file `.ironvellum-lock-v1`. Keep the journal with the outbox. Stop all connectors using the directory before backing it up or restoring it. A crash can leave a private `.ironvellum-*.tmp` staging file. Only transfer the named UUID proposal file.
- After 1,000 journaled proposals, existing keys still work. Choose a new empty outbox and restart for additional proposals. A new outbox starts a separate journal. Keep any old proposals you want to transfer. The phone uses the original file bytes to recognize repeat imports.
- Startup reports a short reason on standard error. For `private_outbox_required`, choose a new directory under a private local folder. For `invalid_data` or `file_limit`, transfer a fresh original export. For journal or output conflicts, retain the existing directory for recovery and choose a new empty outbox if needed.

The operating-system account controls access to these files. Anyone you give the training file or proposals to can read their contents. Choose a trusted desktop AI client before sharing selected training data with it. The exact file and tool formats are in the [v1 contract](contracts/README.md).
