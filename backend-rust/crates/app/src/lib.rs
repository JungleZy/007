#![recursion_limit = "512"]

mod domain;
mod platform;
mod router;
mod runtime;
mod shared;
mod ws;

pub async fn run() {
    runtime::init_tracing();
    if let Err(error) = runtime::run().await {
        tracing::error!(error = %error, "rust backend stopped with an error");
        std::process::exit(1);
    }
}
