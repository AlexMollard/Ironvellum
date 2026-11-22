import importlib
import asyncio
from pathlib import Path
import subprocess
import sys
from types import SimpleNamespace

import pytest

from ironvellum_local_connector.desktop_model import DesktopObserver, DesktopState, FolderUnavailable, open_folder
from ironvellum_local_connector.settings import ConnectorSettings, load_settings, save_settings
from test_stdio import PROPOSAL
from test_watch_stdio import adopted, call, connect, export, pending_choice


def source(token):
    return dict(datasetId="dataset", snapshotId="snapshot", sourceSha256=token,
                exportedAtMs=1000, coverage=dict(workoutsIncluded=12, setsIncluded=180, savedWorkoutsIncluded=4))


def test_configured_is_not_loaded_after_session_expires():
    report = dict(configured=True, availableSource=source("new"), sessions=[], pending=[], code="ok")
    state = DesktopState.from_report(report, [])
    assert state.status_key == "configured"
    assert state.loaded == ()
    assert state.available.token == "new"


def test_live_sources_are_separate_from_diagnostics():
    report = dict(configured=True, availableSource=source("new"), code="invalid_data",
                  sessionProblem="invalid_session", sessions=[
                      dict(loadedSource=source("old"), clientVerified=True, reloadRequired=False),
                      dict(loadedSource=source("other"), clientVerified=False, reloadRequired=True),
                  ], pending=[source("pending")])
    state = DesktopState.from_report(report, [])
    assert state.status_key == "running"
    assert (state.verified, state.session_count, state.reload_required) == (1, 2, True)
    assert {row.token for row in state.loaded} == {"old", "other"}
    assert state.pending[0].token == "pending"
    assert state.problem == "invalid_data" and state.session_problem == "invalid_session"


@pytest.mark.parametrize("code, status", [("ok", "awaiting_training"), ("invalid_data", "awaiting_training")])
def test_empty_or_invalid_training(code, status):
    state = DesktopState.from_report(dict(configured=True, code=code), [])
    assert state.status_key == status
    assert state.problem == (None if code == "ok" else code)


def test_draft_text_is_literal_and_original_filename_is_retained():
    item = dict(name="[$env:PATH]; {name}\n\0\u202eUpper", filename="original.json")
    state = DesktopState.from_report({}, [item])
    assert state.drafts[0].name == "[$env:PATH]; {name}���Upper"
    assert state.drafts[0].filename == "original.json"


def test_folder_opener_passes_one_literal_path(tmp_path, monkeypatch):
    folder = tmp_path / "[$(whoami)]; folder"
    folder.mkdir()
    monkeypatch.setattr(sys, "platform", "linux")
    monkeypatch.setattr("shutil.which", lambda name: "/bin/xdg-open")
    calls = []
    monkeypatch.setattr(subprocess, "run", lambda args, **kwargs: calls.append((args, kwargs)))
    open_folder(folder)
    assert calls[0][0] == ["/bin/xdg-open", str(folder)]
    assert not calls[0][1].get("shell")
    monkeypatch.setattr("shutil.which", lambda name: None)
    with pytest.raises(FolderUnavailable):
        open_folder(folder)


def test_missing_tk_returns_guidance_without_touching_settings(monkeypatch, capsys):
    from ironvellum_local_connector.desktop import run
    monkeypatch.setitem(sys.modules, "tkinter", None)
    assert run(Path("missing/settings.json"), "en", "plain") == 2
    message = capsys.readouterr().err
    assert "gui_unavailable" in message and "status --settings" in message


def test_headless_import_stays_independent_of_tk(monkeypatch):
    monkeypatch.setitem(sys.modules, "tkinter", None)
    importlib.import_module("ironvellum_local_connector.__main__")
    importlib.import_module("ironvellum_local_connector.desktop")


def test_keep_selected_training_after_its_only_export_is_replaced(tmp_path):
    settings = ConnectorSettings(tmp_path / "input", tmp_path / "outbox", tmp_path / "state",
                                 watch_enabled=True)
    settings.input_directory.mkdir()
    a, original = export(settings, 1)
    config = tmp_path / "config" / "settings.json"
    save_settings(config, ConnectorSettings(*settings.endpoints, original["datasetId"], True))
    observer = DesktopObserver(config)

    async def run():
        async with connect(config) as client:
            await client.list_tools()
            await adopted(client, a)
            b, newer = export(settings, 2, other=True)
            (settings.input_directory / "2.json").replace(settings.input_directory / "1.json")
            status = await pending_choice(client)
            assert status["sourceSha256"] == a
            assert [row["sourceSha256"] for row in status["pending"]] == [b]

            observer.select(original["datasetId"])

            assert load_settings(config).selected_dataset_id == original["datasetId"]
            assert (await adopted(client, a))["sourceSha256"] == a
            observer.select(newer["datasetId"])
            assert load_settings(config).selected_dataset_id == newer["datasetId"]
            await adopted(client, b)

    asyncio.run(asyncio.wait_for(run(), timeout=25))


