package org.protox.http

import io.netty.handler.codec.http.HttpScheme
import org.protox.WILDCARD_URL_PATTERN

/**
 * Created by fengzh on 7/19/16.
 */


class WildcardURL(text: String) {

    val scheme: HttpScheme?
    val hostPattern: String
    val isWildcard: Boolean
    val port: Int

    init {
        if (!WILDCARD_URL_PATTERN.matcher(text).matches()) {
            throw IllegalArgumentException("%s is not a valid wildcard url".format(text))
        }
        val holder = text.split("://")
        if (holder.size == 2) {
            if (holder[0].equals("https", true)) {
                port = 443
                scheme = HttpScheme.HTTPS
            } else {
                port = 80
                scheme = HttpScheme.HTTP
            }
            hostPattern = holder[1]
        } else {
            scheme = HttpScheme.HTTP
            hostPattern = holder[0]
            port = 80
        }

        isWildcard = hostPattern.startsWith("*.")
    }

}