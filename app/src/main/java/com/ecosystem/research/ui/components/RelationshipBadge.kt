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
import com.ecosystem.research.core.model.EvidenceRelationship
import com.ecosystem.research.ui.theme.*

@Composable
fun RelationshipBadge(
    relationship: EvidenceRelationship,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, label) = when (relationship) {
        EvidenceRelationship.SUPPORTS -> Triple(SupportsGreenBg, SupportsGreen, "SUPPORTS")
        EvidenceRelationship.CONTRADICTS -> Triple(ContradictsRedBg, ContradictsRed, "CONTRADICTS")
        EvidenceRelationship.MIXED -> Triple(MixedAmberBg, MixedAmber, "MIXED")
        EvidenceRelationship.BACKGROUND -> Triple(Color(0xFFE2E8F0), Color(0xFF334155), "BACKGROUND")
        EvidenceRelationship.RELATED -> Triple(Color(0xFFE0F2FE), Color(0xFF0369A1), "RELATED")
        EvidenceRelationship.UNDETERMINED -> Triple(Color(0xFFF1F5F9), Color(0xFF64748B), "UNDETERMINED")
    }

    Text(
        text = label,
        color = textColor,
        fontSize = 11.sp,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier
            .background(bgColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
