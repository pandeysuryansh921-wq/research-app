package com.ecosystem.research

import com.ecosystem.research.core.ecosystem.StatisticalExportEngine
import com.ecosystem.research.core.model.EvidenceMatrix
import com.ecosystem.research.core.model.MatrixCell
import com.ecosystem.research.core.model.Source
import org.junit.Assert.*
import org.junit.Test

class StatisticalExportEngineTest {

    private val testSources = listOf(
        Source(
            id = "s1",
            title = "Randomized Trial of Drug X",
            authors = listOf("John Smith", "Alice Jones"),
            year = 2021
        ),
        Source(
            id = "s2",
            title = "Comparative Evaluation of Drug Y",
            authors = listOf("David Miller"),
            year = 2022
        )
    )

    private val testMatrix = EvidenceMatrix(
        id = "m1",
        projectId = "p1",
        title = "Cardiovascular Outcomes Matrix"
    )

    private val testCells = listOf(
        MatrixCell("m1", "s1", "population", "250 subjects (125 active, 125 control)"),
        MatrixCell("m1", "s1", "outcome", "Primary MACE events"),
        MatrixCell("m1", "s1", "result", "12 events in active vs 28 in control"),
        MatrixCell("m1", "s2", "population", "400 patients (200 active, 200 control)"),
        MatrixCell("m1", "s2", "outcome", "All-cause mortality"),
        MatrixCell("m1", "s2", "result", "18 events in active vs 35 in control")
    )

    @Test
    fun testGenerateRMetaScript() {
        val script = StatisticalExportEngine.generateRMetaScript(testMatrix, testSources, testCells)
        assertNotNull(script)
        assertTrue(script.contains("library(meta)"))
        assertTrue(script.contains("metabin("))
        assertTrue(script.contains("sm = \"RR\""))
        assertTrue(script.contains("method = \"MH\""))
        assertTrue(script.contains("forest("))
        assertTrue(script.contains("funnel("))
        assertTrue(script.contains("Cardiovascular Outcomes Matrix"))
    }

    @Test
    fun testGenerateRevManXml() {
        val xml = StatisticalExportEngine.generateRevManXml(testMatrix, testSources, testCells)
        assertNotNull(xml)
        assertTrue(xml.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))
        assertTrue(xml.contains("<COCHRANE_REVIEW"))
        assertTrue(xml.contains("<STUDIES_AND_REFERENCES>"))
        assertTrue(xml.contains("<DATA_AND_ANALYSES>"))
        assertTrue(xml.contains("Smith et al., 2021"))
        assertTrue(xml.contains("</COCHRANE_REVIEW>"))
    }

    @Test
    fun testGeneratePythonNotebookAndScript() {
        val notebook = StatisticalExportEngine.generatePythonNotebook(testMatrix, testSources, testCells)
        assertNotNull(notebook)
        assertTrue(notebook.contains("\"cells\": ["))
        assertTrue(notebook.contains("statsmodels") || notebook.contains("events_exp"))
        assertTrue(notebook.contains("\"nbformat\": 4"))

        val pyScript = StatisticalExportEngine.generatePythonScript(testMatrix, testSources, testCells)
        assertNotNull(pyScript)
        assertTrue(pyScript.contains("import pandas as pd"))
        assertTrue(pyScript.contains("import matplotlib.pyplot as plt"))
        assertTrue(pyScript.contains("plt.savefig(\"python_forest_plot.png\""))
    }
}
