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
import com.ecosystem.research.core.model.OriginType
import com.ecosystem.research.core.model.ProvenanceRecord
import com.ecosystem.research.core.model.VerificationState
import com.ecosystem.research.ui.theme.*

@Composable
fun ProvenanceBadge(
    provenance: ProvenanceRecord,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, label) = when {
        provenance.originType == OriginType.AI_EXTRACTED || provenance.originType == OriginType.AI_INFERRED -> {
            if (provenance.verificationState == VerificationState.CONFIRMED) {
                Triple(SupportsGreenBg, SupportsGreen, "AI Confirmed")
            } else {
                Triple(AiExtractedPurpleBg, AiExtractedPurple, "AI (Unverified)")
            }
        }
        provenance.originType == OriginType.DIRECT_EXCERPT -> {
            Triple(Color(0xFFE0F2FE), Color(0xFF0369A1), "Direct Excerpt")
        }
        provenance.verificationState == VerificationState.CONFIRMED -> {
            Triple(SupportsGreenBg, SupportsGreen, "Verified")
        }
        else -> {
            Triple(Color(0xFFF1F5F9), Color(0xFF475569), "Metadata")
        }
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
