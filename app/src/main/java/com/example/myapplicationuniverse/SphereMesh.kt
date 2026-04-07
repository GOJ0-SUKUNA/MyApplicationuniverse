package com.example.myapplicationuniverse

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.cos
import kotlin.math.sin

class SphereMesh(stacks: Int = 22, slices: Int = 32) {
    val positionBuffer: FloatBuffer
    val normalBuffer: FloatBuffer
    val vertexCount: Int

    init {
        val positions = ArrayList<Float>()
        val normals = ArrayList<Float>()

        for (i in 0 until stacks) {
            val phi1 = Math.PI * i.toDouble() / stacks.toDouble() - Math.PI / 2.0
            val phi2 = Math.PI * (i + 1).toDouble() / stacks.toDouble() - Math.PI / 2.0

            for (j in 0 until slices) {
                val theta1 = 2.0 * Math.PI * j.toDouble() / slices.toDouble()
                val theta2 = 2.0 * Math.PI * (j + 1).toDouble() / slices.toDouble()

                val v1 = spherePoint(phi1, theta1)
                val v2 = spherePoint(phi2, theta1)
                val v3 = spherePoint(phi2, theta2)
                val v4 = spherePoint(phi1, theta2)

                addTri(positions, normals, v1, v2, v3)
                addTri(positions, normals, v1, v3, v4)
            }
        }

        vertexCount = positions.size / 3

        positionBuffer = ByteBuffer
            .allocateDirect(positions.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        positions.forEach { positionBuffer.put(it) }
        positionBuffer.position(0)

        normalBuffer = ByteBuffer
            .allocateDirect(normals.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        normals.forEach { normalBuffer.put(it) }
        normalBuffer.position(0)
    }

    private fun spherePoint(phi: Double, theta: Double): FloatArray {
        val cp = cos(phi)
        val x = (cp * cos(theta)).toFloat()
        val y = sin(phi).toFloat()
        val z = (cp * sin(theta)).toFloat()
        return floatArrayOf(x, y, z)
    }

    private fun addTri(
        positions: MutableList<Float>,
        normals: MutableList<Float>,
        a: FloatArray,
        b: FloatArray,
        c: FloatArray
    ) {
        listOf(a, b, c).forEach { v ->
            positions.add(v[0]); positions.add(v[1]); positions.add(v[2])
            normals.add(v[0]); normals.add(v[1]); normals.add(v[2])
        }
    }
}
