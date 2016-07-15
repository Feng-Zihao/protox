package org.protox

import io.netty.handler.codec.http.HttpHeaderNames.HOST
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpScheme
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

    class Rule(val match: String, val forward: String) {

        val matchUri = URI(match)
        val forwardUri = URI(forward)
        val forwardHost: String get() = forwardUri.host
        val forwardPort: Int get() {
            if (forwardUri.port < 0) {
                if (forwardUri.scheme.equals(HttpScheme.HTTPS.name().toString(), false)) {
                    return HttpScheme.HTTPS.port()
                } else if (forwardUri.scheme.equals(HttpScheme.HTTP.name().toString(), false)) {
                    return HttpScheme.HTTP.port()
                }
            }
            return forwardUri.port
        }

        val forwardHttps: Boolean get() = forwardUri.scheme.equals(HttpScheme.HTTPS.name().toString(), false)

        fun match(request: HttpRequest): Boolean {
            var host = request.headers()[HOST.toString()]
            if (host.contains(":")) {
                host = host.split(":")[0]
            }
            return host.equals(matchUri.host, true)
        }

    }
}