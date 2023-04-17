package ee.ioc.phon.android.speechutils.editor

import android.os.Build
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
    val resultString = StringBuffer(input)
    editFuns.forEach {
        val regexMatcher = it.pattern.matcher(resultString)
        while (regexMatcher.find()) {
            // TODO: lift required API to N everywhere
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                regexMatcher.appendReplacement(resultString, it.apply(regexMatcher))
            }
        }
        regexMatcher.appendTail(resultString)
    }

    return resultString.toString()
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
        return applyToInt(m.group(1), m.group(1).toInt(), m.group(3).toInt()).toString()
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

public class Sel(ic: InputConnection) : EditFunction() {
    override val pattern: Pattern = F_SELECTION

    private val selectedText: String by lazy {
        val cs: CharSequence = ic.getSelectedText(0)
        cs.toString()
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