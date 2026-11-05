package app.coordinate.lab

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Small, original geometric vectors instead of shipping a full icon catalogue. */
object AppIcons {
    // Filled, optically balanced rounded bars; not two hairline strokes.
    val Pause by lazy {ImageVector.Builder("PauseRounded",24.dp,24.dp,24f,24f).addPath(
        pathData=PathParser().parsePathString("M7.2,5H8.8Q10,5 10,6.2V17.8Q10,19 8.8,19H7.2Q6,19 6,17.8V6.2Q6,5 7.2,5Z M15.2,5H16.8Q18,5 18,6.2V17.8Q18,19 16.8,19H15.2Q14,19 14,17.8V6.2Q14,5 15.2,5Z").toNodes(),
        fill=SolidColor(Color.Black),stroke=null
    ).build()}
    val Lock by lazy {make("FocusMap","M6,10H18V21H6Z M8,10V6A4,4 0,0 1,16,6V10 M12,14V17")}
    val Unlock by lazy {make("ExitFocusMap","M6,10H18V21H6Z M8,10V6A4,4 0,0 1,16,6 M12,14V17")}
    val Night by lazy {make("Night","M20,14A8,8 0,1 1,10,4A6,6 0,0 0,20,14Z")}
    val Bus by lazy {make("Bus","M5,4Q12,1 19,4V18H5Z M5,11H19 M12,4V11 M7,18V21 M17,18V21 M8,15H9 M15,15H16")}
    val Loop by lazy {make("Loop","M20,9A8,8 0,0 0,6,6L3,9 M3,4V9H8 M4,15A8,8 0,0 0,18,18L21,15 M16,15H21V20")}
    val More by lazy {make("More","M12,4A1,1 0,1 1,12,6A1,1 0,1 1,12,4 M12,11A1,1 0,1 1,12,13A1,1 0,1 1,12,11 M12,18A1,1 0,1 1,12,20A1,1 0,1 1,12,18")}
    val ClearAll by lazy {make("ClearAll","M4,6H20 M9,6V3H15V6 M6,6L7,21H17L18,6 M10,10V17 M14,10V17")}
    val Undo by lazy {make("Undo","M9,5L4,10L9,15 M4,10H14C22,10 22,21 14,21")}
    val Check by lazy {make("Check","M4,12L9,17L20,6")}
    val Swap by lazy {make("SwitchMode","M4,7H20 M15,2L20,7L15,12 M20,17H4 M9,12L4,17L9,22")}
    val Edit by lazy {make("Edit","M4,16L15,5L19,9L8,20L3,21Z M13,7L17,11 M15,5L17,3L21,7L19,9")}
    private fun make(name: String,path: String)=ImageVector.Builder(name,24.dp,24.dp,24f,24f).addPath(
        pathData=PathParser().parsePathString(path).toNodes(),fill=null,stroke=SolidColor(Color.Black),
        strokeLineWidth=1.8f,strokeLineCap=StrokeCap.Round,strokeLineJoin=StrokeJoin.Round
    ).build()
    val Map by lazy {make("Map","M3,5L9,3L15,5L21,3V19L15,21L9,19L3,21Z M9,3V19 M15,5V21")}
    val BookmarkBorder by lazy {make("Bookmark","M6,3H18V21L12,17L6,21Z")}
    val Bookmark by lazy {make("Saved","M6,3H18V21L12,17L6,21Z M9,8L11,10L15,6")}
    val Tune by lazy {make("Tune","M4,6H9 M15,6H20 M4,12H14 M19,12H20 M4,18H6 M12,18H20 M12,3V9 M17,9V15 M9,15V21")}
    val Search by lazy {make("Search","M17,10A7,7 0,1 1,3,10A7,7 0,1 1,17,10 M15,15L21,21")}
    val LocationOn by lazy {make("Location","M12,22C9,18 5,14 5,9A7,7 0,1 1,19,9C19,14 15,18 12,22Z M15,9A3,3 0,1 1,9,9A3,3 0,1 1,15,9")}
    val MyLocation by lazy {make("Target","M20,12A8,8 0,1 1,4,12A8,8 0,1 1,20,12 M12,1V6 M12,18V23 M1,12H6 M18,12H23 M14,12A2,2 0,1 1,10,12A2,2 0,1 1,14,12")}
    val Route by lazy {make("Route","M5,20V14H18V4 M3,19L5,21L7,19 M16,5L18,3L20,5 M5,5H10 M5,8V3")}
    val Flag by lazy {make("Flag","M5,22V3 M5,4C10,0 14,8 20,3V15C14,20 10,12 5,16")}
    val Stop by lazy {make("Stop","M5,5H19V19H5Z")}
    val PlayArrow by lazy {make("Play","M7,3L21,12L7,21Z")}
    val Close by lazy {make("Close","M5,5L19,19 M5,19L19,5")}
    val ArrowForward by lazy {make("Forward","M3,12H21 M14,5L21,12L14,19")}
    val Add by lazy {make("Add","M12,4V20 M4,12H20")}
    val Remove by lazy {make("Remove","M4,12H20")}
    val ExpandMore by lazy {make("ExpandMore","M6,9L12,15L18,9")}
    val ExpandLess by lazy {make("ExpandLess","M6,15L12,9L18,15")}
    val Layers by lazy {make("Layers","M12,3L22,9L12,15L2,9Z M3,13L12,19L21,13 M3,17L12,23L21,17")}
    val Shield by lazy {make("Shield","M12,2L21,6V12C21,18 16,21 12,23C8,21 3,18 3,12V6Z")}
    val VerifiedUser by lazy {make("Verified","M12,2L21,6V12C21,18 16,21 12,23C8,21 3,18 3,12V6Z M7,12L10,15L17,8")}
}
