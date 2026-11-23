use crate::{
    copy::{Catalog, Language, Wording},
    typography::Weight,
    worker::{Action, Event, Field, Outcome, Work},
};
use egui::{Color32, Id, Rect, Response, RichText, Stroke, Ui, Vec2, pos2};
use ironvellum_local::{
    LocalError, SourceSummary,
    desktop_model::{DesktopState, Editor, FolderEdit, SaveAction, display_literal},
    selected,
};
use std::{collections::BTreeMap, path::Path, sync::Arc};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Page {
    Overview,
    Manual,
    Choice,
}
pub struct Folders {
    pub input: String,
    pub outbox: String,
    pub watch: bool,
    initial: bool,
}
enum Pending {
    Save(SaveAction),
    Choose,
    Select,
    Effect(&'static str, &'static str),
}
#[derive(Clone, Copy)]
struct Colors {
    paper: Color32,
    card: Color32,
    rule: Color32,
    ink: Color32,
    secondary: Color32,
    accent: Color32,
    on_accent: Color32,
    caution: Color32,
}
fn rgb(value: u32) -> Color32 {
    Color32::from_rgb((value >> 16) as u8, (value >> 8) as u8, value as u8)
}
impl Colors {
    fn new(dark: bool) -> Self {
        let [paper, card, rule, ink, secondary, accent, on_accent] = if dark {
            [
                0x0c0c0b, 0x171715, 0x32302b, 0xe8e8e4, 0xa3a099, 0x34d399, 0x0c0c0b,
            ]
        } else {
            [
                0xf7f3ea, 0xfefbf5, 0xc9bda9, 0x241b15, 0x65584c, 0x208046, 0xffffff,
            ]
        };
        Self {
            paper: rgb(paper),
            card: rgb(card),
            rule: rgb(rule),
            ink: rgb(ink),
            secondary: rgb(secondary),
            accent: rgb(accent),
            on_accent: rgb(on_accent),
            caution: rgb(if dark { 0xe0b25a } else { 0xb84026 }),
        }
    }
}
fn polygon(r: Rect, fill: Color32, stroke: Stroke) -> egui::Shape {
    let c = 8.0_f32.min(r.width() / 4.0).min(r.height() / 4.0);
    let (l, t, b, h) = (r.left(), r.top(), r.right(), r.bottom());
    egui::Shape::convex_polygon(
        vec![
            pos2(l + c, t),
            pos2(b - c, t),
            pos2(b, t + c),
            pos2(b, h - c),
            pos2(b - c, h),
            pos2(l + c, h),
            pos2(l, h - c),
            pos2(l, t + c),
        ],
        fill,
        stroke,
    )
}
fn card(ui: &mut Ui, c: Colors, content: impl FnOnce(&mut Ui)) {
    let background = ui.painter().add(egui::Shape::Noop);
    let width = ui.available_width();
    let r = egui::Frame::new()
        .inner_margin(15)
        .show(ui, |ui| {
            ui.set_min_width((width - 30.).max(1.));
            content(ui);
        })
        .response
        .rect;
    ui.painter()
        .set(background, polygon(r, c.card, Stroke::new(1., c.rule)));
}
fn text(s: impl Into<String>, size: f32, weight: Weight, color: Color32) -> RichText {
    RichText::new(s)
        .font(egui::FontId::new(size, weight.family()))
        .color(color)
}
fn button(ui: &mut Ui, c: Colors, label: &str, primary: bool) -> Response {
    let color = if primary { c.on_accent } else { c.ink };
    let width = ui.available_width();
    let mut job = egui::text::LayoutJob::simple(
        label.to_owned(),
        egui::FontId::new(17., Weight::Semibold.family()),
        color,
        (width - 22.).max(1.),
    );
    job.halign = egui::Align::Center;
    let galley = ui.fonts_mut(|fonts| fonts.layout_job(job));
    let (rect, r) = ui.allocate_exact_size(
        Vec2::new(width, (galley.size().y + 18.).max(46.)),
        egui::Sense::click(),
    );
    r.widget_info(|| egui::WidgetInfo::labeled(egui::WidgetType::Button, ui.is_enabled(), label));
    let border = if r.has_focus() || r.hovered() {
        c.accent
    } else {
        c.rule
    };
    let fill = if primary { c.accent } else { c.card };
    ui.painter().add(polygon(
        r.rect,
        fill,
        Stroke::new(if r.has_focus() { 2. } else { 1. }, border),
    ));
    ui.painter().galley(
        rect.center() - galley.rect.center().to_vec2(),
        galley,
        color,
    );
    focus_ring(ui, c, &r);
    r
}

