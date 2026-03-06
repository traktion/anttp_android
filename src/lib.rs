use anttp::config::anttp_config::AntTpConfig;
use jni::objects::{JClass, JString};
use jni::JNIEnv;
use log::{error, info, warn, LevelFilter};
use android_logger::{Config, FilterBuilder};
use once_cell::sync::Lazy;
use std::sync::Mutex;
use tokio::runtime::Runtime;
use tokio::sync::oneshot;
use tokio::time::{timeout, Duration};
use clap::Parser;

static RUNTIME_CONTROL: Lazy<Mutex<Option<oneshot::Sender<()>>>> = Lazy::new(|| Mutex::new(None));

#[unsafe(no_mangle)]
pub extern "system" fn Java_uk_co_antnode_anttp_Native_start(
    mut env: JNIEnv,
    _class: JClass,
    data_dir: JString,
) {
    android_logger::init_once(
        Config::default()
            .with_max_level(LevelFilter::Trace)
            .with_tag("AntTP")
            .with_filter(
                FilterBuilder::new()
                    .parse("info,anttp=info,ant_api=warn,ant_client=warn,autonomi::networking=error,ant_bootstrap=error,chunk_streamer=info")
                    .build(),
            ),
    );

    let data_dir: String = match env.get_string(&data_dir) {
        Ok(s) => s.into(),
        Err(e) => {
            error!("AntTP: Failed to get data_dir from JNI: {:?}", e);
            return;
        }
    };

    if let Err(e) = std::env::set_current_dir(&data_dir) {
        error!("AntTP: Failed to set current directory to {}: {:?}", data_dir, e);
    } else {
        info!("AntTP: Set current directory to {}", data_dir);
    }

    let mut control = RUNTIME_CONTROL.lock().unwrap();
    if control.is_some() {
        warn!("AntTP: Engine already running");
        return;
    }

    let (tx, _rx) = oneshot::channel();
    *control = Some(tx);

    std::thread::spawn(move || {
        let rt = match Runtime::new() {
            Ok(rt) => rt,
            Err(e) => {
                error!("AntTP: Failed to create Tokio runtime: {:?}", e);
                return;
            }
        };

        rt.block_on(async move {
            info!("AntTP: Engine starting on background thread...");

            let app_config = AntTpConfig::try_parse_from(&["anttp", "--grpc-disabled"]).unwrap();
            if let Err(e) = anttp::run_server(app_config).await {
                error!("AntTP: Server error: {:?}", e);
            }
            
            info!("AntTP: Engine shutting down...");
        });
    });
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_uk_co_antnode_anttp_Native_stop(
    _env: JNIEnv,
    _class: JClass,
) {
    let mut control = RUNTIME_CONTROL.lock().unwrap();
    if let Some(_tx) = control.take() {
        std::thread::spawn(move || {
            let rt = match Runtime::new() {
                Ok(rt) => rt,
                Err(e) => {
                    error!("AntTP: Failed to create Tokio runtime for stop: {:?}", e);
                    return;
                }
            };

            rt.block_on(async move {
                info!("AntTP: Shutdown requested...");
                match timeout(Duration::from_secs(5), anttp::stop_server()).await {
                    Ok(Ok(_)) => info!("AntTP: Shutdown successful"),
                    Ok(Err(e)) => error!("AntTP: Shutdown error: {}", e),
                    Err(_) => warn!("AntTP: Shutdown timed out, server might still be stopping"),
                }
            });
        });
        info!("AntTP: Shutdown signal initiated");
    } else {
        warn!("AntTP: Engine not running");
    }
}
