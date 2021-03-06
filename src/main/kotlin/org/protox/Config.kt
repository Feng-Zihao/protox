package org.protox

import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpScheme
import org.protox.http.WildcardURL
import org.slf4j.LoggerFactory
import java.net.URL

/**
 * Created by fengzh on 7/15/16.
 */

class Config(val listen: Int = 8080,
             val ssl: SSL? = null,
             val rules: List<ProxyRule> = emptyList()) {

    fun matchProxyRuleOrNull(request: HttpRequest): ProxyRule? {
        return rules.firstOrNull {
            it.match(request)
        }
    }

    class SSL(val listen: Int, val cert: String, val key: String){}

    class ProxyRule(match: String, forward: String) {

        val LOGGER = LoggerFactory.getLogger(ProxyRule::class.java)

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
                return prefix + forwardRule.hostPattern.substring(2)
            }
            return forwardRule.hostPattern
        }

        fun getReturnedHost(host: String): String {
            if (matchRule.isWildcard && forwardRule.isWildcard) {
                var prefix = host.substring(0, host.length + 2 - forwardRule.hostPattern.length)
                return prefix + matchRule.hostPattern.substring(2)
            }
            return matchRule.hostPattern
        }

        fun getReturnedLocation(location: String): String {
            var url = URL(location)
            if (url.port != matchRule.port) {
                return location
            }
            val replacedHost = getReturnedHost(url.host)
            val replacedLocation = URL(if (matchRule.scheme!!.equals(HttpScheme.HTTP)) "http" else "https", replacedHost, url.file).toString()
            LOGGER.debug("{} {}", forwardRule.hostPattern, matchRule.hostPattern)
            LOGGER.debug("{} -> {}", location, replacedLocation)
            return replacedLocation
        }

    }

}