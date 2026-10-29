package de.uniwuerzburg.omosim.core.models

/**
 * Activity types.
 */
enum class ActivityType {
    HOME, WORK, BUSINESS, SCHOOL, SHOPPING, OTHER;

    fun matSimName() : String {
        return when(this) {
            HOME -> "home"
            WORK -> "work"
            BUSINESS -> "work"
            SCHOOL -> "education"
            SHOPPING -> "shopping"
            OTHER -> "other"
        }
    }
}