package org.protox

import io.netty.handler.codec.http.HttpHeaderNames.HOST
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpScheme
import org.protox.http.WildcardURL
import java.net.URI

/**
 * Created by fengzh on 7/15/16.
 */

class Config(val listen: Int = 8080,
             val serverCert: String? = null,
             val serverKey: String? = null,
             val rules: List<Rule> = emptyList()) {

    fun matchRuleOrNull(request: HttpRequest): Rule? {
        return rules.firstOrNull {
            it.match(request)
        }
    }

    class Rule(match: String, forward: String) {

        val matchUrl = WildcardURL(match)
        val forwardUrl = WildcardURL(forward)

        fun match(request: HttpRequest): Boolean {
            var host = request.headers()["X-Forwarded-Host"]
            if (host.isNullOrBlank()) {
                host = request.headers()[HOST.toString()]
            }
            if (host.contains(":")) {
                host = host.split(":")[0]
            }
            println(host)

            return match(host)

        }

        fun match(host: String): Boolean {
            if (matchUrl.isWildcard) {
                // wildcard domain

                // match   :         *.abc.xyz   remove * and reversed    zyx.cba.
                // host    : 1)        abc.xyz                            zyx.cba
                //         : 2)   a123.abc.xyz                            zyx.cba.321a

                // reversed

                val reverseMatchHost = matchUrl.hostPattern.substring(1).reversed()
                val reverseHost = host.reversed()

                return ((reverseHost + ".").startsWith(reverseMatchHost, true))
            }
            return host.equals(matchUrl.hostPattern, false)
        }
    }

}