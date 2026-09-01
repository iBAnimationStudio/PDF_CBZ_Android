use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::jboolean;
use std::fs::{File, create_dir_all};
use std::io::{Read, Write, BufWriter};
use std::path::Path;
use walkdir::WalkDir;
use zip::write::FileOptions;
use zip::{ZipArchive, ZipWriter};

// ⚡ Added PdfDocumentReference here!
use printpdf::{PdfDocument, PdfDocumentReference, Mm, Image, ImageTransform};

// ==========================================================================
// 1. EXTRACT CBZ ENGINE (Unzip utility)
// ==========================================================================
#[no_mangle]
pub extern "system" fn Java_com_ibanimation_converter_NativeEngine_extractCbz(
    mut env: JNIEnv,
    _class: JClass,
    zip_path: JString,
    extract_path: JString,
) -> jboolean {
    let zip_path: String = match env.get_string(&zip_path) {
        Ok(s) => s.into(),
        Err(_) => return false as jboolean,
    };
    let extract_path: String = match env.get_string(&extract_path) {
        Ok(s) => s.into(),
        Err(_) => return false as jboolean,
    };
    let file = match File::open(&zip_path) {
        Ok(f) => f,
        Err(_) => return false as jboolean,
    };
    let mut archive = match ZipArchive::new(file) {
        Ok(a) => a,
        Err(_) => return false as jboolean,
    };

    for i in 0..archive.len() {
        let mut file = match archive.by_index(i) {
            Ok(f) => f,
            Err(_) => return false as jboolean,
        };
        let outpath = match file.enclosed_name() {
            Some(path) => Path::new(&extract_path).join(path),
            None => continue,
        };

        if (*file.name()).ends_with('/') {
            create_dir_all(&outpath).unwrap();
        } else {
            if let Some(p) = outpath.parent() {
                if !p.exists() {
                    create_dir_all(p).unwrap();
                }
            }
            let mut outfile = match File::create(&outpath) {
                Ok(f) => f,
                Err(_) => return false as jboolean,
            };
            std::io::copy(&mut file, &mut outfile).unwrap();
        }
    }
    true as jboolean
}

// ==========================================================================
// 2. PACK TO CBZ ENGINE (Zip compilation utility)
// ==========================================================================
#[no_mangle]
pub extern "system" fn Java_com_ibanimation_converter_NativeEngine_packToCbz(
    mut env: JNIEnv,
    _class: JClass,
    src_dir: JString,
    out_cbz: JString,
) -> jboolean {
    let src_dir: String = match env.get_string(&src_dir) {
        Ok(s) => s.into(),
        Err(_) => return false as jboolean,
    };
    let out_cbz: String = match env.get_string(&out_cbz) {
        Ok(s) => s.into(),
        Err(_) => return false as jboolean,
    };
    let file = match File::create(&out_cbz) {
        Ok(f) => f,
        Err(_) => return false as jboolean,
    };

    let mut zip = ZipWriter::new(file);
    let options = FileOptions::default()
        .compression_method(zip::CompressionMethod::Deflated)
        .unix_permissions(0o755);

    let src_path = Path::new(&src_dir);
    let walk = WalkDir::new(src_path);

    for entry in walk.into_iter().filter_map(|e| e.ok()) {
        let path = entry.path();
        let name = path.strip_prefix(src_path).unwrap().to_str().unwrap();

        if path.is_file() {
            if zip.start_file(name, options).is_err() { return false as jboolean; }
            let mut f = match File::open(path) {
                Ok(file) => file,
                Err(_) => return false as jboolean,
            };
            let mut buffer = Vec::new();
            if f.read_to_end(&mut buffer).is_err() { return false as jboolean; }
            if zip.write_all(&buffer).is_err() { return false as jboolean; }
        } else if !name.is_empty() {
            if zip.add_directory(name, options).is_err() { return false as jboolean; }
        }
    }
    zip.finish().is_ok() as jboolean
}

