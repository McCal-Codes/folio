package com.mccal.folio

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderOrRoomTest {
    private val icon = Rect(100f, 100f, 200f, 200f)

    @Test fun `the middle of an app is a circle 64 percent as wide as its icon`() {
        assertTrue(FolderOrRoom.inMiddle(Offset(150f, 150f), icon))
        assertTrue(FolderOrRoom.inMiddle(Offset(181f, 150f), icon))
        assertFalse(FolderOrRoom.inMiddle(Offset(183f, 150f), icon))
        // A corner of the icon is its edge, not its middle: it asks for room.
        assertFalse(FolderOrRoom.inMiddle(Offset(175f, 175f), icon))
    }

    @Test fun `without a held icon or an icon on screen there is no middle`() {
        assertFalse(FolderOrRoom.inMiddle(null, icon))
        assertFalse(FolderOrRoom.inMiddle(Offset(150f, 150f), null))
        assertFalse(FolderOrRoom.inMiddle(Offset(0f, 0f), Rect.Zero))
    }

    @Test fun `the starting values are the ones chosen in the Lab`() {
        assertEquals(250L, FolderOrRoom.FOLDER_WAIT_MS)
        assertEquals(120L, FolderOrRoom.ROOM_WAIT_MS)
        assertEquals(.18f, FolderOrRoom.PLATE_GROWTH, 0f)
        assertEquals(.86f, FolderOrRoom.ICON_INSIDE, 0f)
    }
}
