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
        var holder = text.split("://")
        var tempPort: Int
        var tempHostPattern: String
        if (holder.size == 2) {
            if (holder[0].equals("https", true)) {
                tempPort = 443
                scheme = HttpScheme.HTTPS
            } else {
                tempPort = 80
                scheme = HttpScheme.HTTP
            }
            tempHostPattern = holder[1]
        } else {
            scheme = HttpScheme.HTTP
            tempHostPattern = holder[0]
            tempPort = 80
        }
        holder = tempHostPattern.split(":")
        if (holder.size == 2) {
            tempHostPattern = holder[0]
            tempPort = holder[1].toInt()
        }

        port = tempPort
        hostPattern = tempHostPattern
        isWildcard = hostPattern.startsWith("*.")
    }

}