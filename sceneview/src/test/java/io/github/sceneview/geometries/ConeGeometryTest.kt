package io.github.sceneview.geometries

import dev.romainguy.kotlin.math.Float3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConeGeometryTest {

    @Test
    fun `default radius is 1`() {
        assertEquals(1.0f, Cone.DEFAULT_RADIUS, 0f)
    }

    @Test
    fun `default height is 2`() {
        assertEquals(2.0f, Cone.DEFAULT_HEIGHT, 0f)
    }

    @Test
    fun `default center is origin`() {
        assertEquals(Float3(0f, 0f, 0f), Cone.DEFAULT_CENTER)
    }

    @Test
    fun `default side count is 24`() {
        assertEquals(24, Cone.DEFAULT_SIDE_COUNT)
    }

    @Test
    fun `getVertices produces non-empty list`() {
        val vertices = Cone.getVertices(1f, 2f, Float3(0f), 8)
        assertTrue("Should have vertices", vertices.isNotEmpty())
    }

    @Test
    fun `getIndices returns single primitive`() {
        val indices = Cone.getIndices(8)
        assertEquals(1, indices.size)
    }

    @Test
    fun `all index values are within vertex range`() {
        val sideCount = 8
        val vertices = Cone.getVertices(1f, 2f, Float3(0f), sideCount)
        val indices = Cone.getIndices(sideCount)
        for (idx in indices[0]) {
            assertTrue("Index $idx out of range [0, ${vertices.size})", idx in 0 until vertices.size)
        }
    }

    @Test
    fun `vertices y range matches half-height`() {
        val height = 2f
        val vertices = Cone.getVertices(1f, height, Float3(0f), 12)
        val minY = vertices.minOf { it.position.y }
        val maxY = vertices.maxOf { it.position.y }
        assertEquals(-height / 2f, minY, 0.001f)
        assertEquals(height / 2f, maxY, 0.001f)
    }

    @Test
    fun `tip vertex is at top`() {
        val height = 4f
        val vertices = Cone.getVertices(1f, height, Float3(0f), 6)
        val topVertices = vertices.filter { it.position.y == height / 2f }
        assertTrue("Should have tip vertices", topVertices.isNotEmpty())
    }

    @Test
    fun `vertices shift when center is non-zero`() {
        val center = Float3(10f, 20f, 30f)
        val height = 2f
        val vertices = Cone.getVertices(1f, height, center, 6)
        val minY = vertices.minOf { it.position.y }
        val maxY = vertices.maxOf { it.position.y }
        assertEquals(center.y - height / 2f, minY, 0.001f)
        assertEquals(center.y + height / 2f, maxY, 0.001f)
    }

    @Test
    fun `all vertices have normals and UVs`() {
        val vertices = Cone.getVertices(1f, 2f, Float3(0f), 6)
        for (v in vertices) {
            assertTrue("Missing normal", v.normal != null)
            assertTrue("Missing UV", v.uvCoordinate != null)
        }
    }
}