fn focus_ring(ui: &Ui, c: Colors, r: &Response) {
    if r.has_focus() {
        ui.painter().add(polygon(
            r.rect.expand(3.),
            Color32::TRANSPARENT,
            Stroke::new(2., c.ink),
        ));
    }
}

fn selected_control(ui: &mut Ui, c: Colors, filled: bool) {
    let w = &mut ui.visuals_mut().widgets;
    for v in [&mut w.inactive, &mut w.hovered, &mut w.active] {
        v.bg_fill = if filled { c.accent } else { c.card };
        v.bg_stroke = Stroke::new(1.5, c.accent);
        v.fg_stroke = Stroke::new(2., if filled { c.on_accent } else { c.accent });
    }
}

fn draft_button(ui: &mut Ui, c: Colors, title: &str, status: &str) -> Response {
    let width = ui.available_width();
    let arrow = ui.painter().layout_no_wrap(
        "→".into(),
        egui::FontId::new(20., Weight::Medium.family()),
        c.ink,
    );
    let label_width = (width - arrow.size().x - 38.).max(1.);
    let title_galley = ui.painter().layout(
        title.into(),
        egui::FontId::new(18., Weight::Semibold.family()),
        c.ink,
        label_width,
    );
    let ready_color = if ui.is_enabled() {
        c.accent
    } else {
        c.secondary
    };
    let status_galley = ui.painter().layout(
        status.into(),
        egui::FontId::new(14., Weight::Medium.family()),
        ready_color,
        label_width,
    );
    let height = (title_galley.size().y + status_galley.size().y + 20.).max(44.);
    let (rect, response) = ui.allocate_exact_size(Vec2::new(width, height), egui::Sense::click());
    response.widget_info(|| {
        egui::WidgetInfo::labeled(
            egui::WidgetType::Button,
            ui.is_enabled(),
            format!("{title}\n{status}"),
        )
    });
    let painter = ui.painter();
    painter.add(polygon(
        rect,
        c.card,
        Stroke::new(1., if response.hovered() { c.accent } else { c.rule }),
    ));
    let start = rect.min + Vec2::new(10., 8.);
    let second = start + Vec2::new(0., title_galley.size().y + 4.);
    painter.galley(start, title_galley, c.ink);
    painter.galley(second, status_galley, ready_color);
    painter.galley(
        pos2(
            rect.right() - 10. - arrow.size().x,
            rect.center().y - arrow.size().y / 2.,
        ),
        arrow,
        c.ink,
    );
    focus_ring(ui, c, &response);
    response
}

