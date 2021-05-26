/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.servicetalk.examples.http.helloworld.async.streaming;

import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.client.api.DelegatingConnectionFactory;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.netty.HttpClients;
import io.servicetalk.logging.api.LogLevel;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ClientSslConfigBuilder;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.internal.NettyConnectionContext;

import io.netty.channel.Channel;
import io.netty.handler.ssl.SslHandler;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;

import static io.servicetalk.http.api.HttpSerializationProviders.textDeserializer;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2;
import static io.servicetalk.test.resources.DefaultTestCerts.serverPemHostname;

public final class HelloWorldStreamingClient {

    public static void main(String[] args) throws Exception {
        ChannelHolder holder = new ChannelHolder();
        try (StreamingHttpClient client = HttpClients.forSingleAddress("localhost", 8080)
                .sslConfig(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                        .peerHost(serverPemHostname()).build())
                .enableWireLogging("wire", LogLevel.DEBUG, () -> true)
                .protocols(h2().enableFrameLogging("frame", LogLevel.DEBUG, () -> true).build())
                .appendConnectionFactoryFilter(holder)
                .buildStreaming()) {
            // This example is demonstrating asynchronous execution, but needs to prevent the main thread from exiting
            // before the response has been processed. This isn't typical usage for a streaming API but is useful for
            // demonstration purposes.
            CountDownLatch responseProcessedLatch = new CountDownLatch(1);
            client.request(client.get("/sayHello"))
                    .beforeOnSuccess(response -> System.out.println(response.toString((name, value) -> value)))
                    .flatMapPublisher(resp -> resp.payloadBody(textDeserializer()))
                    .afterFinally(responseProcessedLatch::countDown)
                    .forEach(System.out::println);

            responseProcessedLatch.await();

            SslHandler sslHandler = holder.channel.pipeline().get(SslHandler.class);
            sslHandler.closeOutbound().sync();
            System.out.println("sslHandler.closeOutbound()");
            // ((DuplexChannel) holder.channel).shutdownOutput().sync();
            // System.out.println("shutdownOutput");
            Thread.sleep(10_000);
        }
    }

    private static class ChannelHolder implements ConnectionFactoryFilter<InetSocketAddress, FilterableStreamingHttpConnection> {

        Channel channel;

        @Override
        public ConnectionFactory<InetSocketAddress, FilterableStreamingHttpConnection> create(
                final ConnectionFactory<InetSocketAddress, FilterableStreamingHttpConnection> original) {
            return new DelegatingConnectionFactory<InetSocketAddress, FilterableStreamingHttpConnection>(original) {

                @Override
                public Single<FilterableStreamingHttpConnection> newConnection(final InetSocketAddress address,
                                                                               final TransportObserver observer) {
                    return delegate().newConnection(address, observer).whenOnSuccess(connection -> {
                        final NettyConnectionContext ctx = (NettyConnectionContext) connection.connectionContext();
                        channel = ctx.nettyChannel();
                        ctx.onClosing().whenFinally(() -> System.err.println("onClosing: " + channel)).subscribe();
                    });
                }
            };
        }
    }
}
