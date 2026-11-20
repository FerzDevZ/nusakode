package id.kodenusa.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Komponen dasar KodeNusa. Semua tombol minimal punya keadaan normal, ditekan, nonaktif, dan
 * memuat — bukan hanya warna yang berubah karena kursor.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled && !loading,
        shape = RoundedCornerShape(KodenusaPalette.RADIUS_MEDIUM.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(KodenusaPalette.ACCENT),
            contentColor = Color(KodenusaPalette.ACCENT_INK),
            disabledContainerColor = Color(KodenusaPalette.SURFACE_RAISED),
            disabledContentColor = Color(KodenusaPalette.TEXT_DIM),
        ),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = Color(KodenusaPalette.ACCENT_INK),
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier.height(48.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) Color(KodenusaPalette.TEXT) else Color(KodenusaPalette.TEXT_DIM),
        )
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .background(Color(KodenusaPalette.SURFACE), RoundedCornerShape(KodenusaPalette.RADIUS_MEDIUM.dp))
            .border(
                BorderStroke(1.dp, Color(KodenusaPalette.BORDER)),
                RoundedCornerShape(KodenusaPalette.RADIUS_MEDIUM.dp),
            )
            .padding(16.dp),
    ) {
        content()
    }
}

@Composable
fun Pill(text: String, color: Int, modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(Color(color).copy(alpha = 0.14f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(Color(color), CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = Color(color))
    }
}

@Composable
fun KodeField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    helper: String? = null,
    isError: Boolean = false,
    singleLine: Boolean = true,
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color(KodenusaPalette.TEXT_DIM))
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = singleLine,
            isError = isError,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = JetBrainsMono, fontSize = 15.sp),
            keyboardOptions = KeyboardOptions(imeAction = if (singleLine) ImeAction.Done else ImeAction.Default),
            shape = RoundedCornerShape(KodenusaPalette.RADIUS_SMALL.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(KodenusaPalette.ACCENT),
                unfocusedBorderColor = Color(KodenusaPalette.BORDER_STRONG),
                errorBorderColor = Color(KodenusaPalette.ERROR),
                focusedContainerColor = Color(KodenusaPalette.SURFACE_RAISED),
                unfocusedContainerColor = Color(KodenusaPalette.SURFACE),
                cursorColor = Color(KodenusaPalette.ACCENT),
                focusedTextColor = Color(KodenusaPalette.TEXT),
                unfocusedTextColor = Color(KodenusaPalette.TEXT),
            ),
        )
        if (helper != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                helper,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) Color(KodenusaPalette.ERROR) else Color(KodenusaPalette.TEXT_DIM),
            )
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Color(KodenusaPalette.TEXT))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(KodenusaPalette.TEXT_DIM),
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        action?.invoke()
    }
}
