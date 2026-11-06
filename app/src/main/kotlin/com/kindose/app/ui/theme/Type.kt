package com.kindose.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.kindose.app.R

/**
 * Authentic Bundled Google Sans Display & Google Sans Font Families (`res/font/`).
 * Eliminates any fallback to default Android Roboto on emulators or non-Pixel devices.
 */
val GoogleSansDisplayFamily = FontFamily(
    Font(R.font.google_sans_display_regular, weight = FontWeight.Normal),
    Font(R.font.google_sans_display_medium, weight = FontWeight.Medium),
    Font(R.font.google_sans_display_medium, weight = FontWeight.SemiBold),
    Font(R.font.google_sans_display_bold, weight = FontWeight.Bold),
    Font(R.font.google_sans_display_bold, weight = FontWeight.ExtraBold)
)

val GoogleSansTextFamily = FontFamily(
    Font(R.font.google_sans_regular, weight = FontWeight.Normal),
    Font(R.font.google_sans_medium, weight = FontWeight.Medium),
    Font(R.font.google_sans_medium, weight = FontWeight.SemiBold),
    Font(R.font.google_sans_bold, weight = FontWeight.Bold),
    Font(R.font.google_sans_bold, weight = FontWeight.ExtraBold)
)

val GoogleSansMonoFamily = FontFamily(
    Font(R.font.google_sans_code, weight = FontWeight.Normal),
    Font(R.font.google_sans_code, weight = FontWeight.Medium)
)

/**
 * Material 3 Expressive Typography Scale using authentic Google Sans Display for hero headers/numbers
 * and Google Sans for crisp, high-legibility body copy and controls.
 */
val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = GoogleSansDisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 54.sp,
        lineHeight = 60.sp,
        letterSpacing = (-1.0).sp
    ),
    displayMedium = TextStyle(
        fontFamily = GoogleSansDisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        lineHeight = 50.sp,
        letterSpacing = (-0.8).sp
    ),
    displaySmall = TextStyle(
        fontFamily = GoogleSansDisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = GoogleSansDisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.4).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = GoogleSansDisplayFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.2).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = GoogleSansDisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.1).sp
    ),
    titleLarge = TextStyle(
        fontFamily = GoogleSansDisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.1).sp
    ),
    titleMedium = TextStyle(
        fontFamily = GoogleSansTextFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontFamily = GoogleSansTextFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = GoogleSansTextFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 25.sp,
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = GoogleSansTextFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp
    ),
    bodySmall = TextStyle(
        fontFamily = GoogleSansTextFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp
    ),
    labelLarge = TextStyle(
        fontFamily = GoogleSansTextFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    labelMedium = TextStyle(
        fontFamily = GoogleSansTextFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp
    ),
    labelSmall = TextStyle(
        fontFamily = GoogleSansTextFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.3.sp
    )
)
