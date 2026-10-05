import java.io.File
import java.io.IOException
import java.net.URI

plugins {
    id("com.android.application")
}

// ملف google-services.json (إعداد Firebase) يجب أن يكون في مجلد app/.
// بدونه كان بناء المهمة processDebugGoogleServices يفشل. الآن يُطبَّق البلجن فقط
// عند وجود الملف، وإلا يُبنى التطبيق بشكل طبيعي وتظهر رسالة "Firebase غير مُعدّ"
// داخل التطبيق بدل فشل البناء (FirebaseSyncManager يتعامل مع هذه الحالة).
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
} else {
    logger.warn("google-services.json غير موجود في app/ - تم تخطي بلجن Google Services (النسخ السحابي معطّل).")
}

// أرقام الإصدار تأتي من GitHub Actions عند وجودها، وإلا قيم افتراضية للبناء المحلي
val ciRunNumber: Int? = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
val ciVersionName: String? = System.getenv("APP_VERSION_NAME")?.takeIf { it.isNotBlank() }

// ---------------------------------------------------------------------------------------
// sherpa-onnx (الصوت العصبي المحلي): تُنزَّل حزمة AAR الرسمية من إصدارات GitHub مرة واحدة
// إلى app/libs (وتُتجاهَل في git). المهمة تعمل قبل البناء تلقائيًا ولا تُعيد التنزيل إن وُجد الملف.
// ---------------------------------------------------------------------------------------
val sherpaVersion = "1.13.4"
val sherpaAar: File = layout.projectDirectory.file("libs/sherpa-onnx-$sherpaVersion.aar").asFile
val downloadSherpaOnnx = tasks.register("downloadSherpaOnnx") {
    outputs.file(sherpaAar)
    onlyIf { !sherpaAar.exists() }
    doLast {
        val url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaVersion/sherpa-onnx-$sherpaVersion.aar"
        sherpaAar.parentFile.mkdirs()
        val part = File(sherpaAar.parentFile, sherpaAar.name + ".part")
        var lastError: Exception? = null
        for (attempt in 1..3) {
            try {
                val conn = URI(url).toURL().openConnection()
                conn.connectTimeout = 30_000
                conn.readTimeout = 180_000
                conn.getInputStream().use { input ->
                    part.outputStream().use { output -> input.copyTo(output) }
                }
                if (part.length() < 1_000_000L) throw IOException("الملف المُنزَّل صغير جدًا (${part.length()} بايت)")
                if (!part.renameTo(sherpaAar)) throw IOException("تعذّر نقل الملف المُنزَّل")
                lastError = null
                break
            } catch (e: Exception) {
                lastError = e
                part.delete()
                Thread.sleep(2000L * attempt)
            }
        }
        if (lastError != null) {
            throw GradleException("فشل تنزيل sherpa-onnx من $url", lastError)
        }
    }
}

android {
    namespace = "com.ast2012a.clinicalmaster"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.salman.ast2012a"
        minSdk = 24
        // targetSdk تُركت على 34 عمدًا: رفعها إلى 35+ يفرض وضع Edge-to-Edge على كل الشاشات
        // ويغيّر شكل التصميم الحالي. compileSdk = 36 يعطيك أحدث المكتبات بدون هذا التأثير.
        targetSdk = 34
        versionCode = ciRunNumber ?: 1
        versionName = ciVersionName ?: (if (ciRunNumber != null) "1.0.$ciRunNumber" else "1.0")
    }

    // توقيع نسخة Release: يُفعَّل فقط لو مفاتيح التوقيع موجودة (أسرار GitHub)
    val keystorePath: String? = System.getenv("KEYSTORE_FILE")
    signingConfigs {
        if (!keystorePath.isNullOrBlank() && file(keystorePath).exists()) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8: تصغير الكود + إزالة الموارد غير المستخدمة لتقليل حجم الحزمة
            isMinifyEnabled = true
            isShrinkResources = true
            // إبقاء معماريات الهواتف الحقيقية فقط (يحذف مكتبات x86 الأصلية الثقيلة).
            // عند النشر بصيغة AAB يوزّع Play المعمارية المناسبة لكل جهاز تلقائيًا.
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            }
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // الـ Lint يعمل ويُنتج تقريرًا لكن لا يُفشل البناء
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.2.0")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.3.0")
    // مطلوبة لواجهات الإشعارات الحقيقية (NotificationCompat/NotificationManagerCompat)
    // وطلب الأذونات في وقت التشغيل (ActivityResultLauncher) في SettingsActivity
    implementation("androidx.core:core:1.19.1")
    implementation("androidx.activity:activity:1.13.0")

    // النسخ الاحتياطي السحابي لملفات المرضى (Firebase Firestore + مصادقة
    // مجهولة تلقائية لكل جهاز، بدون شاشة تسجيل دخول). الإصدارات تُدار عبر
    // Firebase BOM حتى تبقى متوافقة مع بعضها دائمًا.
    implementation(platform("com.google.firebase:firebase-bom:34.18.0"))
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-analytics")

    // استخراج نص حقيقي من ملفات PDF اللي يرفعها المستخدم على التخزين
    // السحابي (CloudStorageClient) - يُستخدم من DocumentTextExtractor
    // وCloudKnowledgeManager عشان Phizyo AI يقدر "يقرأ" محتوى هذه الملفات
    // فعليًا ويستخدمها كمصدر تأريض إضافي، بدل ما تفضل ملفات مغلقة عليه.
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // القراءة الصوتية بالأصوات العصبية المجانية (اتصال WebSocket في EdgeTtsClient)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // التعرّف الضوئي على الحروف (Tesseract) لقراءة ملفات PDF الممسوحة ضوئيًا/المصوَّرة صوتيًا
    // (عربي + إنجليزي) - نموذج اللغة يُنزَّل مرة واحدة عند أول استخدام (PdfOcr).
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.7.0")

    // الصوت العصبي المحلي (نموذج Piper العربي يعمل على الجهاز بلا إنترنت) - NeuralVoiceManager.
    // النموذج نفسه لا يدخل الـ APK: يُنزَّل مرة واحدة عند اختيار المحرك.
    // مهم: المكتبة غير منشورة على Maven Central ولا Google Maven (كان هذا سبب فشل البناء:
    // "Could not find com.k2fsa.sherpa.onnx:sherpa-onnx-android"). تُنشر فقط كملف AAR
    // ضمن إصدارات GitHub الرسمية، لذلك تُنزَّل تلقائيًا بالمهمة downloadSherpaOnnx أعلاه.
    implementation(files(sherpaAar).builtBy(downloadSherpaOnnx))
    // فكّ أرشيف النموذج (tar.bz2) أثناء التنزيل. الإصدار 1.21 بلا اعتماديات إضافية ويعمل على minSdk 24.
    implementation("org.apache.commons:commons-compress:1.21")
}
