package org.protox

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.netty.handler.codec.http.HttpScheme
import org.apache.commons.io.IOUtils
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.protox.http.WildcardURL
import java.util.regex.Pattern
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProtoxTest {

    val mapper = ObjectMapper(YAMLFactory()).registerKotlinModule()

    @Test fun testReadYaml() {
        val configText = IOUtils.toString(Config::class.java.getResource("/protox.yaml").toURI())
        println(configText)
        val config = mapper.readValue(configText, Config::class.java)
        assertThat(config.rules.size, equalTo(1))
//        assertThat(config.rules[0].forwardUrl.tex, equalTo("http://gg.aaa.com"))
    }

    @Test fun testRule() {
        var rule = Config.ProxyRule(match = "https://aa.bb.com", forward = "https://cc.aa.cc")
        assertThat(rule.forwardRule.scheme, equalTo(HttpScheme.HTTPS))
        assertThat(rule.forwardRule.isIPv4, equalTo(false))

        rule = Config.ProxyRule(match = "https://11.11.11.11:8080", forward = "https://11.11.11.11:8080")
        assertThat(rule.forwardRule.scheme, equalTo(HttpScheme.HTTPS))
        assertThat(rule.forwardRule.port, equalTo(8080))
        assertThat(rule.forwardRule.isIPv4, equalTo(true))
    }

    @Test fun testWildcardMatching() {
        val rule = Config.ProxyRule(match = "*.abc.xyz", forward = "https://*.remote.abc.xyz")
        assertTrue { rule.match("abc.xyz") }
        assertTrue { rule.match("a123.abc.xyz") }
        assertFalse { rule.match("abc.xyzz") }
        assertFalse { rule.match("abcc.xyz") }
    }

    @Test fun testURLRegex() {
        val pattern = Pattern.compile("((http(s?))://)?(([0-9a-zA-Z-_]+)(\\.[0-9a-zA-Z-_]+)*)")

        assertTrue { pattern.matcher("http://abc").matches() }
        assertTrue { pattern.matcher("http://a123bc.com").matches() }
        assertTrue { pattern.matcher("https://a123.123bc.com").matches() }
        assertFalse { pattern.matcher("ftp://a123.123bc.com").matches() }
        assertFalse { pattern.matcher("https://.123bc.com").matches() }
    }

    @Test fun testWildcardURLPattern() {
        var pattern = WILDCARD_URL_PATTERN

        assertTrue { pattern.matcher("http://abc").matches() }
        assertTrue { pattern.matcher("http://a23.abc").matches() }
        assertTrue { pattern.matcher("http://*.abc").matches() }
        assertTrue { pattern.matcher("https://*.abc.abc").matches() }
        assertFalse { pattern.matcher("ftp://*.abc.abc").matches() }
        assertFalse { pattern.matcher("http://.abc.abc").matches() }
        assertFalse { pattern.matcher("http://*abc.abc").matches() }

        assertTrue { pattern.matcher("http://10.200.83.160:8081").matches() }

    }

    @Test fun testWildcardURL() {
        val u1 = WildcardURL("http://*.abc.com")
        assertThat(u1.hostPattern, equalTo("*.abc.com"))
        assertThat(u1.isWildcard, equalTo(true))
        assertThat(u1.scheme, equalTo(HttpScheme.HTTP))
    }

    @Test fun testIPPattern() {
        assertTrue { IPV4_PATTREN.matcher("1.1.1.1").matches() }
        assertFalse { IPV4_PATTREN.matcher("1.1.1").matches() }
        assertFalse { IPV4_PATTREN.matcher("a.a.a.a").matches() }
    }


}
