"""Optional native Tk window. The connected AI client owns all stdio sessions."""
from datetime import datetime
from pathlib import Path
from queue import Empty, Queue
import sys
from threading import Thread

from .desktop_copy import load_copy
from .desktop_model import DesktopObserver, literal, open_folder


BACKGROUND = "#0e0e0e"
CARD = "#181816"
BUTTON = "#262624"
BORDER = "#373631"
TEXT = "#efede9"
SECONDARY = "#bfbbb3"
ACCENT = "#2dd3a0"
WARNING = "#edcb9e"


def run(settings, language=None, wording="plain"):
    copy = load_copy(language, wording)
    try:
        import tkinter as tk
        from tkinter import filedialog, font
        root = tk.Tk()
    except ImportError:
        print(copy("gui_unavailable"), file=sys.stderr)
        return 2
    except tk.TclError:
        print(copy("gui_unavailable"), file=sys.stderr)
        return 2

    window = DesktopWindow(tk, root, filedialog, font, settings.absolute(), copy)
    try:
        root.mainloop()
    finally:
        window.stop()
    return 0


class DesktopWindow:
    def __init__(self, tk, root, dialogs, font, path, copy):
        self.tk, self.root, self.dialogs, self.copy = tk, root, dialogs, copy
        self.state = None
        self.page = "main"
        self.notice = ""
        self.read_pending = False
        self.action_pending = False
        self.closed = False
        self.after = None
        self.jobs, self.results = Queue(), Queue()
        self.observer = DesktopObserver(path)
        default = font.nametofont("TkDefaultFont")
        self.body_font = default.copy()
        self.heading_font = default.copy()
        size = default.cget("size")
        offset = 2 if size > 0 else -2
        self.body_font.configure(size=size + offset)
        self.heading_font.configure(size=size + 2 * offset, weight="bold")

        root.configure(bg=BACKGROUND)
        root.geometry("545x660")
        root.minsize(280, 260)
        root.protocol("WM_DELETE_WINDOW", self.close)
        self.scroll = tk.Canvas(root, background=BACKGROUND, highlightthickness=0, takefocus=False)
        bar = tk.Scrollbar(root, command=self.scroll.yview)
        bar.pack(side="right", fill="y")
        self.scroll.configure(yscrollcommand=bar.set)
        self.scroll.pack(fill="both", expand=True)
        self.body = tk.Frame(self.scroll, background=BACKGROUND, padx=14, pady=14)
        self.body_id = self.scroll.create_window(0, 0, anchor="nw", window=self.body)
        self.body.bind("<Configure>", self.fit)
        self.scroll.bind("<Configure>", self.fit)
        root.bind("<MouseWheel>", self.wheel)
        root.bind("<Button-4>", self.wheel)
        root.bind("<Button-5>", self.wheel)
        root.bind("<Prior>", lambda _: self.scroll.yview_scroll(-1, "pages"))
        root.bind("<Next>", lambda _: self.scroll.yview_scroll(1, "pages"))
        root.bind("<Escape>", lambda _: self.main_page() if self.page != "main" and not self.action_pending else None)

        Thread(target=self.work, daemon=True, name="ironvellum-desktop-observer").start()
        self.render()
        self.request_status()
        self.poll()

    def work(self):
        while (job := self.jobs.get()) is not None:
            action, success, failure = job
            try:
                value = action()
            except Exception:
                # Every queued job must deliver a result so polling and recovery continue.
                self.results.put((None, success, failure))
            else:
                self.results.put((value, success, ""))

    def submit(self, action, success="", failure="operation_failed"):
        if self.closed or self.action_pending:
            return
        self.action_pending = True
        for widget in self.buttons.values():
            widget.configure(state="disabled")
        self.jobs.put((action, success, failure))

    def request_status(self):
        if not self.closed and not self.action_pending and not self.read_pending:
            self.read_pending = True
            self.jobs.put((self.observer.read, "", "operation_failed"))

    def poll(self):
        self.after = None
        if self.closed:
            return
        try:
            state, success, failure = self.results.get_nowait()
        except Empty:
            pass
        else:
            if success:
                self.action_pending = False
            else:
                self.read_pending = False
            if failure:
                self.notice = self.copy(failure)
                self.render()
            elif success:
                self.notice = self.copy(success)
                if success in {"saved", "selection_saved"}:
                    self.main_page()
                else:
                    self.render()
                self.request_status()
            elif state != self.state:
                self.state = state
                if self.page == "main":
                    self.render()
        self.after = self.root.after(200 if self.action_pending or self.read_pending else 1100, self.tick)

    def tick(self):
        self.request_status()
        self.poll()

    def stop(self):
        if not self.closed:
            self.closed = True
            self.jobs.put(None)

    def close(self):
        self.stop()
        if self.after is not None:
            self.root.after_cancel(self.after)
        self.root.destroy()

    def fit(self, _):
        width = self.scroll.winfo_width()
        self.scroll.itemconfigure(self.body_id, width=width)
        wrap = max(160, width - 68)
        for label in self.labels:
            label.configure(wraplength=wrap)
        self.scroll.configure(scrollregion=self.scroll.bbox("all"))

    def wheel(self, event):
        delta = -1 if event.num == 4 or event.delta > 0 else 1
        self.scroll.yview_scroll(delta * 3, "units")

    def text(self, parent, value, *, heading=False, fg=SECONDARY):
        label = self.tk.Label(parent, text=value, anchor="w", justify="left",
                              bg=parent.cget("bg"), fg=TEXT if heading else fg,
                              font=self.heading_font if heading else self.body_font,
                              wraplength=max(160, self.scroll.winfo_width() - 68))
        label.pack(fill="x", anchor="w", pady=(0, 3))
        self.labels.append(label)
        return label

    def card(self, heading, *, accent=False):
        frame = self.tk.Frame(self.body, bg=ACCENT if accent else CARD, padx=13, pady=9,
                              highlightthickness=1, highlightbackground=ACCENT if accent else BORDER)
        frame.pack(fill="x", pady=(0, 9))
        if heading:
            label = self.text(frame, heading, heading=True)
            if accent:
                label.configure(fg=BACKGROUND)
        return frame

    def button(self, parent, key, action, *, primary=False, focus_id=None):
        label = self.copy(key)
        widget = self.tk.Button(parent, text=label, command=action, takefocus=True,
                                state="disabled" if self.action_pending else "normal",
                                relief="flat", bd=0, highlightthickness=2, highlightbackground=parent.cget("bg"),
                                highlightcolor=ACCENT, activebackground=BORDER, activeforeground=TEXT,
                                bg=ACCENT if primary else BUTTON, fg=BACKGROUND if primary else TEXT,
                                padx=7, pady=7, font=self.body_font,
                                wraplength=max(160, self.scroll.winfo_width() - 68))
        widget.pack(fill="x", pady=(5, 0))
        widget.bind("<Return>", lambda _: widget.invoke())
        widget.bind("<FocusIn>", self.show_focus)
        self.labels.append(widget)
        self.buttons[focus_id or key] = widget

    def show_focus(self, event):
        self.root.update_idletasks()
        top = event.widget.winfo_rooty() - self.body.winfo_rooty()
        view = self.scroll.canvasy(0)
        height = event.widget.winfo_height()
        visible = self.scroll.winfo_height()
        if top < view or top + height > view + visible:
            target = top if top < view else top + height - visible
            self.scroll.yview_moveto(max(0, target) / max(1, self.body.winfo_height()))

    def render(self):
        focused = self.root.focus_get()
        focus_key = next((key for key, button in getattr(self, "buttons", {}).items() if button == focused), None)
        self.labels = []
        self.buttons = {}
        for child in self.body.winfo_children():
            child.destroy()
        self.root.title(self.copy({"main": "title", "settings": "settings_title", "source": "source_title"}[self.page]))
        if self.notice:
            self.text(self.body, self.notice, fg=WARNING)
        if self.page == "settings":
            self.settings_page()
        elif self.page == "source":
            self.sources_page()
        else:
            self.status_page()
        if focus_key in self.buttons:
            self.buttons[focus_key].focus_set()

    def training(self, heading, row, *, identity=False):
        card = self.card(self.copy(heading))
        try:
            date = datetime.fromtimestamp(row.exported_ms / 1000).strftime("%Y-%m-%d %H:%M")
        except (OverflowError, OSError, ValueError):
            date = self.copy("date_unavailable")
        self.text(card, date)
        self.text(card, self.copy("coverage", workouts=row.workouts, sets=row.sets, saved=row.saved))
        if identity:
            self.text(card, self.copy("source_identity", dataset=row.dataset[:8], snapshot=row.snapshot[:8],
                                     token=row.token[:12]))
        return card

    def status_page(self):
        state = self.state
        if state is None:
            self.card(self.copy("checking"))
            return
        banner_key = "configured" if state.status_key == "awaiting_training" else state.status_key
        banner = self.card(self.copy(banner_key), accent=bool(state.session_count))
        if state.session_count:
            label = self.text(banner, self.copy("connections", count=state.session_count, verified=state.verified))
            label.configure(fg=BACKGROUND)
        if state.reload_required:
            self.text(self.body, self.copy("reload"), fg=WARNING)
        if state.session_problem:
            self.text(self.body, self.copy("session_problem"), fg=WARNING)

        if state.problem and state.settings:
            self.card(self.copy("refresh_failed" if state.loaded else "training_problem"))
        for row in state.loaded:
            self.training("loaded", row, identity=len(state.loaded) > 1)
        if state.session_count and not state.loaded:
            self.text(self.body, self.copy("no_loaded"))
        if state.available and state.available not in state.loaded and not state.session_count:
            self.training("available", state.available)
        if state.problem or (state.settings and not (state.loaded or state.available or state.pending)):
            heading = "check_training" if state.settings and state.problem else "awaiting_training"
            card = self.card(self.copy(heading) if state.settings else "")
            self.text(card, self.copy("check_settings" if not state.settings else
                                      "check_file" if state.problem else "transfer"))
            self.button(card, "retry", self.retry, primary=True)
            if state.settings:
                self.button(card, "open_inbox", lambda: self.open(state.settings.input_directory))
        if state.scanning:
            self.text(self.body, self.copy("checking"))
        if state.pending:
            card = self.card(self.copy("different_source"))
            self.button(card, "source_title", self.choose_source, primary=True)
        if state.settings and not state.settings.watch_enabled:
            self.text(self.body, self.copy("watch_off"))

        card = self.card(self.copy("drafts"))
        if state.drafts_problem or not state.drafts:
            self.text(card, self.copy("drafts_problem" if state.drafts_problem else "no_drafts"))
        for draft in state.drafts:
            self.text(card, draft.name, fg=TEXT)
            self.text(card, draft.filename)
        if state.settings:
            self.button(card, "open_drafts", lambda: self.open(state.settings.outbox_directory))

        card = self.card(self.copy("agent"))
        self.text(card, self.copy("agent_hint"))
        self.button(card, "copy", self.copy_request)
        self.button(self.body, "manual", self.open_settings)
        self.text(self.body, self.copy("local"))

    def retry(self):
        self.notice = ""
        self.render()
        self.request_status()

    def open(self, folder):
        self.submit(lambda: open_folder(folder), "folder_opened", "folder_failed")

    def copy_request(self):
        try:
            self.root.clipboard_clear()
            self.root.clipboard_append(self.copy("agent_request"))
        except self.tk.TclError:
            self.notice = self.copy("copy_failed")
        else:
            self.notice = self.copy("copied")
        self.render()

    def main_page(self):
        if self.action_pending:
            return
        self.page = "main"
        self.scroll.yview_moveto(0)
        self.render()

    def open_settings(self):
        current = self.state.settings
        self.inbox = current.input_directory if current else None
        self.outbox = current.outbox_directory if current else None
        self.watch = self.tk.BooleanVar(value=bool(current and current.watch_enabled))
        self.page = "settings"
        self.notice = ""
        self.scroll.yview_moveto(0)
        self.render()
        self.buttons["copy"].focus_set()

    def settings_page(self):
        self.text(self.body, self.copy("agent_hint"))
        self.button(self.body, "copy", self.copy_request)
        for key in ("inbox", "outbox"):
            card = self.card(self.copy(key))
            path = getattr(self, key)
            self.text(card, literal(str(path)) if path else self.copy("no_folder"))
            self.button(card, "choose_folder", lambda key=key: self.choose_folder(key), focus_id=key)
        card = self.card("")
        switch = self.tk.Checkbutton(card, text=self.copy("watch"), variable=self.watch, font=self.body_font,
                                     bg=CARD, fg=TEXT, activebackground=CARD, activeforeground=TEXT,
                                     selectcolor=BACKGROUND, highlightcolor=ACCENT, highlightbackground=CARD,
                                     anchor="w", justify="left", wraplength=max(160, self.scroll.winfo_width() - 68))
        switch.configure(state="disabled" if self.action_pending else "normal")
        self.buttons["watch"] = switch
        switch.pack(fill="x")
        switch.bind("<FocusIn>", self.show_focus)
        self.labels.append(switch)
        self.text(card, self.copy("watch_help"))
        self.text(card, self.copy("watch_off"))
        self.button(self.body, "save", self.save, primary=True)
        self.button(self.body, "cancel", self.main_page)

    def choose_folder(self, key):
        path = getattr(self, key)
        chosen = self.dialogs.askdirectory(parent=self.root, title=self.copy(key), initialdir=str(path) if path else None)
        if chosen:
            setattr(self, key, Path(chosen))
            self.render()

    def save(self):
        inbox, outbox, watch = self.inbox, self.outbox, self.watch.get()
        self.submit(lambda: self.observer.save(inbox, outbox, watch), "saved", "settings_failed")

    def choose_source(self):
        self.choice = self.state
        self.notice = ""
        self.page = "source"
        self.scroll.yview_moveto(0)
        self.render()

    def sources_page(self):
        state = self.choice
        self.text(self.body, self.copy("different_source"), heading=True)
        self.text(self.body, self.copy("choose_source_help"))
        for row in state.loaded:
            self.training("current", row, identity=True)
        if state.available and not state.loaded:
            self.training("available", state.available, identity=True)
        for row in state.pending:
            card = self.training("new_file", row, identity=True)
            if len(state.pending) > 1:
                self.button(card, "use_new", lambda row=row: self.select(row.dataset), focus_id=("use_new", row.dataset))
        self.text(self.body, self.copy("existing_drafts"))
        # A currently loaded source can be different from the diagnostic choice.
        # Only offer to confirm a specific, unambiguous current dataset.
        current = {row.dataset for row in state.loaded}
        if not current and state.available:
            current.add(state.available.dataset)
        if len(current) == 1:
            self.button(self.body, "keep", lambda: self.select(next(iter(current))), primary=True)
        if len(state.pending) == 1:
            self.button(self.body, "use_new", lambda: self.select(state.pending[0].dataset), primary=not current,
                        focus_id=("use_new", state.pending[0].dataset))
        self.button(self.body, "cancel", self.main_page)

    def select(self, dataset):
        self.submit(lambda: self.observer.select(dataset), "selection_saved", "selection_failed")
