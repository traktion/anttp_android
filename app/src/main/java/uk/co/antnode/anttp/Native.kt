package uk.co.antnode.anttp

object Native {
    init {
        System.loadLibrary("anttp_android")
    }

    external fun start(dataDir: String)
    external fun stop()
}
