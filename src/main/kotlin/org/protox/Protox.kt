package org.protox

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.HttpRequestDecoder
import io.netty.handler.codec.http.HttpResponseEncoder
import io.netty.handler.logging.LoggingHandler
import org.apache.commons.io.IOUtils
import org.protox.http.FrontendHandler

var backendEventLoopGroup = NioEventLoopGroup(1)

val bossGroup = NioEventLoopGroup(1)
val workerGroup = NioEventLoopGroup(1)


fun main(args: Array<String>) {

    var serverBootstrap = ServerBootstrap()

    try {
        val mapper = ObjectMapper(YAMLFactory())
        mapper.registerKotlinModule()

        val config = mapper.readValue(
                IOUtils.toString(Config::class.java.getResource("/protox.yaml").toURI()),
                Config::class.java)
        serverBootstrap
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel::class.java)
                .option(ChannelOption.SO_REUSEADDR, true)
                .childHandler(object : ChannelInitializer<SocketChannel> () {
                    override fun initChannel(ch: SocketChannel) {
                        ch.pipeline().addLast(LoggingHandler())
                        ch.pipeline().addLast(HttpRequestDecoder())
                        ch.pipeline().addLast(HttpResponseEncoder())
                        ch.pipeline().addLast(FrontendHandler(config))
                    }
                })
                .childOption(ChannelOption.AUTO_READ, false)
                .bind("0.0.0.0", 9090).sync().channel().closeFuture().sync()

    } finally {
        bossGroup.shutdownGracefully()
        workerGroup.shutdownGracefully()
    }
}
