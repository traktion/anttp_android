package uk.co.antnode.anttp

object Native {
    init {
        System.loadLibrary("anttp_android")
    }

    external fun start()
    external fun stop()
}
