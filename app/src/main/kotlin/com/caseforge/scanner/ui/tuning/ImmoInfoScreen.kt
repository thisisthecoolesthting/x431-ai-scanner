package com.caseforge.scanner.ui.tuning

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.caseforge.scanner.ui.planb.ImmoInfoScreen as PlanbImmoInfoScreen

/**
 * Tuning hub entry for immobilizer reference (bundled assets + optional live DID read).
 */
@Composable
fun ImmoInfoScreen(
    session: TuningSessionContext,
    modifier: Modifier = Modifier,
) {
    PlanbImmoInfoScreen(
        vehicleVin = session.vehicleVin,
        lastRecordedVin = session.lastRecordedVin,
        vciCommunicator = session.vciCommunicator,
        modifier = modifier,
    )
}
