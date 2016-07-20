package org.protox

import io.netty.handler.codec.http.HttpHeaderNames.HOST
import io.netty.handler.codec.http.HttpRequest
import org.protox.http.WildcardURL

/**
 * Created by fengzh on 7/15/16.
 */

class Config(val listen: Int = 8080,
             val serverCert: String? = null,
             val serverKey: String? = null,
             val rules: List<ProxyRule> = emptyList()) {

    fun matchProxyRuleOrNull(request: HttpRequest): ProxyRule? {
        return rules.firstOrNull {
            it.match(request)
        }
    }

    class ProxyRule(match: String, forward: String) {

        val matchRule = WildcardURL(match)
        val forwardRule = WildcardURL(forward)

        fun match(request: HttpRequest): Boolean {
            return match(getOriginalHost(request))
        }

        fun match(host: String): Boolean {
            if (matchRule.isWildcard) {
                // wildcard domain

                // match   :         *.abc.xyz
                // host    : 1)        abc.xyz
                //         : 2)   a123.abc.xyz

                return ("." + host).endsWith(matchRule.hostPattern.substring(1), true)
            }
            return host.equals(matchRule.hostPattern, false)
        }

        fun getForwardHost(host: String): String {
            if (matchRule.isWildcard && forwardRule.isWildcard) {
                var prefix = host.substring(0, host.length + 2 - matchRule.hostPattern.length)
                if (prefix.isNotEmpty()) {
                    prefix += "."
                }
                return prefix + forwardRule.hostPattern.substring(2)
            }
            return forwardRule.hostPattern
        }

        fun getReturnedHost(host: String): String {
            return ""
        }

    }

}