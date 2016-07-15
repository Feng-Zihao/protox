package org.protox

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.sun.javaws.Main
import org.apache.commons.io.IOUtils
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import kotlin.test.assertTrue

class ProtoxTest {

    val mapper = ObjectMapper(YAMLFactory()).registerKotlinModule()

    @Test fun testReadYaml() {
        val configText = IOUtils.toString(Config::class.java.getResource("/protox.yaml").toURI())
        println(configText)
        val config = mapper.readValue(configText, Config::class.java)
        assertThat(config.rules.size, equalTo(1))
        assertThat(config.rules[0].match, equalTo("http://gg.aaa.com"))
    }

    @Test fun testRule() {
        val rule = Config.Rule(match = "https://aa.bb.com", forward = "https://cc.aa.cc")
        assertTrue { rule.forwardHttps }
    }


}
