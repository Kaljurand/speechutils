package ee.ioc.phon.android.speechutils.editor

import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import ee.ioc.phon.android.speechutils.utils.HttpUtils
import kotlinx.coroutines.*
import org.json.JSONException
import java.io.IOException
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern

private val F_SELECTION = Pattern.compile("""@sel\(\)""")

fun expandFuns(input: String, vararg editFuns: EditFunction): String {
    var inputNew = input
    editFuns.forEach {
        val resultString = StringBuffer()
        val regexMatcher = it.pattern.matcher(inputNew)
        while (regexMatcher.find()) {
            regexMatcher.appendReplacement(resultString, it.apply(regexMatcher))
        }
        regexMatcher.appendTail(resultString)
        inputNew = resultString.toString()
    }

    return inputNew
}

fun expandFunsAll(line: String, ic: InputConnection): String {
    return expandFuns(
        line,
        Sel(ic),
        Text(ic),
        Lower(),
        Upper(),
        UrlEncode(),
        GetUrl(),
        Expr(),
        Timestamp()
    )
}

/**
 * Returns the current selection wrapped in regex quotes.
 */
public fun getSelectionAsRe(et: ExtractedText): CharSequence {
    return if (et.selectionStart == et.selectionEnd) {
        ""
    } else "\\Q" + et.text.subSequence(et.selectionStart, et.selectionEnd) + "\\E"
}

abstract class EditFunction {
    abstract val pattern: Pattern
    abstract fun apply(m: Matcher): String
}

class Expr : EditFunction() {
    override val pattern: Pattern = Pattern.compile("""@expr\((\d+) ?([+/*-]) ?(\d+)\)""")

    private fun applyToInt(sign: String, a: Int, b: Int): Int {
        return when (sign) {
            "+" -> a + b
            "-" -> a - b
            "*" -> a * b
            "/" -> a / b
            else -> 0
        }
    }

    override fun apply(m: Matcher): String {
        return applyToInt(m.group(2), m.group(1).toInt(), m.group(3).toInt()).toString()
    }
}

class Timestamp : EditFunction() {
    override val pattern: Pattern = Pattern.compile("""@timestamp\(([^,]+), *([^,]+)\)""")

    private val currentTime: Date by lazy {
        Calendar.getInstance().time
    }

    override fun apply(m: Matcher): String {
        val df: DateFormat = SimpleDateFormat(m.group(1), Locale(m.group(2)))
        return df.format(currentTime)
    }
}

class UrlEncode : EditFunction() {
    override val pattern: Pattern = Pattern.compile("""@urlEncode\((.+?)\)""")

    override fun apply(m: Matcher): String {
        return HttpUtils.encode(m.group(1))
    }
}

class Lower : EditFunction() {
    override val pattern: Pattern = Pattern.compile("""@lower\((.+?)\)""")

    override fun apply(m: Matcher): String {
        return m.group(1).lowercase()
    }
}

class Upper : EditFunction() {
    override val pattern: Pattern = Pattern.compile("""@upper\((.+?)\)""")

    override fun apply(m: Matcher): String {
        return m.group(1).uppercase()
    }
}

class GetUrl : EditFunction() {
    override val pattern: Pattern = Pattern.compile("""@getUrl\((.+?)\)""")

    override fun apply(m: Matcher): String {
        return getUrlWithCatch(m.group(1))
    }
}

class Sel(ic: InputConnection) : EditFunction() {
    override val pattern: Pattern = F_SELECTION

    val selectedText: String by lazy {
        val cs: CharSequence? = ic.getSelectedText(0)
        cs?.toString().orEmpty()
    }

    override fun apply(m: Matcher): String {
        return selectedText
    }
}

class SelFromExtractedText(private val et: ExtractedText) : EditFunction() {
    override val pattern: Pattern = F_SELECTION

    private val selectedTextLazy: String by lazy {
        if (et.selectionStart == et.selectionEnd) {
            ""
        } else """\Q""" + et.text.subSequence(et.selectionStart, et.selectionEnd) + """\E"""
    }

    private val selectedText = getSelectionAsRe(et).toString()

    override fun apply(m: Matcher): String {
        return selectedText
    }
}

class SelEvaluated(private val selectedText: String) : EditFunction() {
    override val pattern: Pattern = F_SELECTION

    override fun apply(m: Matcher): String {
        return this.selectedText
    }
}

class Text(private val ic: InputConnection) : EditFunction() {
    override val pattern: Pattern = Pattern.compile("""@text\(\)""")

    private val text: String by lazy {
        val extractedText: ExtractedText = ic.getExtractedText(ExtractedTextRequest(), 0)
        extractedText.text.toString()
    }

    override fun apply(m: Matcher): String {
        return text
    }
}

suspend fun doSomethingUsefulOne(): Int {
    delay(1000L) // pretend we are doing something useful here
    return 13
}

suspend fun doSomethingThree(v: Deferred<Int>): Int {
    delay(1000L) // pretend we are doing something useful here, too
    return v.await() + 29
}

// TODO: Add the functions as arguments
// Make a fun that calls HTTP and applies JSON query to its result.
suspend fun noh2() {
    val one = CoroutineScope(Dispatchers.IO).async {
        doSomethingUsefulOne()
    }
    val two = CoroutineScope(Dispatchers.IO).async {
        doSomethingThree(one)
    }
    println("The answer is ${two.await()}")
}

fun getUrlWithCatch(url: String): String {
    return try {
        HttpUtils.getUrl(url)
    } catch (e: IOException) {
        "[ERROR: Unable to retrieve " + url + ": " + e.localizedMessage + "]"
    }
}

// https://api.mathjs.org/v4/?expr=2*(7-3)
suspend fun mathjs(expr: String): String {
    val url = "https://api.mathjs.org/v4/?expr=" + HttpUtils.encode(expr)
    val res = CoroutineScope(Dispatchers.IO).async {
        getUrlWithCatch(url)
    }
    return res.await()
}

suspend fun httpJson(editor: InputConnectionCommandEditor, json: String): Op {
    return object : Op("httpJson") {
        override fun run(): Op {
            val jsonExpanded = expandFunsAll(json, editor.inputConnection)
            val res = CoroutineScope(Dispatchers.IO).async {
                try {
                    HttpUtils.fetchUrl(jsonExpanded)
                } catch (e: IOException) {
                    "[ERROR: Unable to query " + jsonExpanded + ": " + e.localizedMessage + "]"
                } catch (e: JSONException) {
                    "[ERROR: Unable to query " + jsonExpanded + ": " + e.localizedMessage + "]"
                }
            }
            CoroutineScope(Dispatchers.IO).async {
                editor.runOp(editor.replaceSel(res.await()))
            }
            return NO_OP
        }
    }
}

fun main() = runBlocking { // this: CoroutineScope
    launch { println(mathjs("1+2")) }
    println("Hello")
}