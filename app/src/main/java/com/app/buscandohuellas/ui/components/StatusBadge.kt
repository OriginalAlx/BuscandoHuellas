package com.app.buscandohuellas.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.buscandohuellas.domain.model.ReportStatus

/**
 * Badge compartido para los tres estados de un reporte.
 *
 * Usa los colores del enum [ReportStatus] y anima la transición cuando el
 * estado cambia (por ejemplo, tras una actualización optimista).
 */
@Composable
fun StatusBadge(
    status: ReportStatus,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = status.backgroundColor,
        label = "statusBackgroundColor"
    )
    val textColor by animateColorAsState(
        targetValue = status.textColor,
        label = "statusTextColor"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(backgroundColor)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.6.sp,
            color = textColor
        )
    }
}
