package com.gopal.jarvis

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) { Home() }
            }
        }
    }
}

private fun open(ctx: Context, i: Intent) {
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    ctx.startActivity(i)
}

@Composable
fun Home() {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }
    val logs by Bus.log.collectAsState()
    var text by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var confirmOn by remember { mutableStateOf(Prefs.confirmEnabled(ctx)) }

    val a11y = tick >= 0 && JarvisAccessibilityService.instance != null
    val overlay = tick >= 0 && Settings.canDrawOverlays(ctx)
    val mic = tick >= 0 &&
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val svcOn = tick >= 0 && AgentService.instance != null

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    fun ok(b: Boolean) = if (b) "✅" else "❌"

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Gopal Jarvis", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("${ok(mic)} माइक   ${ok(a11y)} Accessibility   ${ok(overlay)} Overlay   ${ok(svcOn)} सर्विस")

        Button(modifier = Modifier.fillMaxWidth(), onClick = {
            if (!mic) permLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
            else AgentService.start(ctx)
        }) { Text(if (!mic) "1. माइक/नोटिफिकेशन की इजाज़त दो" else "सर्विस चालू करो") }

        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
            open(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
        }) { Text("2. ऐप info खोलो (⋮ → Allow restricted settings)") }

        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
            open(ctx, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }) { Text("3. Accessibility खोलो → Gopal Jarvis चालू करो") }

        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
            open(ctx, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
        }) { Text("4. Overlay (ऊपर तैरने) की इजाज़त") }

        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
            open(ctx, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }) { Text("5. बैटरी ऑप्टिमाइज़ेशन बंद करो (इस ऐप के लिए)") }

        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(modifier = Modifier.weight(1f), onClick = {
                if (AgentService.instance == null) Bus.add("पहले 'सर्विस चालू करो' दबाओ")
                else AgentService.listenNow()
            }) { Text("🎤 बोलो", fontSize = 18.sp) }
            Button(modifier = Modifier.weight(1f), onClick = { AgentService.stopAll() }) {
                Text("⏹ STOP", fontSize = 18.sp)
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                label = { Text("कमांड लिखो") }, modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                if (AgentService.instance == null) Bus.add("पहले 'सर्विस चालू करो' दबाओ")
                else if (text.isNotBlank()) { AgentService.submit(text.trim()); text = "" }
            }) { Text("भेजो") }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("जोखिम वाले काम से पहले पूछो", modifier = Modifier.weight(1f))
            Switch(checked = confirmOn, onCheckedChange = { confirmOn = it; Prefs.setConfirm(ctx, it) })
        }

        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { showSettings = !showSettings }) {
            Text(if (showSettings) "AI Settings छुपाओ" else "AI Settings (API keys)")
        }
        if (showSettings) {
            Prefs.providers(ctx).forEach { ProviderEditor(it) }
        }

        Text("लॉग", fontWeight = FontWeight.Bold)
        logs.takeLast(40).reversed().forEach { Text(it, fontSize = 13.sp) }
    }
}

@Composable
fun ProviderEditor(p: Provider) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(p.baseUrl) }
    var model by remember { mutableStateOf(p.model) }
    var key by remember { mutableStateOf(p.key) }
    var res by remember { mutableStateOf("") }

    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(p.name, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = key, onValueChange = { key = it }, label = { Text("API key") },
            visualTransformation = PasswordVisualTransformation(), singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = model, onValueChange = { model = it }, label = { Text("Model") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = url, onValueChange = { url = it }, label = { Text("Base URL") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                Prefs.save(ctx, Provider(p.name, url.trim(), model.trim(), key.trim()))
                res = "सेव हो गया"
            }) { Text("सेव") }
            OutlinedButton(onClick = {
                scope.launch {
                    res = "जाँच रहा हूँ…"
                    val cur = Provider(p.name, url.trim(), model.trim(), key.trim())
                    res = withContext(Dispatchers.IO) { LlmClient.test(cur) }
                }
            }) { Text("Test key") }
        }
        if (res.isNotEmpty()) Text(res)
    }
}
