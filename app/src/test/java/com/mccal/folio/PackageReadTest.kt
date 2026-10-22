package com.mccal.folio

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream

class PackageReadTest {
    private val farFuture = Long.MAX_VALUE

    @Test fun aSmallFileComesBackWhole() {
        val data = ByteArray(40_000) { it.toByte() }
        val read = readPackageBytes(ByteArrayInputStream(data), 100_000, farFuture)
        assertArrayEquals(data, (read as PackageRead.Ok).bytes)
    }

    @Test fun anEmptyFileIsNotAPackage() {
        assertEquals(PackageRead.NotAPackage, readPackageBytes(ByteArrayInputStream(ByteArray(0)), 100, farFuture))
    }

    @Test fun aFileOverTheCapIsTooLarge() {
        assertEquals(PackageRead.TooLarge, readPackageBytes(ByteArrayInputStream(ByteArray(5_000)), 4_999, farFuture))
    }

    @Test fun aReadErrorIsNotAPackage() {
        val broken = object : InputStream() { override fun read(): Int = throw java.io.IOException("gone") }
        assertEquals(PackageRead.NotAPackage, readPackageBytes(broken, 100, farFuture))
    }

    @Test fun aSlowButFiniteStreamStillFinishesInsideTheDeadline() {
        var clock = 0L
        val slow = object : InputStream() {
            var left = 3
            override fun read(): Int = -1
            override fun read(b: ByteArray, off: Int, len: Int): Int { clock += 10; return if (left-- > 0) { b[off] = 1; 1 } else -1 }
        }
        val read = readPackageBytes(slow, 100, deadlineNanos = 1_000) { clock }
        assertEquals(3, (read as PackageRead.Ok).bytes.size)
    }

    @Test fun aStreamThatKeepsTricklingPastTheDeadlineTimesOut() {
        var clock = 0L
        val endless = object : InputStream() {
            override fun read(): Int = -1
            override fun read(b: ByteArray, off: Int, len: Int): Int { clock += 400; b[off] = 1; return 1 }
        }
        assertEquals(PackageRead.TimedOut, readPackageBytes(endless, Int.MAX_VALUE, deadlineNanos = 1_000) { clock })
    }

    @Test fun aStreamThatNeverAnswersIsCutOffWhenTheCallerGivesUp() {
        val never = PipedInputStream(PipedOutputStream())
        var result: PackageRead? = null
        val worker = Thread { result = readPackageBytes(never, 100, farFuture) }
        worker.start()
        Thread.sleep(100)
        worker.interrupt()
        worker.join(3_000)
        assertTrue("reader should have stopped", !worker.isAlive)
        assertEquals(PackageRead.TimedOut, result)
    }

    @Test fun theSuspendingReaderGivesUpAtItsDeadlineOnAStreamThatNeverAnswers() {
        val started = System.nanoTime()
        val read = runBlocking { readPackageWithin(300) { PipedInputStream(PipedOutputStream()) } }
        val tookMs = (System.nanoTime() - started) / 1_000_000
        assertEquals(PackageRead.TimedOut, read)
        assertTrue("took ${tookMs}ms", tookMs < 3_000)
    }

    @Test fun aMissingStreamIsNotAPackage() {
        assertEquals(PackageRead.NotAPackage, runBlocking { readPackageWithin(300) { null } })
    }

    @Test fun aReadThatIgnoresInterruptsIsReleasedByClosingTheStreamAtTheDeadline() {
        val release = java.util.concurrent.CountDownLatch(1)
        val closed = java.util.concurrent.atomic.AtomicBoolean(false)
        val stubborn = object : InputStream() {
            override fun read(): Int = -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                // Swallows interrupts, the way a provider's native read can; only close() lets it go.
                while (!closed.get()) { try { release.await(50, java.util.concurrent.TimeUnit.MILLISECONDS) } catch (_: InterruptedException) { } }
                return -1
            }
            override fun close() { closed.set(true); release.countDown() }
        }
        val started = System.nanoTime()
        val read = runBlocking { readPackageWithin(300) { stubborn } }
        val tookMs = (System.nanoTime() - started) / 1_000_000
        assertEquals(PackageRead.TimedOut, read)
        assertTrue("took ${tookMs}ms", tookMs < 3_000)
        assertTrue("the stream was closed", closed.get())
    }
}
