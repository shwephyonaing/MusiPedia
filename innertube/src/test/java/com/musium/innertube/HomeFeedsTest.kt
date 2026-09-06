package com.musium.innertube

import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFeedsTest {
    @Test
    fun chartsResolveAndMixHasSongs() {
        val youtube = Innertube(gl = "MM")
        val apple = youtube.appleMostPlayed(8)
        println("apple=${apple.size}")
        apple.take(5).forEach { println("  APPLE ${it.title} | ${it.artist}") }
        assertTrue("Apple charts should load", apple.size >= 5)

        val trending = apple.mapNotNull { hit ->
            youtube.findSong("${hit.title} ${hit.artist}")?.also { song ->
                println("  HIT ${song.id} | ${song.title} | ${song.subtitle}")
            }
        }
        println("trending=${trending.size}")
        assertTrue("Trending songs should resolve from YouTube", trending.size >= 5)

        val mix = youtube.mixAround(trending.first())
        println("mix=${mix.size}")
        mix.take(8).forEach { println("  MIX ${it.id} | ${it.title} | ${it.subtitle}") }
        assertTrue("For You mix should be more than the seed song", mix.size >= 5)
    }
}
