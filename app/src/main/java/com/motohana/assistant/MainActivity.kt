package com.motohana.assistant

import androidx.activity.compose.setContent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.SmsManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class MainActivity : AppCompatActivity() {
 private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  setContent { MaterialTheme(colorScheme = darkColorScheme()) {
   Home(
    askPermissions = { permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE)) },
    locate = { done ->
     val ok = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
     if (!ok) done(null) else LocationServices.getFusedLocationProviderClient(this).getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).addOnSuccessListener { l -> done(l?.let { "${it.latitude},${it.longitude}" }) }.addOnFailureListener { done(null) }
    },
    send = { number, slot, text ->
     if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
    permissionLauncher.launch(
        arrayOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE
        )
    )
    return@Home
}
     val subs = try { getSystemService(android.telephony.SubscriptionManager::class.java).activeSubscriptionInfoList } catch (_: Exception) { null }
     val id = subs?.firstOrNull { it.simSlotIndex == slot }?.subscriptionId
     if (id == null) android.widget.Toast.makeText(this, "تعذر العثور على الشريحة المحددة", android.widget.Toast.LENGTH_LONG).show()
     else androidx.appcompat.app.AlertDialog.Builder(this).setTitle("تأكيد إرسال SMS")
      .setMessage("قد تُحسب تكلفة على خطك حسب باقتك ومشغل الشبكة. هل توافق على الإرسال؟")
      .setNegativeButton("إلغاء", null).setPositiveButton("أوافق وأرسل") { _, _ ->
       try {
        val manager = if (android.os.Build.VERSION.SDK_INT >= 31) getSystemService(SmsManager::class.java).createForSubscriptionId(id) else @Suppress("DEPRECATION") SmsManager.getDefault()
        manager.sendTextMessage(number, null, text, null, null)
        android.widget.Toast.makeText(this, "تم إرسال طلب SMS إلى نظام الهاتف", android.widget.Toast.LENGTH_LONG).show()
       } catch (e: Exception) { android.widget.Toast.makeText(this, "فشل الإرسال: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show() }
      }.show()
    },
    biometric = { runBiometric() }
   )
  } }
 }
 private fun runBiometric() {
  val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
  if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) return
  BiometricPrompt(this, ContextCompat.getMainExecutor(this), object: BiometricPrompt.AuthenticationCallback() {})
   .authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("التحقق من الهوية").setSubtitle("استخدم البصمة أو قفل الشاشة").setAllowedAuthenticators(authenticators).build())
 }
}

@Composable
private fun Home(askPermissions: () -> Unit, locate: ((String?) -> Unit) -> Unit, send: (String, Int, String) -> Unit, biometric: () -> Unit) {
 var phone by remember { mutableStateOf("") }
 var note by remember { mutableStateOf("موقعي الحالي:") }
 var sim by remember { mutableIntStateOf(0) }
 var coords by remember { mutableStateOf("") }
 var status by remember { mutableStateOf("جاهز") }
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
  Text("موتو حنا", style = MaterialTheme.typography.headlineLarge)
  Text("مساعد شخصي محلي — نسخة تأسيسية")
  HorizontalDivider()
  Text("مشاركة موقعي عبر SMS", style = MaterialTheme.typography.titleLarge)
  OutlinedTextField(phone, { phone = it }, label = { Text("رقم الهاتف") }, modifier = Modifier.fillMaxWidth())
  OutlinedTextField(note, { note = it }, label = { Text("مقدمة الرسالة") }, modifier = Modifier.fillMaxWidth())
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
   FilterChip(selected = sim == 0, onClick = { sim = 0 }, label = { Text("SIM1") })
   FilterChip(selected = sim == 1, onClick = { sim = 1 }, label = { Text("SIM2") })
  }
  Button(onClick = { askPermissions(); locate { value -> coords = value.orEmpty(); status = if (value == null) "تعذر تحديد الموقع أو لم تُمنح الصلاحية" else "تم تحديد الموقع" } }, modifier = Modifier.fillMaxWidth()) { Text("تحديد موقعي") }
  if (coords.isNotBlank()) {
   Text("الإحداثيات: $coords")
   Text("رابط الخريطة: https://www.google.com/maps?q=$coords")
  }
  Button(onClick = {
   if (phone.isBlank()) status = "أدخل رقم الهاتف"
   else if (coords.isBlank()) status = "حدد الموقع أولاً"
   else send(phone.trim(), sim, "$note\nالإحداثيات: $coords\nGoogle Maps: https://www.google.com/maps?q=$coords")
  }, modifier = Modifier.fillMaxWidth()) { Text("مراجعة وإرسال SMS") }
  OutlinedButton(onClick = biometric, modifier = Modifier.fillMaxWidth()) { Text("اختبار البصمة / قفل الشاشة") }
  OutlinedButton(onClick = askPermissions, modifier = Modifier.fillMaxWidth()) { Text("طلب صلاحيات التطبيق") }
  Text("الحالة: $status")
  HorizontalDivider()
  Text("تنبيه: قد تُطبق رسوم SMS. لا يستطيع Android أو التطبيق معرفة السعر النهائي مسبقًا بصورة موحدة. اختيار SIM يعتمد على دعم الجهاز والصلاحيات.")
  Text("هذه نسخة تأسيسية وليست الإصدار النهائي: PIN، إعداد اسم المساعد، اختيار التطبيقات، محلل الأوامر، قاعدة المهام والجدولة ما زالت ضمن مراحل التنفيذ التالية.")
 }
}
