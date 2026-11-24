package com.app.buscandohuellas.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.app.buscandohuellas.domain.model.Message
import com.app.buscandohuellas.domain.model.MessageType
import com.app.buscandohuellas.ui.theme.BrandColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

/**
 * Burbuja de mensaje reutilizable que renderiza texto, imagen o ubicación
 * según [Message.messageType], más una variante temporal mientras se sube una
 * imagen (con spinner superpuesto).
 */
@Composable
fun MessageBubble(
    message: Message,
    isMine: Boolean,
    senderLabel: String
) {
    val bubbleColor = if (isMine) BrandColors.Primary else BrandColors.BubbleIncoming
    val textColor = if (isMine) BrandColors.OnPrimary else BrandColors.TextPrimary
    val shape = if (isMine) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        Text(
            text = senderLabel,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = BrandColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )

        Surface(
            color = bubbleColor,
            contentColor = textColor,
            shape = shape,
            modifier = Modifier.widthIn(max = if (message.messageType == MessageType.TEXT) 320.dp else 280.dp)
        ) {
            when (message.messageType) {
                MessageType.TEXT -> TextBubbleContent(
                    text = message.text,
                    color = textColor
                )

                MessageType.IMAGE -> ImageBubbleContent(
                    imageUrl = message.imageUrl,
                    color = textColor
                )

                MessageType.LOCATION -> LocationBubbleContent(
                    message = message,
                    color = textColor
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = formatMessageTime(message.sentAt),
            fontSize = 10.sp,
            color = BrandColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

/** Burbuja temporal con la miniatura de la imagen y un spinner superpuesto. */
@Composable
fun PendingImageBubble(
    imageUri: Uri?,
    senderLabel: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        Text(
            text = senderLabel,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = BrandColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )

        Surface(
            color = BrandColors.Primary,
            contentColor = BrandColors.OnPrimary,
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 200.dp, height = 180.dp)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(BrandColors.PrimaryLight),
                contentAlignment = Alignment.Center
            ) {
                if (imageUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Imagen enviando",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                CircularProgressIndicator(
                    color = BrandColors.OnPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "Enviando...",
            fontSize = 10.sp,
            color = BrandColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun TextBubbleContent(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = color,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
    )
}

@Composable
private fun ImageBubbleContent(imageUrl: String?, color: androidx.compose.ui.graphics.Color) {
    var showFullscreen by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (!imageUrl.isNullOrBlank()) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(imageUrl)
                .crossfade(true)
                .build(),
            contentDescription = "Foto compartida",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .widthIn(max = 240.dp)
                .heightIn(max = 300.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(BrandColors.BubbleIncoming)
                .clickable { showFullscreen = true }
        )
    } else {
        Text(
            text = "📷 Foto no disponible",
            color = color,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }

    if (showFullscreen && !imageUrl.isNullOrBlank()) {
        Dialog(
            onDismissRequest = { showFullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Black)
                    .clickable { showFullscreen = false },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(imageUrl).crossfade(true).build(),
                    contentDescription = "Foto en pantalla completa",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                TextButton(
                    onClick = { showFullscreen = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Text("Cerrar", color = androidx.compose.ui.graphics.Color.White)
                }
            }
        }
    }
}

@Composable
private fun LocationBubbleContent(
    message: Message,
    color: androidx.compose.ui.graphics.Color
) {
    val context = LocalContext.current
    val lat = message.latitude
    val lon = message.longitude

    Column(modifier = Modifier.padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Place,
                contentDescription = null,
                tint = BrandColors.Primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Ubicación compartida",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Text(
                    text = message.locationLabel ?: "Ubicación sin etiqueta",
                    fontSize = 12.sp,
                    color = color.copy(alpha = 0.85f),
                    textAlign = TextAlign.Start
                )
            }
        }

        if (lat != null && lon != null) {
            Spacer(modifier = Modifier.height(10.dp))
            TextButton(
                onClick = {
                    try {
                        val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon")
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    } catch (_: Exception) {
                        // No hay app de mapas instalada; no hacemos nada.
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Ver en mapa", color = color, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun formatMessageTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(TIME_FORMATTER)
