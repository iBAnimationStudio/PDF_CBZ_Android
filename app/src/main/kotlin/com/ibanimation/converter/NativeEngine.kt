package com.ibanimation.converter

object NativeEngine {
    init {
        System.loadLibrary("cbz_engine")
    }

    external fun packToCbz(sourceDir: String, outputCbz: String): Boolean
    external fun extractCbz(inputCbz: String, outputDir: String): Boolean
    external fun imagesToPdf(imgDir: String, outputPdf: String): Boolean
    
    var onProgressUpdate: ((String, Float) -> Unit)? = null

    @JvmStatic
    fun updateProgress(message: String, progress: Float) {
        onProgressUpdate?.invoke(message, progress)
    }

}
