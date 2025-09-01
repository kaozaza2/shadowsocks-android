package com.shadowsocks.android.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shadowsocks.android.crypto.ShadowsocksCrypto
import com.shadowsocks.android.model.Profile
import com.shadowsocks.android.ui.theme.ShadowsocksAndroidTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorScreen(
    profile: Profile? = null,
    onSave: (Profile) -> Unit = {},
    onCancel: () -> Unit = {},
    onTestConnection: (Profile) -> Unit = {}
) {
    var name by remember { mutableStateOf(profile?.name ?: "") }
    var server by remember { mutableStateOf(profile?.server ?: "") }
    var serverPort by remember { mutableStateOf(profile?.serverPort?.toString() ?: "8388") }
    var password by remember { mutableStateOf(profile?.password ?: "") }
    var method by remember { mutableStateOf(profile?.method ?: ShadowsocksCrypto.Method.AES_256_GCM.displayName) }
    var showPassword by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    
    val focusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    
    val isValid = name.isNotBlank() && 
                  server.isNotBlank() && 
                  serverPort.toIntOrNull() != null && 
                  password.isNotBlank()
    
    LaunchedEffect(Unit) {
        if (profile == null) {
            focusRequester.requestFocus()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Text(
            text = if (profile == null) "Add Profile" else "Edit Profile",
            style = MaterialTheme.typography.headlineMedium
        )
        
        // Profile Name
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Profile Name") },
            leadingIcon = {
                Icon(Icons.Default.Person, contentDescription = null)
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            singleLine = true,
            placeholder = { Text("e.g., My Server") }
        )
        
        // Server Address
        OutlinedTextField(
            value = server,
            onValueChange = { server = it },
            label = { Text("Server Address") },
            leadingIcon = {
                Icon(Icons.Default.Language, contentDescription = null)
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("e.g., example.com or 192.168.1.1") }
        )
        
        // Server Port
        OutlinedTextField(
            value = serverPort,
            onValueChange = { serverPort = it.filter { char -> char.isDigit() } },
            label = { Text("Server Port") },
            leadingIcon = {
                Icon(Icons.Default.Settings, contentDescription = null)
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            placeholder = { Text("8388") }
        )
        
        // Encryption Method
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = method,
                onValueChange = {},
                readOnly = true,
                label = { Text("Encryption Method") },
                leadingIcon = {
                    Icon(Icons.Default.Security, contentDescription = null)
                },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                ShadowsocksCrypto.getSupportedMethods().forEach { cryptoMethod ->
                    DropdownMenuItem(
                        text = { Text(cryptoMethod.displayName) },
                        onClick = {
                            method = cryptoMethod.displayName
                            expanded = false
                        }
                    )
                }
            }
        }
        
        // Password
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            leadingIcon = {
                Icon(Icons.Default.Lock, contentDescription = null)
            },
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showPassword) "Hide password" else "Show password"
                    )
                }
            },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Enter your password") }
        )
        
        // Test Connection Button
        if (isValid) {
            Button(
                onClick = {
                    val testProfile = Profile(
                        id = profile?.id ?: 0,
                        name = name,
                        server = server,
                        serverPort = serverPort.toInt(),
                        password = password,
                        method = method
                    )
                    isTesting = true
                    testResult = null
                    onTestConnection(testProfile)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isTesting
            ) {
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (isTesting) "Testing..." else "Test Connection")
            }
        }
        
        // Test Result
        testResult?.let { result ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (result.contains("success", ignoreCase = true))
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (result.contains("success", ignoreCase = true))
                            Icons.Default.CheckCircle
                        else
                            Icons.Default.Error,
                        contentDescription = null,
                        tint = if (result.contains("success", ignoreCase = true))
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = result,
                        color = if (result.contains("success", ignoreCase = true))
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel")
            }
            
            Button(
                onClick = {
                    val newProfile = Profile(
                        id = profile?.id ?: 0,
                        name = name,
                        server = server,
                        serverPort = serverPort.toInt(),
                        password = password,
                        method = method,
                        isActive = profile?.isActive ?: false,
                        createdAt = profile?.createdAt ?: System.currentTimeMillis(),
                        lastConnectedAt = profile?.lastConnectedAt
                    )
                    onSave(newProfile)
                },
                enabled = isValid,
                modifier = Modifier.weight(1f)
            ) {
                Text("Save")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ProfileEditorScreenPreview() {
    ShadowsocksAndroidTheme {
        ProfileEditorScreen()
    }
}

@Preview(showBackground = true)
@Composable
fun ProfileEditorScreenEditPreview() {
    ShadowsocksAndroidTheme {
        val sampleProfile = Profile(
            id = 1,
            name = "Test Server",
            server = "example.com",
            serverPort = 8388,
            password = "mypassword",
            method = "AES-256-GCM"
        )
        ProfileEditorScreen(profile = sampleProfile)
    }
}