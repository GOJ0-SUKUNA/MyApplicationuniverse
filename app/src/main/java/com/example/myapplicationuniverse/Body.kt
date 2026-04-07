package com.example.myapplicationuniverse

data class Body(
    var x: Double,
    var y: Double,
    var vx: Double,
    var vy: Double,
    val mass: Double,
    val radius: Float,
    val color: Int,
    val trail: MutableList<Pair<Double, Double>> = mutableListOf()
)
