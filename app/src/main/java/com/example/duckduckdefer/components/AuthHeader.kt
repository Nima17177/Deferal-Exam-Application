package com.example.duckduckdefer.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable
fun AuthHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Text(text = title, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = modifier)
    Text(text = subtitle, fontSize = 14.sp)
}
