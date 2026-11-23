use ironvellum_local::LocalError;
use std::path::{Path, PathBuf};

pub trait Effects {
    fn choose(&mut self) -> Result<Option<PathBuf>, LocalError>;
    fn copy(&mut self, text: &str) -> Result<(), LocalError>;
    fn open_folder(&mut self, path: &Path) -> Result<(), LocalError>;
    fn reveal(&mut self, path: &Path) -> Result<(), LocalError>;
}

#[derive(Default)]
pub struct NativeEffects {
    clipboard: Option<arboard::Clipboard>,
}
impl Effects for NativeEffects {
    fn choose(&mut self) -> Result<Option<PathBuf>, LocalError> {
        Ok(rfd::FileDialog::new().pick_folder())
    }
    fn copy(&mut self, text: &str) -> Result<(), LocalError> {
        if self.clipboard.is_none() {
            self.clipboard =
                Some(arboard::Clipboard::new().map_err(|_| LocalError::StorageUnavailable)?);
        }
        self.clipboard
            .as_mut()
            .expect("initialized")
            .set_text(text)
            .map_err(|_| LocalError::StorageUnavailable)
    }
    fn open_folder(&mut self, path: &Path) -> Result<(), LocalError> {
        show(path, false)
    }
    fn reveal(&mut self, path: &Path) -> Result<(), LocalError> {
        show(path, true)
    }
}

#[cfg(target_os = "linux")]
fn show(path: &Path, select: bool) -> Result<(), LocalError> {
    let uri = url::Url::from_file_path(path).map_err(|_| LocalError::InvalidPath)?;
    let method = if select {
        "org.freedesktop.FileManager1.ShowItems"
    } else {
        "org.freedesktop.FileManager1.ShowFolders"
    };
    // dbus-send uses comma to delimit array entries. Encode literal commas in this one URI.
    let arg = format!("array:string:{}", uri.as_str().replace(',', "%2C"));
    let output = std::process::Command::new("dbus-send")
        .args([
            "--session",
            "--type=method_call",
            "--print-reply",
            "--reply-timeout=5000",
            "--dest=org.freedesktop.FileManager1",
            "/org/freedesktop/FileManager1",
            method,
            &arg,
            "string:",
        ])
        .output()
        .map_err(|_| LocalError::StorageUnavailable)?;
    output
        .status
        .success()
        .then_some(())
        .ok_or(LocalError::StorageUnavailable)
}
#[cfg(target_os = "macos")]
fn show(path: &Path, select: bool) -> Result<(), LocalError> {
    let mut command = std::process::Command::new("/usr/bin/open");
    if select {
        command.arg("-R");
    }
    command
        .arg(path)
        .output()
        .ok()
        .filter(|r| r.status.success())
        .map(|_| ())
        .ok_or(LocalError::StorageUnavailable)
}
#[cfg(windows)]
fn show(path: &Path, select: bool) -> Result<(), LocalError> {
    use std::{
        os::windows::ffi::OsStrExt,
        ptr::{null, null_mut},
    };
    use windows_sys::Win32::{
        System::Com::{COINIT_APARTMENTTHREADED, CoInitializeEx, CoUninitialize},
        UI::Shell::{ILCreateFromPathW, ILFree, SHOpenFolderAndSelectItems, ShellExecuteW},
    };
    let literal: Vec<u16> = path.as_os_str().encode_wide().chain(Some(0)).collect();
    // All arguments remain valid until the synchronous shell call returns. The
    // item ID list is owned by this call and is released even on shell refusal.
    unsafe {
        let com = CoInitializeEx(null(), COINIT_APARTMENTTHREADED as u32);
        if com < 0 {
            return Err(LocalError::StorageUnavailable);
        }
        let success = if select {
            let item = ILCreateFromPathW(literal.as_ptr());
            if item.is_null() {
                false
            } else {
                let result = SHOpenFolderAndSelectItems(item, 0, null(), 0);
                ILFree(item);
                result >= 0
            }
        } else {
            ShellExecuteW(
                null_mut(),
                windows_sys::w!("open"),
                literal.as_ptr(),
                null(),
                null(),
                1,
            ) as isize
                > 32
        };
        CoUninitialize();
        success.then_some(()).ok_or(LocalError::StorageUnavailable)
    }
}
