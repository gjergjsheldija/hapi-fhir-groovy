package com.gjergjsheldija.app;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Minimal app backend for tests: records the last request and replies with a configurable response. */
final class StubApp implements AutoCloseable {

	private final HttpServer server;
	private final ExecutorService executor = Executors.newCachedThreadPool();
	final AtomicInteger calls = new AtomicInteger();
	volatile int status = 200;
	volatile String contentType = "application/json";
	volatile String body = "{\"ok\":true}";
	volatile long delayMs = 0;
	volatile String lastBody;
	volatile Headers lastHeaders;

	StubApp() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			calls.incrementAndGet();
			lastHeaders = exchange.getRequestHeaders();
			lastBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			if (delayMs > 0) {
				try {
					Thread.sleep(delayMs);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
			byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", contentType);
			exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
			if (bytes.length > 0) {
				exchange.getResponseBody().write(bytes);
			}
			exchange.close();
		});
		server.setExecutor(executor);
		server.start();
	}

	String url() {
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	@Override
	public void close() {
		server.stop(0);
		executor.shutdownNow();
	}
}