def test_window_close_leaves_an_external_sdk_session_usable(tmp_path):
    from ironvellum_local_connector.desktop import DesktopWindow
    from queue import Queue

    settings = ConnectorSettings(tmp_path / "input", tmp_path / "outbox", tmp_path / "state",
                                 watch_enabled=True)
    settings.input_directory.mkdir()
    token, snapshot = export(settings, 1)
    config = tmp_path / "config" / "settings.json"
    save_settings(config, ConnectorSettings(*settings.endpoints, snapshot["datasetId"], True))
    observer = DesktopObserver(config)

    async def run():
        async with connect(config) as client:
            await client.list_tools()
            await adopted(client, token)
            assert observer.read().loaded[0].token == token

            # Exercise the close callback without a display server. The external
            # SDK client's real process persists across this simulated root close.
            destroyed = []
            window = DesktopWindow.__new__(DesktopWindow)
            window.root = SimpleNamespace(destroy=lambda: destroyed.append(True))
            window.closed, window.after, window.jobs = False, None, Queue()
            window.close()
            assert window.closed and destroyed == [True] and window.jobs.get_nowait() is None

            read = await call(client, "list_workouts", sourceSha256=token)
            assert read["workouts"][0]["id"] == snapshot["workouts"][0]["id"]
            created = await call(client, "create_workout_draft", sourceSha256=token,
                                 draft=PROPOSAL["draft"], idempotencyKey="desktop-close")
            assert (settings.outbox_directory / created["filename"]).is_file()

    asyncio.run(asyncio.wait_for(run(), timeout=25))

def test_worker_delivers_failure_then_processes_next_job():
    from queue import Queue
    from ironvellum_local_connector.desktop import DesktopWindow

    window = DesktopWindow.__new__(DesktopWindow)
    window.jobs, window.results = Queue(), Queue()
    def fail():
        raise ValueError("private detail")
    window.jobs.put((fail, "", "operation_failed"))
    window.jobs.put((lambda: 42, "saved", "settings_failed"))
    window.jobs.put(None)
    window.work()
    assert window.results.get_nowait() == (None, "", "operation_failed")
    assert window.results.get_nowait() == (42, "saved", "")


@pytest.mark.parametrize("page,success", [("settings", "saved"), ("source", "selection_saved")])
def test_controls_wait_for_action_result(page, success):
    from queue import Queue
    from ironvellum_local_connector.desktop import DesktopWindow

    window = DesktopWindow.__new__(DesktopWindow)
    window.closed = window.action_pending = window.read_pending = False
    window.jobs, window.results = Queue(), Queue()
    window.page = page
    states = {}
    keys = ("cancel", "outbox", "watch", "save") if page == "settings" else ("cancel", "use_new", "keep")
    window.buttons = {key: SimpleNamespace(configure=lambda state, k=key: states.update({k: state})) for key in keys}
    window.submit(lambda: None, success, "operation_failed")
    assert states == dict.fromkeys(keys, "disabled")
    window.main_page()
    assert window.page == page
    window.root = SimpleNamespace(after=lambda *args: None)
    window.render = lambda: None
    window.copy = str
    window.results.put((None, success, "operation_failed"))
    window.poll()
    assert not window.action_pending and window.page == page

@pytest.mark.parametrize("page,focus_id", [("settings", "outbox"), ("source", ("use_new", "b"))])
def test_redraw_restores_second_control_by_identity(page, focus_id):
    from ironvellum_local_connector.desktop import DesktopWindow

    class Widget:
        def __init__(self, *args, **options):
            pass

        def pack(self, **options):
            pass

        def bind(self, *args):
            pass

        def configure(self, **options):
            pass

        def focus_set(self):
            focused[0] = self

    window = DesktopWindow.__new__(DesktopWindow)
    focused = [None]
    window.root = SimpleNamespace(focus_get=lambda: focused[0], title=lambda _: None)
    window.body = SimpleNamespace(winfo_children=lambda: [], cget=lambda _: "")
    window.scroll = SimpleNamespace(winfo_width=lambda: 500)
    window.tk = SimpleNamespace(Button=Widget, Checkbutton=Widget)
    window.body_font = window.watch = window.inbox = window.outbox = None
    window.page, window.notice, window.action_pending = page, "", False
    window.copy = str
    window.text = lambda *args, **kwargs: None
    window.card = lambda *args: window.body
    window.training = lambda *args, **kwargs: window.body
    window.choice = SimpleNamespace(loaded=[], available=None,
                                    pending=[SimpleNamespace(dataset=k) for k in ("a", "b")])
    window.render()
    original = window.buttons[focus_id]
    original.focus_set()
    window.render()
    assert focused[0] is window.buttons[focus_id] and focused[0] is not original
