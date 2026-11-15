package com.example.domain

import kotlin.random.Random

/**
 * Pure playback queue utilities implementing Fisher-Yates shuffle
 * and deterministic next/previous traversal over the active queue.
 */
object PlaybackQueueManager {

    /**
     * Performs an unbiased Fisher-Yates shuffle so every song appears
     * exactly once with no repeats until the entire queue finishes.
     */
    fun <T> fisherYatesShuffle(items: List<T>, random: Random = Random.Default): List<T> {
        if (items.size <= 1) return items.toList()
        val result = items.toMutableList()
        for (i in result.lastIndex downTo 1) {
            val j = random.nextInt(i + 1)
            val tmp = result[i]
            result[i] = result[j]
            result[j] = tmp
        }
        return result
    }

    /**
     * Builds a shuffled queue keeping [anchorItem] at index 0 (when provided)
     * followed by a Fisher-Yates shuffle of all remaining items.
     */
    fun <T> buildShuffledQueueKeepingFirst(
        items: List<T>,
        anchorItem: T?,
        random: Random = Random.Default,
        idSelector: (T) -> Long
    ): List<T> {
        if (items.isEmpty()) return emptyList()
        if (anchorItem == null) return fisherYatesShuffle(items, random)
        val anchorId = idSelector(anchorItem)
        val matchedAnchor = items.find { idSelector(it) == anchorId } ?: anchorItem
        val remaining = items.filter { idSelector(it) != anchorId }
        return listOf(matchedAnchor) + fisherYatesShuffle(remaining, random)
    }

    /**
     * Generates a fresh Fisher-Yates cycle avoiding repeating [lastPlayedId]
     * as the very first song of the next cycle when there are 2+ tracks.
     */
    fun <T> reshuffleNextCycle(
        items: List<T>,
        lastPlayedId: Long?,
        random: Random = Random.Default,
        idSelector: (T) -> Long
    ): List<T> {
        val shuffled = fisherYatesShuffle(items, random).toMutableList()
        if (shuffled.size > 1 && lastPlayedId != null && idSelector(shuffled.first()) == lastPlayedId) {
            val swapIdx = 1 + random.nextInt(shuffled.size - 1)
            val first = shuffled[0]
            shuffled[0] = shuffled[swapIdx]
            shuffled[swapIdx] = first
        }
        return shuffled
    }
}
