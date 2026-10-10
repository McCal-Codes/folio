package com.mccal.folio

/**
 * The English text of strings.xml for plain unit tests, which have no Android resources: resolves an R id to its
 * name and formats it the way Resources does. English plurals are "one" for 1 and "other" for everything else.
 */
object EnglishStrings : Strings {
    private val root = generateSequence(java.io.File("").absoluteFile) { it.parentFile }.first { java.io.File(it, "CHANGELOG.md").exists() }
    private val xml by lazy { java.io.File(root, "app/src/main/res/values/strings.xml").readText() }
    private fun unescape(text: String) = text.replace("\\'", "'").replace("\\\"", "\"").replace("\\u0020", " ")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
    private val strings: Map<String, String> by lazy {
        Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL).findAll(xml)
            .associate { it.groupValues[1] to unescape(it.groupValues[2]) }
    }
    private val plurals: Map<String, Map<String, String>> by lazy {
        Regex("""<plurals name="([^"]+)"[^>]*>(.*?)</plurals>""", RegexOption.DOT_MATCHES_ALL).findAll(xml).associate { p ->
            p.groupValues[1] to Regex("""<item quantity="(\w+)">(.*?)</item>""", RegexOption.DOT_MATCHES_ALL)
                .findAll(p.groupValues[2]).associate { it.groupValues[1] to unescape(it.groupValues[2]) }
        }
    }
    private fun name(type: Class<*>, id: Int) = type.fields.first { it.getInt(null) == id }.name
    private fun format(text: String, args: Array<out Any>) = if (args.isEmpty()) text else text.format(*args)

    override fun get(id: Int, vararg args: Any) = format(strings.getValue(name(R.string::class.java, id)), args)
    override fun plural(id: Int, count: Int, vararg args: Any) =
        plurals.getValue(name(R.plurals::class.java, id)).let { format(it[if (count == 1) "one" else "other"] ?: it.getValue("other"), args) }
}
