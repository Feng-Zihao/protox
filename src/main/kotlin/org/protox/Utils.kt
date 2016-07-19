package org.protox

import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.handler.ssl.SslContextBuilder
import io.netty.util.concurrent.Future
import io.netty.util.concurrent.GenericFutureListener
import java.util.regex.Pattern
import javax.net.ssl.SSLEngine

/**
 * Created by fengzh on 7/12/2016 AD.
 */
fun tryCloseChannel(chn: Channel?, listener: GenericFutureListener<out Future<Any>>? = null) {
    if (chn != null && chn.isActive) {
        chn.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener {
            val future = chn.close()
            if (listener != null) {
                future.addListener(listener)
            }
        }
    }
}

var SSLEngine = SslContextBuilder.forClient().build()

val URL_PATTERN = Pattern.compile("(http(s?)://)?(([0-9a-zA-Z-_]+)(\\.[0-9a-zA-Z-_]+)*)")

val WILDCARD_URL_PATTERN : Pattern = Pattern.compile("((http(s?))://)?(\\*\\.)?([0-9a-zA-Z]+\\.)*([0-9a-zA-Z]+)")
