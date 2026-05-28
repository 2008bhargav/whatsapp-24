package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = SecureTeal,
    secondary = IndigoBlue,
    tertiary = BrightCyan,
    background = ObsidianBlack,
    surface = CarbonGray,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    onBackground = androidx.compose.ui.graphics.Color.White,
    onSurface = androidx.compose.ui.graphics.Color.White,
    outline = BorderSteel
  )

private val LightColorScheme = DarkColorScheme // Default to premium dark as it fits the messaging theme best

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Default to a gorgeous dark theme for the app
  dynamicColor: Boolean = false, // Always enforce our stunning branding
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
