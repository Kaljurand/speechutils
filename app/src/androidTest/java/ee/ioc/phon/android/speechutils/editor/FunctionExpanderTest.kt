package ee.ioc.phon.android.speechutils.editor

import androidx.test.ext.junit.runners.AndroidJUnit4
import ee.ioc.phon.android.speechutils.utils.HttpUtils
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val F_SEL = "@sel()"

@RunWith(AndroidJUnit4::class)
class FunctionExpanderTest {
    @Test
    fun test01() {
        val regex = "${F_SEL}|${F_SEL}"
        val selContent = "SEL"
        val gold = "SEL|SEL"
        val regexExpanded1: String = regex.replace(F_SEL, selContent)
        val regexExpanded2 = expandFuns(regex, SelEvaluated(selContent))
        assertTrue(regexExpanded1 == gold)
        assertTrue(regexExpanded2 == gold)
    }

    @Test
    fun test02() {
        val text = "@timestamp(G 'text', de)|@timestamp(G 'text', de)"
        val gold = "n. Chr. text|n. Chr. text"
        val res = expandFuns(text, Timestamp())
        assertTrue(res == gold)
    }

    @Test
    fun test03() {
        val text = "@urlEncode(1+2)"
        val gold = HttpUtils.encode("1+2")
        val res = expandFuns(text, UrlEncode())
        assertTrue(res == gold)
    }

    // TODO: set up internet permission
    // @Test
    fun testXX() {
        val text = "@getUrl(https://api.mathjs.org/v4/?expr=@urlEncode(1+2))"
        val gold = "3"
        val res = expandFuns(text, UrlEncode(), GetUrl())
        assertTrue(res == gold)
    }
}