package com.mccal.folio

import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Dates come from patterns in strings.xml, one per language, so each puts the month and the day in its own order
 * (StandBy, the Big Clock, Lock Cover, the Side Bar, Today View). A typo in one would throw on the phone, in that
 * language only; here every pattern is read with its own language's names.
 */
class DatePatternsTest {
    private val res = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "CHANGELOG.md").exists() }.let { File(it, "app/src/main/res") }

    private fun pattern(dir: String, name: String): String =
        Regex("""<string name="$name">(.*?)</string>""").find(File(res, "$dir/strings.xml").readText())
            ?.groupValues?.get(1) ?: error("no $name in $dir")

    private val friday = LocalDate.of(2026, 10, 2)

    private fun check(dir: String, locale: Locale, expected: Map<String, String>) = expected.forEach { (name, date) ->
        assertEquals("$name in $dir", date, friday.format(DateTimeFormatter.ofPattern(pattern(dir, name), locale)))
    }

    @Test fun `English keeps the order it always had`() = check("values", Locale.US, mapOf(
        "eeee_mmmm_d" to "Friday, October 2", "mmmm_d" to "October 2", "mmm_d" to "Oct 2",
        "eee_mmm_d" to "Fri Oct 2", "eee_mmm_d_2" to "Fri, Oct 2", "d_mmm_yyyy" to "2 Oct 2026",
    ))

    @Test fun `Korean puts the month first and the weekday last`() = check("values-b+ko", Locale.KOREAN, mapOf(
        "eeee_mmmm_d" to "10월 2일 금요일", "mmmm_d" to "10월 2일", "mmm_d" to "10월 2일",
        "eee_mmm_d" to "10월 2일 (금)", "eee_mmm_d_2" to "10월 2일 (금)", "d_mmm_yyyy" to "2026년 10월 2일",
    ))

    @Test fun `Chinese puts the month first and the weekday last`() = check("values-b+zh+Hans", Locale.SIMPLIFIED_CHINESE, mapOf(
        "eeee_mmmm_d" to "10月2日 星期五", "mmmm_d" to "10月2日", "mmm_d" to "10月2日",
        "eee_mmm_d" to "10月2日 周五", "eee_mmm_d_2" to "10月2日 周五", "d_mmm_yyyy" to "2026年10月2日",
    ))
}
