package fr.acinq.phoenix.android

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// primary for testnet
val horizon = Color(0xff91b4d1)

// primary for mainnet light / success color for light theme
val applegreen = Color(0xff50b338)

// primary for mainnet dark / success color for dark theme
val green = Color(0xff1ac486)

// alternative primary for mainnet
val purple = Color(0xff5741d9)

val red500 = Color(0xffd03d33)
val red300 = Color(0xffc76d6d)
val red50 = Color(0xfff9e9ec)

val white = Color.White
val black = Color.Black

val gray900 = Color(0xff2b313e)
val gray800 = Color(0xff3e4556)
val gray700 = Color(0xff4e586c)
val gray600 = Color(0xff5f6b83)
val gray500 = Color(0xff6e7a94)
val gray400 = Color(0xff838da4)
val gray300 = Color(0xff99a2b6)
val gray200 = Color(0xffb5bccc)
val gray100 = Color(0xffd1d7e3)
val gray50 = Color(0xffedeef6)

private val LightColorPalette = lightColors(
    // primary
    primary = horizon,
    primaryVariant = horizon,
    onPrimary = white,
    // secondary = primary
    secondary = horizon,
    onSecondary = white,
    // app background
    background = white,
    onBackground = gray900,
    // components background
    surface = white,
    onSurface = gray900,
    // errors
    error = red300,
    onError = white,
)

private val DarkColorPalette = darkColors(
    // primary
    primary = horizon,
    primaryVariant = horizon,
    onPrimary = white,
    // secondary = primary
    secondary = horizon,
    onSecondary = white,
    // app background
    background = gray900,
    onBackground = gray100,
    // components background
    surface = gray900,
    onSurface = gray100,
    // errors
    error = red500,
    onError = red50,
)

// Set of Material typography styles to start with
val typography = Typography(
    body1 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    )
)

val shapes = Shapes(
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

@Composable
fun PhoenixAndroidTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable() () -> Unit) {
    MaterialTheme(
        colors = if (darkTheme) DarkColorPalette else LightColorPalette,
        typography = typography,
        shapes = shapes,
        content = content
    )
}

@Composable
fun errorColor(): Color = if (isSystemInDarkTheme()) red500 else red300

@Composable
fun successColor(): Color = if (isSystemInDarkTheme()) green else applegreen

@Composable
fun whiteLowOp(): Color = Color(0x33ffffff)