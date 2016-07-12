package org.protox

import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.util.concurrent.Future
import io.netty.util.concurrent.GenericFutureListener

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