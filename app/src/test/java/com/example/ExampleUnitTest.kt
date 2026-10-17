package com.example

import com.example.data.*
import com.example.ui.formatTimecode
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testTimecodeFormatting() {
        val tc = formatTimecode(0L)
        assertEquals("00:00:00:00", tc)

        val tc15s = formatTimecode(15000L)
        assertTrue(tc15s.startsWith("00:00:15"))

        val tcMinute = formatTimecode(65000L)
        assertTrue(tcMinute.startsWith("00:01:05"))
    }

    @Test
    fun testKeyframeInterpolationLinear() {
        val clip = MediaClip(
            name = "TestClip",
            trackId = "track_video_1",
            trackType = TrackType.VIDEO,
            startTimeMs = 0L,
            durationMs = 10000L,
            keyframes = listOf(
                Keyframe(timeMs = 0L, param = KeyframeParam.POSITION_X, value = 0f, curve = InterpolationType.LINEAR),
                Keyframe(timeMs = 10000L, param = KeyframeParam.POSITION_X, value = 100f, curve = InterpolationType.LINEAR)
            )
        )

        // Midpoint calculation
        val kf0 = clip.keyframes[0]
        val kf1 = clip.keyframes[1]
        val t = 0.5f
        val interpVal = kf0.value + (kf1.value - kf0.value) * t
        assertEquals(50f, interpVal, 0.001f)
    }

    @Test
    fun testClipSplittingMath() {
        val originalClip = MediaClip(
            id = "clip_test",
            name = "MainVideo.mp4",
            trackId = "track_video_1",
            trackType = TrackType.VIDEO,
            startTimeMs = 0L,
            sourceStartMs = 1000L,
            durationMs = 10000L,
            speed = 1.0f
        )

        val splitPointMs = 4000L
        val splitOffset = splitPointMs - originalClip.startTimeMs

        val firstPart = originalClip.copy(durationMs = splitOffset)
        val secondPart = originalClip.copy(
            id = "clip_test_part2",
            startTimeMs = splitPointMs,
            sourceStartMs = originalClip.sourceStartMs + (splitOffset * originalClip.speed).toLong(),
            durationMs = originalClip.durationMs - splitOffset
        )

        assertEquals(4000L, firstPart.durationMs)
        assertEquals(4000L, secondPart.startTimeMs)
        assertEquals(6000L, secondPart.durationMs)
        assertEquals(5000L, secondPart.sourceStartMs)
        assertEquals(originalClip.durationMs, firstPart.durationMs + secondPart.durationMs)
    }

    @Test
    fun testAspectRatios() {
        assertEquals(9f / 16f, AspectRatio.RATIO_9_16.ratio, 0.001f)
        assertEquals(16f / 9f, AspectRatio.RATIO_16_9.ratio, 0.001f)
        assertEquals(1f, AspectRatio.RATIO_1_1.ratio, 0.001f)
    }
}
