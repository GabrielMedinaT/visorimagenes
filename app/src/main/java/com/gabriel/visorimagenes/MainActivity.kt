package com.gabriel.visorimagenes

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import coil.compose.rememberAsyncImagePainter
import com.google.accompanist.permissions.*
import android.os.Environment
import java.io.File

data class ImageItem(val uri: Uri, val displayName: String)

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
            Manifest.permission.READ_MEDIA_IMAGES
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
            ImageGrid(folderName = folderName)
        }
        permissionState.shouldShowRationale -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Column(
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                ) {
                    Text("Se requiere permiso para acceder a las imágenes.")
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
fun ImageGrid(folderName: String) {
    val context = LocalContext.current
    var images by remember { mutableStateOf(listOf<ImageItem>()) }
    var selectedImage by remember { mutableStateOf<ImageItem?>(null) } // Imagen seleccionada
    var scaleFactor by remember { mutableStateOf(1f) } // Factor de escala

    // Cargar las imágenes al inicio
    LaunchedEffect(folderName) {
        images = getImagesFromFolder(context, folderName)
    }

    if (images.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Text("No se encontraron imágenes en la carpeta $folderName.")
        }
    } else {
        if (selectedImage != null) {
            // Mostrar la imagen seleccionada ampliada con slider
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((200 * scaleFactor).dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(selectedImage!!.uri),
                        contentDescription = selectedImage!!.displayName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Slider para ajustar el tamaño
                Spacer(modifier = Modifier.height(16.dp))
                Slider(
                    value = scaleFactor,
                    onValueChange = { scaleFactor = it },
                    valueRange = 0.5f..3f,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                // Botón para cerrar la vista ampliada
                FloatingActionButton(
                    onClick = { selectedImage = null },
                    modifier = Modifier.align(androidx.compose.ui.Alignment.End)
                ) {
                    Text("X")
                }
            }
        } else {
            // Mostrar el grid de imágenes
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 128.dp),
                contentPadding = PaddingValues(8.dp),
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(images) { image ->
                    ImageCard(imageItem = image, onClick = { selectedImage = image })
                }
            }
        }
    }
}

@Composable
fun ImageCard(imageItem: ImageItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick), // Manejar clic
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Image(
            painter = rememberAsyncImagePainter(imageItem.uri),
            contentDescription = imageItem.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
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

fun getImagesFromFolder(context: Context, folderName: String): List<ImageItem> {
    val images = mutableListOf<ImageItem>()
    val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.RELATIVE_PATH
    )
    val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
    val selectionArgs = arrayOf("%Pictures/$folderName/%")
    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

    val cursor = context.contentResolver.query(
        uri,
        projection,
        selection,
        selectionArgs,
        sortOrder
    )

    cursor?.use {
        val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val nameColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)

        while (it.moveToNext()) {
            val id = it.getLong(idColumn)
            val name = it.getString(nameColumn)
            val imageUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            images.add(ImageItem(uri = imageUri, displayName = name))
        }
    }

    return images
}
