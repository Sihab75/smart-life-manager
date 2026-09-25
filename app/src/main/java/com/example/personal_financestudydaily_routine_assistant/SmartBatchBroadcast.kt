package com.example.personal_financestudydaily_routine_assistant

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.personal_financestudydaily_routine_assistant.data.database.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
internal fun SmartBatchBroadcastPanel(vm: MainViewModel, isCr: Boolean) {
    val contacts by vm.studentContacts.collectAsState(initial = emptyList())
    val history by vm.broadcastHistory.collectAsState(initial = emptyList())
    var showContacts by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showComposer by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Podcasts, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Smart Batch Broadcast", fontWeight = FontWeight.Bold)
                    Text("${contacts.count { it.isActive }} active contacts · ${history.size} broadcasts",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showContacts = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.People, null); Spacer(Modifier.width(4.dp)); Text("Contacts")
                }
                OutlinedButton(onClick = { showHistory = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.History, null); Spacer(Modifier.width(4.dp)); Text("History")
                }
            }
            Button(
                onClick = { showComposer = true },
                enabled = isCr,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Campaign, null); Spacer(Modifier.width(6.dp))
                Text(if (isCr) "Create broadcast" else "CR access required")
            }
        }
    }
    if (showContacts) ContactManagerDialog(vm, contacts) { showContacts = false }
    if (showHistory) BroadcastHistoryDialog(vm, history, contacts) { showHistory = false }
    if (showComposer) BroadcastComposerDialog(vm, contacts) { showComposer = false }
}

@Composable
private fun ContactManagerDialog(vm: MainViewModel, contacts: List<StudentContactEntity>, dismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<StudentContactEntity?>(null) }
    var showGroup by remember { mutableStateOf(false) }
    val visible = contacts.filter {
        it.studentName.contains(query, true) || it.studentId.contains(query, true) || it.section.contains(query, true)
    }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Batch contacts") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Search contacts") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) })
                if (visible.isEmpty()) EmptyState("No contacts yet. Add classmates manually to build your recipient list.")
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(visible, key = { it.id }) { contact ->
                        ListCard {
                            Column(Modifier.weight(1f)) {
                                Text(contact.studentName, fontWeight = FontWeight.Bold)
                                Text("${contact.studentId} · ${contact.section.ifBlank { "No section" }}")
                                Text("${if (contact.isActive) "Active" else "Inactive"} · ${contact.preferredChannel}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { vm.saveStudentContact(contact.copy(isActive = !contact.isActive)) }) {
                                Icon(if (contact.isActive) Icons.Default.PersonOff else Icons.Default.Person, "Toggle active")
                            }
                            IconButton(onClick = { editing = contact }) { Icon(Icons.Default.Edit, "Edit contact") }
                            IconButton(onClick = { vm.deleteStudentContact(contact) }) { Icon(Icons.Default.Delete, "Delete") }
                        }
                    }
                }
                OutlinedButton(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(6.dp)); Text("Add contact manually")
                }
                OutlinedButton(onClick = { showGroup = true }, enabled = contacts.any { it.isActive }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.GroupAdd, null); Spacer(Modifier.width(6.dp)); Text("Save active contacts as group")
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("Done") } }
    )
    if (showAdd) AddContactDialog(vm) { showAdd = false }
    editing?.let { contact -> EditContactDialog(vm, contact) { editing = null } }
    if (showGroup) SaveGroupDialog(vm, contacts.filter { it.isActive }) { showGroup = false }
}

@Composable
private fun SaveGroupDialog(vm: MainViewModel, contacts: List<StudentContactEntity>, dismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Save recipient group") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${contacts.size} active contacts will be saved to this group.")
            OutlinedTextField(name, { name = it }, label = { Text("Group name") }, singleLine = true)
        }
    }, confirmButton = {
        Button(enabled = name.isNotBlank(), onClick = { vm.saveRecipientGroup(name, contacts.map { it.id }); dismiss() }) {
            Text("Save group")
        }
    }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

@Composable
private fun AddContactDialog(vm: MainViewModel, dismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var studentId by rememberSaveable { mutableStateOf("") }
    var section by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var whatsapp by rememberSaveable { mutableStateOf(false) }
    var messenger by rememberSaveable { mutableStateOf(false) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Add batch contact") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Student name") }, singleLine = true)
            OutlinedTextField(studentId, { studentId = it }, label = { Text("Student ID") }, singleLine = true)
            OutlinedTextField(section, { section = it }, label = { Text("Section") }, singleLine = true)
            OutlinedTextField(phone, { phone = it }, label = { Text("Phone number (optional)") }, singleLine = true)
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(whatsapp, { whatsapp = it }); Text("WhatsApp opt-in/available") }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(messenger, { messenger = it }); Text("Messenger opt-in/available") }
        }

    }, confirmButton = {
        Button(enabled = name.isNotBlank() && studentId.isNotBlank(), onClick = {
            vm.saveStudentContact(StudentContactEntity(studentName = name.trim(), studentId = studentId.trim(),
                section = section.trim(), phoneNumber = phone.trim(), whatsappAvailable = whatsapp, messengerAvailable = messenger))
            dismiss()
        }) { Text("Save contact") }
    }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

