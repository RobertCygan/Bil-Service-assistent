package app.servicepassport.contract

import org.junit.Assert.*
import org.junit.Test

class ServiceTaskTest {
    @Test fun inspectionAndReplacementAreDifferentEngineKeys() {
        val replacement = ServiceTask(ServiceAction.ENGINE_OIL, Operation.REPLACE)
        val inspection = ServiceTask(ServiceAction.ENGINE_OIL, Operation.INSPECT)
        assertNotEquals(replacement.taskCode, inspection.taskCode)
        assertEquals("engine_oil:replace", replacement.taskCode)
    }

    @Test fun unknownOrAmbiguousCodesCannotSilentlyResetAService() {
        listOf("engine_oil", "ENGINE_OIL:REPLACE", "brakes:replace", "unknown:replace", "engine_oil:replace:extra")
            .forEach { assertNull(it, ServiceTask.fromTaskCode(it)) }
    }

    @Test fun supportedKeysRoundTripWithoutTranslation() {
        ServiceAction.entries.forEach { action ->
            action.operations.forEach { operation ->
                val task = ServiceTask(action, operation)
                assertEquals(task, ServiceTask.fromTaskCode(task.taskCode))
            }
        }
    }
}
