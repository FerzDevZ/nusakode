package id.kodenusa.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.kodenusa.ui.Kode

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
    val interaksiTombol = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp).tekan(interaksiTombol),
        interactionSource = interaksiTombol,
        enabled = enabled && !loading,
        shape = RoundedCornerShape(KodenusaPalette.RADIUS_MEDIUM.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Kode.accent,
            contentColor = Kode.accentInk,
            disabledContainerColor = Kode.surfaceRaised,
            disabledContentColor = Kode.textDim,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = Kode.accentInk,
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
    // Tombol sekunder PUNYA wujud: latar naik satu tingkat + garis tepi. Dulu ini TextButton
    // polos sehingga "Impor .zip", "AI", "Simpan" terlihat seperti teks biasa — pengguna tidak
    // tahu bahwa baris itu bisa ditekan (temuan audit UX 0.9.8).
    val interaksiTombol = remember { MutableInteractionSource() }
    Box(
        modifier
            .height(48.dp)
            .tekan(interaksiTombol)
            .background(Kode.surfaceRaised, RoundedCornerShape(KodenusaPalette.RADIUS_SMALL.dp))
            .border(
                BorderStroke(1.dp, if (enabled) Kode.borderStrong else Kode.border),
                RoundedCornerShape(KodenusaPalette.RADIUS_SMALL.dp),
            )
            .clickable(interactionSource = interaksiTombol, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) Kode.text else Kode.textDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .background(Kode.surface, RoundedCornerShape(KodenusaPalette.RADIUS_MEDIUM.dp))
            .border(
                BorderStroke(1.dp, Kode.border),
                RoundedCornerShape(KodenusaPalette.RADIUS_MEDIUM.dp),
            )
            .padding(16.dp),
    ) {
        content()
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(color.copy(alpha = 0.14f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
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
        Text(label, style = MaterialTheme.typography.labelMedium, color = Kode.textDim)
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
                focusedBorderColor = Kode.accent,
                unfocusedBorderColor = Kode.borderStrong,
                errorBorderColor = Kode.error,
                focusedContainerColor = Kode.surfaceRaised,
                unfocusedContainerColor = Kode.surface,
                cursorColor = Kode.accent,
                focusedTextColor = Kode.text,
                unfocusedTextColor = Kode.text,
            ),
        )
        if (helper != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                helper,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) Kode.error else Kode.textDim,
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
        Text(title, style = MaterialTheme.typography.titleMedium, color = Kode.text)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = Kode.textDim,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        action?.invoke()
    }
}

/** Jenis pesan [Banner] — warnanya diambil dari token tema aktif. */
enum class JenisBanner { INFO, OK, PERINGATAN, GALAT }

/**
 * Pita pesan di atas konten. Menggantikan barisan Row+Text manual yang sebelumnya ditulis ulang
 * di banyak layar dengan gaya sedikit berbeda-beda.
 */
@Composable
fun Banner(jenis: JenisBanner, teks: String, modifier: Modifier = Modifier) {
    val warna = when (jenis) {
        JenisBanner.INFO -> Kode.info
        JenisBanner.OK -> Kode.ok
        JenisBanner.PERINGATAN -> Kode.warn
        JenisBanner.GALAT -> Kode.error
    }
    Row(
        modifier
            .fillMaxWidth()
            .background(warna.copy(alpha = 0.12f), RoundedCornerShape(KodenusaPalette.RADIUS_MEDIUM.dp))
            .border(BorderStroke(1.dp, warna.copy(alpha = 0.55f)), RoundedCornerShape(KodenusaPalette.RADIUS_MEDIUM.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(warna, CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(teks, style = MaterialTheme.typography.bodyMedium, color = Kode.text)
    }
}

/**
 * Baris pilihan bersekat. Satu pilihan aktif, diberi latar surfaceRaised + teks aksen —
 * dipakai layar Tema dan tempat lain yang sebelumnya memakai deretan tombol teks.
 */
@Composable
fun SegmentedControl(
    pilihan: List<String>,
    terpilih: Int,
    onPilih: (Int) -> Unit,
    modifier: Modifier = Modifier,
    testTagAwalan: String = "segmen",
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Kode.surface, RoundedCornerShape(KodenusaPalette.RADIUS_MEDIUM.dp))
            .border(BorderStroke(1.dp, Kode.border), RoundedCornerShape(KodenusaPalette.RADIUS_MEDIUM.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        pilihan.forEachIndexed { indeks, label ->
            val aktif = indeks == terpilih
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .background(
                        if (aktif) Kode.surfaceRaised else Color.Transparent,
                        RoundedCornerShape(KodenusaPalette.RADIUS_SMALL.dp),
                    )
                    .clickable { onPilih(indeks) }
                    .testTag("$testTagAwalan-$indeks"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (aktif) Kode.accent else Kode.textDim,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Tombol sekunder berikon: wujud sama dengan [GhostButton] tetapi isi ikon vektor.
 * [konten] wajib — ikon tanpa nama tidak bisa dibaca pembaca layar.
 */
@Composable
fun GhostIconButton(
    ikon: ImageVector,
    konten: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaksiTombol = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(48.dp)
            .tekan(interaksiTombol)
            .background(Kode.surfaceRaised, RoundedCornerShape(KodenusaPalette.RADIUS_SMALL.dp))
            .border(
                BorderStroke(1.dp, if (enabled) Kode.borderStrong else Kode.border),
                RoundedCornerShape(KodenusaPalette.RADIUS_SMALL.dp),
            )
            .clickable(interactionSource = interaksiTombol, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            ikon,
            contentDescription = konten,
            tint = if (enabled) Kode.text else Kode.textDim,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** Skala tekan 0,98 saat ditekan — mikrointeraksi standar tombol KodeNusa (150 ms). */
fun Modifier.tekan(interaksi: MutableInteractionSource): Modifier = composed {
    val ditekan by interaksi.collectIsPressedAsState()
    val skala by animateFloatAsState(if (ditekan) 0.98f else 1f, animationSpec = tween(150), label = "tekan")
    this.graphicsLayer { scaleX = skala; scaleY = skala }
}
