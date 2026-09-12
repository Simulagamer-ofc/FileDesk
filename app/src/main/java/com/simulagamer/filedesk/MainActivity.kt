package com.simulagamer.filedesk

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FileDeskApp() }
    }
}

@Composable
private fun FileDeskApp() {
    var darkMode by remember { mutableStateOf(false) }
    MaterialTheme(colorScheme = if (darkMode) darkColorScheme() else lightColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            FileExplorer(darkMode = darkMode, onToggleTheme = { darkMode = !darkMode })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileExplorer(darkMode: Boolean, onToggleTheme: () -> Unit) {
    val context = LocalContext.current
    val wideScreen = LocalConfiguration.current.screenWidthDp >= 840
    val prefs = remember { context.getSharedPreferences("filedesk", Context.MODE_PRIVATE) }

    var rootUri by remember { mutableStateOf(prefs.getString("root_uri", null)?.let(Uri::parse)) }
    var currentDir by remember { mutableStateOf(rootUri?.let { DocumentFile.fromTreeUri(context, it) }) }
    var history by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var files by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<DocumentFile?>(null) }
    var renameTarget by remember { mutableStateOf<DocumentFile?>(null) }
    var deleteTarget by remember { mutableStateOf<DocumentFile?>(null) }
    var showCreateFolder by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val folderPicker = rememberLauncherForActivityResult(OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            prefs.edit().putString("root_uri", uri.toString()).apply()
            rootUri = uri
            currentDir = DocumentFile.fromTreeUri(context, uri)
            history = emptyList()
            refreshKey++
        }
    }

    LaunchedEffect(currentDir?.uri, refreshKey) {
        val dir = currentDir ?: return@LaunchedEffect
        loading = true
        files = withContext(Dispatchers.IO) {
            runCatching {
                dir.listFiles().sortedWith(
                    compareByDescending<DocumentFile> { it.isDirectory }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name ?: "" }
                )
            }.getOrElse {
                errorMessage = "Não foi possível abrir esta pasta."
                emptyList()
            }
        }
        loading = false
    }

    val visibleFiles = remember(files, search) {
        if (search.isBlank()) files else files.filter {
            (it.name ?: "").contains(search, ignoreCase = true)
        }
    }

    fun openEntry(file: DocumentFile) {
        if (file.isDirectory) {
            currentDir?.let { history = history + it }
            currentDir = file
            search = ""
            selected = null
        } else {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(file.uri, file.type ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { context.startActivity(intent) }
                .onFailure { errorMessage = "Nenhum aplicativo disponível para abrir este arquivo." }
        }
    }

    fun goBack() {
        val previous = history.lastOrNull() ?: return
        history = history.dropLast(1)
        currentDir = previous
        search = ""
        selected = null
    }

    Scaffold(
        topBar = {
            Surface(tonalElevation = 2.dp) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("FileDesk", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onToggleTheme) {
                            Icon(if (darkMode) Icons.Default.LightMode else Icons.Default.DarkMode, "Alternar tema")
                        }
                        FilledTonalButton(onClick = { folderPicker.launch(rootUri) }) {
                            Icon(Icons.Default.FolderOpen, null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (rootUri == null) "Escolher pasta" else "Trocar pasta")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { goBack() }, enabled = history.isNotEmpty()) {
                            Icon(Icons.Default.ArrowBack, "Voltar")
                        }
                        IconButton(
                            onClick = {
                                currentDir?.parentFile?.let {
                                    currentDir?.let { dir -> history = history + dir }
                                    currentDir = it
                                    search = ""
                                }
                            },
                            enabled = currentDir?.parentFile != null
                        ) { Icon(Icons.Default.ArrowUpward, "Subir") }
                        IconButton(onClick = { refreshKey++ }, enabled = currentDir != null) {
                            Icon(Icons.Default.Refresh, "Atualizar")
                        }

                        Surface(
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            shape = RoundedCornerShape(8.dp),
                            tonalElevation = 1.dp
                        ) {
                            Text(
                                currentDir?.name ?: "Nenhuma pasta selecionada",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (currentDir != null) {
                            OutlinedTextField(
                                value = search,
                                onValueChange = { search = it },
                                modifier = Modifier.widthIn(min = 180.dp, max = 300.dp),
                                singleLine = true,
                                placeholder = { Text("Pesquisar") },
                                leadingIcon = { Icon(Icons.Default.Search, null) }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        if (currentDir == null) {
            EmptyStart(
                modifier = Modifier.padding(innerPadding),
                onChooseFolder = { folderPicker.launch(null) }
            )
        } else {
            Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                if (wideScreen) {
                    Sidebar(
                        currentName = currentDir?.name ?: "Armazenamento",
                        onHome = {
                            rootUri?.let {
                                currentDir = DocumentFile.fromTreeUri(context, it)
                                history = emptyList()
                                search = ""
                            }
                        },
                        onChooseFolder = { folderPicker.launch(rootUri) }
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    CommandBar(
                        onCreateFolder = { showCreateFolder = true },
                        onRename = { selected?.let { renameTarget = it } },
                        onDelete = { selected?.let { deleteTarget = it } },
                        hasSelection = selected != null
                    )

                    if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    FileHeader()

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(visibleFiles, key = { it.uri.toString() }) { file ->
                            FileRow(
                                file = file,
                                selected = selected?.uri == file.uri,
                                onClick = { selected = file },
                                onDoubleClick = { openEntry(file) },
                                onOpen = { openEntry(file) },
                                onRename = { renameTarget = file },
                                onDelete = { deleteTarget = file }
                            )
                            HorizontalDivider()
                        }
                    }

                    Surface(tonalElevation = 2.dp) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${visibleFiles.size} itens", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.weight(1f))
                            selected?.let {
                                Text(it.name ?: "Item selecionado", style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateFolder) {
        NameDialog(
            title = "Nova pasta",
            initialValue = "Nova pasta",
            confirmLabel = "Criar",
            onDismiss = { showCreateFolder = false },
            onConfirm = { name ->
                showCreateFolder = false
                if (name.isNotBlank()) {
                    if (currentDir?.createDirectory(name.trim()) == null) {
                        errorMessage = "Não foi possível criar a pasta."
                    }
                    refreshKey++
                }
            }
        )
    }

    renameTarget?.let { target ->
        NameDialog(
            title = "Renomear",
            initialValue = target.name ?: "",
            confirmLabel = "Salvar",
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                renameTarget = null
                if (newName.isNotBlank()) {
                    if (!target.renameTo(newName.trim())) errorMessage = "Não foi possível renomear este item."
                    refreshKey++
                }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Excluir item") },
            text = { Text("Deseja excluir “${target.name ?: "este item"}”?") },
            confirmButton = {
                TextButton(onClick = {
                    if (!target.delete()) errorMessage = "Não foi possível excluir este item."
                    if (selected?.uri == target.uri) selected = null
                    deleteTarget = null
                    refreshKey++
                }) { Text("Excluir") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") }
            }
        )
    }

    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("FileDesk") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { errorMessage = null }) { Text("OK") } }
        )
    }
}

@Composable
private fun EmptyStart(modifier: Modifier = Modifier, onChooseFolder: () -> Unit) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(18.dp))
            Text("Bem-vindo ao FileDesk", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Escolha uma pasta do tablet para começar. O FileDesk manterá a autorização para os próximos acessos.",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(22.dp))
            Button(onClick = onChooseFolder) {
                Icon(Icons.Default.Folder, null)
                Spacer(Modifier.width(8.dp))
                Text("Escolher pasta")
            }
        }
    }
}

@Composable
private fun Sidebar(currentName: String, onHome: () -> Unit, onChooseFolder: () -> Unit) {
    Surface(modifier = Modifier.width(230.dp).fillMaxHeight(), tonalElevation = 1.dp) {
        Column(modifier = Modifier.padding(10.dp)) {
            NavigationDrawerItem(
                label = { Text("Início") }, selected = true, onClick = onHome,
                icon = { Icon(Icons.Default.Home, null) }
            )
            NavigationDrawerItem(
                label = { Text("Escolher local") }, selected = false, onClick = onChooseFolder,
                icon = { Icon(Icons.Default.FolderOpen, null) }
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "ESTE DISPOSITIVO", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp)
            )
            Spacer(Modifier.height(6.dp))
            NavigationDrawerItem(
                label = { Text(currentName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                selected = false, onClick = {},
                icon = { Icon(Icons.Default.Storage, null) }
            )
        }
    }
}

@Composable
private fun CommandBar(
    onCreateFolder: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    hasSelection: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalButton(onClick = onCreateFolder) {
            Icon(Icons.Default.CreateNewFolder, null)
            Spacer(Modifier.width(6.dp))
            Text("Nova pasta")
        }
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onRename, enabled = hasSelection) {
            Icon(Icons.Default.DriveFileRenameOutline, null)
            Spacer(Modifier.width(5.dp))
            Text("Renomear")
        }
        TextButton(onClick = onDelete, enabled = hasSelection) {
            Icon(Icons.Default.DeleteOutline, null)
            Spacer(Modifier.width(5.dp))
            Text("Excluir")
        }
    }
}

@Composable
private fun FileHeader() {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text("Nome", modifier = Modifier.weight(1.6f), fontWeight = FontWeight.SemiBold)
        Text("Data", modifier = Modifier.weight(0.8f), fontWeight = FontWeight.SemiBold)
        Text("Tipo", modifier = Modifier.weight(0.7f), fontWeight = FontWeight.SemiBold)
        Text("Tamanho", modifier = Modifier.weight(0.6f), fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    file: DocumentFile,
    selected: Boolean,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Box {
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(
                    if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    else MaterialTheme.colorScheme.surface
                )
                .combinedClickable(
                    onClick = onClick,
                    onDoubleClick = onDoubleClick,
                    onLongClick = { menuExpanded = true }
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1.6f), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                    null,
                    tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(10.dp))
                Text(file.name ?: "Sem nome", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            Text(formatDate(file.lastModified()), modifier = Modifier.weight(0.8f),
                style = MaterialTheme.typography.bodySmall, maxLines = 1)
            Text(
                if (file.isDirectory) "Pasta"
                else file.type?.substringAfterLast('/')?.uppercase(Locale.getDefault()) ?: "Arquivo",
                modifier = Modifier.weight(0.7f), style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                if (file.isDirectory) "—" else Formatter.formatShortFileSize(context, file.length()),
                modifier = Modifier.weight(0.6f), style = MaterialTheme.typography.bodySmall, maxLines = 1
            )
        }

        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text("Abrir") },
                onClick = { menuExpanded = false; onOpen() },
                leadingIcon = { Icon(Icons.Default.OpenInNew, null) }
            )
            DropdownMenuItem(
                text = { Text("Renomear") },
                onClick = { menuExpanded = false; onRename() },
                leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) }
            )
            DropdownMenuItem(
                text = { Text("Excluir") },
                onClick = { menuExpanded = false; onDelete() },
                leadingIcon = { Icon(Icons.Default.DeleteOutline, null) }
            )
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    initialValue: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true, label = { Text("Nome") })
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

private fun formatDate(timestamp: Long): String {
    if (timestamp <= 0L) return "—"
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))
}
