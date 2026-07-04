package io.github.sceneview.geometries

import dev.romainguy.kotlin.math.Float3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TorusGeometryTest {

    @Test
    fun `default major radius is 1`() {
        assertEquals(1.0f, Torus.DEFAULT_MAJOR_RADIUS, 0f)
    }

    @Test
    fun `default minor radius is 0_3`() {
        assertEquals(0.3f, Torus.DEFAULT_MINOR_RADIUS, 0f)
    }

    @Test
    fun `default center is origin`() {
        assertEquals(Float3(0f, 0f, 0f), Torus.DEFAULT_CENTER)
    }

    @Test
    fun `getVertices produces non-empty list`() {
        val vertices = Torus.getVertices(1f, 0.3f, Float3(0f), 8, 6)
        assertTrue("Should have vertices", vertices.isNotEmpty())
    }

    @Test
    fun `vertex count is (majorSegments+1) x (minorSegments+1)`() {
        val major = 8
        val minor = 6
        val vertices = Torus.getVertices(1f, 0.3f, Float3(0f), major, minor)
        assertEquals((major + 1) * (minor + 1), vertices.size)
    }

    @Test
    fun `getIndices returns single primitive`() {
        val indices = Torus.getIndices(8, 6)
        assertEquals(1, indices.size)
    }

    @Test
    fun `index count is majorSegments x minorSegments x 6`() {
        val major = 8
        val minor = 6
        val indices = Torus.getIndices(major, minor)
        assertEquals(major * minor * 6, indices[0].size)
    }

    @Test
    fun `all index values are within vertex range`() {
        val major = 12
        val minor = 8
        val vertices = Torus.getVertices(1f, 0.3f, Float3(0f), major, minor)
        val indices = Torus.getIndices(major, minor)
        for (idx in indices[0]) {
            assertTrue("Index $idx out of range [0, ${vertices.size})", idx in 0 until vertices.size)
        }
    }

    @Test
    fun `torus y range matches minor radius`() {
        val minorRadius = 0.5f
        val vertices = Torus.getVertices(2f, minorRadius, Float3(0f), 16, 8)
        val minY = vertices.minOf { it.position.y }
        val maxY = vertices.maxOf { it.position.y }
        assertEquals(-minorRadius, minY, 0.01f)
        assertEquals(minorRadius, maxY, 0.01f)
    }

    @Test
    fun `vertices shift when center is non-zero`() {
        val center = Float3(5f, 10f, 15f)
        val vertices = Torus.getVertices(1f, 0.3f, center, 8, 6)
        val avgY = vertices.map { it.position.y }.average().toFloat()
        assertEquals(center.y, avgY, 0.1f)
    }

    @Test
    fun `all vertices have normals and UVs`() {
        val vertices = Torus.getVertices(1f, 0.3f, Float3(0f), 8, 6)
        for (v in vertices) {
            assertTrue("Missing normal", v.normal != null)
            assertTrue("Missing UV", v.uvCoordinate != null)
        }
    }
}
