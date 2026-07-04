package io.github.sceneview.geometries

import dev.romainguy.kotlin.math.Float3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CapsuleGeometryTest {

    @Test
    fun `default radius is 0_5`() {
        assertEquals(0.5f, Capsule.DEFAULT_RADIUS, 0f)
    }

    @Test
    fun `default height is 2`() {
        assertEquals(2.0f, Capsule.DEFAULT_HEIGHT, 0f)
    }

    @Test
    fun `default center is origin`() {
        assertEquals(Float3(0f, 0f, 0f), Capsule.DEFAULT_CENTER)
    }

    @Test
    fun `getVertices produces non-empty list`() {
        val vertices = Capsule.getVertices(0.5f, 2f, Float3(0f), 4, 8)
        assertTrue("Should have vertices", vertices.isNotEmpty())
    }

    @Test
    fun `vertex count is (2*capStacks+1) x (sideSlices+1)`() {
        val capStacks = 4
        val sideSlices = 8
        val vertices = Capsule.getVertices(0.5f, 2f, Float3(0f), capStacks, sideSlices)
        // Top hemisphere: (capStacks+1) rings, bottom: capStacks rings (shares equator)
        assertEquals((2 * capStacks + 1) * (sideSlices + 1), vertices.size)
    }

    @Test
    fun `getIndices returns single primitive`() {
        val indices = Capsule.getIndices(4, 8)
        assertEquals(1, indices.size)
    }

    @Test
    fun `all index values are within vertex range`() {
        val capStacks = 4
        val sideSlices = 8
        val vertices = Capsule.getVertices(0.5f, 2f, Float3(0f), capStacks, sideSlices)
        val indices = Capsule.getIndices(capStacks, sideSlices)
        for (idx in indices[0]) {
            assertTrue("Index $idx out of range [0, ${vertices.size})", idx in 0 until vertices.size)
        }
    }

    @Test
    fun `total height is cylinder height plus 2 radii`() {
        val radius = 0.5f
        val height = 2f
        val vertices = Capsule.getVertices(radius, height, Float3(0f), 8, 12)
        val minY = vertices.minOf { it.position.y }
        val maxY = vertices.maxOf { it.position.y }
        val totalHeight = maxY - minY
        assertEquals(height + 2 * radius, totalHeight, 0.05f)
    }

    @Test
    fun `vertices shift when center is non-zero`() {
        val center = Float3(5f, 10f, 15f)
        val vertices = Capsule.getVertices(0.5f, 2f, center, 4, 8)
        val avgY = vertices.map { it.position.y }.average().toFloat()
        assertEquals(center.y, avgY, 0.5f)
    }

    @Test
    fun `all vertices have normals and UVs`() {
        val vertices = Capsule.getVertices(0.5f, 2f, Float3(0f), 4, 8)
        for (v in vertices) {
            assertTrue("Missing normal", v.normal != null)
            assertTrue("Missing UV", v.uvCoordinate != null)
        }
    }

    @Test
    fun `capsule is rotationally symmetric — all ring radii are equal`() {
        val radius = 0.5f
        val vertices = Capsule.getVertices(radius, 2f, Float3(0f), 4, 12)
        // Equator ring (at y near 0) should have x^2+z^2 ≈ radius^2
        val equatorVertices = vertices.filter { kotlin.math.abs(it.position.y) < 0.01f }
        for (v in equatorVertices) {
            val ringR = kotlin.math.sqrt(v.position.x * v.position.x + v.position.z * v.position.z)
            assertEquals(radius, ringR, 0.01f)
        }
    }
}
