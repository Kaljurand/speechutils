package ee.ioc.phon.android.speechutils.editor

import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
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
        Expr(),
        Timestamp()
    )
}

fun expandFuns2(line: String, selectedText: String, ic: InputConnection): String {
    return expandFuns(
        line,
        SelEvaluated(selectedText),
        Text(ic),
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

class Sel(ic: InputConnection) : EditFunction() {
    override val pattern: Pattern = F_SELECTION

    private val selectedText: String by lazy {
        val cs: CharSequence? = ic.getSelectedText(0)
        cs?.toString().orEmpty()
    }

    override fun apply(m: Matcher): String {
        return selectedText
    }
}

class SelFromExtractedText(private val et: ExtractedText) : EditFunction() {
    override val pattern: Pattern = F_SELECTION

    private val selectedText: String by lazy {
        if (et.selectionStart == et.selectionEnd) {
            ""
        } else """\Q""" + et.text.subSequence(et.selectionStart, et.selectionEnd) + """\E"""
    }

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