@Composable
private fun EditContactDialog(vm: MainViewModel, contact: StudentContactEntity, dismiss: () -> Unit) {
    var name by rememberSaveable(contact.id) { mutableStateOf(contact.studentName) }
    var studentId by rememberSaveable(contact.id) { mutableStateOf(contact.studentId) }
    var section by rememberSaveable(contact.id) { mutableStateOf(contact.section) }
    var phone by rememberSaveable(contact.id) { mutableStateOf(contact.phoneNumber) }
    var whatsapp by rememberSaveable(contact.id) { mutableStateOf(contact.whatsappAvailable) }
    var messenger by rememberSaveable(contact.id) { mutableStateOf(contact.messengerAvailable) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Edit batch contact") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Student name") }, singleLine = true)
            OutlinedTextField(studentId, { studentId = it }, label = { Text("Student ID") }, singleLine = true)
            OutlinedTextField(section, { section = it }, label = { Text("Section") }, singleLine = true)
            OutlinedTextField(phone, { phone = it }, label = { Text("Phone number") }, singleLine = true)
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(whatsapp, { whatsapp = it }); Text("WhatsApp opt-in/available") }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(messenger, { messenger = it }); Text("Messenger opt-in/available") }
        }
    }, confirmButton = {
        Button(enabled = name.isNotBlank() && studentId.isNotBlank(), onClick = {
            vm.saveStudentContact(contact.copy(studentName = name.trim(), studentId = studentId.trim(),
                section = section.trim(), phoneNumber = phone.trim(), whatsappAvailable = whatsapp, messengerAvailable = messenger))
            dismiss()
        }) { Text("Save changes") }
    }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

