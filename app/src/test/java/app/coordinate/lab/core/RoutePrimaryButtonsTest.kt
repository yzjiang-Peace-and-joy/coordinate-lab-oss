package app.coordinate.lab.core
import org.junit.Assert.*
import org.junit.Test

class RoutePrimaryButtonsTest {
    private fun button(active: Boolean=false,route: Boolean=true,editing: Boolean=false,planning: Boolean=false,error: String?=null,count: Int=3,path: Int=0)=
        RoutePrimaryButtons.resolve(active,route,editing,planning,error,count,path)
    @Test fun selectedPointsRequireConfirmationNotStart() {assertEquals(RoutePrimaryButton(RoutePrimaryAction.CONFIRM,true),button(editing=true))}
    @Test fun completePreviewEnablesSecondClickToStart() {assertEquals(RoutePrimaryButton(RoutePrimaryAction.START,true),button(path=250))}
    @Test fun planningNeverStartsEvenWithPreviousGeometry() {assertEquals(RoutePrimaryButton(RoutePrimaryAction.PLANNING,false),button(planning=true,path=250))}
    @Test fun failedPlanningCanRetryButCannotStart() {assertEquals(RoutePrimaryAction.CONFIRM,button(error="限流",path=250).action)}
    @Test fun newEditsInvalidatePreviousStart() {assertEquals(RoutePrimaryAction.CONFIRM,button(editing=true,path=250).action)}
    @Test fun clearAndInsufficientPointsDisableConfirmation() {listOf(0,1,21).forEach {assertFalse(button(count=it).enabled)}}
    @Test fun activeSessionAlwaysOffersStopAndSinglePointOffersStart() {
        assertEquals(RoutePrimaryButton(RoutePrimaryAction.STOP,true),button(active=true,planning=true))
        assertEquals(RoutePrimaryButton(RoutePrimaryAction.START,true),button(route=false,count=0))
    }
}
