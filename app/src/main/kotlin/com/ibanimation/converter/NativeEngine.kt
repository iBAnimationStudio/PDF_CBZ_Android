package com.ibanimation.converter

object NativeEngine {
    init {
        System.loadLibrary("cbz_engine")
    }

    external fun packToCbz(sourceDir: String, outputCbz: String): Boolean
    external fun extractCbz(inputCbz: String, outputDir: String): Boolean
}
