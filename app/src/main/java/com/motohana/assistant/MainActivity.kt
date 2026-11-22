package com.motohana.assistant

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
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
    private lateinit var content: LinearLayout
    private var currentLocationText = ""
    private val prefs by lazy { getSharedPreferences("moto_hana_data", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!SecurityStore.hasPin(this)) showPinSetup() else showPinLogin()
    }

    private fun showPinSetup() {
        val input = EditText(this).apply {
            hint = "أنشئ PIN من 4 إلى 8 أرقام"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this).setTitle("إعداد الحماية")
            .setMessage("أنشئ رمز دخول للتطبيق")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("حفظ", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val pin = input.text.toString()
                        if (pin.length !in 4..8 || pin.any { !it.isDigit() }) {
                            input.error = "أدخل من 4 إلى 8 أرقام"
                        } else {
                            SecurityStore.savePin(this, pin)
                            dialog.dismiss()
                            showMain()
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun showPinLogin() {
        val input = EditText(this).apply {
            hint = "PIN"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this).setTitle("موتو حنا")
            .setMessage("أدخل رمز الدخول")
            .setView(input).setCancelable(false)
            .setPositiveButton("دخول") { _, _ ->
                if (SecurityStore.verifyPin(this, input.text.toString())) showMain()
                else {
                    Toast.makeText(this, "رمز PIN غير صحيح", Toast.LENGTH_LONG).show()
                    showPinLogin()
                }
            }
            .setNeutralButton("استخدام البصمة") { _, _ -> authenticateBiometric() }
            .show()
    }

    private fun showMain() {
        val scroll = ScrollView(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 28, 36, 36)
            setBackgroundColor(0xFF10111A.toInt())
        }
        scroll.addView(content)
        setContentView(scroll)
        val name = prefs.getString("assistant_name", "موتو حنا") ?: "موتو حنا"
        titleText(name, 28)
        subtitle("مساعد شخصي محلي — البيانات محفوظة على الهاتف")
        section("الرئيسية")
        button("إعداد اسم المساعد") { editAssistantName() }
        button("إعداد البصمة / التحقق البيومتري") { authenticateBiometric() }
        button("إدارة صلاحيات التطبيق") { requestPermissions() }
        button("تفعيل قراءة الإشعارات") { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        button("اختيار التطبيقات لمراقبتها") { chooseWatchedApps() }
        section("الأوامر والمهام")
        button("إضافة تذكير") { addReminderDialog() }
        button("عرض / حذف التذكيرات") { showReminders() }
        button("آخر أمر تم التقاطه") { showLastCommand() }
        section("مشاركة الموقع عبر SMS")
        button("تحديد موقعي الحالي") { getLocation() }
        button("إعداد وإرسال رسالة موقعي") { composeLocationSms() }
        section("إجراءات الهاتف")
        button("فتح إعدادات الموقع") { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
        button("فتح إعدادات الشبكة") { startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }
        button("فتح إعدادات التطبيقات") { startActivity(Intent(Settings.ACTION_APPLICATION_SETTINGS)) }
        section("الحماية والخصوصية")
        button("تغيير PIN") { changePin() }
        button("معلومات حدود Android") { showLimitations() }
        subtitle("الإصدار 1.0 — يعمل محليًا ولا يستخدم خادمًا أو API.")
    }

    private fun titleText(text: String, size: Float) {
        val v = TextView(this).apply { this.text = text; textSize = size; setTextColor(0xFFFFFFFF.toInt()); gravity = Gravity.CENTER; setPadding(0, 18, 0, 12) }
        content.addView(v)
    }
    private fun subtitle(text: String) {
        val v = TextView(this).apply { this.text = text; textSize = 14f; setTextColor(0xFFB7B9C8.toInt()); setPadding(0, 8, 0, 14) }
        content.addView(v)
    }
    private fun section(text: String) {
        val v = TextView(this).apply { this.text = text; textSize = 19f; setTextColor(0xFFB9AEFF.toInt()); setPadding(0, 20, 0, 8) }
        content.addView(v)
    }
    private fun button(text: String, action: () -> Unit) {
        val b = Button(this).apply {
            this.text = text
            isAllCaps = false
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF5146A8.toInt()))
            setOnClickListener { action() }
        }
        content.addView(b, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10 })
    }

    private fun editAssistantName() {
        val input = EditText(this).apply { setText(prefs.getString("assistant_name", "موتو حنا")) }
        AlertDialog.Builder(this).setTitle("اسم المساعد").setView(input)
            .setPositiveButton("حفظ") { _, _ ->
                val value = input.text.toString().trim()
                if (value.isNotEmpty()) prefs.edit().putString("assistant_name", value).apply()
                showMain()
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE)
        if (android.os.Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), requestCode)
    }

    private fun chooseWatchedApps() {
        val pm = packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
        val labels = apps.map { pm.getApplicationLabel(it).toString() }.toTypedArray()
        val packages = apps.map { it.packageName }
        val selected = prefs.getStringSet("watched_packages", emptySet()) ?: emptySet()
        val checked = BooleanArray(apps.size) { selected.contains(packages[it]) }
        AlertDialog.Builder(this).setTitle("اختر التطبيقات")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton("حفظ") { _, _ ->
                val chosen = packages.indices.filter { checked[it] }.map { packages[it] }.toSet()
                prefs.edit().putStringSet("watched_packages", chosen).apply()
                Toast.makeText(this, "تم حفظ اختيار التطبيقات", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun addReminderDialog() {
        val input = EditText(this).apply { hint = "نص التذكير" }
        val cal = Calendar.getInstance().apply { add(Calendar.MINUTE, 5) }
        val wrapper = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 10, 40, 0); addView(input) }
        val choose = Button(this).apply { text = "موعد التذكير: ${SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(cal.time)}"; isAllCaps = false }
        wrapper.addView(choose)
        choose.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                TimePickerDialog(this, { _, h, min ->
                    cal.set(y, m, d, h, min, 0)
                    choose.text = "موعد التذكير: ${SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(cal.time)}"
                }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
        AlertDialog.Builder(this).setTitle("إضافة تذكير").setView(wrapper)
            .setPositiveButton("حفظ") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty() && cal.timeInMillis > System.currentTimeMillis()) {
                    ReminderStore.add(this, text, cal.timeInMillis)
                    Toast.makeText(this, "تم حفظ التذكير", Toast.LENGTH_SHORT).show()
                } else Toast.makeText(this, "اكتب التذكير واختر موعدًا مستقبليًا", Toast.LENGTH_LONG).show()
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun showReminders() {
        val items = ReminderStore.all(this)
        if (items.isEmpty()) {
            AlertDialog.Builder(this).setMessage("لا توجد تذكيرات محفوظة").setPositiveButton("حسنًا", null).show()
            return
        }
        val labels = items.map { "${it.text}\n${SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(it.at))}" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("التذكيرات — اضغط لحذف تذكير")
            .setItems(labels) { _, index ->
                AlertDialog.Builder(this).setMessage("حذف هذا التذكير؟").setPositiveButton("حذف") { _, _ ->
                    ReminderStore.remove(this, items[index].id)
                    showReminders()
                }.setNegativeButton("إلغاء", null).show()
            }.setNegativeButton("إغلاق", null).show()
    }

    private fun getLocation() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), requestCode)
            Toast.makeText(this, "امنح صلاحية الموقع ثم اضغط تحديد الموقع مرة أخرى", Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, "جارٍ تحديد الموقع...", Toast.LENGTH_SHORT).show()
        LocationServices.getFusedLocationProviderClient(this)
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc ->
                if (loc == null) Toast.makeText(this, "لم يتم الحصول على موقع. تأكد من تشغيل GPS.", Toast.LENGTH_LONG).show()
                else {
                    currentLocationText = "${loc.latitude},${loc.longitude}"
                    prefs.edit().putString("last_location", currentLocationText).apply()
                    AlertDialog.Builder(this).setTitle("تم تحديد الموقع")
                        .setMessage("الإحداثيات: $currentLocationText\n\nhttps://www.google.com/maps?q=$currentLocationText")
                        .setPositiveButton("نسخ") { _, _ ->
                            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Location", currentLocationText))
                        }.setNegativeButton("إغلاق", null).show()
                }
            }.addOnFailureListener {
                Toast.makeText(this, "تعذر تحديد الموقع: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
            }
    }

    private fun composeLocationSms() {
        if (currentLocationText.isBlank()) {
            currentLocationText = prefs.getString("last_location", "").orEmpty()
        }
        if (currentLocationText.isBlank()) {
            Toast.makeText(this, "حدد موقعك أولًا", Toast.LENGTH_LONG).show()
            return
        }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(36, 8, 36, 0) }
        val number = EditText(this).apply { hint = "رقم المستلم"; inputType = InputType.TYPE_CLASS_PHONE }
        val note = EditText(this).apply { setText("موقعي الحالي:") }
        val sim = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            addView(RadioButton(this@MainActivity).apply { id = 1; text = "SIM1"; isChecked = true })
            addView(RadioButton(this@MainActivity).apply { id = 2; text = "SIM2" })
        }
        box.addView(number); box.addView(note); box.addView(sim)
        AlertDialog.Builder(this).setTitle("إرسال موقعي عبر SMS").setView(box)
            .setPositiveButton("مراجعة الرسالة") { _, _ ->
                val recipient = number.text.toString().trim()
                if (recipient.isBlank()) { Toast.makeText(this, "أدخل رقم المستلم", Toast.LENGTH_LONG).show(); return@setPositiveButton }
                val slot = if (sim.checkedRadioButtonId == 2) 1 else 0
                val body = "${note.text}\nالإحداثيات: $currentLocationText\nGoogle Maps: https://www.google.com/maps?q=$currentLocationText"
                confirmSms(recipient, slot, body)
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun confirmSms(number: String, slot: Int, body: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions()
            Toast.makeText(this, "امنح صلاحيات SMS والهاتف ثم أعد المحاولة", Toast.LENGTH_LONG).show()
            return
        }
        val subscriptions = try {
            getSystemService(android.telephony.SubscriptionManager::class.java).activeSubscriptionInfoList
        } catch (_: Exception) { null }
        val subId = subscriptions?.firstOrNull { it.simSlotIndex == slot }?.subscriptionId
        if (subId == null) {
            Toast.makeText(this, "الشريحة المطلوبة غير متاحة أو لم يتم منح صلاحية الهاتف", Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this).setTitle("تأكيد إرسال الرسالة")
            .setMessage("سيتم إرسال الرسالة من ${if (slot == 0) "SIM1" else "SIM2"}.\nقد تُفرض رسوم حسب باقتك. لا يمكن ضمان معرفة السعر مسبقًا.\n\n$body")
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("أوافق وأرسل") { _, _ ->
                try {
                    val sms = smsManagerForSubscription(subId)
                    val parts = sms.divideMessage(body)
                    if (parts.size > 1) sms.sendMultipartTextMessage(number, null, parts, null, null)
                    else sms.sendTextMessage(number, null, body, null, null)
                    Toast.makeText(this, "تم تمرير طلب الإرسال إلى نظام الهاتف", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "تعذر إرسال الرسالة: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }.show()
    }


    @Suppress("DEPRECATION")
    private fun smsManagerForSubscription(subscriptionId: Int): SmsManager {
        return if (android.os.Build.VERSION.SDK_INT >= 31) {
            getSystemService(SmsManager::class.java).createForSubscriptionId(subscriptionId)
        } else {
            SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
        }
    }

    private fun showLastCommand() {
        val command = prefs.getString("last_command", null)
        AlertDialog.Builder(this).setTitle("آخر إشعار يحتوي اسم المساعد")
            .setMessage(command ?: "لم يتم التقاط أي أمر بعد.\nفعّل صلاحية قراءة الإشعارات واختر التطبيقات.")
            .setPositiveButton("حسنًا", null).show()
    }

    private fun authenticateBiometric() {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val state = BiometricManager.from(this).canAuthenticate(authenticators)
        if (state != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(this, "فعّل بصمة أو قفل شاشة في إعدادات الهاتف أولًا", Toast.LENGTH_LONG).show()
            return
        }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                Toast.makeText(this@MainActivity, "تم التحقق بنجاح", Toast.LENGTH_SHORT).show()
            }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("موتو حنا")
                .setSubtitle("التحقق من الهوية")
                .setAllowedAuthenticators(authenticators)
                .build()
        )
    }

    private fun changePin() {
        val oldPin = EditText(this).apply { hint = "PIN الحالي"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD }
        val newPin = EditText(this).apply { hint = "PIN الجديد (4-8 أرقام)"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 8, 40, 0); addView(oldPin); addView(newPin) }
        AlertDialog.Builder(this).setTitle("تغيير PIN").setView(box)
            .setPositiveButton("حفظ") { _, _ ->
                val new = newPin.text.toString()
                if (!SecurityStore.verifyPin(this, oldPin.text.toString())) Toast.makeText(this, "PIN الحالي غير صحيح", Toast.LENGTH_LONG).show()
                else if (new.length !in 4..8 || new.any { !it.isDigit() }) Toast.makeText(this, "PIN الجديد يجب أن يكون 4-8 أرقام", Toast.LENGTH_LONG).show()
                else { SecurityStore.savePin(this, new); Toast.makeText(this, "تم تغيير PIN", Toast.LENGTH_SHORT).show() }
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun showLimitations() {
        AlertDialog.Builder(this).setTitle("حدود Android")
            .setMessage("• التطبيق العادي لا يستطيع منع إلغاء تثبيته أو مسح بياناته.\n• Android الحديث يمنع التطبيقات العادية من تشغيل/إيقاف وضع الطيران وبيانات الهاتف مباشرة.\n• WhatsApp لا يتيح إرسال رسائل شخصية أو Live Location تلقائيًا عبر واجهة عامة؛ يمكن فتح التطبيق وتجهيز المحتوى مع تدخل المستخدم.\n• تكلفة SMS تحددها شركة الاتصالات ولا يمكن ضمان عرضها مسبقًا.\n• البصمة هنا متاحة للتحقق عند الطلب؛ حماية PIN هي بوابة الدخول الأساسية.")
            .setPositiveButton("حسنًا", null).show()
    }

    override fun onResume() {
        super.onResume()
        if (::content.isInitialized) {
            // لا نعيد بناء الواجهة تلقائيًا حتى لا نفقد حالة المستخدم.
        }
    }
}
