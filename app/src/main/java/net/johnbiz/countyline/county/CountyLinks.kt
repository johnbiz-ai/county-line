package net.johnbiz.countyline.county

import net.johnbiz.countyline.core.County
import net.johnbiz.countyline.core.fullName
import java.net.URLEncoder

/**
 * Outbound links for a county. All are plain web URLs opened with `ACTION_VIEW`, so the app
 * itself never touches the network. Wikipedia and Wikivoyage use `Special:Search`, which jumps
 * straight to the article on an exact title match and avoids guessing article titles.
 */
object CountyLinks {

    fun thingsToDo(county: County, kind: String?): String =
        "https://www.google.com/maps/search/?api=1&query=" +
            encode("things to do in ${county.fullName(kind)}, ${county.stateAbbr}")

    fun wikipedia(county: County, kind: String?): String =
        "https://en.wikipedia.org/wiki/Special:Search?search=" + encode(searchTerm(county, kind))

    fun wikivoyage(county: County, kind: String?): String =
        "https://en.wikivoyage.org/wiki/Special:Search?search=" + encode(searchTerm(county, kind))

    private fun searchTerm(county: County, kind: String?) = "${county.fullName(kind)}, ${county.stateName}"

    // The String-charset overload works on every API level (the Charset overload needs API 33).
    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
