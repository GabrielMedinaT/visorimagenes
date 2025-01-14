package com.gabriel.visorimagenes

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.rememberAsyncImagePainter
import com.google.accompanist.permissions.*
import android.os.Environment
import java.io.File

data class MediaItem(val uri: Uri, val displayName: String, val isVideo: Boolean)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Asegúrate de que el directorio MyImages exista
        ensureMyImagesDirectoryExists()

        // Define el nombre de la carpeta
        val folderName = "MyImages"

        setContent {
            MaterialTheme {
                // Solicita permiso y muestra la UI principal
                PermissionRequestScreen(folderName = folderName)
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionRequestScreen(folderName: String) {
    val sdkVersion = android.os.Build.VERSION.SDK_INT

    // Define los permisos a solicitar según la versión de Android
    val permissions = if (sdkVersion >= android.os.Build.VERSION_CODES.TIRAMISU) {
        listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO
        )
    } else {
        listOf(
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
    }

    val permissionState = rememberMultiplePermissionsState(permissions = permissions)
    val hasAllPermissions = permissionState.allPermissionsGranted

    LaunchedEffect(Unit) {
        if (!hasAllPermissions) {
            permissionState.launchMultiplePermissionRequest()
        }
    }

    when {
        hasAllPermissions -> {
            MediaGrid(folderName = folderName)
        }
        permissionState.shouldShowRationale -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Column(
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                ) {
                    Text("Se requiere permiso para acceder a los medios.")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { permissionState.launchMultiplePermissionRequest() }) {
                        Text("Solicitar Permiso")
                    }
                }
            }
        }
        else -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Column(
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                ) {
                    Text("Permiso denegado. Ve a Configuración para habilitarlo.")
                }
            }
        }
    }
}

@Composable
fun MediaGrid(folderName: String) {
    val context = LocalContext.current
    var mediaList by remember { mutableStateOf(listOf<MediaItem>()) }
    var selectedMediaIndex by remember { mutableStateOf<Int?>(null) }

    // Cargar medios al inicio
    LaunchedEffect(folderName) {
        mediaList = getMediaFromFolder(context, folderName)
    }

    if (mediaList.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Text("No se encontraron medios en la carpeta $folderName.")
        }
    } else {
        if (selectedMediaIndex != null) {
            // Mostrar carrusel con medios seleccionados
            MediaCarousel(
                mediaList = mediaList,
                startIndex = selectedMediaIndex!!,
                onClose = { selectedMediaIndex = null }
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 128.dp),
                contentPadding = PaddingValues(8.dp),
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(mediaList) { media ->
                    MediaCard(mediaItem = media, onClick = { selectedMediaIndex = mediaList.indexOf(media) })
                }
            }
        }
    }
}

@Composable
fun MediaCard(mediaItem: MediaItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        if (mediaItem.isVideo) {
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    painter = rememberAsyncImagePainter(mediaItem.uri),
                    contentDescription = mediaItem.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Text(
                    text = "Video",
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.align(androidx.compose.ui.Alignment.BottomStart)
                )
            }
        } else {
            Image(
                painter = rememberAsyncImagePainter(mediaItem.uri),
                contentDescription = mediaItem.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun MediaCarousel(mediaList: List<MediaItem>, startIndex: Int, onClose: () -> Unit) {
    var currentIndex by remember { mutableStateOf(startIndex) }
    var scaleFactor by remember { mutableStateOf(1f) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (mediaList[currentIndex].isVideo) {
                VideoPlayer(
                    uri = mediaList[currentIndex].uri,
                    onClose = onClose
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((300 * scaleFactor).dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(mediaList[currentIndex].uri),
                            contentDescription = mediaList[currentIndex].displayName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Slider(
                        value = scaleFactor,
                        onValueChange = { scaleFactor = it },
                        valueRange = 0.5f..3f,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }

        LazyRow(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(mediaList.size) { index ->
                val media = mediaList[index]
                MediaThumbnail(
                    mediaItem = media,
                    isSelected = index == currentIndex,
                    onClick = { currentIndex = index }
                )
            }
        }

        Button(
            onClick = onClose,
            modifier = Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally).padding(16.dp)
        ) {
            Text("Cerrar")
        }
    }
}

@Composable
fun MediaThumbnail(mediaItem: MediaItem, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .size(100.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 8.dp else 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
        )
    ) {
        if (mediaItem.isVideo) {
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    painter = rememberAsyncImagePainter(mediaItem.uri),
                    contentDescription = mediaItem.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Text(
                    text = "Video",
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.align(androidx.compose.ui.Alignment.BottomStart)
                )
            }
        } else {
            Image(
                painter = rememberAsyncImagePainter(mediaItem.uri),
                contentDescription = mediaItem.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun VideoPlayer(uri: Uri, onClose: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { context ->
            VideoView(context).apply {
                setVideoURI(uri)
                setMediaController(MediaController(context).apply {
                    setAnchorView(this@apply)
                })
                start()
            }
        }, modifier = Modifier.fillMaxSize())

        FloatingActionButton(
            onClick = onClose,
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Text("X")
        }
    }
}

fun ensureMyImagesDirectoryExists(): File {
    val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
    val myImagesDir = File(picturesDir, "MyImages")

    if (!myImagesDir.exists()) {
        myImagesDir.mkdirs()
    }

    return myImagesDir
}

fun getMediaFromFolder(context: Context, folderName: String): List<MediaItem> {
    val mediaList = mutableListOf<MediaItem>()

    // Consultar imágenes
    val imageUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val videoUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

    val projection = arrayOf(
        MediaStore.MediaColumns._ID,
        MediaStore.MediaColumns.DISPLAY_NAME,
        MediaStore.MediaColumns.RELATIVE_PATH
    )

    val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
    val selectionArgs = arrayOf("%Pictures/$folderName/%")
    val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

    // Consultar imágenes
    context.contentResolver.query(imageUri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val name = cursor.getString(nameColumn)
            val uri = ContentUris.withAppendedId(imageUri, id)
            mediaList.add(MediaItem(uri, name, isVideo = false))
        }
    }

    // Consultar videos
    context.contentResolver.query(videoUri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val name = cursor.getString(nameColumn)
            val uri = ContentUris.withAppendedId(videoUri, id)
            mediaList.add(MediaItem(uri, name, isVideo = true))
        }
    }

    return mediaList
}
