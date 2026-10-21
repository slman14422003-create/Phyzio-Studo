# skills — واجهة المرضى وتقويم الجلسات

## المهمة المنجزة
- إعادة تصميم قائمة المرضى لتصبح أبسط على المعالج الفيزيائي.
- تقويم شهري لكل مريض يعرض أيام الجلسات والمدة، مع حذف الجلسة بكبسولة.

## الملفات
- SessionCalendarView.java: تقويم مرسوم يدويًا (سبت إلى جمعة من اليمين). اليوم ذو الجلسة يظهر بخلفية ناعمة ومدة الجلسات بالدقائق، اليوم المجدول بإطار منقط، اليوم الحالي بإطار، المحدد بتعبئة كاملة.
- PatientDetailActivity.java: بطاقة الموعد القادم (دمج الجدول والموعد)، بطاقة التقويم مع التنقل بين الأشهر، قائمة الجلسات مع التصفية حسب اليوم المختار.
- PatientsActivity.java: شريط إحصاء واحد، ترتيب جديد "الأقرب موعدًا"، بطاقة مريض مختصرة.
- PatientDialogs.java: نسخة جديدة من showSession تقبل يوم مبدئي (presetDayStart) لإضافة جلسة على اليوم المحدد.
- res/layout: activity_patient_detail، activity_patients، item_patient_row، item_session_row، item_payment_row.
- res/drawable: bg_capsule_delete، bg_capsule_soft، bg_date_block، ic_nav_right، ic_nav_left.
- res/values/patient_ui.xml: كل النصوص العربية الجديدة (الكود الجديد بلا نصوص عربية).

## سلوك الواجهة
- الضغط على يوم في التقويم يصفّي قائمة الجلسات لذلك اليوم، والضغط عليه مرة ثانية يلغي التصفية.
- زر إضافة جلسة يستخدم اليوم المحدد ووقت الجدول المعتاد إن وُجد.
- حذف جلسة أو دفعة: كبسولة حذف بجانب الصف مع نافذة تأكيد. الضغط على صف الجلسة يفتح التعديل.

## ملاحظات للمتابعة
- لم يُجرَ بناء كامل (لا يوجد Android SDK في بيئة العمل): شغّل البناء وراجع أي خطأ.
- ملف Patient.java وبقية الملفات لم تُعدَّل وما زالت تحوي تعليقات عربية قديمة.
- أفكار لاحقة: عرض أسبوعي، سحب لحذف الجلسة، تلوين التقويم حسب انخفاض الألم.

# skills — جدول الجلسات الأسبوعي + حذف البروتوكولات الثابتة + Semo AI

## المهمة المنجزة
- جدول أسبوعي لكل المرضى (الاسم، الأيام، الساعة) أعلى قائمة المرضى، قابل للطي، ويحترم البحث، والضغط على الصف يفتح ملف المريض.
- حذف كل البيانات الثابتة (clinical_database وmodes_encyclopedia وanatomy_reference) وإبقاء user_guide فقط.
- حذف شاشتي الموسوعة والتشريح وأزرارهما، وحذف آلية تعديل الحالات الثابتة وصف الإعدادات الخاص بها.
- بقيت الحالات المخصصة (حالاتي) هي المصدر الوحيد للحالات في البحث والمفضلة والمساعد.
- المساعد أصبح اسمه Semo AI في كل النصوص الظاهرة والتعليمات.

## الملفات
- PatientsActivity.java: buildScheduleTable وscheduleDays وscheduleTime وopenPatient.
- res/layout: item_schedule_table، item_schedule_row. res/values/schedule_ui.xml: النصوص الجديدة.
- DataManager.java: حذف تحميل الحالات الثابتة والتعديلات وسياق الموسوعة، allCases صارت المخصصة فقط.
- CaseDetailActivity / AddEditCaseActivity / CaseItem / SettingsActivity / MainActivity: إزالة كل ما يخص الحالات الثابتة.
- AiAssistantActivity.java: منع الإرسال المزدوج أثناء الرد، إزالة الرد القديم من السجل عند إعادة التوليد، حماية من الانهيار وبعد إغلاق الشاشة.
- AiPrompts.java / AiOrchestrator.java: إزالة ادعاء وجود الموسوعة والتشريح وقاعدة الجهاز كمصادر.
- حُذفت موارد يتيمة: layouts (item_field_block، item_stat_chip)، وعدة drawables، وmenu_main، ونصوص غير مستخدمة.

## ملاحظات للمتابعة
- لم يُجرَ بناء (لا يوجد Android SDK ولا javac هنا): شغّل البناء وراجع أي خطأ.
- الجدول يعرض وقتًا واحدًا لكل مريض لأن النموذج يخزن ساعة واحدة لكل الأيام.
- ملفات الكلمات (tashkeel_dict وvalid_words وtaa_haa_lexicon) تخص القراءة الصوتية وتُركت كما هي.

# skills — إعادة تصميم الحالات وبرامج العلاج + حركة وإحساس حي + رسومات متحركة

## المهمة المنجزة
- نموذج الحالة صار سريريًا للمعالج: الفئة، منطقة الإصابة، المرحلة، الشدة، الأعراض، الفحص والتقييم، الأهداف، طرق العلاج، الجرعة، عدد الجلسات أسبوعيًا وإجمالًا، مدة الجلسة، تمارين منزلية، معايير التقدم، الموانع. حُذفت حقول نوع التيار (النمط، التردد، القناة، الأقطاب) من النموذج وبقيت للقراءة في الحالات القديمة.
- نموذج برنامج العلاج: الفئة المستهدفة، المنطقة، المرحلة، مدة البرنامج بالأسابيع، الجلسات أسبوعيًا، طرق العلاج، قوالب المراحل والتمارين، الأدوات، مقاييس النتائج، معايير التقدم، المتابعة، البرنامج المنزلي، الموانع.
- كل حقل إما خيارات سريعة للضغط أو خانة كتابة، والخانات الكبيرة لها كبسولات تضيف عبارة جاهزة.
- حركة عامة: دخول متتابع لعناصر كل شاشة، تصغير عند الضغط، انتقالات شاشات بعمق (انزلاق + تكبير + تلاشي).
- رسومات متحركة (14 مشهد) في النماذج والتفاصيل والحالات الفارغة والإعدادات والدليل والجدول والمريض والخصوصية والرئيسية والشاشة الافتتاحية.

## الملفات
- جديدة: FormKit.java، FlowLayout.java، IllustrationView.java، Motion.java، res/values/clinical_forms.xml، res/values/illustration_attrs.xml.
- معدّلة: CaseItem، TreatmentProgram، AddEditCaseActivity، AddEditTreatmentProgramActivity، CaseDetailActivity، TreatmentProgramDetailActivity، CaseRowAdapter، MyCasesActivity، TreatmentProgramsActivity، DataManager، ClinicalMasterApp، SplashActivity.
- layouts: activity_add_edit، activity_add_edit_treatment_program، view_empty_state، activity_splash، item_treatment_program_row. anim: slide_in_right، slide_out_left، slide_in_left، slide_out_right، slide_up_in، slide_down_out.

## ملاحظات للمتابعة
- لم يُجرَ بناء (لا يوجد Android SDK هنا): شغّل البناء وراجع أي خطأ.
- Motion يحقن الرسوم تلقائيًا في الشاشات ويستثني PdfViewer وAiAssistant من الدخول المتتابع.
- الحالات القديمة تعمل كما هي ولا تضيع بياناتها عند التعديل.