// ==========================================================================
// 3. ZERO-OVERHEAD IMAGES TO PDF ENGINE (The GC Killer)
// ==========================================================================
#[no_mangle]
pub extern "system" fn Java_com_ibanimation_converter_NativeEngine_imagesToPdf(
    mut env: JNIEnv,
    _class: JClass,
    img_dir: JString,
    output_pdf: JString,
) -> jboolean {
    use ::image::GenericImageView;

    let dir_str: String = match env.get_string(&img_dir) {
        Ok(s) => s.into(),
        Err(_) => return false as jboolean,
    };
    let out_str: String = match env.get_string(&output_pdf) {
        Ok(s) => s.into(),
        Err(_) => return false as jboolean,
    };

    let mut entries: Vec<_> = match std::fs::read_dir(&dir_str) {
        Ok(paths) => paths.filter_map(|p| p.ok()).collect(),
        Err(_) => return false as jboolean,
    };
    
    entries.sort_by_key(|dir| dir.path());
    let total_pages = entries.len();

    // ⚡ Changed type to Option<PdfDocumentReference>!
    let mut doc_opt: Option<PdfDocumentReference> = None;

    for (img_index, entry) in entries.iter().enumerate() {
        let path = entry.path();
        if let Some(ext) = path.extension().and_then(|e| e.to_str()) {
            if ["jpg", "jpeg", "png", "webp"].contains(&ext.to_lowercase().as_str()) {
                
                let msg = format!("Binding Page {}/{}", img_index + 1, total_pages);
                let progress = (img_index as f32) / (total_pages as f32);
                if let Ok(jmsg) = env.new_string(&msg) {
                    if let Ok(cls) = env.find_class("com/ibanimation/converter/NativeEngine") {
                        let _ = env.call_static_method(
                            &cls,
                            "updateProgress",
                            "(Ljava/lang/String;F)V",
                            &[(&jmsg).into(), progress.into()],
                        );
                    }
                }

                if let Ok(img) = ::image::open(&path) {
                    let (width, height) = img.dimensions();
                    
                    // 1 inch = 25.4 mm. Calculate page dimensions at exactly 300 DPI!
                    let w_mm = Mm((width as f64 / 300.0) * 25.4);
                    let h_mm = Mm((height as f64 / 300.0) * 25.4);

                    let transform = ImageTransform {
                        dpi: Some(300.0),
                        ..Default::default()
                    };

                    if doc_opt.is_none() {
                        let (new_doc, page1, layer1) = PdfDocument::new("Manga Doc", w_mm, h_mm, "Layer 1");
                        let layer = new_doc.get_page(page1).get_layer(layer1);
                        
                        let pdf_img = Image::from_dynamic_image(&img);
                        pdf_img.add_to_layer(layer, transform);
                        
                        doc_opt = Some(new_doc);
                    } else {
                        let d = doc_opt.as_mut().unwrap();
                        let (new_page, new_layer) = d.add_page(w_mm, h_mm, "Layer 1");
                        let layer = d.get_page(new_page).get_layer(new_layer);
                        
                        let pdf_img = Image::from_dynamic_image(&img);
                        pdf_img.add_to_layer(layer, transform);
                    }
                }
            }
        }
    }

    let save_msg = "Compressing & saving PDF to storage (Hang tight!)...";
    if let Ok(jmsg) = env.new_string(&save_msg) {
        if let Ok(cls) = env.find_class("com/ibanimation/converter/NativeEngine") {
            let _ = env.call_static_method(
                &cls,
                "updateProgress",
                "(Ljava/lang/String;F)V",
                &[(&jmsg).into(), 0.99f32.into()],
            );
        }
    }

    if let Some(doc) = doc_opt {
        match File::create(&out_str) {
            Ok(file) => {
                let mut writer = BufWriter::new(file);
                doc.save(&mut writer).is_ok() as jboolean
            }
            Err(_) => false as jboolean,
        }
    } else {
        false as jboolean
    }
}
