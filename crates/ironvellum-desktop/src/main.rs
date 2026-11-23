use ironvellum_desktop::{
    copy::Catalog,
    effects::NativeEffects,
    options::{self, Options, Parsed},
    typography,
    window::DesktopWindow,
    worker::{Event, Work, Worker},
};
use ironvellum_local::{LocalError, Selected};
use std::{
    path::PathBuf,
    process::ExitCode,
    time::{Duration, Instant},
};

struct App {
    view: DesktopWindow,
    worker: Option<Worker>,
    settings: Selected,
    connector: PathBuf,
    poll_due: Instant,
    poll_outstanding: bool,
    in_flight: Option<u64>,
}
impl App {
    fn new(cc: &eframe::CreationContext<'_>, options: Options, connector: PathBuf) -> Self {
        cc.egui_ctx
            .set_fonts(typography::definitions(options.language));
        cc.egui_ctx.set_theme(options.appearance);
        Self {
            view: DesktopWindow::new(options.language, options.wording),
            worker: None,
            settings: options.settings,
            connector,
            poll_due: Instant::now(),
            poll_outstanding: false,
            in_flight: None,
        }
    }
    fn failed(&mut self) {
        if let Some(id) = self.in_flight.take() {
            self.view
                .accept(Event::Finished(id, Err(LocalError::StorageUnavailable)));
        }
        self.view
            .accept(Event::Observed(Err(LocalError::StorageUnavailable)));
        self.worker = None;
        self.poll_outstanding = false;
        self.poll_due = Instant::now() + Duration::from_secs(1);
    }
    fn dispatch(&mut self, job: Work) {
        match job {
            Work::Act(id, _) => self.in_flight = Some(id),
            Work::Poll => {
                if self.poll_outstanding {
                    return;
                }
                self.poll_outstanding = true
            }
        };
        if self
            .worker
            .as_ref()
            .is_none_or(|w| w.requests.send(job).is_err())
        {
            self.failed()
        }
    }
}
impl eframe::App for App {
    fn update(&mut self, ctx: &egui::Context, _frame: &mut eframe::Frame) {
        while let Some(worker) = &self.worker {
            match worker.events.try_recv() {
                Ok(e) => {
                    match &e {
                        Event::Observed(_) => self.poll_outstanding = false,
                        Event::Finished(id, _) if self.in_flight == Some(*id) => {
                            self.in_flight = None
                        }
                        _ => {}
                    }
                    self.view.accept(e);
                }
                Err(std::sync::mpsc::TryRecvError::Empty) => break,
                Err(std::sync::mpsc::TryRecvError::Disconnected) => {
                    self.failed();
                    break;
                }
            }
        }
        if Instant::now() >= self.poll_due {
            self.poll_due = Instant::now() + Duration::from_secs(1);
            if self.worker.is_none() {
                self.worker = Worker::start(
                    self.settings.clone(),
                    self.connector.clone(),
                    NativeEffects::default,
                    ctx.clone(),
                )
                .ok();
            }
            self.dispatch(Work::Poll);
        }
        for job in self.view.render(ctx) {
            self.dispatch(job)
        }
        ctx.request_repaint_after(Duration::from_secs(1));
    }
}
fn run() -> Result<(), String> {
    let options = match options::parse(std::env::args_os().skip(1))? {
        Parsed::Help => {
            println!("{}", options::HELP);
            return Ok(());
        }
        Parsed::Run(options) => options,
    };
    let unavailable = Catalog::new(options.language, options.wording)
        .text("gui_unavailable")
        .unwrap();
    #[cfg(target_os = "linux")]
    if ["DISPLAY", "WAYLAND_DISPLAY", "WAYLAND_SOCKET"]
        .iter()
        .all(|key| std::env::var_os(key).is_none_or(|v| v.is_empty()))
    {
        return Err(unavailable.into());
    }
    let connector = std::env::current_exe()
        .map_err(|_| unavailable)?
        .with_file_name(format!(
            "ironvellum-connector{}",
            std::env::consts::EXE_SUFFIX
        ));
    let native = eframe::NativeOptions {
        renderer: eframe::Renderer::Wgpu,
        viewport: egui::ViewportBuilder::default()
            .with_title("IRONVELLUM")
            .with_inner_size([780., 680.])
            .with_min_inner_size([265., 180.]),
        ..Default::default()
    };
    eframe::run_native(
        "Ironvellum",
        native,
        Box::new(move |cc| Ok(Box::new(App::new(cc, options, connector)))),
    )
    .map_err(|_| unavailable.into())
}
fn main() -> ExitCode {
    match run() {
        Ok(()) => ExitCode::SUCCESS,
        Err(e) => {
            eprintln!("{e}");
            ExitCode::from(2)
        }
    }
}
