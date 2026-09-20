package com.ecosystem.research

import com.ecosystem.research.core.model.*
import org.junit.Assert.*
import org.junit.Test

class MultiDisciplinaryFrameworksTest {

    @Test
    fun testPeoFrameworkTerms() {
        val peo = PeoFramework(
            population = "Adults over 65",
            exposure = "Fine particulate matter PM2.5",
            outcome = "Cognitive decline"
        )
        val terms = peo.toQueryTerms()
        assertEquals(3, terms.size)
        assertTrue(terms.contains("Adults over 65"))
        assertTrue(terms.contains("Fine particulate matter PM2.5"))
        assertTrue(terms.contains("Cognitive decline"))
    }

    @Test
    fun testCsBenchmarkFrameworkTerms() {
        val cs = CsBenchmarkFramework(
            benchmarkDataset = "ImageNet-1K",
            proposedAlgorithm = "Linear Attention Transformer",
            baseline = "ResNet-50",
            targetMetric = "Top-1 Accuracy"
        )
        val terms = cs.toQueryTerms()
        assertEquals(4, terms.size)
        assertTrue(terms.contains("ImageNet-1K"))
        assertTrue(terms.contains("Linear Attention Transformer"))
        assertTrue(terms.contains("Top-1 Accuracy"))
    }

    @Test
    fun testBioinformaticsFrameworkTerms() {
        val bio = BioinformaticsFramework(
            variant = "rs1801133",
            gene = "MTHFR",
            phenotype = "Hyperhomocysteinemia",
            associationMethod = "GWAS logistic regression"
        )
        val terms = bio.toQueryTerms()
        assertEquals(4, terms.size)
        assertTrue(terms.contains("rs1801133"))
        assertTrue(terms.contains("MTHFR"))
        assertTrue(terms.contains("Hyperhomocysteinemia"))
    }

    @Test
    fun testSocialScienceFrameworkTerms() {
        val soc = SocialScienceFramework(
            targetGroup = "Remote software developers",
            phenomenon = "Burnout and asynchronous isolation",
            context = "Post-pandemic distributed teams",
            methodology = "Grounded theory semi-structured interviews"
        )
        val terms = soc.toQueryTerms()
        assertEquals(4, terms.size)
        assertTrue(terms.contains("Remote software developers"))
        assertTrue(terms.contains("Burnout and asynchronous isolation"))
    }

    @Test
    fun testDisciplineDefaults() {
        val project = ResearchProject(title = "Clinical AI Evaluation")
        assertEquals(Discipline.MEDICAL, project.discipline)

        val csProject = ResearchProject(
            title = "Attention Mechanisms",
            discipline = Discipline.CS
        )
        assertEquals(Discipline.CS, csProject.discipline)
    }
}
