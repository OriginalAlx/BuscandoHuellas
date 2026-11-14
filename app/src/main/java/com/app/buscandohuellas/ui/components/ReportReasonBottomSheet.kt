package com.app.buscandohuellas.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.buscandohuellas.domain.model.ReportReason
import com.app.buscandohuellas.ui.theme.BrandColors

/**
 * Bottom sheet para reportar una publicación.
 *
 * La selección del motivo y el texto libre viven en el ViewModel; este
 * composable solo refleja el estado y notifica las interacciones.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportReasonBottomSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
    selectedReason: ReportReason?,
    onReasonSelected: (ReportReason) -> Unit,
    customReasonText: String,
    onCustomReasonChanged: (String) -> Unit,
    isSubmitting: Boolean
) {
    if (!isVisible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val canSubmit = selectedReason != null &&
        (selectedReason != ReportReason.OTHER || customReasonText.isNotBlank()) &&
        !isSubmitting

    ModalBottomSheet(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        sheetState = sheetState,
        containerColor = BrandColors.Surface,
        contentWindowInsets = { WindowInsets.safeDrawing }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Reportar publicación",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = BrandColors.TextPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Cuéntanos por qué quieres reportar esta publicación.",
                style = MaterialTheme.typography.bodyMedium,
                color = BrandColors.TextSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Column(modifier = Modifier.selectableGroup()) {
                ReportReason.entries.forEach { reason ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedReason == reason,
                                enabled = !isSubmitting,
                                role = Role.RadioButton,
                                onClick = { onReasonSelected(reason) }
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedReason == reason,
                            onClick = null,
                            enabled = !isSubmitting
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = reason.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            color = BrandColors.TextPrimary
                        )
                    }
                }
            }

            if (selectedReason == ReportReason.OTHER) {
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = customReasonText,
                    onValueChange = onCustomReasonChanged,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSubmitting,
                    label = { Text("Detalle del motivo *") },
                    placeholder = { Text("Describe brevemente el motivo...") },
                    minLines = 3,
                    maxLines = 5,
                    supportingText = {
                        Text(text = "${customReasonText.length}/500")
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onSubmit,
                enabled = canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Enviando...")
                } else {
                    Text(
                        text = "Enviar reporte",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
