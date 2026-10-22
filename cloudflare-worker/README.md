# ووركر الملفات (Cloudflare Worker + مستودع GitHub)

الملفات تُخزَّن داخل مجلد `files/` في مستودع GitHub، والتطبيق يكلّم الووركر فقط.

1. أنشئ مستودعًا **Private** جديدًا (مثلًا `phyzio-files`) وفيه فرع `main`.
2. GitHub > Settings > Developer settings > Fine-grained tokens: توكن لهذا المستودع فقط،
   صلاحية **Contents: Read and write**.
3. عدّل `GITHUB_REPO` في `wrangler.toml` إلى `owner/repo`.
4. شغّل:
   ```
   cd cloudflare-worker
   npx wrangler deploy
   npx wrangler secret put GITHUB_TOKEN
   npx wrangler secret put SHARED_SECRET   # اختياري لكن مستحسن
   ```
5. بالتطبيق: الملفات السحابية > الإعدادات: الصق رابط الووركر، ولو ضبطت SHARED_SECRET
   الصقه في خانة الرمز.

الحد الأقصى للملف 25MB. تاريخ آخر تعديل يُجلب لأول 40 ملف فقط (حد الطلبات الفرعية).
