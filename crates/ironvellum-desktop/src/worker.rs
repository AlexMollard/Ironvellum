use crate::effects::Effects;
use ironvellum_local::{
    LocalError, Selected,
    desktop_model::{
        DesktopObserver, DesktopState, FolderEdit, ReadyDraft, resolve_folder,
        resolve_observed_draft,
    },
    selected,
    setup::{Client, SetupOptions, configure_initial},
};
use std::{
    panic::{AssertUnwindSafe, catch_unwind},
    path::PathBuf,
    sync::mpsc::{self, Receiver, Sender},
    time::{Instant, SystemTime, UNIX_EPOCH},
};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Field {
    Input,
    Outbox,
}
pub enum Action {
    Save { edit: FolderEdit, initial: bool },
    Select(String),
    Choose(Field),
    Copy(String),
    Folder(Selected),
    Reveal(Box<ReadyDraft>),
}
pub enum Work {
    Poll,
    Act(u64, Action),
}
pub enum Outcome {
    Done,
    Chosen(Field, Option<PathBuf>),
}
pub enum Event {
    Observed(Result<std::sync::Arc<DesktopState>, LocalError>),
    Finished(u64, Result<Outcome, LocalError>),
}
pub struct Worker {
    pub requests: Sender<Work>,
    pub events: Receiver<Event>,
}

impl Worker {
    pub fn start<E: Effects + 'static>(
        settings: Selected,
        connector: PathBuf,
        effects: impl FnOnce() -> E + Send + 'static,
        ctx: egui::Context,
    ) -> std::io::Result<Self> {
        let (requests, rx) = mpsc::channel();
        let (tx, events) = mpsc::channel();
        std::thread::Builder::new()
            .name("ironvellum-desktop-worker".into())
            .spawn(move || {
                let mut observer = DesktopObserver::new(settings.clone());
                let mut effects = effects();
                for work in rx {
                    if let Work::Act(id, action) = work {
                        let result = protected(|| {
                            perform(&settings, &connector, &mut observer, &mut effects, action)
                        });
                        if tx.send(Event::Finished(id, result)).is_err() {
                            break;
                        }
                        ctx.request_repaint();
                    }
                    let result = protected(|| {
                        let ms = SystemTime::now()
                            .duration_since(UNIX_EPOCH)
                            .map_err(|_| LocalError::Clock)?
                            .as_millis();
                        Ok(observer.read(
                            Instant::now(),
                            ms.try_into().map_err(|_| LocalError::Clock)?,
                        ))
                    });
                    if tx
                        .send(Event::Observed(result.map(std::sync::Arc::new)))
                        .is_err()
                    {
                        break;
                    }
                    ctx.request_repaint();
                }
            })?;
        Ok(Self { requests, events })
    }
}

fn protected<T>(job: impl FnOnce() -> Result<T, LocalError>) -> Result<T, LocalError> {
    catch_unwind(AssertUnwindSafe(job)).unwrap_or(Err(LocalError::StorageUnavailable))
}
fn perform(
    settings: &Selected,
    connector: &std::path::Path,
    observer: &mut DesktopObserver,
    effects: &mut impl Effects,
    action: Action,
) -> Result<Outcome, LocalError> {
    match action {
        Action::Save {
            edit,
            initial: true,
        } => {
            if !connector.is_file() {
                return Err(LocalError::NotFound);
            }
            let mut filename = settings
                .as_path()
                .file_name()
                .ok_or(LocalError::InvalidPath)?
                .to_owned();
            filename.push(".state");
            configure_initial(SetupOptions {
                settings: settings.clone(),
                input: edit.input.ok_or(LocalError::MissingFolders)?,
                outbox: edit.outbox.ok_or(LocalError::MissingFolders)?,
                state: selected(&settings.as_path().with_file_name(filename))?,
                dataset: None,
                watch: Some(edit.watch_enabled),
                client: Client::ConfigOnly,
                executable: selected(connector)?,
            })?;
        }
        Action::Save {
            edit,
            initial: false,
        } => observer.save_existing(&edit)?,
        Action::Select(dataset) => observer.select(&dataset)?,
        Action::Choose(field) => return Ok(Outcome::Chosen(field, effects.choose()?)),
        Action::Copy(text) => {
            let locations = serde_json::json!({
                "connectorExecutable":connector.to_str().ok_or(LocalError::InvalidPath)?,
                "settingsFile":settings.as_path().to_str().ok_or(LocalError::InvalidPath)?,
            });
            effects.copy(&format!("{text}\n\n{locations}"))?;
        }
        Action::Folder(folder) => effects.open_folder(&resolve_folder(&folder)?)?,
        Action::Reveal(draft) => effects.reveal(&resolve_observed_draft(&draft)?)?,
    }
    Ok(Outcome::Done)
}
