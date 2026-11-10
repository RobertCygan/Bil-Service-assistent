package app.servicepassport.android

import app.servicepassport.android.data.ProfileProblem
import app.servicepassport.android.data.ProfileValidation
import org.junit.Assert.*
import org.junit.Test

class ProfileValidationTest {
    @Test fun unknownModelYearIsAllowedWithoutGuessing() {
        assertNull(ProfileValidation.problem("My car", "", 2026))
    }

    @Test fun invalidValuesAreRejectedBeforeDatabaseWrite() {
        assertEquals(ProfileProblem.NAME_REQUIRED, ProfileValidation.problem("  ", "2022", 2026))
        assertEquals(ProfileProblem.INVALID_YEAR, ProfileValidation.problem("Car", "9999999999999", 2026))
        assertEquals(ProfileProblem.INVALID_YEAR, ProfileValidation.problem("Car", "1800", 2026))
        assertEquals(ProfileProblem.INVALID_YEAR, ProfileValidation.problem("Car", "2040", 2026))
    }
}