@Composable
private fun BroadcastComposerDialog(vm: MainViewModel, contacts: List<StudentContactEntity>, dismiss: () -> Unit) {
    val activeContacts = contacts.filter { it.isActive }
    val groups by vm.recipientGroups.collectAsState(initial = emptyList())
    var step by rememberSaveable { mutableIntStateOf(0) }
    var type by rememberSaveable { mutableStateOf("CR announcement") }
    var title by rememberSaveable { mutableStateOf("") }
    var details by rememberSaveable { mutableStateOf("") }
    var course by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }
    var time by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf("Entire batch") }
    var section by rememberSaveable { mutableStateOf("") }
    var groupId by rememberSaveable { mutableLongStateOf(0L) }
    var search by rememberSaveable { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    var channels by remember { mutableStateOf(setOf("In-App")) }
    val filtered = activeContacts.filter { it.studentName.contains(search, true) || it.studentId.contains(search, true) }
    val selected = activeContacts.filter { it.id in selectedIds }
    val sections = activeContacts.map { it.section }.filter { it.isNotBlank() }.distinct().sorted()
    val recipientList = when (mode) {
        "Entire batch" -> activeContacts
        "Specific section" -> activeContacts.filter { it.section == section }
        "Saved group" -> groups.firstOrNull { it.id == groupId }?.contactIds?.split(",")?.mapNotNull { it.toLongOrNull() }
            ?.let { ids -> activeContacts.filter { it.id in ids } } ?: emptyList()
        else -> selected
    }
    val types = listOf("Class routine", "CT/Exam", "Assignment", "Teacher notice", "Room change", "Presentation", "Announcement", "CR announcement")
    val channelNames = listOf("In-App", "WhatsApp", "Messenger")
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (step == 3) "Preview broadcast" else "Create announcement") }, text = {
        Column(Modifier.fillMaxWidth().heightIn(max = 600.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Step ${step + 1} of 4 · ${listOf("Announcement", "Recipients", "Delivery channels", "Preview")[step]}",
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            when (step) {
                0 -> {
                    Text("Update type", fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        types.take(4).forEach { FilterChip(type == it, { type = it }, label = { Text(it, maxLines = 1) }) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        types.drop(4).forEach { FilterChip(type == it, { type = it }, label = { Text(it, maxLines = 1) }) }
                    }
                    OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Announcement title") }, singleLine = true)
                    OutlinedTextField(details, { details = it }, Modifier.fillMaxWidth(), label = { Text("Details") })
                    OutlinedTextField(course, { course = it }, Modifier.fillMaxWidth(), label = { Text("Course code (optional)") }, singleLine = true)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(date, { date = it }, Modifier.weight(1f), label = { Text("Date") }, singleLine = true)
                        OutlinedTextField(time, { time = it }, Modifier.weight(1f), label = { Text("Time") }, singleLine = true)
                    }
                }
                1 -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf("Entire batch", "Specific section", "Selected students", "Saved group").forEach { FilterChip(mode == it, { mode = it }, label = { Text(it) }) }
                    }
                    if (mode == "Specific section") {
                        if (sections.isEmpty()) Text("Add section values to contacts before filtering.", style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            sections.forEach { value -> FilterChip(section == value, { section = value }, label = { Text(value) }) }
                        }
                    }
                    if (mode == "Saved group") {
                        if (groups.isEmpty()) Text("No saved recipient groups are configured yet. Create one from Batch Contacts.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            groups.forEach { group -> FilterChip(groupId == group.id, { groupId = group.id }, label = { Text(group.name) }) }
                        }
                    }
                    OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), label = { Text("Search students") }, singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(selectedIds.size == activeContacts.size && activeContacts.isNotEmpty(), {
                            selectedIds = if (it) activeContacts.map { contact -> contact.id }.toSet() else emptySet()
                        })
                        Text("Select all · ${if (mode == "Entire batch") activeContacts.size else recipientList.size} selected")
                    }
                    filtered.forEach { contact ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(contact.id in selectedIds, { checked ->
                                selectedIds = if (checked) selectedIds + contact.id else selectedIds - contact.id
                            })
                            Column {
                                Text(contact.studentName, fontWeight = FontWeight.SemiBold)
                                Text("${contact.studentId} · ${contact.section}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (activeContacts.isEmpty()) EmptyState("Add active batch contacts before selecting recipients.")
                }
                2 -> {
                    Text("Only channels with official configuration should be used. WhatsApp and Messenger remain unavailable until a secure backend and opt-in flow are configured.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    channelNames.forEach { channel ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(channel in channels, { checked -> channels = if (checked) channels + channel else channels - channel })
                            Text(channel, Modifier.weight(1f))
                            Text(if (channel == "In-App") "Configured" else "Not configured",
                                color = if (channel == "In-App") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                3 -> {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(details)
                    Text(listOf(course, date, time).filter { it.isNotBlank() }.joinToString(" · "))
                    HorizontalDivider()
                    Text("Recipients: ${recipientList.size} students", fontWeight = FontWeight.Bold)
                    Text("Channels: ${channels.joinToString(", ")}")
                    if (channels.any { it != "In-App" }) Text("External channels will report unavailable until official API configuration and recipient opt-in are provided.",
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }, confirmButton = {
        Button(enabled = when (step) { 0 -> title.isNotBlank(); 1 -> recipientList.isNotEmpty(); 2 -> channels.isNotEmpty(); else -> true }, onClick = {
            if (step < 3) step++ else {
                vm.publishBroadcast(type, title, details, course, date, time, mode, channels.toList(), recipientList, "Class Representative (CR)", false)
                dismiss()
            }
        }) { Text(if (step == 3) "Confirm & Publish" else "Next") }
    }, dismissButton = {
        TextButton(onClick = { if (step > 0) step-- else dismiss() }) { Text(if (step > 0) "Back" else "Cancel") }
    })
}

@Composable
private fun BroadcastHistoryDialog(vm: MainViewModel, history: List<BatchAnnouncementEntity>, contacts: List<StudentContactEntity>, dismiss: () -> Unit) {
    var selected by remember { mutableStateOf<BatchAnnouncementEntity?>(null) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Broadcast history") }, text = {
        Column(Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
            if (history.isEmpty()) EmptyState("No broadcasts published yet.")
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(history, key = { it.id }) { item ->
                    ListCard {
                        Column(Modifier.weight(1f)) {
                            Text(item.title, fontWeight = FontWeight.Bold)
                            Text("${item.createdAtMillis.asDateTime()} · ${item.recipientCount} recipients")
                            Text(item.channels.replace(",", " · "), style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { selected = item }) { Text("Details") }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = dismiss) { Text("Done") } })
    selected?.let { announcement ->
        val attempts by vm.deliveryAttempts(announcement.id).collectAsState(initial = emptyList())
        AlertDialog(onDismissRequest = { selected = null }, title = { Text("Delivery details") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(announcement.title, fontWeight = FontWeight.Bold)
                announcement.channels.split(",").forEach { channel ->
                    val result = attempts.filter { it.channel == channel.trim() }
                    Text("${channel.trim()}: ${result.count { it.status == "Successful" }}/${result.size} successful")
                }
                attempts.filter { it.status != "Successful" }.take(3).forEach {
                    Text("${contacts.firstOrNull { contact -> contact.id == it.contactId }?.studentName ?: "Student"} · ${it.channel}: ${it.message}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }, confirmButton = { TextButton(onClick = { selected = null }) { Text("Close") } })
    }
}

private fun Long.asDateTime(): String =
    SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault()).format(Date(this))
