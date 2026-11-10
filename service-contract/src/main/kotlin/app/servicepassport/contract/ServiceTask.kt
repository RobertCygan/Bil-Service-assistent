package app.servicepassport.contract

/** Wire values are permanent identifiers, never translated strings or manufacturer intervals. */
enum class ServiceAction(val code: String, val operations: Set<Operation>) {
    ENGINE_OIL("engine_oil", setOf(Operation.REPLACE, Operation.INSPECT)),
    GEARBOX_OIL("gearbox_oil", setOf(Operation.REPLACE, Operation.INSPECT)),
    BRAKE_FLUID("brake_fluid", setOf(Operation.REPLACE, Operation.INSPECT)),
    COOLANT("coolant", setOf(Operation.REPLACE, Operation.INSPECT)),
    SPARK_PLUGS("spark_plugs", setOf(Operation.REPLACE, Operation.INSPECT)),
    TIMING_BELT("timing_belt", setOf(Operation.REPLACE, Operation.INSPECT)),
    AIR_FILTER("air_filter", setOf(Operation.REPLACE, Operation.INSPECT)),
    CABIN_FILTER("cabin_filter", setOf(Operation.REPLACE, Operation.INSPECT)),
    BRAKES("brakes", setOf(Operation.INSPECT)),
    BATTERY_12V("battery_12v", setOf(Operation.TEST, Operation.REPLACE)),
}

enum class Operation(val code: String) { REPLACE("replace"), INSPECT("inspect"), TEST("test") }

data class ServiceTask(val action: ServiceAction, val operation: Operation) {
    init { require(operation in action.operations) { "Unsupported action/operation pair" } }

    /** The same complete key must be used for rule, confirmed history and OCR mapping. */
    val taskCode: String get() = "${action.code}:${operation.code}"

    companion object {
        const val CONTRACT_VERSION = 1

        fun fromTaskCode(code: String): ServiceTask? {
            val parts = code.split(':')
            if (parts.size != 2) return null
            val action = ServiceAction.entries.firstOrNull { it.code == parts[0] } ?: return null
            val operation = action.operations.firstOrNull { it.code == parts[1] } ?: return null
            return ServiceTask(action, operation)
        }
    }
}
