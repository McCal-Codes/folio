package com.mccal.folio

import android.content.Intent
import android.os.BadParcelableException
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class Ghost(val n: Int) : Parcelable {
    override fun describeContents() = 0
    override fun writeToParcel(dest: Parcel, flags: Int) { dest.writeInt(n) }
    companion object { @JvmField val CREATOR = object : Parcelable.Creator<Ghost> {
        override fun createFromParcel(source: Parcel) = Ghost(source.readInt())
        override fun newArray(size: Int) = arrayOfNulls<Ghost>(size)
    } }
}

/**
 * An app that starts Folio's exported Home with an extra of a class Folio does not have. The bundle is built the way it
 * arrives from another process: marshalled with a class name that this app's class loader cannot find.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class UnreadableExtrasTest {
    private fun foreignBundle(): Bundle {
        val name = Ghost::class.java.name
        val real = Bundle().apply { putString("duo_destination", "search"); putParcelable("from_another_app", Ghost(7)) }
        val out = Parcel.obtain()
        out.writeBundle(real)
        var bytes = out.marshall(); out.recycle()
        val gone = name.dropLast(1) + "_"
        for (charset in listOf(Charsets.UTF_16LE, Charsets.UTF_8)) {
            val from = name.toByteArray(charset); val to = gone.toByteArray(charset)
            var i = 0; var hit = false
            while (i <= bytes.size - from.size) {
                if (from.indices.all { bytes[i + it] == from[it] }) { to.copyInto(bytes, i); hit = true; i += from.size } else i++
            }
            if (hit) break
        }
        val back = Parcel.obtain()
        back.unmarshall(bytes, 0, bytes.size); back.setDataPosition(0)
        return back.readBundle(javaClass.classLoader)!!.also { back.recycle() }
    }

    @Test fun anExtraOfAnUnknownClassMakesAnOrdinaryReadThrow() {
        val intent = Intent().replaceExtras(foreignBundle())
        try { intent.getStringExtra("duo_destination"); fail("expected the read to throw") } catch (e: RuntimeException) {
            assertTrue(e.toString(), e is BadParcelableException || e.cause is ClassNotFoundException || e.message.orEmpty().contains("ClassNotFound"))
        }
    }

    @Test fun dropUnreadableExtrasTurnsItIntoAnIntentWithNoExtras() {
        val intent = Intent().replaceExtras(foreignBundle())
        assertTrue(intent.dropUnreadableExtras())
        assertNull(intent.getStringExtra("duo_destination"))
        assertFalse(intent.getBooleanExtra("anything", false))
        intent.removeExtra("duo_destination")
    }

    @Test fun readableExtrasAreLeftAlone() {
        val intent = Intent().putExtra("duo_destination", "search").putExtra("n", 3)
        assertFalse(intent.dropUnreadableExtras())
        assertEquals("search", intent.getStringExtra("duo_destination"))
        assertEquals(3, intent.getIntExtra("n", 0))
    }

    @Test fun anIntentWithoutExtrasIsFine() {
        assertFalse(Intent().dropUnreadableExtras())
    }
}
