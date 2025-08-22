package de.uniwuerzburg.omosim.core.models

enum class Sex {
    MALE, FEMALE, UNDEFINED;

    fun matSimName() : String? {
        return when(this) {
            MALE -> "m"
            FEMALE -> "f"
            UNDEFINED -> null
        }
    }
}