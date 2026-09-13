package com.simulagamer.filedesk

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import java.io.File
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

private enum class ViewMode { DETAILS, GRID }
private enum class SortMode { NAME, DATE, TYPE, SIZE }
private data class ExplorerTab(val id: Int, val dir: DocumentFile)
private data class ClipboardState(val items: List<DocumentFile>, val cut: Boolean)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExplorerScreen(
    darkMode: Boolean,
    onToggleTheme: () -> Unit,
    incomingUri: Uri? = null,
    onIncomingHandled: () -> Unit = {},
    allFilesAccess: Boolean = false,
    onRequestAllFilesAccess: () -> Unit = {}
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val wideScreen = configuration.screenWidthDp >= 840
    val prefs = remember { context.getSharedPreferences("filedesk_v1", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val keyboardFocus = remember { FocusRequester() }

    val workspaceFile = remember {
        File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir,
            "FileDesk"
        ).apply {
            mkdirs()
            listOf("Documentos", "Imagens", "Vídeos", "Downloads", "Compactados", "Projetos").forEach { File(this, it).mkdirs() }
        }
    }
    val workspaceRoot = remember { DocumentFile.fromFile(workspaceFile) }
    val deviceRoot = remember(allFilesAccess) {
        if (allFilesAccess) DocumentFile.fromFile(Environment.getExternalStorageDirectory()) else workspaceRoot
    }
    var nextTabId by remember { mutableIntStateOf(1) }
    var tabs by remember { mutableStateOf<List<ExplorerTab>>(emptyList()) }
    var activeTabId by remember { mutableIntStateOf(0) }

    var backHistory by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var forwardHistory by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var files by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var selectedUris by remember { mutableStateOf<Set<String>>(emptySet()) }
    var clipboard by remember { mutableStateOf<ClipboardState?>(null) }
    var viewMode by remember { mutableStateOf(ViewMode.DETAILS) }
    var sortMode by remember { mutableStateOf(SortMode.NAME) }

    var showCreateFolder by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<DocumentFile?>(null) }
    var deleteTargets by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var propertiesTarget by remember { mutableStateOf<DocumentFile?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val currentDir = tabs.firstOrNull { it.id == activeTabId }?.dir

    fun replaceCurrentDir(dir: DocumentFile) {
        if (tabs.isEmpty()) {
            val id = nextTabId++
            tabs = listOf(ExplorerTab(id, dir))
            activeTabId = id
        } else {
            tabs = tabs.map { if (it.id == activeTabId) it.copy(dir = dir) else it }
        }
        selectedUris = emptySet()
        search = ""
    }

    fun navigateTo(dir: DocumentFile, pushHistory: Boolean = true) {
        currentDir?.let {
            if (pushHistory && it.uri != dir.uri) backHistory = backHistory + it
        }
        if (pushHistory) forwardHistory = emptyList()
        replaceCurrentDir(dir)
    }

    fun selectedFiles(): List<DocumentFile> =
        files.filter { selectedUris.contains(it.uri.toString()) }

    fun copySelection(cut: Boolean) {
        val chosen = selectedFiles()
        if (chosen.isNotEmpty()) clipboard = ClipboardState(chosen, cut)
    }

    fun requestDeleteSelection() {
        val chosen = selectedFiles()
        if (chosen.isNotEmpty()) deleteTargets = chosen
    }

    fun requestRenameSelection() {
        selectedFiles().singleOrNull()?.let { renameTarget = it }
    }

    fun requestProperties() {
        selectedFiles().singleOrNull()?.let { propertiesTarget = it }
    }

    fun pasteClipboard() {
        val destination = currentDir ?: return
        val clip = clipboard ?: return
        scope.launch {
            loading = true
            val result = withContext(Dispatchers.IO) {
                var allOk = true
                clip.items.forEach { source ->
                    val copied = copyDocument(context, source, destination)
                    if (!copied) allOk = false
                    if (copied && clip.cut) {
                        runCatching { source.delete() }.onFailure { allOk = false }
                    }
                }
                allOk
            }
            loading = false
            if (!result) errorMessage = "Alguns itens não puderam ser colados."
            if (clip.cut) clipboard = null
            refreshKey++
        }
    }

    val importPicker = rememberLauncherForActivityResult(OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            val destination = currentDir ?: workspaceRoot
            scope.launch {
                loading = true
                val imported = withContext(Dispatchers.IO) {
                    var ok = true
                    uris.forEach { uri ->
                        if (!importUri(context, uri, destination)) ok = false
                    }
                    ok
                }
                loading = false
                if (!imported) errorMessage = "Alguns arquivos não puderam ser importados."
                refreshKey++
            }
        }
    }

    LaunchedEffect(allFilesAccess) {
        if (tabs.isEmpty()) {
            replaceCurrentDir(deviceRoot)
        } else if (allFilesAccess && currentDir?.uri == workspaceRoot.uri) {
            replaceCurrentDir(deviceRoot)
        }
    }

    LaunchedEffect(incomingUri, allFilesAccess) {
        val uri = incomingUri ?: return@LaunchedEffect
        val target = if (allFilesAccess) {
            autoImportDestination(context, uri)
        } else {
            workspaceRoot.findFile("Compactados") ?: workspaceRoot
        }
        loading = true
        val ok = withContext(Dispatchers.IO) { importUri(context, uri, target) }
        loading = false
        if (ok) {
            navigateTo(target)
            refreshKey++
        } else {
            errorMessage = "Não foi possível importar o arquivo."
        }
        onIncomingHandled()
    }

    LaunchedEffect(currentDir?.uri, refreshKey) {
        val dir = currentDir ?: return@LaunchedEffect
        loading = true
        files = withContext(Dispatchers.IO) {
            runCatching { dir.listFiles().toList() }.getOrElse { emptyList() }
        }
        loading = false
        selectedUris = selectedUris.intersect(files.map { it.uri.toString() }.toSet())
    }

    val visibleFiles = remember(files, search, sortMode) {
        val filtered = if (search.isBlank()) files else files.filter {
            (it.name ?: "").contains(search, ignoreCase = true)
        }
        val itemComparator = when (sortMode) {
            SortMode.NAME -> compareBy<DocumentFile> { (it.name ?: "").lowercase(Locale.getDefault()) }
            SortMode.DATE -> compareByDescending<DocumentFile> { it.lastModified() }
            SortMode.TYPE -> compareBy<DocumentFile> { if (it.isDirectory) "" else it.type ?: "" }
                .thenBy { (it.name ?: "").lowercase(Locale.getDefault()) }
            SortMode.SIZE -> compareByDescending<DocumentFile> { if (it.isDirectory) -1L else it.length() }
        }
        filtered.sortedWith(
            compareByDescending<DocumentFile> { it.isDirectory }.then(itemComparator)
        )
    }

    fun extractArchive(file: DocumentFile, createFolder: Boolean = true) {
        val destination = currentDir ?: workspaceRoot
        scope.launch {
            loading = true
            val extracted = withContext(Dispatchers.IO) {
                extractZip(context, file, destination, createFolder)
            }
            loading = false
            if (extracted == null) errorMessage = "Não foi possível extrair este arquivo ZIP."
            refreshKey++
        }
    }

    fun compressSelection() {
        val chosen = selectedFiles()
        val destination = currentDir ?: workspaceRoot
        if (chosen.isEmpty()) return
        scope.launch {
            loading = true
            val created = withContext(Dispatchers.IO) {
                createZip(context, chosen, destination)
            }
            loading = false
            if (created == null) errorMessage = "Não foi possível criar o arquivo ZIP."
            else selectedUris = setOf(created.uri.toString())
            refreshKey++
        }
    }

    fun openEntry(file: DocumentFile) {
        if (file.isDirectory) {
            navigateTo(file)
        } else if (isZipFile(file)) {
            extractArchive(file, createFolder = true)
        } else {
            val openUri = if (file.uri.scheme == "file") {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    File(file.uri.path ?: return)
                )
            } else file.uri
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(openUri, file.type ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { context.startActivity(intent) }
                .onFailure { errorMessage = "Nenhum aplicativo disponível para abrir este arquivo." }
        }
    }

    fun openNewTab() {
        val dir = currentDir ?: workspaceRoot
        val id = nextTabId++
        tabs = tabs + ExplorerTab(id, dir)
        activeTabId = id
        backHistory = emptyList()
        forwardHistory = emptyList()
        selectedUris = emptySet()
    }

    fun closeTab(id: Int) {
        if (tabs.size <= 1) return
        val index = tabs.indexOfFirst { it.id == id }
        tabs = tabs.filterNot { it.id == id }
        if (activeTabId == id) {
            activeTabId = tabs[max(0, index - 1)].id
        }
        selectedUris = emptySet()
        backHistory = emptyList()
        forwardHistory = emptyList()
    }

    LaunchedEffect(Unit) {
        runCatching { keyboardFocus.requestFocus() }
    }

    val rootModifier = Modifier
        .fillMaxSize()
        .focusRequester(keyboardFocus)
        .focusable()
        .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when {
                event.isCtrlPressed && event.key == Key.A -> {
                    selectedUris = visibleFiles.map { it.uri.toString() }.toSet(); true
                }
                event.isCtrlPressed && event.key == Key.C -> {
                    copySelection(false); true
                }
                event.isCtrlPressed && event.key == Key.X -> {
                    copySelection(true); true
                }
                event.isCtrlPressed && event.key == Key.V -> {
                    pasteClipboard(); true
                }
                event.isCtrlPressed && event.key == Key.T -> {
                    openNewTab(); true
                }
                event.key == Key.Delete -> {
                    requestDeleteSelection(); true
                }
                event.key == Key.F2 -> {
                    requestRenameSelection(); true
                }
                event.key == Key.Enter -> {
                    selectedFiles().singleOrNull()?.let { openEntry(it) }
                    true
                }
                else -> false
            }
        }

    Column(modifier = rootModifier) {
        AppHeader(
            darkMode = darkMode,
            onToggleTheme = onToggleTheme,
            onImport = { importPicker.launch(arrayOf("*/*")) }
        )

        if (!allFilesAccess) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.FolderShared, null)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Para usar o FileDesk como explorador principal, permita acesso a todos os arquivos.",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.width(10.dp))
                    Button(onClick = onRequestAllFilesAccess) { Text("Permitir acesso") }
                }
            }
        }

        if (tabs.isNotEmpty()) {
            TabsBar(
                tabs = tabs,
                activeId = activeTabId,
                onActivate = {
                    activeTabId = it
                    selectedUris = emptySet()
                    backHistory = emptyList()
                    forwardHistory = emptyList()
                },
                onClose = ::closeTab,
                onNewTab = ::openNewTab
            )
        }

        NavigationBar(
            currentDir = currentDir,
            search = search,
            onSearchChange = { search = it },
            canBack = backHistory.isNotEmpty(),
            canForward = forwardHistory.isNotEmpty(),
            onBack = {
                val previous = backHistory.lastOrNull() ?: return@NavigationBar
                currentDir?.let { forwardHistory = forwardHistory + it }
                backHistory = backHistory.dropLast(1)
                replaceCurrentDir(previous)
            },
            onForward = {
                val next = forwardHistory.lastOrNull() ?: return@NavigationBar
                currentDir?.let { backHistory = backHistory + it }
                forwardHistory = forwardHistory.dropLast(1)
                replaceCurrentDir(next)
            },
            onUp = {
                currentDir?.parentFile?.let { navigateTo(it) }
            },
            onRefresh = { refreshKey++ }
        )

        if (currentDir == null) {
            StartScreen(onImport = { importPicker.launch(arrayOf("*/*")) })
        } else {
            Row(modifier = Modifier.weight(1f)) {
                if (wideScreen) {
                    Sidebar(
                        workspaceRoot = workspaceRoot,
                        deviceRoot = deviceRoot,
                        allFilesAccess = allFilesAccess,
                        currentDir = currentDir,
                        onNavigate = { navigateTo(it) }
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    CommandBar(
                        hasSelection = selectedUris.isNotEmpty(),
                        singleSelection = selectedUris.size == 1,
                        hasClipboard = clipboard?.items?.isNotEmpty() == true,
                        viewMode = viewMode,
                        onImport = { importPicker.launch(arrayOf("*/*")) },
                        onCreateFolder = { showCreateFolder = true },
                        onCut = { copySelection(true) },
                        onCopy = { copySelection(false) },
                        onPaste = ::pasteClipboard,
                        onRename = ::requestRenameSelection,
                        onDelete = ::requestDeleteSelection,
                        onProperties = ::requestProperties,
                        onCompress = ::compressSelection,
                        onToggleView = {
                            viewMode = if (viewMode == ViewMode.DETAILS) ViewMode.GRID else ViewMode.DETAILS
                        },
                        onSort = { showSortMenu = true }
                    )

                    if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

                    Box(modifier = Modifier.weight(1f)) {
                        if (viewMode == ViewMode.DETAILS) {
                            DetailsView(
                                context = context,
                                files = visibleFiles,
                                selectedUris = selectedUris,
                                onSelectionChange = { selectedUris = it },
                                onOpen = ::openEntry,
                                onCut = { clipboard = ClipboardState(listOf(it), true) },
                                onCopy = { clipboard = ClipboardState(listOf(it), false) },
                                onRename = { renameTarget = it },
                                onProperties = { propertiesTarget = it },
                                onDelete = { deleteTargets = listOf(it) },
                                onExtractZip = { extractArchive(it, createFolder = true) }
                            )
                        } else {
                            GridView(
                                files = visibleFiles,
                                selectedUris = selectedUris,
                                onSelectionChange = { selectedUris = it },
                                onOpen = ::openEntry,
                                onProperties = { propertiesTarget = it }
                            )
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            SortMode.values().forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(sortLabel(mode)) },
                                    onClick = { sortMode = mode; showSortMenu = false },
                                    leadingIcon = {
                                        if (sortMode == mode) Icon(Icons.Default.Check, null)
                                    }
                                )
                            }
                        }
                    }

                    StatusBar(
                        totalItems = visibleFiles.size,
                        selectedCount = selectedUris.size,
                        clipboard = clipboard
                    )
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
                if (currentDir?.createDirectory(name.trim()) == null) {
                    errorMessage = "Não foi possível criar a pasta."
                }
                refreshKey++
            }
        )
    }

    renameTarget?.let { target ->
        NameDialog(
            title = "Renomear",
            initialValue = target.name ?: "",
            confirmLabel = "Salvar",
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                renameTarget = null
                if (!target.renameTo(name.trim())) errorMessage = "Não foi possível renomear este item."
                refreshKey++
            }
        )
    }

    if (deleteTargets.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { deleteTargets = emptyList() },
            title = { Text("Excluir") },
            text = {
                Text(
                    if (deleteTargets.size == 1)
                        "Deseja excluir “${deleteTargets.first().name ?: "este item"}”?"
                    else "Deseja excluir ${deleteTargets.size} itens?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val targets = deleteTargets
                    deleteTargets = emptyList()
                    scope.launch {
                        loading = true
                        withContext(Dispatchers.IO) { targets.forEach { runCatching { it.delete() } } }
                        loading = false
                        selectedUris = emptySet()
                        refreshKey++
                    }
                }) { Text("Excluir") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargets = emptyList() }) { Text("Cancelar") }
            }
        )
    }

    propertiesTarget?.let { file ->
        PropertiesDialog(
            context = context,
            file = file,
            onDismiss = { propertiesTarget = null }
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
private fun AppHeader(
    darkMode: Boolean,
    onToggleTheme: () -> Unit,
    onImport: () -> Unit
) {
    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("FileDesk", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onToggleTheme) {
                Icon(if (darkMode) Icons.Default.LightMode else Icons.Default.DarkMode, "Alternar tema")
            }
            TextButton(onClick = onImport) {
                Icon(Icons.Default.FileDownload, null)
                Spacer(Modifier.width(6.dp))
                Text("Importar arquivos")
            }
        }
    }
}

