package net.johnbiz.countyline.core

import java.text.NumberFormat
import java.util.Locale

/** Display formatting for [CountyFacts]. */
object CountyFactsFormat {

    fun count(value: Int, locale: Locale): String = NumberFormat.getIntegerInstance(locale).format(value)

    /** Whole numbers from 10 up; one decimal below, so sparse areas don't read as "0". */
    fun measure(value: Double, locale: Locale): String {
        val digits = if (value < 10.0) 1 else 0
        return NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = digits
            maximumFractionDigits = digits
        }.format(value)
    }

    /** One-line summary for notifications, e.g. `"pop. 609,863 · 1,288/sq mi"`; `null` if nothing is known. */
    fun summary(facts: CountyFacts, locale: Locale): String? {
        val parts = listOfNotNull(
            facts.population?.let { "pop. ${count(it, locale)}" },
            facts.density?.let { "${measure(it, locale)}/sq mi" },
        )
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }
}
