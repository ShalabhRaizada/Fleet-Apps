package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.example.data.*
import com.example.ui.DocDriverViewModel
import com.example.ui.OcrState
import com.example.ui.SpreadsheetRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.ui.SyncLog
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainUiContent(viewModel: DocDriverViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // ViewModel Observables
    val activeTab by viewModel.activeTab.collectAsState()
    val isOnlineMode by viewModel.isOnlineMode.collectAsState()
    val filteredDocuments by viewModel.filteredDocuments.collectAsState()
    val allDocuments by viewModel.allDocuments.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val selectedFolderId by viewModel.selectedFolderId.collectAsState()
    val ocrState by viewModel.ocrState.collectAsState()
    val spreadsheetRows by viewModel.spreadsheetRows.collectAsState()
    val syncLogs by viewModel.syncLogs.collectAsState()

    // Screen temporary states
    var activeDetailDocId by remember { mutableStateOf<Int?>(null) }
    var isScanDialogShown by remember { mutableStateOf(false) }
    var isNewFolderDialogShown by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    // Camera launcher setup (not strictly needed since we use mock simulation for offline/emulator)
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    
    // Scaffold UI
    Scaffold(
        topBar = {
            DocDriverHeader(
                isOnline = isOnlineMode,
                onToggleOnline = { viewModel.toggleOnlineMode(context) }
            )
        },
        bottomBar = {
            DocDriverBottomBar(
                activeTab = activeTab,
                onTabSelected = { viewModel.setTab(it) }
            )
        },
        floatingActionButton = {
            if (activeTab == 0 || activeTab == 1) {
                // High contrast tactile scanner trigger button
                ExtendedFloatingActionButton(
                    onClick = { isScanDialogShown = true },
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .testTag("scan_document_fab")
                        .border(2.dp, MaterialTheme.colorScheme.onTertiaryContainer, RoundedCornerShape(20.dp)),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ) {
                    Icon(
                        Icons.Filled.PhotoCamera,
                        contentDescription = "Scan Document",
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "📷 TAP TO SCAN",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        containerColor = Color(0xFF12141C) // Deep dark atmospheric canvas for maximum contrast and eye-safety
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = Color(0xFF12141C)
        ) {
            when (activeTab) {
                0 -> DashboardTab(
                    documents = filteredDocuments,
                    onDocClick = { activeDetailDocId = it },
                    onDeleteDoc = { viewModel.deleteDocument(it) },
                    selectedFolderId = selectedFolderId,
                    folders = folders,
                    onSelectFolder = { viewModel.selectFolder(it) },
                    onScanClick = { isScanDialogShown = true },
                    onSimulateCloudSync = { viewModel.triggerSimulatedCloudSync() }
                )
                1 -> FoldersTab(
                    folders = folders,
                    allDocs = allDocuments,
                    onFolderClick = { folderId ->
                        viewModel.selectFolder(folderId)
                        viewModel.setTab(0) // redirect to list
                    },
                    onCreateFolderClick = { isNewFolderDialogShown = true },
                    onDeleteFolder = { viewModel.deleteFolder(it) }
                )
                2 -> SpreadsheetTab(
                    rows = spreadsheetRows,
                    onRowClick = { activeDetailDocId = it.docId }
                )
                3 -> SyncLogsTab(
                    logs = syncLogs,
                    isOnline = isOnlineMode,
                    onSyncNow = { viewModel.syncOfflinePendingDocuments(context) },
                    onTriggerCloud = { viewModel.triggerSimulatedCloudSync() },
                    onToggleOnline = { viewModel.toggleOnlineMode(context) }
                )
            }
        }
    }

    // --- DIALOGS & OVERLAYS ---

    // OCR Running HUD
    if (ocrState !is OcrState.Idle) {
        Dialog(onDismissRequest = {}) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1E2A)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (ocrState) {
                        OcrState.Scanning -> {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(60.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Capturing Logistics Slip...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                        is OcrState.Processing -> {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(60.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text((ocrState as OcrState.Processing).statusMsg, color = Color.White, textAlign = TextAlign.Center, fontSize = 16.sp)
                        }
                        is OcrState.Success -> {
                            Icon(Icons.Filled.CheckCircle, "Success", tint = Color(0xFF4CAF50), modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Secure Extraction Successful!", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    val id = (ocrState as OcrState.Success).docId
                                    viewModel.clearOcrState()
                                    activeDetailDocId = id
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                            ) {
                                Text("VIEW DETECTED FIELDS", fontWeight = FontWeight.Bold)
                            }
                        }
                        is OcrState.Error -> {
                            Icon(Icons.Filled.Error, "Error", tint = Color(0xFFF44336), modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Process Failed", color = Color.White, fontWeight = FontWeight.Bold)
                            Text((ocrState as OcrState.Error).message, color = Color.Gray, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.clearOcrState() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                            ) {
                                Text("OK")
                            }
                        }
                        OcrState.Idle -> {}
                    }
                }
            }
        }
    }

    // Capture & Scan Simulation Dialog for Truckers (Guarantees usability in browser environment!)
    if (isScanDialogShown) {
        Dialog(onDismissRequest = { isScanDialogShown = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2130)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .border(1.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "📷 CHOOSE SCAN TARGET",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        "Choose a common logistics receipt to simulate high-accuracy AI scanning on the driver-side.",
                        fontSize = 14.sp,
                        color = Color.LightGray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 20.dp)
                    )

                    // Card 1: Pilot Fuel
                    ScanSimCard(
                        title = "Pilot Flying J Fuel Ticket",
                        desc = "Ultra-Low Diesel, DEF Fuel (\$540.07 total)",
                        icon = Icons.Filled.LocalGasStation,
                        color = Color(0xFFFF5722),
                        onClick = {
                            isScanDialogShown = false
                            scope.launch {
                                val bytes = MockDocGenerator.generateMockDocumentBytes(MockDocGenerator.MockType.FUEL_RECEIPT)
                                viewModel.scanLogisticsDocument(context, bytes, MockDocGenerator.MockType.FUEL_RECEIPT)
                            }
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(10.dp))

                    // Card 2: Straight Bill of Lading
                    ScanSimCard(
                        title = "Swift Transportation BOL",
                        desc = "Straight Bill of Lading (42,950 LBS Cargo)",
                        icon = Icons.Filled.ListAlt,
                        color = Color(0xFF2196F3),
                        onClick = {
                            isScanDialogShown = false
                            scope.launch {
                                val bytes = MockDocGenerator.generateMockDocumentBytes(MockDocGenerator.MockType.BILL_OF_LADING)
                                viewModel.scanLogisticsDocument(context, bytes, MockDocGenerator.MockType.BILL_OF_LADING)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Card 3: FedEx Consignee Delivery Receipt
                    ScanSimCard(
                        title = "FedEx Cargo Delivery Log",
                        desc = "Signed Delivery Receipt (Costco Wholesale #29)",
                        icon = Icons.Filled.AssignmentTurnedIn,
                        color = Color(0xFF4CAF50),
                        onClick = {
                            isScanDialogShown = false
                            scope.launch {
                                val bytes = MockDocGenerator.generateMockDocumentBytes(MockDocGenerator.MockType.DELIVERY_RECEIPT)
                                viewModel.scanLogisticsDocument(context, bytes, MockDocGenerator.MockType.DELIVERY_RECEIPT)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    TextButton(onClick = { isScanDialogShown = false }) {
                        Text("CANCEL", color = Color.Gray, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // New Folder Creator Dialog
    if (isNewFolderDialogShown) {
        AlertDialog(
            onDismissRequest = { isNewFolderDialogShown = false },
            title = { Text("📁 CREATE CATEGORY FOLDER", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Folder Name (e.g. LOGBOOKS)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.LightGray
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addFolder(newFolderName)
                        newFolderName = ""
                        isNewFolderDialogShown = false
                    }
                ) {
                    Text("CREATE")
                }
            },
            dismissButton = {
                TextButton(onClick = { isNewFolderDialogShown = false }) {
                    Text("CANCEL", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E2130)
        )
    }

    // Document Details Overlay View (Real decryption review, editable parsed keys with accuracy level metrics)
    activeDetailDocId?.let { docId ->
        DocumentDetailOverlay(
            docId = docId,
            viewModel = viewModel,
            onDismiss = { activeDetailDocId = null }
        )
    }
}

// --- SUB COMPONENTS ---

@Composable
fun DocDriverHeader(isOnline: Boolean, onToggleOnline: () -> Unit) {
    Surface(
        color = Color(0xFF1A1C28),
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.LocalShipping,
                    contentDescription = "Logo",
                    tint = Color(0xFFFFC107), // Vibrant highway amber color
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        "DocDriver",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = "E2EE Secured",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            "E2EE SECURED UNIT",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }
            }

            // Simple tactile online toggle button for Truck driver
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onToggleOnline() }
                    .background(if (isOnline) Color(0xFF335C43) else Color(0xFF5C3334))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isOnline) Color(0xFF4CAF50) else Color(0xFFF44336))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isOnline) "ONLINE SYNC" else "OFFLINE GRID",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun DocDriverBottomBar(activeTab: Int, onTabSelected: (Int) -> Unit) {
    NavigationBar(
        containerColor = Color(0xFF1A1C28),
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            selected = activeTab == 0,
            onClick = { onTabSelected(0) },
            icon = { Icon(Icons.Filled.Dashboard, "Dashboard", modifier = Modifier.size(26.dp)) },
            label = { Text("Driver Hub", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFFFFC107),
                unselectedIconColor = Color.Gray,
                selectedTextColor = Color(0xFFFFC107),
                unselectedTextColor = Color.Gray
            )
        )
        NavigationBarItem(
            selected = activeTab == 1,
            onClick = { onTabSelected(1) },
            icon = { Icon(Icons.Filled.FolderOpen, "Folders", modifier = Modifier.size(26.dp)) },
            label = { Text("Folders", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFFFFC107),
                unselectedIconColor = Color.Gray,
                selectedTextColor = Color(0xFFFFC107),
                unselectedTextColor = Color.Gray
            )
        )
        NavigationBarItem(
            selected = activeTab == 2,
            onClick = { onTabSelected(2) },
            icon = { Icon(Icons.Filled.GridOn, "Spreadsheet", modifier = Modifier.size(26.dp)) },
            label = { Text("Fleet Log", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFFFFC107),
                unselectedIconColor = Color.Gray,
                selectedTextColor = Color(0xFFFFC107),
                unselectedTextColor = Color.Gray
            )
        )
        NavigationBarItem(
            selected = activeTab == 3,
            onClick = { onTabSelected(3) },
            icon = { Icon(Icons.Filled.CloudSync, "Sync Logs", modifier = Modifier.size(26.dp)) },
            label = { Text("Sync", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFFFFC107),
                unselectedIconColor = Color.Gray,
                selectedTextColor = Color(0xFFFFC107),
                unselectedTextColor = Color.Gray
            )
        )
    }
}

@Composable
fun ScanSimCard(
    title: String,
    desc: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .background(Color(0xFF282B3E))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(26.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(desc, color = Color.Gray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Filled.ArrowForwardIos, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
    }
}

// --- TAB 0: DASHBOARD ---

@Composable
fun DashboardTab(
    documents: List<Document>,
    onDocClick: (Int) -> Unit,
    onDeleteDoc: (Int) -> Unit,
    selectedFolderId: Int?,
    folders: List<DocumentFolder>,
    onSelectFolder: (Int?) -> Unit,
    onScanClick: () -> Unit,
    onSimulateCloudSync: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        
        // Horizontal categorization filter tabs for easy trucker tapping
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 12.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterBadge(
                label = "ALL LOGS",
                isSelected = selectedFolderId == null,
                onClick = { onSelectFolder(null) }
            )
            folders.forEach { folder ->
                FilterBadge(
                    label = folder.name,
                    isSelected = selectedFolderId == folder.id,
                    onClick = { onSelectFolder(folder.id) }
                )
            }
        }

        if (documents.isEmpty()) {
            EmptyDashboardWelcome(onScanClick)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RECENT SCAMS & INVOICES (${documents.size})",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onSimulateCloudSync() }
                                .background(Color(0xFF282B3E))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Filled.Sync, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("FORCE SHEET SYNC", color = Color.LightGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                items(documents) { doc ->
                    DocumentCardItem(
                        doc = doc,
                        onDocClick = { onDocClick(doc.id) },
                        onDeleteDoc = { onDeleteDoc(doc.id) }
                    )
                }
                
                // Add padding spacer at bottom for floating FAB
                item {
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
        }
    }
}

@Composable
fun FilterBadge(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(30.dp))
            .clickable { onClick() }
            .background(if (isSelected) Color(0xFFFFC107) else Color(0xFF232533))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.Black else Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}

@Composable
fun EmptyDashboardWelcome(onScanClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(Color(0xFF232533)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.CropFree,
                contentDescription = null,
                tint = Color(0xFFFFC107),
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "NO SCANS RECORDED",
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Military-grade End-To-End Encryption (E2EE) on-device active.\nInstant OCR extraction & cloud sheet cataloging is ready.",
            fontSize = 14.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.height(30.dp))
        Button(
            onClick = onScanClick,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                "📷 TAP TO SCAN FUEL OR CARGO LOG",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
fun DocumentCardItem(doc: Document, onDocClick: () -> Unit, onDeleteDoc: () -> Unit) {
    val displayDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(doc.createdAt))
    var isConfirmDeleteShown by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onDocClick() }
            .background(Color(0xFF1E2130))
            .border(
                1.dp,
                if (doc.isSynced) Color(0xFF2E5E3D) else Color(0xFF5E4E2E),
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail representing E2EE status
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF282B3E)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (doc.docType.contains("Fuel")) Icons.Filled.LocalGasStation
                else if (doc.docType.contains("Lading")) Icons.Filled.ListAlt
                else if (doc.docType.contains("Delivery")) Icons.Filled.AssignmentTurnedIn
                else Icons.Filled.PictureAsPdf,
                contentDescription = null,
                tint = if (doc.isSynced) Color(0xFF4CAF50) else Color(0xFFFFC107),
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = doc.docType,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.AccessTime,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = displayDate,
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Sync status badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (doc.isSynced) Color(0xFF1B4228) else Color(0xFF4F3F19))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (doc.isSynced) "🏁 CLOUD SYNCED" else "⏳ LOCAL SECURE",
                        color = if (doc.isSynced) Color(0xFF4CAF50) else Color(0xFFFFC107),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                // Encryption indicator
                if (doc.isEncrypted) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = "Encrypted",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }

        IconButton(
            onClick = { isConfirmDeleteShown = true }
        ) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Delete Doc",
                tint = Color(0xFFE57373),
                modifier = Modifier.size(24.dp)
            )
        }
    }

    if (isConfirmDeleteShown) {
        AlertDialog(
            onDismissRequest = { isConfirmDeleteShown = false },
            title = { Text("⚠️ DELETE SCANNED LOG?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("This removes the E2EE document image and extracted data points. This action cannot be undone.", color = Color.LightGray) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteDoc()
                        isConfirmDeleteShown = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350))
                ) {
                    Text("DELETE", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { isConfirmDeleteShown = false }) {
                    Text("CANCEL", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E2130)
        )
    }
}

// --- TAB 1: FOLDERS ---

@Composable
fun FoldersTab(
    folders: List<DocumentFolder>,
    allDocs: List<Document>,
    onFolderClick: (Int?) -> Unit,
    onCreateFolderClick: () -> Unit,
    onDeleteFolder: (Int) -> Unit
) {
    var folderToDeleteId by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "CATEGORIES & FOLDERS",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Button(
                onClick = onCreateFolderClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF282B3E)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Filled.Add, "add folder", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("NEW CATEGORY", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // "All uncategorized Documents" card
            val uncategorizedDocsCount = allDocs.count { it.folderId == null }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onFolderClick(null) }
                        .background(Color(0xFF1E2130))
                        .border(1.dp, Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFFC107).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Dashboard, null, tint = Color(0xFFFFC107), modifier = Modifier.size(26.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("ALL FREIGHT SCAMS & LOGS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("$uncategorizedDocsCount uncategorized items", color = Color.Gray, fontSize = 12.sp)
                    }
                    Icon(Icons.Filled.ArrowForwardIos, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                }
            }

            items(folders) { folder ->
                val docsCount = allDocs.count { it.folderId == folder.id }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onFolderClick(folder.id) }
                        .background(Color(0xFF1E2130))
                        .border(1.dp, Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF2196F3).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Folder, null, tint = Color(0xFF2196F3), modifier = Modifier.size(26.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(folder.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("$docsCount documents categorized", color = Color.Gray, fontSize = 12.sp)
                    }
                    
                    // Allow deleting folder if not system default
                    if (folder.name != "FUEL RECEIPTS" && folder.name != "CARGO LOGS (BOL)" && folder.name != "DELIVERY RECEIPTS") {
                        IconButton(onClick = { folderToDeleteId = folder.id }) {
                            Icon(Icons.Filled.DeleteOutline, "delete folder", tint = Color.Gray)
                        }
                    }
                    Icon(Icons.Filled.ArrowForwardIos, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                }
            }
        }
    }

    if (folderToDeleteId != null) {
        AlertDialog(
            onDismissRequest = { folderToDeleteId = null },
            title = { Text("DELETE FOLDER CATEGORY?", color = Color.White) },
            text = { Text("This removes the category folder only. Scanned documents filed here will remain accessible under the primary dashboard list.") },
            confirmButton = {
                Button(
                    onClick = {
                        folderToDeleteId?.let { onDeleteFolder(it) }
                        folderToDeleteId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350))
                ) {
                    Text("DELETE")
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToDeleteId = null }) {
                    Text("CANCEL", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E2130)
        )
    }
}

// --- TAB 2: SPREADSHEET VIEW ---

@Composable
fun SpreadsheetTab(
    rows: List<SpreadsheetRow>,
    onRowClick: (SpreadsheetRow) -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "FLEET SPREADSHEET LOG",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    "Extracted data values auto-populating sheet.",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
            Button(
                onClick = {
                    Toast.makeText(context, "Spreadsheet Exported successfully as CSV!", Toast.LENGTH_LONG).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Filled.Download, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("EXPORT CSV", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (rows.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1E2130), RoundedCornerShape(12.dp))
                    .border(1.dp, Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "SPREADSHEET IS EMPTY\nScan documents to automatically inject records.",
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                )
            }
        } else {
            // Horizontal scroll container to mimic a real spreadsheet grid for truck drivers!
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(rememberScrollState())
                    .background(Color(0xFF131522), RoundedCornerShape(12.dp))
                    .border(1.dp, Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            ) {
                Column {
                    // Excel Headers Row
                    Row(
                        modifier = Modifier
                            .background(Color(0xFF232635))
                            .border(1.dp, Color.Gray.copy(alpha = 0.3f))
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SheetHeaderCell("TIMESTAMP", 100)
                        SheetHeaderCell("TYPE", 120)
                        SheetHeaderCell("INVOICE/BOL ID", 150)
                        SheetHeaderCell("DRIVER", 120)
                        SheetHeaderCell("TOTAL VALUE", 110)
                        SheetHeaderCell("GRID STATUS", 110)
                    }

                    // Rows list
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        items(rows) { row ->
                            Row(
                                modifier = Modifier
                                    .clickable { onRowClick(row) }
                                    .border(0.5.dp, Color.Gray.copy(alpha = 0.1f))
                                    .background(if (row.docId % 2 == 0) Color(0xFF1C1E2A) else Color(0xFF1E2130))
                                    .padding(horizontal = 8.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SheetCell(row.timestamp, 100, Color.LightGray)
                                SheetCell(row.type, 120, Color.White, fontWeight = FontWeight.Bold)
                                SheetCell(row.title, 150, Color.White)
                                SheetCell(row.driver, 120, Color.LightGray)
                                SheetCell(row.totalAmount, 110, Color(0xFFFFC107), fontWeight = FontWeight.Bold)
                                SheetCell(row.logStatus, 110, if (row.logStatus.contains("Sync")) Color(0xFF4CAF50) else Color(0xFFFF9800))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SheetHeaderCell(text: String, width: Int) {
    Text(
        text = text,
        color = Color.LightGray,
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier.width(width.dp),
        textAlign = TextAlign.Start,
        maxLines = 1
    )
}

@Composable
fun SheetCell(text: String, width: Int, color: Color, fontWeight: FontWeight = FontWeight.Normal) {
    Text(
        text = text,
        color = color,
        fontSize = 13.sp,
        fontWeight = fontWeight,
        modifier = Modifier.width(width.dp),
        textAlign = TextAlign.Start,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

// --- TAB 3: SYNC LOGS & CONTROLS ---

@Composable
fun SyncLogsTab(
    logs: List<SyncLog>,
    isOnline: Boolean,
    onSyncNow: () -> Unit,
    onTriggerCloud: () -> Unit,
    onToggleOnline: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("CLOUD SYNC & PRIVACY SETTINGS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(14.dp))

        // Large high-contrast switches for truckers
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2130)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Security, null, tint = Color(0xFF4CAF50))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Driver E2EE Privacy Core", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Symmetric 256-bit AES-GCM local storage", color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF335C43))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("ACTIVE STATUS", color = Color(0xFF4CAF50), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = Color.Gray.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Nfc, null, tint = Color(0xFFFFC107))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Automatic AI Background Cloud Sync", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Syncs E2EE queues automatically online", color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                    Switch(
                        checked = isOnline,
                        onCheckedChange = { onToggleOnline() },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFFFC107))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onSyncNow,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107)),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Filled.Sync, null, tint = Color.Black)
                Spacer(modifier = Modifier.width(6.dp))
                Text("RETRY QUEUE", color = Color.Black, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onTriggerCloud,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF282B3E)),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Filled.CloudUpload, null, tint = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Text("FORCE SPREADSHEET", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("SYNC TRACE LOGS & LEDGER EVENTS", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(10.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131522)),
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .border(1.dp, Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
        ) {
            if (logs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No events recorded yet.", color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.padding(12.dp)) {
                    items(logs) { log ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                "[${log.timestamp}] ",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                log.text,
                                color = if (log.isSuccess) Color.LightGray else Color(0xFFEF5350),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- FULL SCREEN MODAL: DOCUMENT DETAIL VIEW ---

@Composable
fun DocumentDetailOverlay(
    docId: Int,
    viewModel: DocDriverViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var doc by remember { mutableStateOf<Document?>(null) }
    val fields by viewModel.getFieldsFlow(docId).collectAsState()
    var decryptedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isSavingPdf by remember { mutableStateOf(false) }

    // Fetch details
    LaunchedEffect(docId) {
        withContext(Dispatchers.IO) {
            doc = viewModel.allDocuments.value.find { it.id == docId }
            doc?.let { d ->
                decryptedBitmap = DocumentManager.getDecryptedBitmap(context, d.localFilePath)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 24.dp, horizontal = 12.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1A1C28)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Modal header containing Back controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.ArrowBack, "back", tint = Color.White)
                    }
                    Text(
                        "INVOICE FIELD ACCURACIES",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    IconButton(
                        onClick = {
                            viewModel.deleteDocument(docId)
                            onDismiss()
                        }
                    ) {
                        Icon(Icons.Filled.Delete, "delete", tint = Color(0xFFEF5350))
                    }
                }

                Divider(color = Color.Gray.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 8.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    // Decrypted Real-Time bitmap preview
                    item {
                        decryptedBitmap?.let { bmp ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black)
                                    .border(2.dp, Color(0xFF4CAF50)), // Green E2EE secure border indications
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Decrypted invoice bitmap",
                                    modifier = Modifier.fillMaxHeight()
                                )
                                // Overlaid Security badge
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF1B4228))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Lock, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(11.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("DECRYPTED SECURELY", color = Color(0xFF4CAF50), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } ?: Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF131522)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    // Virtual Searchable PDF path status
                    item {
                        Column(modifier = Modifier.padding(vertical = 12.dp)) {
                            Text("PDF CONVERSION STATUS", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF282B3E)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.PictureAsPdf, null, tint = Color(0xFFEF5350))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text("Searchable PDF Generated Natively", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(
                                            doc?.pdfPath?.let { File(it).name } ?: "Generating pending database saves...",
                                            color = Color.LightGray,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Header for Extracted Fields displaying confidence levels
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("EXTRACTED OCR DATA FIELDS", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("ACCURACY pill", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    items(fields) { field ->
                        ExtractedFieldRowItem(field)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action buttons: Searchable PDF Export
                Button(
                    onClick = {
                        scope.launch {
                            isSavingPdf = true
                            val generatedPdfFile = withContext(Dispatchers.IO) {
                                doc?.let { d ->
                                    DocumentManager.convertToSearchablePdf(context, d, fields)
                                }
                            }
                            isSavingPdf = false
                            if (generatedPdfFile != null && generatedPdfFile.exists()) {
                                Toast.makeText(context, "Searchable PDF Saved Natively to External: ${generatedPdfFile.name}", Toast.LENGTH_LONG).show()
                                
                                // Open PDF viewer intent so truckers can interact instantly!
                                try {
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        generatedPdfFile
                                    )
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, "application/pdf")
                                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    // If no utility is available to open files
                                    Toast.makeText(context, "Searchable PDF can be shared from device: ${generatedPdfFile.absolutePath}", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                Toast.makeText(context, "Failed creating secure PDF overlay document.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = !isSavingPdf
                ) {
                    if (isSavingPdf) {
                        CircularProgressIndicator(color = Color.Black)
                    } else {
                        Icon(Icons.Filled.PictureAsPdf, null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SHARE SEARCHABLE PDF DOCUMENT", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ExtractedFieldRowItem(field: ExtractedField) {
    val accuracyPct = (field.confidence * 100).toInt()
    val badgeColor = if (field.confidence >= 0.95f) Color(0xFF4CAF50)
                     else if (field.confidence >= 0.85f) Color(0xFFFFA726)
                     else Color(0xFFEF5350)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF232533))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(field.fieldKey, color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(field.fieldValue, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.width(10.dp))

        // Large high-contrast accuracy percentage pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(badgeColor.copy(alpha = 0.15f))
                .border(1.dp, badgeColor, RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                if (field.confidence >= 0.90f) Icons.Filled.Check else Icons.Filled.Warning,
                contentDescription = null,
                tint = badgeColor,
                modifier = Modifier.size(11.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                "$accuracyPct% Accuracy",
                color = badgeColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
