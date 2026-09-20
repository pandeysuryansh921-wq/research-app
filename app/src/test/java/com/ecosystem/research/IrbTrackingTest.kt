package com.ecosystem.research

import com.ecosystem.research.core.model.IrbProtocol
import com.ecosystem.research.core.model.IrbStatus
import org.junit.Assert.*
import org.junit.Test

class IrbTrackingTest {

    @Test
    fun testActiveProtocolAlert() {
        val now = System.currentTimeMillis()
        val proto = IrbProtocol(
            projectId = "p1",
            protocolNumber = "IRB-2024-001",
            institution = "Harvard IRB",
            title = "Clinical Genomics Trial",
            status = IrbStatus.APPROVED,
            approvalDate = now - (30L * 24L * 60L * 60L * 1000L),
            expirationDate = now + (180L * 24L * 60L * 60L * 1000L) // 180 days in future
        )

        val alert = proto.getComplianceAlert(currentTime = now)
        assertTrue(alert.startsWith("ACTIVE"))
        assertTrue(alert.contains("days remaining"))
    }

    @Test
    fun testExpiringSoonProtocolAlert() {
        val now = System.currentTimeMillis()
        val proto = IrbProtocol(
            projectId = "p1",
            protocolNumber = "IRB-2024-002",
            institution = "MIT COUHES",
            title = "Biometric Sensor Study",
            status = IrbStatus.APPROVED,
            expirationDate = now + (15L * 24L * 60L * 60L * 1000L) // 15 days in future
        )

        val alert = proto.getComplianceAlert(currentTime = now)
        assertTrue(alert.startsWith("EXPIRING_SOON"))
        assertTrue(alert.contains("15 days"))
    }

    @Test
    fun testExpiredProtocolAlert() {
        val now = System.currentTimeMillis()
        val proto = IrbProtocol(
            projectId = "p1",
            protocolNumber = "IRB-2023-999",
            institution = "NIH IRB",
            title = "Observational Study",
            status = IrbStatus.APPROVED,
            expirationDate = now - (5L * 24L * 60L * 60L * 1000L) // 5 days in past
        )

        val alert = proto.getComplianceAlert(currentTime = now)
        assertEquals("EXPIRED", alert)
    }

    @Test
    fun testExemptProtocolAlert() {
        val proto = IrbProtocol(
            projectId = "p1",
            protocolNumber = "IRB-EXEMPT-01",
            institution = "University IRB",
            title = "De-identified Survey Study",
            status = IrbStatus.EXEMPT
        )

        val alert = proto.getComplianceAlert()
        assertEquals("EXEMPT", alert)
    }
}
