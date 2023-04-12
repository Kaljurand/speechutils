package ee.ioc.phon.android.speechutils.editor

import android.os.Build
import android.view.inputmethod.InputConnection
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern

private val F_SELECTION = Pattern.compile("@sel\\(\\)")
private val F_TIMESTAMP = Pattern.compile("@timestamp\\(([^,]+), *([^,]+)\\)")

fun expandFuns(input: String, vararg editFuns: EditFunction): String {
    val resultString = StringBuffer(input)
    editFuns.forEach {
        val regexMatcher = it.pattern.matcher(resultString)
        while (regexMatcher.find()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                regexMatcher.appendReplacement(resultString, it.apply(regexMatcher))
            }
        }
        regexMatcher.appendTail(resultString)
    }

    return resultString.toString()
}

abstract class EditFunction {
    abstract val pattern: Pattern
    abstract fun apply(m: Matcher): String
}

class Timestamp : EditFunction() {
    override val pattern: Pattern = F_TIMESTAMP

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