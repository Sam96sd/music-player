package com.example.licence

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val WHATSAPP = "963947941447"

/** Shown instead of the app until a valid activation code for this phone is entered. */
@Composable
fun ActivationScreen(onActivated: () -> Unit) {
    val context = LocalContext.current
    val device = remember { Licence.deviceCode(context) }
    var typed by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun tryActivate() {
        val digits = typed.filter { it.isDigit() }
        when {
            digits.length != 10 -> error = "رمز التفعيل من 10 أرقام · The activation code has 10 digits"
            Licence.isValid(device, digits) -> {
                Licence.save(context, digits)
                onActivated()
            }
            else -> error = "رمز التفعيل غير صحيح لهذا الجهاز · Wrong code for this phone"
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Muffin", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(6.dp))
            Text(
                "تفعيل التطبيق على هذا الجوال\nActivate Muffin on this phone",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(28.dp))
            Text("رمز الجهاز · Device code", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                device,
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("device_code")
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { openWhatsApp(context, device) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("أرسل الرمز عبر واتساب · Send on WhatsApp") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { copy(context, device) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("نسخ رمز الجهاز · Copy device code") }
            Spacer(Modifier.height(28.dp))
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it.filter { c -> c.isDigit() || c == '-' }.take(11); error = null },
                label = { Text("رمز التفعيل · Activation code") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { tryActivate() }),
                isError = error != null,
                modifier = Modifier.fillMaxWidth().testTag("activation_input")
            )
            error?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { tryActivate() },
                modifier = Modifier.fillMaxWidth().testTag("activate_button")
            ) { Text("تفعيل · Activate") }
        }
    }
}

private fun openWhatsApp(context: Context, device: String) {
    val msg = "مرحباً، أرغب في تفعيل تطبيق Muffin للموسيقى. رمز الجهاز: $device"
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$WHATSAPP?text=" + Uri.encode(msg)))
    runCatching { context.startActivity(intent) }
        .onFailure { Toast.makeText(context, "0947941447", Toast.LENGTH_LONG).show() }
}

private fun copy(context: Context, device: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Muffin device code", device))
    Toast.makeText(context, "تم النسخ · Copied", Toast.LENGTH_SHORT).show()
}
