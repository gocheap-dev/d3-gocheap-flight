package com.gocheap.travel

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform