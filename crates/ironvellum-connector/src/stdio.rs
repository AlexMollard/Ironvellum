use std::{
    fs::File,
    future::Future,
    io,
    panic::{AssertUnwindSafe, catch_unwind, resume_unwind},
    sync::atomic::{AtomicBool, Ordering},
};

static CLAIMED: AtomicBool = AtomicBool::new(false);

/// Enter stdio serving mode once in a dedicated headless process, before other
/// threads, buffered stdout writes, or runtime initialization. The callback owns
/// stdin, a private wire writer, and stderr. Await service shutdown (including
/// response flush) before returning. Return directly to process exit afterwards.
/// Callback panics propagate after the runtime starts background shutdown.
///
/// Standard output is redirected for the rest of the process, including exit
/// hooks. A failed claim is terminal too. This API is unsuitable for embedding
/// in a desktop process or dispatching subsequent management commands.
pub fn run_protocol_stdio<F, Fut, T>(serve: F) -> io::Result<T>
where
    F: FnOnce(tokio::io::Stdin, tokio::fs::File, tokio::io::Stderr) -> Fut,
    Fut: Future<Output = T>,
{
    if CLAIMED.swap(true, Ordering::AcqRel) {
        return Err(io::Error::other("protocol stdio already claimed"));
    }
    let wire = claim_stdout()?;
    let runtime = tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()?;
    let session = async {
        serve(
            tokio::io::stdin(),
            tokio::fs::File::from_std(wire),
            tokio::io::stderr(),
        )
        .await
    };
    // Only intercept unwinding to release the runtime. Neither the future nor
    // any of its state is reused after a panic.
    let result = catch_unwind(AssertUnwindSafe(|| runtime.block_on(session)));
    // Tokio cannot cancel an in-flight blocking stdin read, even after a panic.
    runtime.shutdown_background();
    Ok(result.unwrap_or_else(|payload| resume_unwind(payload)))
}

#[cfg(unix)]
fn claim_stdout() -> io::Result<File> {
    use std::os::fd::AsFd;
    let wire = io::stdout().as_fd().try_clone_to_owned()?;
    // SAFETY: Only the dedicated process entry point changes these standard
    // descriptors, before any threads are started. The clone is independent.
    if unsafe { libc::dup2(libc::STDERR_FILENO, libc::STDOUT_FILENO) } < 0 {
        return Err(io::Error::last_os_error());
    }
    Ok(wire.into())
}

#[cfg(windows)]
fn claim_stdout() -> io::Result<File> {
    use std::os::windows::io::{AsHandle, AsRawHandle};
    use windows_sys::Win32::System::Console::{STD_OUTPUT_HANDLE, SetStdHandle};
    let wire = io::stdout().as_handle().try_clone_to_owned()?;
    // Redirect the CRT as well as the process handle table used by Rust.
    // SAFETY: The caller owns process startup. The CRT owns descriptors 1 and
    // 2. The OS table borrows stderr, which remains live throughout serving.
    if unsafe { libc::dup2(2, 1) } < 0 {
        return Err(io::Error::other("stdio redirection failed"));
    }
    if unsafe { SetStdHandle(STD_OUTPUT_HANDLE, io::stderr().as_raw_handle()) } == 0 {
        return Err(io::Error::last_os_error());
    }
    Ok(wire.into())
}
