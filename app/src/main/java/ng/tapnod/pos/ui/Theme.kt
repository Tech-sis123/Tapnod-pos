package ng.tapnod.pos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ng.tapnod.pos.R

/** Swiss palette: paper ground, black ink, one lime accent, one signal red. */
object TP {
    val Ground = Color(0xFFE8E8E3)
    val Paper = Color(0xFFFFFFFF)
    val Soft = Color(0xFFF4F4F0)
    val Line = Color(0xFFD9D9D3)
    val Ink = Color(0xFF0B0B0B)
    val InkRaised = Color(0xFF242424)
    val Mute = Color(0xFF66665F)
    val InkMute = Color(0xFFA3A39C)
    val Lime = Color(0xFFC8F031)
    val Red = Color(0xFFE4002B)
    val White = Color(0xFFFFFFFF)
}

val GAP: Dp = 8.dp
val TileShape = RoundedCornerShape(22.dp)

@OptIn(ExperimentalTextApi::class)
val Inter = FontFamily(
    Font(R.font.inter_variable, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.inter_variable, weight = FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.inter_variable, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.inter_variable, weight = FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.inter_variable, weight = FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
    Font(R.font.inter_variable, weight = FontWeight.Black, variationSettings = FontVariation.Settings(FontVariation.weight(900)))
)

@Composable
fun TapnodTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = TP.Ink,
            onPrimary = TP.Lime,
            secondary = TP.Lime,
            onSecondary = TP.Ink,
            background = TP.Ground,
            onBackground = TP.Ink,
            surface = TP.Paper,
            onSurface = TP.Ink,
            error = TP.Red
        )
    ) {
        ProvideTextStyle(TextStyle(fontFamily = Inter, color = TP.Ink)) {
            content()
        }
    }
}

/** One bento cell. Every screen is built from these on a strict 8dp grid. */
@Composable
fun Tile(
    modifier: Modifier = Modifier,
    color: Color = TP.Paper,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    padding: Dp = 18.dp,
    content: @Composable BoxScope.() -> Unit
) {
    var m = modifier.clip(TileShape).background(color)
    if (onClick != null) {
        m = m.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
    }
    Box(m.padding(padding), content = content)
}

/** Small uppercase label that sits in a tile's corner. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = TP.Mute) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.4.sp,
        maxLines = 1
    )
}

@Composable
fun BrandMark(modifier: Modifier = Modifier, dark: Boolean = true) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(TP.Lime)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "tapnod",
            color = if (dark) TP.White else TP.Ink,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.6).sp
        )
    }
}
