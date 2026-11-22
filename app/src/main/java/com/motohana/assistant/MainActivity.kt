package com.motohana.assistant

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.telephony.SmsManager
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private val requestCode = 501
    private lateinit var root: LinearLayout
    private lateinit var page: LinearLayout
    private val prefs by lazy { getSharedPreferences("moto_hana_data", MODE_PRIVATE) }
    private var currentLocationText = ""
    private var authenticated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xFF0B1020.toInt()
        window.navigationBarColor = 0xFF0B1020.toInt()
        if (!SecurityStore.hasPin(this)) showPinSetup() else showLogin()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun text(value: String, size: Float, color: Int = 0xFFEAF0FF.toInt(), bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun baseScreen(): LinearLayout {
        val scroll = ScrollView(this).apply { setBackgroundColor(0xFF0B1020.toInt()); isFillViewport = true }
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(22), dp(20), dp(28)); setBackgroundColor(0xFF0B1020.toInt()) }
        scroll.addView(root)
        setContentView(scroll)
        return root
    }

    private fun showPinSetup() {
        val box = baseScreen()
        box.gravity = Gravity.CENTER_VERTICAL
        box.addView(text("MOTO HANA", 28f, 0xFF9D8CFF.toInt(), true))
        box.addView(text("إعداد الحماية لأول مرة", 20f, bold = true))
        box.addView(text("أنشئ رمز PIN من 4 إلى 8 أرقام. يمكنك استخدام بصمة الهاتف بعد ذلك.", 14f, 0xFF9AA8C4.toInt()))
        val pin = EditText(this).apply { hint = "PIN جديد"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD; setTextColor(-1); setHintTextColor(0xFF8995B0.toInt()) }
        box.addView(pin, fieldParams())
        val confirm = EditText(this).apply { hint = "تأكيد PIN"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD; setTextColor(-1); setHintTextColor(0xFF8995B0.toInt()) }
        box.addView(confirm, fieldParams())
        primaryButton(box, "حفظ وفتح التطبيق") {
            val a = pin.text.toString(); val b = confirm.text.toString()
            if (a.length !in 4..8 || a.any { !it.isDigit() } || a != b) toast("تأكد من تطابق PIN وأنه من 4 إلى 8 أرقام")
            else { SecurityStore.savePin(this, a); authenticated = true; showMain("الرئيسية") }
        }
    }

    private fun showLogin() {
        val box = baseScreen()
        box.gravity = Gravity.CENTER_VERTICAL
        box.addView(text("MOTO HANA", 30f, 0xFF9D8CFF.toInt(), true))
        box.addView(text("مرحبًا بعودتك", 22f, bold = true))
        box.addView(text("افتح المساعد باستخدام PIN أو البصمة.", 14f, 0xFF9AA8C4.toInt()))
        val pin = EditText(this).apply { hint = "أدخل PIN"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD; setTextColor(-1); setHintTextColor(0xFF8995B0.toInt()) }
        box.addView(pin, fieldParams())
        primaryButton(box, "دخول باستخدام PIN") {
            if (SecurityStore.verifyPin(this, pin.text.toString())) { authenticated = true; showMain("الرئيسية") }
            else toast("رمز PIN غير صحيح")
        }
        secondaryButton(box, "الدخول بالبصمة") { authenticateBiometric() }
        if (BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS) {
            box.postDelayed({ if (!authenticated && !isFinishing) authenticateBiometric() }, 350)
        }
    }

    private fun fieldParams() = LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(12); bottomMargin = dp(8) }
    private fun card(parent: LinearLayout, title: String, subtitle: String, action: () -> Unit) {
        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14)); setBackgroundColor(0xFF171F33.toInt()); isClickable = true; isFocusable = true; setOnClickListener { action() } }
        c.addView(text(title, 16f, 0xFFEAF0FF.toInt(), true)); c.addView(text(subtitle, 12f, 0xFF9AA8C4.toInt()))
        val lp = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }; parent.addView(c, lp)
    }
    private fun primaryButton(parent: LinearLayout, label: String, action: () -> Unit) = addButton(parent, label, true, action)
    private fun secondaryButton(parent: LinearLayout, label: String, action: () -> Unit) = addButton(parent, label, false, action)
    private fun addButton(parent: LinearLayout, label: String, primary: Boolean, action: () -> Unit) {
        val b = Button(this).apply { text = label; isAllCaps = false; setTextColor(-1); textSize = 14f; setBackgroundTintList(android.content.res.ColorStateList.valueOf(if (primary) 0xFF6555C7.toInt() else 0xFF26334D.toInt())); setOnClickListener { action() } }
        parent.addView(b, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(7); bottomMargin = dp(7) })
    }

    private fun showMain(tab: String) {
        authenticated = true
        ReminderStore.restore(this)
        val box = baseScreen()
        box.addView(text("MOTO HANA", 13f, 0xFF9D8CFF.toInt(), true))
        box.addView(text(prefs.getString("assistant_name", "موتو حنا") ?: "موتو حنا", 27f, bold = true))
        box.addView(text("مساعدك الشخصي • يعمل محليًا على هاتفك", 13f, 0xFF9AA8C4.toInt()))
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(12), 0, dp(14)) }
        listOf("الرئيسية", "المهام", "الإجراءات", "السجل", "الإعدادات").forEach { label ->
            val b = TextView(this).apply { text = label; gravity = Gravity.CENTER; textSize = 11f; setTextColor(if (label == tab) 0xFFB8ACFF.toInt() else 0xFF9AA8C4.toInt()); setPadding(dp(5), dp(9), dp(5), dp(9)); setOnClickListener { showMain(label) } }
            nav.addView(b, LinearLayout.LayoutParams(0, -2, 1f))
        }
        box.addView(nav)
        when (tab) {
            "المهام" -> {
                card(box, "إضافة تذكير", "إنشاء تذكير بموعد محدد") { addReminderDialog() }
                card(box, "قائمة التذكيرات", "عرض التذكيرات المحفوظة وحذفها") { showReminders() }
                card(box, "آخر أمر تم التقاطه", "عرض آخر إشعار احتوى اسم المساعد") { showLastCommand() }
            }
            "الإجراءات" -> {
                card(box, "تحديد الموقع الحالي", "الحصول على الإحداثيات ورابط Google Maps") { getLocation() }
                card(box, "إرسال موقعي برسالة SMS", "اختيار الرقم والشريحة ومراجعة التكلفة المحتملة") { composeLocationSms() }
                card(box, "إعدادات الهاتف", "فتح إعدادات الشبكة والموقع") { showPhoneSettings() }
            }
            "السجل" -> {
                val last = prefs.getString("last_command", "لم يتم التقاط أوامر بعد") ?: "لم يتم التقاط أوامر بعد"
                card(box, "آخر رسالة ملتقطة", last) { showLastCommand() }
                card(box, "حالة محرك الأوامر", "الإصدار الحالي يلتقط الرسائل ويُنشئ التذكيرات المدعومة فقط؛ الأوامر غير المدعومة لا تُنفذ تلقائيًا.") { showLimitations() }
            }
            "الإعدادات" -> {
                card(box, "اسم المساعد", prefs.getString("assistant_name", "موتو حنا") ?: "موتو حنا") { editAssistantName() }
                card(box, "التطبيقات المراقبة", "اختيار التطبيقات التي يقرأ إشعاراتها") { chooseWatchedApps() }
                card(box, "صلاحيات التطبيق", "إدارة الموقع والرسائل والهاتف والإشعارات") { requestPermissions() }
                card(box, "قراءة الإشعارات", "فتح إعدادات الوصول إلى الإشعارات") { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                card(box, "الحماية", "تغيير PIN أو اختبار البصمة") { securityOptions() }
                card(box, "حول وحدود Android", "معرفة ما يمكن تنفيذه تلقائيًا وما يحتاج تدخلًا") { showLimitations() }
            }
            else -> {
                card(box, "المساعد نشط محليًا", "اسم المساعد: ${prefs.getString("assistant_name", "موتو حنا")}") { editAssistantName() }
                card(box, "إعداد استقبال الأوامر", "اختر التطبيقات وفعّل صلاحية قراءة الإشعارات") { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                card(box, "المهام القادمة", "${ReminderStore.all(this).size} تذكير محفوظ") { showMain("المهام") }
                card(box, "إجراءات الموقع", "تحديد موقعك ومشاركته برسالة نصية") { showMain("الإجراءات") }
                card(box, "آخر رسالة", prefs.getString("last_command", "لا توجد رسائل ملتقطة") ?: "لا توجد رسائل ملتقطة") { showMain("السجل") }
            }
        }
        box.addView(text("بياناتك محفوظة على الهاتف. لا يوجد حساب أو اشتراك أو خادم.", 11f, 0xFF65738F.toInt()))
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    private fun editAssistantName() {
        val input = EditText(this).apply { setText(prefs.getString("assistant_name", "موتو حنا")) }
        AlertDialog.Builder(this).setTitle("اسم المساعد").setView(input).setPositiveButton("حفظ") { _, _ ->
            val v = input.text.toString().trim(); if (v.isNotEmpty()) prefs.edit().putString("assistant_name", v).apply(); showMain("الإعدادات")
        }.setNegativeButton("إلغاء", null).show()
    }
    private fun requestPermissions() {
        val p = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE)
        if (android.os.Build.VERSION.SDK_INT >= 33) p.add(Manifest.permission.POST_NOTIFICATIONS)
        ActivityCompat.requestPermissions(this, p.toTypedArray(), requestCode)
    }
    private fun chooseWatchedApps() {
        val pm = packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA).filter { pm.getLaunchIntentForPackage(it.packageName) != null }.sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
        val labels = apps.map { pm.getApplicationLabel(it).toString() }.toTypedArray(); val packages = apps.map { it.packageName }
        val selected = prefs.getStringSet("watched_packages", emptySet()) ?: emptySet(); val checked = BooleanArray(apps.size) { selected.contains(packages[it]) }
        AlertDialog.Builder(this).setTitle("اختر التطبيقات").setMultiChoiceItems(labels, checked) { _, i, yes -> checked[i] = yes }
            .setPositiveButton("حفظ") { _, _ -> prefs.edit().putStringSet("watched_packages", packages.indices.filter { checked[it] }.map { packages[it] }.toSet()).apply(); toast("تم حفظ التطبيقات") }.setNegativeButton("إلغاء", null).show()
    }
    private fun addReminderDialog() {
        val input = EditText(this).apply { hint = "اكتب نص التذكير"; setTextColor(-1); setHintTextColor(0xFF8995B0.toInt()) }
        val cal = Calendar.getInstance().apply { add(Calendar.MINUTE, 5) }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0); addView(input) }
        val pick = Button(this).apply { isAllCaps = false; text = "الموعد: ${fmt(cal.timeInMillis)}"; setOnClickListener {
            DatePickerDialog(this@MainActivity, { _, y, m, d -> TimePickerDialog(this@MainActivity, { _, h, min -> cal.set(y,m,d,h,min,0); text = "الموعد: ${fmt(cal.timeInMillis)}" }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show() }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        } }
        box.addView(pick)
        AlertDialog.Builder(this).setTitle("تذكير جديد").setView(box).setPositiveButton("حفظ") { _, _ ->
            val t = input.text.toString().trim(); if (t.isNotEmpty() && cal.timeInMillis > System.currentTimeMillis()) { ReminderStore.add(this, t, cal.timeInMillis); toast("تم حفظ وجدولة التذكير") } else toast("أدخل نصًا وموعدًا صحيحًا")
        }.setNegativeButton("إلغاء", null).show()
    }
    private fun fmt(time: Long) = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(time))
    private fun showReminders() {
        val items = ReminderStore.all(this); if (items.isEmpty()) { toast("لا توجد تذكيرات"); return }
        AlertDialog.Builder(this).setTitle("التذكيرات").setItems(items.map { "${it.text}\n${fmt(it.at)}" }.toTypedArray()) { _, i ->
            AlertDialog.Builder(this).setMessage("حذف التذكير؟").setPositiveButton("حذف") { _, _ -> ReminderStore.remove(this, items[i].id); showReminders() }.setNegativeButton("إلغاء", null).show()
        }.setNegativeButton("إغلاق", null).show()
    }
    private fun getLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) { requestPermissions(); return }
        toast("جارٍ تحديد الموقع")
        LocationServices.getFusedLocationProviderClient(this).getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc -> if (loc == null) toast("تعذر تحديد الموقع. تحقق من GPS.") else {
                currentLocationText = "${loc.latitude},${loc.longitude}"; prefs.edit().putString("last_location", currentLocationText).apply()
                AlertDialog.Builder(this).setTitle("موقعك الحالي").setMessage("$currentLocationText\nhttps://www.google.com/maps?q=$currentLocationText")
                    .setPositiveButton("نسخ") { _, _ -> (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("الموقع", "https://www.google.com/maps?q=$currentLocationText")) }
                    .setNeutralButton("فتح الخريطة") { _, _ -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$currentLocationText"))) }.setNegativeButton("إغلاق", null).show()
            } }.addOnFailureListener { toast("فشل تحديد الموقع: ${it.localizedMessage}") }
    }
    private fun composeLocationSms() {
        if (currentLocationText.isBlank()) currentLocationText = prefs.getString("last_location", "").orEmpty()
        if (currentLocationText.isBlank()) { toast("حدد الموقع أولًا"); getLocation(); return }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val number = EditText(this).apply { hint = "رقم المستلم"; inputType = InputType.TYPE_CLASS_PHONE; setTextColor(-1) }; val note = EditText(this).apply { setText("موقعي الحالي:"); setTextColor(-1) }
        val sim = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL; addView(RadioButton(this@MainActivity).apply { id = 1; text = "SIM1"; isChecked = true }); addView(RadioButton(this@MainActivity).apply { id = 2; text = "SIM2" }) }
        box.addView(number); box.addView(note); box.addView(sim)
        AlertDialog.Builder(this).setTitle("رسالة الموقع").setView(box).setPositiveButton("مراجعة") { _, _ ->
            val n = number.text.toString().trim(); if (n.isBlank()) { toast("أدخل رقم المستلم"); return@setPositiveButton }
            val slot = if (sim.checkedRadioButtonId == 2) 1 else 0
            val body = "${note.text}\nالإحداثيات: $currentLocationText\nGoogle Maps: https://www.google.com/maps?q=$currentLocationText"
            confirmSms(n, slot, body)
        }.setNegativeButton("إلغاء", null).show()
    }
    private fun confirmSms(number: String, slot: Int, body: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) { requestPermissions(); return }
        val subs = try { getSystemService(android.telephony.SubscriptionManager::class.java).activeSubscriptionInfoList } catch (_: Exception) { null }
        val subId = subs?.firstOrNull { it.simSlotIndex == slot }?.subscriptionId
        if (subId == null) { toast("الشريحة غير متاحة أو تعذر قراءتها"); return }
        AlertDialog.Builder(this).setTitle("تأكيد الإرسال").setMessage("الإرسال من ${if (slot == 0) "SIM1" else "SIM2"}. قد تُحسب رسوم حسب باقتك.\n\n$body")
            .setNegativeButton("إلغاء", null).setPositiveButton("أوافق وأرسل") { _, _ -> try {
                val manager = if (android.os.Build.VERSION.SDK_INT >= 31) getSystemService(SmsManager::class.java).createForSubscriptionId(subId) else SmsManager.getSmsManagerForSubscriptionId(subId)
                val parts = manager.divideMessage(body); if (parts.size > 1) manager.sendMultipartTextMessage(number, null, parts, null, null) else manager.sendTextMessage(number, null, body, null, null)
                toast("تم تسليم طلب SMS لنظام الهاتف؛ تحقق من تقرير التسليم أو تطبيق الرسائل")
            } catch (e: Exception) { toast("تعذر الإرسال: ${e.localizedMessage}") } }.show()
    }
    private fun showLastCommand() { val s = prefs.getString("last_command", "لم يتم التقاط أوامر بعد") ?: "لم يتم التقاط أوامر بعد"; AlertDialog.Builder(this).setTitle("آخر أمر ملتقط").setMessage(s).setPositiveButton("حسنًا", null).show() }
    private fun showPhoneSettings() { val options = arrayOf("الموقع", "الشبكة", "إعدادات التطبيقات"); AlertDialog.Builder(this).setItems(options) { _, i -> val action = when(i) { 0 -> Settings.ACTION_LOCATION_SOURCE_SETTINGS; 1 -> Settings.ACTION_WIRELESS_SETTINGS; else -> Settings.ACTION_APPLICATION_SETTINGS }; startActivity(Intent(action)) }.show() }
    private fun authenticateBiometric() {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG
        val state = BiometricManager.from(this).canAuthenticate(authenticators)
        if (state != BiometricManager.BIOMETRIC_SUCCESS) { toast("البصمة غير متاحة. استخدم PIN أو فعّل بصمة الهاتف."); return }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { super.onAuthenticationSucceeded(result); authenticated = true; showMain("الرئيسية") }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { super.onAuthenticationError(errorCode, errString); toast("تعذر التحقق: $errString") }
            override fun onAuthenticationFailed() { super.onAuthenticationFailed(); toast("لم يتم التعرف على البصمة") }
        })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("موتو حنا").setSubtitle("استخدم بصمة الإصبع لفتح التطبيق").setNegativeButtonText("إلغاء").setAllowedAuthenticators(authenticators).build())
    }
    private fun securityOptions() {
        AlertDialog.Builder(this).setTitle("الحماية").setItems(arrayOf("اختبار البصمة", "تغيير PIN")) { _, i -> if (i == 0) authenticateBiometric() else changePin() }.show()
    }
    private fun changePin() {
        val old = EditText(this).apply { hint = "PIN الحالي"; inputType = 18 }; val new = EditText(this).apply { hint = "PIN الجديد"; inputType = 18 }
        val box = LinearLayout(this).apply { orientation = 1; setPadding(dp(20), dp(8), dp(20), 0); addView(old); addView(new) }
        AlertDialog.Builder(this).setTitle("تغيير PIN").setView(box).setPositiveButton("حفظ") { _, _ -> val p = new.text.toString(); if (!SecurityStore.verifyPin(this, old.text.toString())) toast("PIN الحالي غير صحيح") else if (p.length !in 4..8 || p.any { !it.isDigit() }) toast("PIN الجديد يجب أن يكون 4-8 أرقام") else { SecurityStore.savePin(this, p); toast("تم تغيير PIN") } }.setNegativeButton("إلغاء", null).show()
    }
    private fun showLimitations() { AlertDialog.Builder(this).setTitle("حدود Android والنسخة الحالية").setMessage("• البصمة تفتح التطبيق مباشرة بعد نجاح التحقق.\n• التطبيق يلتقط إشعارات التطبيقات المختارة.\n• التذكيرات اليدوية تعمل محليًا.\n• لا يمكن لتطبيق عادي تغيير وضع الطيران أو بيانات الهاتف مباشرة على Android الحديث.\n• المكالمات المجدولة تحتاج إجراءً ظاهرًا للمستخدم بسبب قيود تشغيل المكالمات من الخلفية.\n• WhatsApp لا يوفر واجهة عامة لإرسال الرسائل أو Live Location سرًا.\n• إرسال SMS يتطلب إذنًا وموافقة صريحة وقد تُفرض رسوم من شركة الاتصالات.\n• منع حذف التطبيق أو مسح بياناته غير ممكن لتطبيق عادي دون إدارة جهاز.").setPositiveButton("حسنًا", null).show() }
    override fun onResume() { super.onResume() }
}
