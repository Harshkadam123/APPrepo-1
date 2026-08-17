package com.harsh.jarvis.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelBenchmarkTest {
    @Test fun report_aggregates_samples() {
        val report = ModelBenchmarkReport(listOf(
            ModelBenchmarkSample(ModelTask.INTENT, 10, 4, 2, true, .9, 1000, 992),
            ModelBenchmarkSample(ModelTask.INTENT, 20, 6, 2, false, .4, 992, 990)
        ))
        assertEquals(.5, report.successRate, 0.0001)
        assertEquals(15.0, report.averageLatencyMs, 0.0001)
    }
}
