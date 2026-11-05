package app.coordinate.lab

import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorPath
import org.junit.Assert.*
import org.junit.Test

class PauseIconTest {
    @Test fun pauseUsesTwoFilledShapesRatherThanHairlineStrokes() {
        val icon=AppIcons.Pause
        assertEquals(24f,icon.viewportWidth,0f)
        assertEquals(24f,icon.viewportHeight,0f)
        val path=icon.root[0] as VectorPath
        assertNotNull(path.fill)
        assertNull(path.stroke)
        assertEquals(2,path.pathData.count {it is PathNode.Close})
    }
}