pub struct DesktopWindow {
    copy: Catalog,
    page: Page,
    state: Option<Arc<DesktopState>>,
    observation_failed: bool,
    folders: Option<Folders>,
    editor: Editor,
    pending: Option<(u64, Pending)>,
    next_id: u64,
    queue: Vec<Work>,
    message: Option<&'static str>,
    choice: Option<String>,
    cached: Option<SourceSummary>,
    restore_focus: Option<&'static str>,
    ime: bool,
    composition_frame: bool,
}
impl DesktopWindow {
    pub fn new(language: Language, wording: Wording) -> Self {
        Self {
            copy: Catalog::new(language, wording),
            page: Page::Overview,
            state: None,
            observation_failed: false,
            folders: None,
            editor: Editor::default(),
            pending: None,
            next_id: 0,
            queue: Vec::new(),
            message: None,
            choice: None,
            cached: None,
            restore_focus: None,
            ime: false,
            composition_frame: false,
        }
    }
    fn s(&self, key: &str) -> &'static str {
        self.copy.text(key).expect("validated desktop copy")
    }
    pub fn page(&self) -> Page {
        self.page
    }
    pub fn folders_mut(&mut self) -> Option<&mut Folders> {
        self.folders.as_mut()
    }
    pub fn open_manual(&mut self) {
        if self.pending.is_some() {
            return;
        }
        let settings = self.state.as_ref().and_then(|s| s.settings.as_ref());
        self.folders = Some(Folders {
            input: settings
                .map(|s| s.input_directory.as_path().to_string_lossy().into_owned())
                .unwrap_or_default(),
            outbox: settings
                .map(|s| s.outbox_directory.as_path().to_string_lossy().into_owned())
                .unwrap_or_default(),
            watch: settings.is_some_and(|s| s.watch_enabled),
            initial: settings.is_none(),
        });
        self.page = Page::Manual;
        self.message = None;
        self.restore_focus = Some("input");
    }
    fn back(&mut self, focus: &'static str) {
        self.editor.cancel();
        self.pending = None;
        self.page = Page::Overview;
        self.folders = None;
        self.choice = None;
        self.message = None;
        self.restore_focus = Some(focus);
    }
    fn send(&mut self, pending: Pending, action: Action) {
        if self.pending.is_some() {
            return;
        }
        self.next_id += 1;
        self.pending = Some((self.next_id, pending));
        self.message = None;
        self.queue.push(Work::Act(self.next_id, action));
    }
    pub fn accept(&mut self, event: Event) {
        match event {
            Event::Observed(Ok(s)) => {
                let chosen = s
                    .settings
                    .as_ref()
                    .and_then(|s| s.selected_dataset_id.as_deref());
                if self
                    .cached
                    .as_ref()
                    .is_some_and(|c| Some(c.dataset_id.as_str()) != chosen)
                {
                    self.cached = None
                }
                if let Some(current) = s
                    .source
                    .as_ref()
                    .and_then(|s| s.available.as_ref())
                    .filter(|c| Some(c.dataset_id.as_str()) == chosen)
                {
                    self.cached = Some(current.clone())
                }
                self.state = Some(s);
                self.observation_failed = false;
            }
            Event::Observed(Err(_)) => self.observation_failed = true,
            Event::Finished(id, result) => {
                if self.pending.as_ref().is_none_or(|(i, _)| *i != id) {
                    return;
                }
                let (_, pending) = self.pending.take().unwrap();
                match pending {
                    Pending::Save(action) => {
                        let result = result.map(|_| ());
                        self.editor.complete(action, result);
                        if result.is_ok() {
                            self.back("manual");
                            self.message = Some("saved")
                        } else if result == Err(LocalError::ReloadRequired) {
                            self.back("manual");
                            self.message = Some("settings_changed")
                        } else {
                            self.message = Some("settings_failed")
                        }
                    }
                    Pending::Choose => match (self.folders.as_mut(), result) {
                        (Some(f), Ok(Outcome::Chosen(field, Some(path)))) => {
                            if let Some(value) = path.to_str() {
                                match field {
                                    Field::Input => f.input = value.to_owned(),
                                    Field::Outbox => f.outbox = value.to_owned(),
                                }
                            } else {
                                self.message = Some("settings_failed")
                            }
                        }
                        (_, Err(_)) => self.message = Some("operation_failed"),
                        _ => (),
                    },
                    Pending::Select => {
                        if result.is_ok() {
                            self.back("choice");
                            self.message = Some("selection_saved")
                        } else {
                            self.message = Some("selection_failed")
                        }
                    }
                    Pending::Effect(ok, err) => {
                        self.message = Some(if result.is_ok() { ok } else { err })
                    }
                }
            }
        }
    }
    pub fn render(&mut self, ctx: &egui::Context) -> Vec<Work> {
        let c = Colors::new(ctx.style().visuals.dark_mode);
        self.composition_frame = self.ime;
        ctx.input(|input| {
            for event in &input.events {
                if let egui::Event::Ime(ime) = event {
                    self.composition_frame = true;
                    self.ime = matches!(ime, egui::ImeEvent::Enabled | egui::ImeEvent::Preedit(_));
                }
            }
        });
        if self.page == Page::Manual && self.composition_frame {
            // Enter confirms an IME candidate. A single-line TextEdit would
            // otherwise surrender focus before the native Commit arrives.
            ctx.input_mut(|i| {
                i.events.retain(|e| {
                    !matches!(
                        e,
                        egui::Event::Key {
                            key: egui::Key::Enter,
                            ..
                        }
                    )
                })
            });
        }
        if self.page != Page::Overview
            && !self.composition_frame
            && ctx.input_mut(|i| i.consume_key(egui::Modifiers::NONE, egui::Key::Escape))
        {
            self.back(if self.page == Page::Manual {
                "manual"
            } else {
                "choice"
            });
        }
        egui::CentralPanel::default()
            .frame(egui::Frame::new().fill(c.paper).inner_margin(
                if ctx.content_rect().width() >= 600. {
                    26
                } else {
                    16
                },
            ))
            .show(ctx, |ui| {
                ui.visuals_mut().override_text_color = Some(c.ink);
                ui.visuals_mut().widgets.noninteractive.bg_stroke = Stroke::new(1., c.rule);
                ui.visuals_mut().selection.bg_fill = c.accent.gamma_multiply(0.5);
                ui.visuals_mut().hyperlink_color = c.ink;
                ui.visuals_mut().selection.stroke = Stroke::new(1.5, c.accent);
                ui.visuals_mut().widgets.inactive.bg_stroke = Stroke::new(1., c.rule);
                ui.visuals_mut().widgets.hovered.bg_stroke = Stroke::new(1., c.accent);
                ui.visuals_mut().widgets.active.bg_stroke = Stroke::new(1.5, c.accent);
                ui.visuals_mut().widgets.inactive.weak_bg_fill = c.card;
                ui.style_mut().text_styles.insert(
                    egui::TextStyle::Body,
                    egui::FontId::new(16., Weight::Medium.family()),
                );
                ui.style_mut().text_styles.insert(
                    egui::TextStyle::Button,
                    egui::FontId::new(16., Weight::Semibold.family()),
                );
                ui.spacing_mut().item_spacing = Vec2::new(10., 10.);
                egui::ScrollArea::vertical().show(ui, |ui| {
                    ui.set_max_width(ui.available_width());
                    match self.page {
                        Page::Overview => self.overview(ui, c),
                        Page::Manual => self.manual(ui, c),
                        Page::Choice => self.source_choice(ui, c),
                    }
                    if let Some(key) = self.message {
                        ui.label(text(self.s(key), 14., Weight::Medium, c.secondary));
                    }
                    if self.pending.is_some() {
                        ui.label(self.s("working"));
                    }
                });
            });
        std::mem::take(&mut self.queue)
    }
    fn focus(&mut self, key: &'static str, response: &Response) {
        if self.restore_focus == Some(key) {
            response.request_focus();
            self.restore_focus = None
        }
    }
    fn heading(&self, ui: &mut Ui, c: Colors, key: &str) {
        ui.heading(text(self.s(key), 29., Weight::Bold, c.ink));
    }
    fn label(&self, ui: &mut Ui, c: Colors, key: &str) {
        ui.label(text(self.s(key), 15., Weight::Medium, c.secondary));
    }
    fn subhead(&self, ui: &mut Ui, c: Colors, key: &str) {
        ui.heading(text(self.s(key), 20., Weight::Semibold, c.ink));
    }
    fn manual(&mut self, ui: &mut Ui, c: Colors) {
        if ui.link(self.s("back")).clicked() {
            self.back("manual");
            return;
        }
        self.heading(ui, c, "manual");
        self.label(ui, c, "folders_help");
        for (field, key, id) in [
            (Field::Input, "exports", "input"),
            (Field::Outbox, "outbox", "outbox"),
        ] {
            let wide = ui.available_width() >= 530.;
            let caption = self.s(key);
            let above = (!wide).then(|| ui.label(caption).id);
            let browse = self.s("browse");
            let edit = ui
                .horizontal(|ui| {
                    let label = above.unwrap_or_else(|| {
                        let width = ui.available_width().min(165.);
                        ui.allocate_ui_with_layout(
                            Vec2::new(width, 30.),
                            egui::Layout::left_to_right(egui::Align::Center),
                            |ui| {
                                ui.set_min_width(width);
                                ui.label(caption)
                            },
                        )
                        .inner
                        .id
                    });
                    let f = self.folders.as_mut().unwrap();
                    let buffer = match field {
                        Field::Input => &mut f.input,
                        Field::Outbox => &mut f.outbox,
                    };
                    let browse_width = ui
                        .painter()
                        .layout_no_wrap(
                            browse.to_owned(),
                            egui::TextStyle::Button.resolve(ui.style()),
                            c.ink,
                        )
                        .rect
                        .width()
                        + ui.spacing().button_padding.x * 2.;
                    let browse_width = browse_width.max(68.);
                    let remaining =
                        ui.available_width() - browse_width - ui.spacing().item_spacing.x - 18.;
                    let edit = ui
                        .add_enabled(
                            self.pending.is_none(),
                            egui::TextEdit::singleline(buffer)
                                .id(Id::new(("folders", id)))
                                .margin(egui::Margin::symmetric(8, 5))
                                .desired_width(remaining.max(32.)),
                        )
                        .labelled_by(label);
                    if ui
                        .add_enabled(
                            self.pending.is_none(),
                            egui::Button::new(browse).min_size(Vec2::new(browse_width, 30.)),
                        )
                        .labelled_by(label)
                        .clicked()
                    {
                        self.send(Pending::Choose, Action::Choose(field));
                    }
                    edit
                })
                .inner;
            self.focus(id, &edit);
        }
        let caption = self.s("auto_load");
        ui.scope(|ui| {
            let watch = &mut self.folders.as_mut().unwrap().watch;
            if *watch {
                selected_control(ui, c, true);
            }
            ui.add_enabled(
                self.pending.is_none(),
                egui::Checkbox::new(watch, text(caption, 16., Weight::Medium, c.ink)),
            );
        });
        ui.separator();
        self.label(ui, c, "reconnect");
        self.buttons(
            ui,
            c,
            &[("cancel", false), ("save_changes", true)],
            |this, key| {
                if key == "cancel" {
                    this.back("manual");
                    return;
                }
                if this.composition_frame || this.pending.is_some() {
                    return;
                }
                let f = this.folders.as_ref().unwrap();
                let (Ok(input), Ok(outbox)) = (
                    selected(Path::new(&f.input)),
                    selected(Path::new(&f.outbox)),
                ) else {
                    this.message = Some("settings_failed");
                    return;
                };
                let initial = f.initial;
                this.editor.stage(FolderEdit {
                    input: Some(input),
                    outbox: Some(outbox),
                    watch_enabled: f.watch,
                });
                if let Some(action) = this.editor.dispatch() {
                    let edit = action.edit.clone();
                    this.send(Pending::Save(action), Action::Save { edit, initial });
                }
            },
        );
    }
    fn buttons(
        &mut self,
        ui: &mut Ui,
        c: Colors,
        actions: &[(&str, bool)],
        mut click: impl FnMut(&mut Self, &str),
    ) {
        let width = ui.available_width();
        let min = actions
            .iter()
            .map(|(key, _)| {
                ui.painter()
                    .layout_no_wrap(
                        self.s(key).into(),
                        egui::FontId::new(16., Weight::Semibold.family()),
                        c.ink,
                    )
                    .size()
                    .x
                    + 32.
            })
            .fold(0., f32::max);
        let mut draw = |ui: &mut Ui, i: usize| {
            let (key, primary) = actions[i];
            let enabled = (key == "cancel" || self.pending.is_none())
                && match key {
                    "open_exports" | "open_drafts" => {
                        self.state
                            .as_ref()
                            .is_some_and(|s| s.settings.is_some() && !s.settings_stale)
                            && !self.observation_failed
                    }
                    "save_changes" => {
                        self.state.is_some()
                            && !self.observation_failed
                            && self
                                .state
                                .as_ref()
                                .is_some_and(|s| s.settings_problem.is_none())
                    }
                    "confirm" => self.choice.is_some() && !self.observation_failed,
                    _ => true,
                };
            let r = ui
                .scope_builder(
                    egui::UiBuilder::new()
                        .id_salt(("desktop-action", key))
                        .global_scope(true),
                    |ui| {
                        ui.add_enabled_ui(enabled, |ui| button(ui, c, self.s(key), primary))
                            .inner
                    },
                )
                .inner;
            if r.clicked() {
                click(self, key)
            }
        };
        if width >= (min + 10.) * actions.len() as f32 {
            ui.columns(actions.len(), |cols| {
                for (i, col) in cols.iter_mut().enumerate() {
                    draw(col, i)
                }
            });
        } else {
            for i in 0..actions.len() {
                draw(ui, i)
            }
        }
    }
    fn overview(&mut self, ui: &mut Ui, c: Colors) {
        self.heading(ui, c, "companion");
        self.label(ui, c, "tagline");
        let state = self.state.clone().unwrap_or_default();
        let healthy = !self.observation_failed
            && !state.sessions_stale
            && state.session_problem.is_none()
            && state.settings_problem.is_none();
        let initialized = state
            .sessions
            .iter()
            .filter(|s| s.record.initialized)
            .count();
        let (groups, summary_problem) = loaded_groups(&state);
        card(ui, c, |ui| {
            let (title, help) = if self.state.is_none() && !self.observation_failed {
                ("checking", None)
            } else if !healthy {
                ("session_problem", None)
            } else if state.settings.is_none() {
                ("unconfigured", None)
            } else if state.sessions.is_empty() {
                ("ready_to_connect", Some("disconnected"))
            } else if initialized == 0 {
                ("running", None)
            } else {
                (
                    "connected",
                    if groups.is_empty() && summary_problem {
                        None
                    } else {
                        Some(if groups.is_empty() {
                            "no_loaded"
                        } else {
                            "loaded_help"
                        })
                    },
                )
            };
            ui.horizontal_top(|ui| {
                // Decorative shield; the adjacent labeled heading supplies the status semantics.
                let (r, _) = ui.allocate_exact_size(Vec2::new(23., 25.), egui::Sense::hover());
                let p = r.min;
                ui.painter().add(egui::Shape::closed_line(
                    vec![
                        p + Vec2::new(3., 3.),
                        p + Vec2::new(19., 3.),
                        p + Vec2::new(21., 7.),
                        p + Vec2::new(13., 21.),
                        p + Vec2::new(9., 21.),
                        p + Vec2::new(1., 7.),
                    ],
                    Stroke::new(2., c.accent),
                ));
                ui.vertical(|ui| {
                    self.subhead(ui, c, title);
                    if let Some(key) = help {
                        self.label(ui, c, key);
                    }
                    if healthy && state.settings.is_some() {
                        ui.label(
                            self.copy
                                .format(
                                    "session_evidence",
                                    &[
                                        ("running", &state.sessions.len().to_string()),
                                        ("initialized", &initialized.to_string()),
                                        ("verified", &state.verified_count().to_string()),
                                    ],
                                )
                                .unwrap(),
                        );
                    }
                    if summary_problem && healthy {
                        self.label(ui, c, "session_problem");
                    }
                });
            });
        });
        let trouble = state.source_problem.is_some() || state.source_stale;
        let training = |this: &Self, ui: &mut Ui| {
            for (source, sessions) in groups.values() {
                if trouble {
                    card(ui, c, |ui| {
                        this.subhead(
                            ui,
                            c,
                            if healthy {
                                "last_valid"
                            } else {
                                "last_observed"
                            },
                        );
                        ui.label(
                            date(source.exported_at_ms)
                                .unwrap_or_else(|| this.s("date_unavailable").into()),
                        );
                        if state.sessions.len() > 1 {
                            ui.label(sessions.join(" · "));
                        }
                        ui.separator();
                        ui.label(this.coverage(source));
                    });
                    continue;
                }
                this.training(
                    ui,
                    c,
                    if !healthy { "last_observed" } else { "loaded" },
                    source,
                    (state.sessions.len() > 1).then_some(sessions),
                    !state.settings_stale
                        && !this.observation_failed
                        && state.settings.as_ref().is_some_and(|s| s.watch_enabled),
                );
            }
            if let Some(available) = state.source.as_ref().and_then(|s| s.available.as_ref())
                && !groups.values().any(|(s, _)| s == available)
            {
                this.training(
                    ui,
                    c,
                    if state.source_stale || this.observation_failed {
                        "last_observed"
                    } else {
                        "available"
                    },
                    available,
                    None,
                    false,
                );
            } else if groups.is_empty() {
                card(ui, c, |ui| {
                    let (title, help) = if trouble
                        || summary_problem
                        || this.observation_failed
                        || state.settings_problem.is_some()
                    {
                        ("training_problem", "check_file")
                    } else if this.state.is_none()
                        || state.source.as_ref().is_some_and(|s| s.scanning)
                    {
                        ("checking", "transfer_help")
                    } else if state.settings.is_none() {
                        ("agent", "agent_hint")
                    } else if state.source.as_ref().is_some_and(|s| !s.pending.is_empty()) {
                        ("available", "choose_source_help")
                    } else {
                        ("no_training", "transfer_help")
                    };
                    this.subhead(ui, c, title);
                    this.label(ui, c, help);
                });
            }
        };
        if trouble {
            training(self, ui);
        } else if ui.available_width() >= 630. && (!groups.is_empty() || !state.drafts.is_empty()) {
            ui.horizontal_top(|ui| {
                let width = ui.available_width() - ui.spacing().item_spacing.x;
                let layout = egui::Layout::top_down(egui::Align::Min);
                let training =
                    ui.allocate_ui_with_layout(Vec2::new(width * 0.57, 0.), layout, |ui| {
                        training(self, ui)
                    });
                ui.allocate_ui_with_layout(Vec2::new(width * 0.43, 0.), layout, |ui| {
                    self.drafts(
                        ui,
                        c,
                        &state,
                        if groups.len() == 1 {
                            training.response.rect.height()
                        } else {
                            0.
                        },
                    )
                });
            });
        } else {
            training(self, ui);
            if !groups.is_empty() || !state.drafts.is_empty() || state.archive_problem.is_some() {
                self.drafts(ui, c, &state, 0.)
            }
        }
        if trouble {
            card(
                ui,
                Colors {
                    rule: c.caution,
                    ..c
                },
                |ui| {
                    ui.horizontal(|ui| {
                        let (rect, _) =
                            ui.allocate_exact_size(Vec2::splat(20.), egui::Sense::hover());
                        let p = rect.min;
                        let stroke = Stroke::new(1.5, c.caution);
                        ui.painter().add(egui::Shape::closed_line(
                            vec![
                                p + Vec2::new(10., 1.),
                                p + Vec2::new(19., 18.),
                                p + Vec2::new(1., 18.),
                            ],
                            stroke,
                        ));
                        ui.painter().line_segment(
                            [p + Vec2::new(10., 7.), p + Vec2::new(10., 12.)],
                            stroke,
                        );
                        ui.painter()
                            .circle_filled(p + Vec2::new(10., 15.), 1., c.caution);
                        self.subhead(
                            ui,
                            c,
                            if state.source_stale {
                                "training_problem"
                            } else {
                                "unreadable"
                            },
                        );
                    });
                    self.label(
                        ui,
                        c,
                        if !groups.is_empty() && healthy {
                            "retry_retained"
                        } else {
                            "check_file"
                        },
                    )
                },
            );
            if !state.drafts.is_empty() || state.archive_problem.is_some() {
                self.drafts(ui, c, &state, 0.);
            }
        }
        if state.settings_problem.is_some() {
            self.label(ui, c, "check_settings");
        }
        if state.reload_required() {
            self.label(ui, c, "reload");
        }
        if state.source.as_ref().is_some_and(|s| s.scanning) && !groups.is_empty() {
            self.label(ui, c, "checking");
        }
        let candidates = state.source.as_ref().is_some_and(|s| !s.pending.is_empty());
        if !candidates && self.restore_focus == Some("choice") {
            self.restore_focus = Some("manual")
        }
        if candidates {
            let r = ui.add_enabled(
                self.pending.is_none() && !state.source_stale && !self.observation_failed,
                egui::Button::new(self.s("source_heading")),
            );
            self.focus("choice", &r);
            if r.clicked() {
                self.page = Page::Choice;
                self.choice = state
                    .settings
                    .as_ref()
                    .and_then(|s| s.selected_dataset_id.clone());
                self.restore_focus = Some("candidate");
            }
        }
        let actions = if trouble {
            vec![
                ("open_drafts", false),
                ("open_exports", false),
                ("retry", true),
            ]
        } else {
            vec![
                (
                    if groups.is_empty() && state.drafts.is_empty() {
                        "open_exports"
                    } else {
                        "open_drafts"
                    },
                    false,
                ),
                ("setup_copy", true),
            ]
        };
        self.buttons(ui, c, &actions, |this, key| match key {
            "setup_copy" => this.send(
                Pending::Effect("copied", "copy_failed"),
                Action::Copy(this.s("agent_request").to_owned()),
            ),
            "retry" => this.queue.push(Work::Poll),
            _ => {
                if let Some(s) = &state.settings {
                    this.send(
                        Pending::Effect("folder_opened", "folder_failed"),
                        Action::Folder(if key == "open_exports" {
                            s.input_directory.clone()
                        } else {
                            s.outbox_directory.clone()
                        }),
                    );
                }
            }
        });
        if !trouble
            && (self.observation_failed
                || state.archive_problem.is_some()
                || state.session_problem.is_some()
                || state.settings_problem.is_some())
            && ui.button(self.s("retry")).clicked()
        {
            self.queue.push(Work::Poll);
        }
        let manual = ui
            .vertical_centered(|ui| {
                ui.add_enabled(self.pending.is_none(), egui::Link::new(self.s("manual")))
            })
            .inner;
        self.focus("manual", &manual);
        if manual.clicked() {
            self.open_manual()
        }
        ui.separator();
        ui.vertical_centered(|ui| self.label(ui, c, "transfer_footer"));
    }
    fn training(
        &self,
        ui: &mut Ui,
        c: Colors,
        title: &str,
        s: &SourceSummary,
        sessions: Option<&Vec<String>>,
        updates: bool,
    ) {
        card(ui, c, |ui| {
            self.subhead(ui, c, title);
            ui.label(date(s.exported_at_ms).unwrap_or_else(|| self.s("date_unavailable").into()));
            if let Some(ids) = sessions {
                ui.label(ids.join(" · "));
            }
            ui.separator();
            let metrics = [
                (s.workouts_included, "completed_workouts"),
                (s.sets_included, "logged_sets"),
                (s.saved_workouts_included, "saved_workouts"),
            ];
            ui.columns(3, |cols| {
                for (col, (n, key)) in cols.iter_mut().zip(metrics) {
                    col.with_layout(egui::Layout::top_down(egui::Align::Min), |col| {
                        col.label(text(n.to_string(), 23., Weight::Bold, c.ink));
                        self.label(col, c, key);
                    });
                }
            });
            if updates {
                ui.separator();
                self.label(ui, c, "updated");
            }
        });
    }
    fn coverage(&self, s: &SourceSummary) -> String {
        self.copy
            .format(
                "coverage",
                &[
                    ("workouts", &s.workouts_included.to_string()),
                    ("sets", &s.sets_included.to_string()),
                    ("saved", &s.saved_workouts_included.to_string()),
                ],
            )
            .unwrap()
    }
    fn drafts(&mut self, ui: &mut Ui, c: Colors, s: &DesktopState, min_height: f32) {
        card(ui, c, |ui| {
            ui.set_min_height((min_height - 30.).max(0.));
            self.subhead(ui, c, "drafts");
            if s.archive_problem.is_some() || s.drafts_stale || self.observation_failed {
                self.label(ui, c, "drafts_problem")
            } else if s.drafts.is_empty() {
                self.label(ui, c, "no_drafts")
            }
            for row in &s.drafts {
                ui.push_id(&row.draft_id, |ui| {
                    let r = ui
                        .add_enabled_ui(
                            self.pending.is_none() && !s.drafts_stale && !self.observation_failed,
                            |ui| draft_button(ui, c, &row.display_name, self.s("ready_review")),
                        )
                        .inner;
                    if r.clicked() {
                        self.send(
                            Pending::Effect("reveal_requested", "folder_failed"),
                            Action::Reveal(Box::new(row.clone())),
                        );
                    }
                });
            }
        });
    }
    fn source_choice(&mut self, ui: &mut Ui, c: Colors) {
        self.heading(ui, c, "source_heading");
        self.label(ui, c, "archive_found");
        let state = self.state.clone().unwrap_or_default();
        let selected = state
            .settings
            .as_ref()
            .and_then(|s| s.selected_dataset_id.as_deref());
        let candidates = state
            .source
            .as_ref()
            .map(|s| s.pending.as_slice())
            .unwrap_or(&[]);
        if let Some(id) = selected {
            let source = self.cached.clone().or_else(|| {
                loaded_groups(&state)
                    .0
                    .into_values()
                    .map(|(s, _)| s)
                    .find(|s| s.dataset_id == id)
            });
            self.radio(ui, c, "keep", id, source.as_ref());
        }
        for source in candidates {
            if Some(source.dataset_id.as_str()) != selected {
                self.radio(ui, c, "other_archive", &source.dataset_id, Some(source));
            }
        }
        self.label(ui, c, "existing_drafts");
        self.buttons(
            ui,
            c,
            &[("cancel", false), ("confirm", true)],
            |this, key| {
                if key == "cancel" {
                    this.back("choice")
                } else if let Some(id) = this.choice.clone() {
                    this.send(Pending::Select, Action::Select(id))
                }
            },
        );
    }
    fn radio(
        &mut self,
        ui: &mut Ui,
        c: Colors,
        key: &str,
        id: &str,
        source: Option<&SourceSummary>,
    ) {
        let selected = self.choice.as_deref() == Some(id);
        card(
            ui,
            Colors {
                rule: if selected { c.accent } else { c.rule },
                ..c
            },
            |ui| {
                if selected {
                    selected_control(ui, c, false);
                } else if self.pending.is_none() {
                    let widgets = &mut ui.visuals_mut().widgets;
                    for state in [
                        &mut widgets.inactive,
                        &mut widgets.hovered,
                        &mut widgets.active,
                    ] {
                        state.bg_fill = c.card;
                        state.bg_stroke = Stroke::new(2., c.secondary);
                    }
                    widgets.active.bg_stroke = Stroke::new(3., c.ink);
                }
                let r = ui.add_enabled(
                    self.pending.is_none(),
                    egui::RadioButton::new(
                        selected,
                        text(self.s(key), 18., Weight::Semibold, c.ink),
                    ),
                );
                self.focus("candidate", &r);
                if r.clicked() {
                    self.choice = Some(id.to_owned())
                }
                if let Some(source) = source {
                    ui.label(
                        date(source.exported_at_ms)
                            .unwrap_or_else(|| self.s("date_unavailable").into()),
                    );
                    ui.label(self.coverage(source));
                } else {
                    ui.label(display_literal(id));
                }
            },
        );
    }
}

fn date(ms: u64) -> Option<String> {
    chrono::DateTime::from_timestamp_millis(ms.try_into().ok()?).map(|d| {
        d.with_timezone(&chrono::Local)
            .format("%Y-%m-%d %H:%M")
            .to_string()
    })
}
type SourceGroups = BTreeMap<(String, String, String), (SourceSummary, Vec<String>)>;
fn loaded_groups(s: &DesktopState) -> (SourceGroups, bool) {
    let mut groups = BTreeMap::new();
    let mut summary_problem = false;
    for row in &s.sessions {
        if let Some(loaded) = &row.record.loaded_source {
            let Ok(src) = loaded.summary() else {
                summary_problem = true;
                continue;
            };
            groups
                .entry((
                    src.dataset_id.clone(),
                    src.snapshot_id.clone(),
                    src.sha256.clone(),
                ))
                .or_insert_with(|| (src, Vec::new()))
                .1
                .push(display_literal(&row.id));
        }
    }
    (groups, summary_problem)
}
