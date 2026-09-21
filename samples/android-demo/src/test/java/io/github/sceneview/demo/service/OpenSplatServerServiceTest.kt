package io.github.sceneview.demo.service

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenSplatServerServiceTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Test
    fun testServerStatusSerialization() {
        val jsonStr = """{"status":"ok","service":"opensplat-server"}"""
        val parsed = json.decodeFromString<ServerStatusResponse>(jsonStr)
        assertEquals("ok", parsed.status)
        assertEquals("opensplat-server", parsed.service)

        val encoded = json.encodeToString(parsed)
        assertTrue(encoded.contains("opensplat-server"))
    }

    @Test
    fun testSystemStatsSerialization() {
        val jsonStr = """
            {
                "cpu": {"percent": 24.5, "cores": 16},
                "ram": {"total": 34359738368, "available": 17179869184, "used": 17179869184, "percent": 50.0},
                "disk": {"total": 1000000000, "used": 500000000, "free": 500000000, "percent": 50.0},
                "gpu": {
                    "available": true,
                    "name": "NVIDIA GeForce RTX 4090",
                    "load": 82.0,
                    "memory_used": 12884901888,
                    "memory_total": 25769803776,
                    "memory_percent": 50.0,
                    "temp": 64
                },
                "active_jobs_count": 2
            }
        """.trimIndent()
        val stats = json.decodeFromString<SystemStatsResponse>(jsonStr)
        assertNotNull(stats.cpu)
        assertEquals(16, stats.cpu?.cores)
        assertNotNull(stats.gpu)
        assertTrue(stats.gpu?.available == true)
        assertEquals("NVIDIA GeForce RTX 4090", stats.gpu?.name)
        assertEquals(2, stats.activeJobsCount)
    }

    @Test
    fun testJobStatusSerialization() {
        val jsonStr = """
            {
                "metadata": {
                    "job_id": "test_job_123",
                    "job_type": "video",
                    "status": "training",
                    "progress": 0.45,
                    "step": 450,
                    "total_steps": 1000,
                    "loss": 0.0123
                },
                "logs": ["Init done", "Training step 100", "Training step 450"]
            }
        """.trimIndent()
        val status = json.decodeFromString<JobStatusResponse>(jsonStr)
        assertEquals("test_job_123", status.metadata.jobId)
        assertEquals("training", status.metadata.status)
        assertEquals(450, status.metadata.step)
        assertEquals(3, status.logs.size)
        assertEquals("Training step 450", status.logs.last())
    }
}
