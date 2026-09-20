package com.ecosystem.research.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ecosystem.research.core.model.ReadingStatus

@Composable
fun ReadingStatusBadge(
    status: ReadingStatus,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor) = when (status) {
        ReadingStatus.INBOX -> Pair(Color(0xFFF1F5F9), Color(0xFF475569))
        ReadingStatus.TO_SCREEN -> Pair(Color(0xFFFEF3C7), Color(0xFF92400E))
        ReadingStatus.TO_READ -> Pair(Color(0xFFE0F2FE), Color(0xFF0369A1))
        ReadingStatus.READING -> Pair(Color(0xFFEDE9FE), Color(0xFF6D28D9))
        ReadingStatus.READ -> Pair(Color(0xFFDCFCE7), Color(0xFF15803D))
        ReadingStatus.KEY_PAPER -> Pair(Color(0xFFFCE7F3), Color(0xFFBE185D))
        ReadingStatus.ARCHIVED -> Pair(Color(0xFFF3F4F6), Color(0xFF9CA3AF))
        ReadingStatus.EXCLUDED -> Pair(Color(0xFFFEE2E2), Color(0xFFB91C1C))
    }

    Text(
        text = status.name.replace("_", " "),
        color = textColor,
        fontSize = 11.sp,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier
            .background(bgColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
fun PreprintBadge(
    isPreprint: Boolean,
    preprintSource: String? = null,
    modifier: Modifier = Modifier
) {
    if (!isPreprint) return

    val label = if (preprintSource != null) {
        "⚠️ Preprint ($preprintSource)"
    } else {
        "⚠️ Preprint (not peer-reviewed)"
    }

    Text(
        text = label,
        color = Color(0xFF991B1B), // Dark Red
        fontSize = 11.sp,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier
            .background(Color(0xFFFEE2E2), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

