# PdfCbzConverter 🚀

A high-performance, hybrid Android application built to bulk-convert comic book and document archives between **PDF** and **CBZ** formats at native engine speeds.

Powered by a dual-engine architecture: a low-level **Rust native core** handles lighting-fast archive compression/decompression, while a modern **Jetpack Compose Material 3** UI handles the processing pipeline entirely on-device.

---

## Features ✨

* **Bi-Directional Bulk Conversion:** Seamlessly batch-process multiple files from `PDF ➔ CBZ` and `CBZ ➔ PDF` in a single click.
* **Rust Native Core:** Bypasses sluggish high-level runtime overhead by using a compiled `.so` C-bridge engine (`cbz_engine`) for file streaming.
* **Scoped Storage Bypass:** Built-in Material 3 custom directory browser that navigates direct Linux disk paths, completely avoiding Android's restrictive system Scoped Storage blocks.
* **Granular Pre-Processing Scan:** Instantly analyzes directories before running, prompting a dynamic sheet showing recognizable files, individual item selection, and a "Select All" toggle.
* **Verbose Progress Tracking:** Real-time progress bar ($0\%$ to $100\%$) alongside ultra-granular thread updates detailing exactly which page or image is being compiled at that millisecond.
* **Modern Material 3 UI:** Clean, responsive user experience utilizing state-driven Jetpack Compose components.

---

## Architecture 🛠️


```
Jetpack Compose UI (Kotlin)
(JNI Bridge)
         ▼
NativeEngine Object / C++ JNI
(Low-Level Stream)
         ▼
Rust Compiled Core Engine
```

* **Frontend:** Jetpack Compose, Kotlin, Coroutines for asynchronous background threads.
* **Backend:** Low-level native C++ bindings interfacing with a compiled Rust utility for zip compression and page extraction.

---

## How it Works under the Hood 🧠

### PDF to CBZ
1. The app utilizes Android's native low-level `PdfRenderer` graphics engine to slice PDF vectors into high-resolution, hardware-accelerated JPEG matrices.
2. The extracted page array is instantly piped into the compiled **Rust native engine** (`packToCbz`), compressing the image payload into a packed CBZ container at raw hardware speeds.

### CBZ to PDF
1. The compressed CBZ structure is unzipped via Rust (`extractCbz`) into an volatile cached stream.
2. Native `PdfDocument` graphics canvas wrappers bind the sequential bitmaps back into full vector-compliant PDF structures.

---

built entirely on-device via Android Code Studio and Termux.

---

### Note:
The app code was written by AI. And may contain bugs.
For now i have not found any bugs at my end. if you find any plese male a issue on this repo. I will try my best to fix it.

The rust core is currently compiled for only arm64 CPUs.
my ide dosenot support rust compilation so i had to compile it externally. i will provide the cource code soon.

The apk from release may not install.
I'm still figuring out how to setup the GitHub action...
So i will preffer to compile the app directly from the cource code. i will fix it soon.

Contribution are always welcome... 