@Composable
private fun TabsBar(
    tabs: List<ExplorerTab>,
    activeId: Int,
    onActivate: (Int) -> Unit,
    onClose: (Int) -> Unit,
    onNewTab: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { tab ->
            Surface(
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                color = if (tab.id == activeId) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f),
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 10.dp)
                ) {
                    Text(
                        tab.dir.name ?: "Pasta",
                        modifier = Modifier.widthIn(max = 160.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(onClick = { onActivate(tab.id) }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Tab, "Ativar aba", modifier = Modifier.size(18.dp))
                    }
                    if (tabs.size > 1) {
                        IconButton(onClick = { onClose(tab.id) }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.Close, "Fechar aba", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
        IconButton(onClick = onNewTab) { Icon(Icons.Default.Add, "Nova aba") }
    }
}

@Composable
private fun NavigationBar(
    currentDir: DocumentFile?,
    search: String,
    onSearchChange: (String) -> Unit,
    canBack: Boolean,
    canForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onUp: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, enabled = canBack) { Icon(Icons.Default.ArrowBack, "Voltar") }
        IconButton(onClick = onForward, enabled = canForward) { Icon(Icons.Default.ArrowForward, "Avançar") }
        IconButton(onClick = onUp, enabled = currentDir?.parentFile != null) { Icon(Icons.Default.ArrowUpward, "Subir") }
        IconButton(onClick = onRefresh, enabled = currentDir != null) { Icon(Icons.Default.Refresh, "Atualizar") }

        Surface(
            modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
            shape = RoundedCornerShape(6.dp),
            tonalElevation = 0.dp
        ) {
            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(explorerPathLabel(currentDir), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        if (currentDir != null) {
            OutlinedTextField(
                value = search,
                onValueChange = onSearchChange,
                modifier = Modifier.widthIn(min = 170.dp, max = 320.dp),
                singleLine = true,
                placeholder = { Text("Pesquisar") },
                leadingIcon = { Icon(Icons.Default.Search, null) }
            )
        }
    }
}

@Composable
private fun Sidebar(
    workspaceRoot: DocumentFile,
    deviceRoot: DocumentFile,
    allFilesAccess: Boolean,
    currentDir: DocumentFile,
    onNavigate: (DocumentFile) -> Unit
) {
    val quick = remember(workspaceRoot.uri, deviceRoot.uri, allFilesAccess) {
        if (allFilesAccess) {
            listOf(
                Triple("Downloads", Icons.Default.Download, File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath)),
                Triple("Documentos", Icons.Default.Description, File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).absolutePath)),
                Triple("Imagens", Icons.Default.Image, File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath)),
                Triple("Vídeos", Icons.Default.Movie, File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).absolutePath)),
                Triple("Música", Icons.Default.AudioFile, File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).absolutePath)),
                Triple("Compactados", Icons.Default.Archive, File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "FileDesk/Compactados")),
                Triple("Aplicativos", Icons.Default.Android, File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "FileDesk/Aplicativos"))
            ).map { (name, icon, file) ->
                file.mkdirs()
                Triple(name, icon, DocumentFile.fromFile(file))
            }
        } else {
            listOf(
                "Documentos" to Icons.Default.Description,
                "Imagens" to Icons.Default.Image,
                "Vídeos" to Icons.Default.Movie,
                "Downloads" to Icons.Default.Download,
                "Compactados" to Icons.Default.Archive,
                "Projetos" to Icons.Default.Work
            ).mapNotNull { (name, icon) -> workspaceRoot.findFile(name)?.let { Triple(name, icon, it) } }
        }
    }

    Surface(modifier = Modifier.width(220.dp).fillMaxHeight(), tonalElevation = 0.dp) {
        LazyColumn(modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp)) {
            item {
                NavigationDrawerItem(
                    label = { Text(if (allFilesAccess) "Este dispositivo" else "FileDesk") },
                    selected = deviceRoot.uri == currentDir.uri,
                    onClick = { onNavigate(deviceRoot) },
                    icon = { Icon(Icons.Default.Computer, null) }
                )
                if (allFilesAccess) {
                    NavigationDrawerItem(
                        label = { Text("Minhas pastas FileDesk") },
                        selected = workspaceRoot.uri == currentDir.uri,
                        onClick = { onNavigate(workspaceRoot) },
                        icon = { Icon(Icons.Default.FolderSpecial, null) }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (allFilesAccess) "ACESSO RÁPIDO" else "MINHAS PASTAS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
            items(quick) { (name, icon, dir) ->
                NavigationDrawerItem(
                    label = { Text(name) },
                    selected = dir.uri == currentDir.uri,
                    onClick = { onNavigate(dir) },
                    icon = { Icon(icon, null) }
                )
            }
            item {
                Spacer(Modifier.height(14.dp))
                Text(
                    "IMPORTAÇÃO",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                Text(
                    if (allFilesAccess)
                        "O FileDesk está lendo o armazenamento real do dispositivo. Arquivos abertos ou compartilhados com o app são direcionados automaticamente para a categoria correspondente."
                    else
                        "Arquivos externos só entram após você selecioná-los no Android.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun CommandBar(
    hasSelection: Boolean,
    singleSelection: Boolean,
    hasClipboard: Boolean,
    viewMode: ViewMode,
    onImport: () -> Unit,
    onCreateFolder: () -> Unit,
    onCut: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onProperties: () -> Unit,
    onCompress: () -> Unit,
    onToggleView: () -> Unit,
    onSort: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onImport) {
            Icon(Icons.Default.FileDownload, null); Spacer(Modifier.width(6.dp)); Text("Importar")
        }
        Spacer(Modifier.width(6.dp))
        TextButton(onClick = onCreateFolder) {
            Icon(Icons.Default.CreateNewFolder, null); Spacer(Modifier.width(6.dp)); Text("Nova pasta")
        }
        Spacer(Modifier.width(6.dp))
        TextButton(onClick = onCut, enabled = hasSelection) { Icon(Icons.Default.ContentCut, null); Spacer(Modifier.width(4.dp)); Text("Recortar") }
        TextButton(onClick = onCopy, enabled = hasSelection) { Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(4.dp)); Text("Copiar") }
        TextButton(onClick = onPaste, enabled = hasClipboard) { Icon(Icons.Default.ContentPaste, null); Spacer(Modifier.width(4.dp)); Text("Colar") }
        TextButton(onClick = onRename, enabled = singleSelection) { Icon(Icons.Default.DriveFileRenameOutline, null); Spacer(Modifier.width(4.dp)); Text("Renomear") }
        TextButton(onClick = onDelete, enabled = hasSelection) { Icon(Icons.Default.DeleteOutline, null); Spacer(Modifier.width(4.dp)); Text("Excluir") }
        TextButton(onClick = onProperties, enabled = singleSelection) { Icon(Icons.Default.Info, null); Spacer(Modifier.width(4.dp)); Text("Propriedades") }
        TextButton(onClick = onCompress, enabled = hasSelection) { Icon(Icons.Default.Archive, null); Spacer(Modifier.width(4.dp)); Text("Compactar ZIP") }
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onSort) { Icon(Icons.Default.Sort, null); Spacer(Modifier.width(4.dp)); Text("Classificar") }
        TextButton(onClick = onToggleView) {
            Icon(if (viewMode == ViewMode.DETAILS) Icons.Default.GridView else Icons.Default.ViewList, null)
            Spacer(Modifier.width(4.dp))
            Text(if (viewMode == ViewMode.DETAILS) "Grade" else "Detalhes")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailsView(
    context: Context,
    files: List<DocumentFile>,
    selectedUris: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    onOpen: (DocumentFile) -> Unit,
    onCut: (DocumentFile) -> Unit,
    onCopy: (DocumentFile) -> Unit,
    onRename: (DocumentFile) -> Unit,
    onProperties: (DocumentFile) -> Unit,
    onDelete: (DocumentFile) -> Unit,
    onExtractZip: (DocumentFile) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f))
                .padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            Spacer(Modifier.width(40.dp))
            Text("Nome", modifier = Modifier.weight(1.6f), fontWeight = FontWeight.SemiBold)
            Text("Data", modifier = Modifier.weight(.8f), fontWeight = FontWeight.SemiBold)
            Text("Tipo", modifier = Modifier.weight(.7f), fontWeight = FontWeight.SemiBold)
            Text("Tamanho", modifier = Modifier.weight(.55f), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(42.dp))
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(files, key = { it.uri.toString() }) { file ->
                var menu by remember { mutableStateOf(false) }
                val id = file.uri.toString()
                val selected = id in selectedUris
                Box {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f) else MaterialTheme.colorScheme.surface)
                            .combinedClickable(
                                onClick = {
                                    onSelectionChange(
                                        if (selected) selectedUris - id
                                        else if (selectedUris.isEmpty()) setOf(id)
                                        else selectedUris + id
                                    )
                                },
                                onDoubleClick = { onOpen(file) },
                                onLongClick = { menu = true }
                            )
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selected,
                            onCheckedChange = {
                                onSelectionChange(if (it) selectedUris + id else selectedUris - id)
                            }
                        )
                        Row(modifier = Modifier.weight(1.6f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                fileDisplayIcon(file),
                                null,
                                tint = if (file.isDirectory || isZipFile(file)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(9.dp))
                            Text(file.name ?: "Sem nome", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(formatDate(file.lastModified()), modifier = Modifier.weight(.8f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        Text(fileType(file), modifier = Modifier.weight(.7f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (file.isDirectory) "—" else Formatter.formatShortFileSize(context, file.length()), modifier = Modifier.weight(.55f), style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Mais opções") }
                    }
                    HorizontalDivider(modifier = Modifier.align(Alignment.BottomCenter))

                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Abrir") }, onClick = { menu = false; onOpen(file) }, leadingIcon = { Icon(Icons.Default.OpenInNew, null) })
                        if (isZipFile(file)) {
                            DropdownMenuItem(
                                text = { Text("Extrair tudo") },
                                onClick = { menu = false; onExtractZip(file) },
                                leadingIcon = { Icon(Icons.Default.Unarchive, null) }
                            )
                        }
                        DropdownMenuItem(text = { Text("Recortar") }, onClick = { menu = false; onCut(file) }, leadingIcon = { Icon(Icons.Default.ContentCut, null) })
                        DropdownMenuItem(text = { Text("Copiar") }, onClick = { menu = false; onCopy(file) }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) })
                        DropdownMenuItem(text = { Text("Renomear") }, onClick = { menu = false; onRename(file) }, leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) })
                        DropdownMenuItem(text = { Text("Propriedades") }, onClick = { menu = false; onProperties(file) }, leadingIcon = { Icon(Icons.Default.Info, null) })
                        DropdownMenuItem(text = { Text("Excluir") }, onClick = { menu = false; onDelete(file) }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridView(
    files: List<DocumentFile>,
    selectedUris: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    onOpen: (DocumentFile) -> Unit,
    onProperties: (DocumentFile) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(140.dp),
        contentPadding = PaddingValues(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        gridItems(files, key = { it.uri.toString() }) { file ->
            val id = file.uri.toString()
            val selected = id in selectedUris
            Card(
                modifier = Modifier.height(128.dp).combinedClickable(
                    onClick = { onSelectionChange(if (selected) selectedUris - id else selectedUris + id) },
                    onDoubleClick = { onOpen(file) },
                    onLongClick = { onProperties(file) }
                ),
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)
                )
            ) {
                Box(Modifier.fillMaxSize().padding(10.dp)) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = {
                            onSelectionChange(if (it) selectedUris + id else selectedUris - id)
                        },
                        modifier = Modifier.align(Alignment.TopEnd)
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.align(Alignment.Center)) {
                        Icon(
                            fileDisplayIcon(file),
                            null,
                            modifier = Modifier.size(44.dp),
                            tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(file.name ?: "Sem nome", maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBar(
    totalItems: Int,
    selectedCount: Int,
    clipboard: ClipboardState?
) {
    Surface(tonalElevation = 2.dp) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${totalItems} itens", style = MaterialTheme.typography.bodySmall)
            if (selectedCount > 0) {
                Spacer(Modifier.width(14.dp))
                Text("${selectedCount} selecionado(s)", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.weight(1f))
            clipboard?.let {
                Text(
                    "${it.items.size} item(ns) ${if (it.cut) "recortado(s)" else "copiado(s)"}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun StartScreen(onImport: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("Bem-vindo ao FileDesk", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text("Seu espaço FileDesk está pronto. Importe somente os arquivos externos que desejar.")
            Spacer(Modifier.height(18.dp))
            Button(onClick = onImport) {
                Icon(Icons.Default.FileDownload, null); Spacer(Modifier.width(7.dp)); Text("Importar arquivos")
            }
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
        text = { OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true, label = { Text("Nome") }) },
        confirmButton = { TextButton(onClick = { if (value.isNotBlank()) onConfirm(value) }, enabled = value.isNotBlank()) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun PropertiesDialog(
    context: Context,
    file: DocumentFile,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Propriedades") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(file.name ?: "Sem nome", fontWeight = FontWeight.SemiBold)
                HorizontalDivider()
                PropertyLine("Tipo", fileType(file))
                PropertyLine("Tamanho", if (file.isDirectory) "Pasta" else Formatter.formatFileSize(context, file.length()))
                PropertyLine("Modificado", formatDate(file.lastModified()))
                PropertyLine("Leitura", if (file.canRead()) "Sim" else "Não")
                PropertyLine("Gravação", if (file.canWrite()) "Sim" else "Não")
                Text(file.uri.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
    )
}

@Composable
private fun PropertyLine(label: String, value: String) {
    Row {
        Text(label, modifier = Modifier.width(90.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value)
    }
}

private fun importUri(context: Context, sourceUri: Uri, destination: DocumentFile): Boolean {
    return runCatching {
        val resolver = context.contentResolver
        val sourceDoc = DocumentFile.fromSingleUri(context, sourceUri)
        val originalName = sourceDoc?.name ?: "arquivo"
        val mime = resolver.getType(sourceUri) ?: "application/octet-stream"
        val name = uniqueName(destination, originalName)
        val target = destination.createFile(mime, name) ?: return@runCatching false
        val input = resolver.openInputStream(sourceUri) ?: return@runCatching false
        val output = if (target.uri.scheme == "file") {
            java.io.FileOutputStream(File(target.uri.path ?: return@runCatching false))
        } else resolver.openOutputStream(target.uri) ?: return@runCatching false
        input.use { i -> output.use { o -> i.copyTo(o) } }
        true
    }.getOrDefault(false)
}

private suspend fun copyDocument(context: Context, source: DocumentFile, destination: DocumentFile): Boolean {
    return runCatching {
        if (source.isDirectory) {
            val folderName = uniqueName(destination, source.name ?: "Pasta")
            val newDir = destination.createDirectory(folderName) ?: return@runCatching false
            var ok = true
            for (child in source.listFiles()) {
                if (!copyDocument(context, child, newDir)) ok = false
            }
            ok
        } else {
            val originalName = source.name ?: "arquivo"
            val name = uniqueName(destination, originalName)
            val target = destination.createFile(source.type ?: "application/octet-stream", name) ?: return@runCatching false
            val input = if (source.uri.scheme == "file") {
                java.io.FileInputStream(File(source.uri.path ?: return@runCatching false))
            } else context.contentResolver.openInputStream(source.uri) ?: return@runCatching false
            val output = if (target.uri.scheme == "file") {
                java.io.FileOutputStream(File(target.uri.path ?: return@runCatching false))
            } else context.contentResolver.openOutputStream(target.uri) ?: return@runCatching false
            input.use { i -> output.use { o -> i.copyTo(o) } }
            true
        }
    }.getOrDefault(false)
}

private fun uniqueName(destination: DocumentFile, desired: String): String {
    if (destination.findFile(desired) == null) return desired
    val dot = desired.lastIndexOf('.')
    val base = if (dot > 0) desired.substring(0, dot) else desired
    val ext = if (dot > 0) desired.substring(dot) else ""
    var n = 2
    while (destination.findFile("${base} (${n})${ext}") != null) n++
    return "${base} (${n})${ext}"
}

private fun formatDate(timestamp: Long): String {
    if (timestamp <= 0L) return "—"
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))
}

private fun fileType(file: DocumentFile): String {
    if (file.isDirectory) return "Pasta"
    return file.type?.substringAfterLast('/')?.uppercase(Locale.getDefault()) ?: "Arquivo"
}

private fun sortLabel(mode: SortMode): String = when (mode) {
    SortMode.NAME -> "Nome"
    SortMode.DATE -> "Data de modificação"
    SortMode.TYPE -> "Tipo"
    SortMode.SIZE -> "Tamanho"
}

private fun quickLabel(name: String): String = when (name) {
    "Download" -> "Downloads"
    "Documents" -> "Documentos"
    "Pictures" -> "Imagens"
    "Movies" -> "Vídeos"
    "Music" -> "Música"
    else -> name
}


private fun autoImportDestination(context: Context, sourceUri: Uri): DocumentFile {
    val resolver = context.contentResolver
    val source = DocumentFile.fromSingleUri(context, sourceUri)
    val name = source?.name?.lowercase().orEmpty()
    val mime = resolver.getType(sourceUri).orEmpty()

    val dir = when {
        name.endsWith(".zip") || mime == "application/zip" || mime == "application/x-zip-compressed" ->
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "FileDesk/Compactados")
        name.endsWith(".apk") || mime == "application/vnd.android.package-archive" ->
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "FileDesk/Aplicativos")
        mime.startsWith("image/") ->
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "FileDesk")
        mime.startsWith("video/") ->
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "FileDesk")
        mime.startsWith("audio/") ->
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "FileDesk")
        mime.startsWith("text/") ||
            mime == "application/pdf" ||
            mime.contains("word") ||
            mime.contains("excel") ||
            mime.contains("spreadsheet") ||
            mime.contains("presentation") ->
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "FileDesk")
        else ->
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "FileDesk/Outros")
    }

    dir.mkdirs()
    return DocumentFile.fromFile(dir)
}


private fun explorerPathLabel(dir: DocumentFile?): String {
    if (dir == null) return "Este dispositivo"
    val raw = dir.uri.path ?: return dir.name ?: "Este dispositivo"
    val storage = Environment.getExternalStorageDirectory().absolutePath
    val clean = raw.removePrefix(storage).trim('/')
    if (clean.isBlank()) return "Este dispositivo"
    return "Este dispositivo  ›  " + clean.split('/').joinToString("  ›  ") { quickLabel(it) }
}

private fun fileDisplayIcon(file: DocumentFile) = when {
    file.isDirectory -> Icons.Default.Folder
    isZipFile(file) -> Icons.Default.Archive
    file.type?.startsWith("image/") == true -> Icons.Default.Image
    file.type?.startsWith("video/") == true -> Icons.Default.Movie
    file.type?.startsWith("audio/") == true -> Icons.Default.AudioFile
    file.type == "application/pdf" -> Icons.Default.PictureAsPdf
    file.name?.endsWith(".apk", ignoreCase = true) == true -> Icons.Default.Android
    else -> Icons.Default.InsertDriveFile
}
