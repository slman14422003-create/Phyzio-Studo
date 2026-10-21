# ===== قواعد R8 لتطبيق AST-2012A =====

# أرقام الأسطر في تقارير الأعطال (يُرفع ملف mapping.txt مع كل إصدار)
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# PDFBox-Android: يحمّل الخطوط والفلاتر والموارد بالاسم ديناميكيًا
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**
-dontwarn org.spongycastle.**
-dontwarn javax.xml.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**

# Firebase/Firestore: قواعدها تأتي مع المكتبات نفسها (consumer rules)

# commons-compress: كلاسات اختيارية (ضغط/تشفير) غير مستخدمة هنا
-dontwarn org.apache.commons.compress.**
-dontwarn org.tukaani.xz.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
