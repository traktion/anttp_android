use anttp::AppConfig;
use jni::objects::JClass;
use jni::JNIEnv;
use once_cell::sync::Lazy;
use std::sync::Mutex;
use tokio::runtime::Runtime;
use tokio::sync::oneshot;

static RUNTIME_CONTROL: Lazy<Mutex<Option<oneshot::Sender<()>>>> = Lazy::new(|| Mutex::new(None));

#[unsafe(no_mangle)]
pub extern "system" fn Java_uk_co_antnode_anttp_Native_start(
    _env: JNIEnv,
    _class: JClass,
) {
    let mut control = RUNTIME_CONTROL.lock().unwrap();
    if control.is_some() {
        eprintln!("AntTP: Engine already running");
        return;
    }

    let (tx, rx) = oneshot::channel();
    *control = Some(tx);

    std::thread::spawn(move || {
        let rt = match Runtime::new() {
            Ok(rt) => rt,
            Err(e) => {
                eprintln!("AntTP: Failed to create Tokio runtime: {:?}", e);
                return;
            }
        };

        rt.block_on(async move {
            println!("AntTP: Engine starting on background thread...");
            
            let app_config = AppConfig::default();
            if let Err(e) = anttp::run_server(app_config).await {
                eprintln!("AntTP: Server error: {:?}", e);
            }
            
            println!("AntTP: Engine shutting down...");
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
        anttp::stop_server();
        println!("AntTP: Shutdown signal sent");
    } else {
        eprintln!("AntTP: Engine not running");
    }
}

#[cfg(test)]
mod tests {
    #[test]
    fn it_works() {
        assert_eq!(2 + 2, 4);
    }
}
