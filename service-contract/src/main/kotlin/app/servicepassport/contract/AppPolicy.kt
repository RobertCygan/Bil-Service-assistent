package app.servicepassport.contract

/** App presentation defaults approved by the owner; never manufacturer service intervals. */
object AppPolicy {
    const val DUE_SOON_KM = 1500
    const val DUE_SOON_DAYS = 30
    const val ODOMETER_STALE_AFTER_DAYS = 90
}
