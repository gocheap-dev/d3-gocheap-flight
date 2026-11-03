package com.gocheap.flights

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform