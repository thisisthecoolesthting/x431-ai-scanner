@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caseforge.scanner.ui.customer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.caseforge.scanner.data.AppDatabase
import com.caseforge.scanner.data.CustomerEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CustomerEditScreen(
    db: AppDatabase,
    customerId: Long?,
    onSaved: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(customerId != null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var existingFound by remember { mutableStateOf(false) }

    LaunchedEffect(customerId) {
        if (customerId != null) {
            loading = true
            val match = withContext(Dispatchers.IO) {
                db.sessionDao().listCustomers().firstOrNull { it.id == customerId }
            }
            if (match != null) {
                existingFound = true
                name = match.name
                phone = match.phone.orEmpty()
                email = match.email.orEmpty()
                notes = match.notes.orEmpty()
            }
            loading = false
        } else {
            loading = false
        }
    }

    val isEditMode = customerId != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditMode) "Edit customer" else "New customer") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                CircularProgressIndicator(Modifier.padding(24.dp))
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isEditMode && existingFound) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                ) {
                    Text(
                        "Editing existing customers isn't supported by the current DAO. " +
                            "Saving will create a new customer record.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it; error = null },
                label = { Text("Name") },
                singleLine = true,
                isError = error != null,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Phone") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onBack,
                    enabled = !saving,
                    modifier = Modifier.weight(1f),
                ) { Text("Cancel") }
                Button(
                    enabled = !saving,
                    onClick = {
                        if (name.isBlank()) {
                            error = "Name is required."
                            return@Button
                        }
                        saving = true
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                db.sessionDao().insertCustomer(
                                    CustomerEntity(
                                        name = name.trim(),
                                        phone = phone.trim().ifBlank { null },
                                        email = email.trim().ifBlank { null },
                                        notes = notes.trim().ifBlank { null },
                                    )
                                )
                            }
                            saving = false
                            onSaved()
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(if (saving) "Saving..." else "Save") }
            }
        }
    }
}
