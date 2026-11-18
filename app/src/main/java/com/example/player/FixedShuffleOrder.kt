package com.example.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ShuffleOrder
import com.example.domain.PlaybackQueueManager

/**
 * Deterministic Media3 [ShuffleOrder] backed by an explicit Fisher-Yates permutation
 * so every track in the queue plays once before any repeat and Next/Previous follow
 * the shuffled queue across the app UI, notification, lock screen, and headsets.
 */
@OptIn(UnstableApi::class)
class FixedShuffleOrder(
    shuffledOrder: IntArray
) : ShuffleOrder {

    private val order: IntArray = shuffledOrder.copyOf()
    private val positionOfIndex: IntArray = IntArray(order.size) { -1 }.also { posMap ->
        for (pos in order.indices) {
            val itemIdx = order[pos]
            if (itemIdx in posMap.indices) {
                posMap[itemIdx] = pos
            }
        }
    }

    override fun getLength(): Int = order.size

    override fun getNextIndex(index: Int): Int {
        if (order.isEmpty() || index !in positionOfIndex.indices) return C.INDEX_UNSET
        val pos = positionOfIndex[index]
        if (pos < 0) return C.INDEX_UNSET
        val nextPos = pos + 1
        return if (nextPos < order.size) order[nextPos] else C.INDEX_UNSET
    }

    override fun getPreviousIndex(index: Int): Int {
        if (order.isEmpty() || index !in positionOfIndex.indices) return C.INDEX_UNSET
        val pos = positionOfIndex[index]
        if (pos <= 0) return C.INDEX_UNSET
        return order[pos - 1]
    }

    override fun getLastIndex(): Int {
        return if (order.isNotEmpty()) order[order.lastIndex] else C.INDEX_UNSET
    }

    override fun getFirstIndex(): Int {
        return if (order.isNotEmpty()) order[0] else C.INDEX_UNSET
    }

    override fun cloneAndInsert(insertionIndex: Int, insertionCount: Int): ShuffleOrder {
        val newSize = (order.size + insertionCount).coerceAtLeast(0)
        if (newSize == 0) return FixedShuffleOrder(IntArray(0))
        val shifted = order.map { idx -> if (idx >= insertionIndex) idx + insertionCount else idx }
        val inserted = PlaybackQueueManager.fisherYatesShuffle(
            (insertionIndex until insertionIndex + insertionCount).toList()
        )
        return FixedShuffleOrder((shifted + inserted).toIntArray())
    }

    override fun cloneAndRemove(indexFrom: Int, indexToExclusive: Int): ShuffleOrder {
        val removeCount = (indexToExclusive - indexFrom).coerceAtLeast(0)
        if (removeCount == 0) return FixedShuffleOrder(order)
        val updated = buildList {
            for (idx in order) {
                when {
                    idx in indexFrom until indexToExclusive -> Unit
                    idx >= indexToExclusive -> add(idx - removeCount)
                    else -> add(idx)
                }
            }
        }
        return FixedShuffleOrder(updated.toIntArray())
    }

    override fun cloneAndClear(): ShuffleOrder {
        return FixedShuffleOrder(IntArray(0))
    }

    companion object {
        fun buildAnchoredFisherYates(size: Int, anchorIndex: Int): IntArray {
            if (size <= 0) return IntArray(0)
            if (anchorIndex !in 0 until size) {
                return PlaybackQueueManager.fisherYatesShuffle((0 until size).toList()).toIntArray()
            }
            val remaining = (0 until size).filter { it != anchorIndex }
            val shuffledRest = PlaybackQueueManager.fisherYatesShuffle(remaining)
            return (listOf(anchorIndex) + shuffledRest).toIntArray()
        }
    }
}
