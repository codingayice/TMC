package cn.ayice.tmc.communication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.ayice.tmc.enums.CacheOperation;
import cn.ayice.tmc.model.AccessEvent;
import cn.ayice.tmc.sdk.TmcMetrics;
import cn.ayice.tmc.util.JsonUtils;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * AccessReporter 异步上报测试。
 *
 * <p>保障访问事件能够被后台线程写成 JSON line，同时验证队列满时丢弃事件而不阻塞业务线程。</p>
 */
class AccessReporterTest {

    @Test
    void shouldWriteAccessEventToRsyslogAsJsonLine() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            CompletableFuture<String> received = CompletableFuture.supplyAsync(() -> readOneLine(serverSocket));
            TmcMetrics metrics = new TmcMetrics();
            AccessReporter reporter = new AccessReporter(properties(serverSocket.getLocalPort(), 10), metrics);

            reporter.report(event("product:1"));

            String line = received.get(3, TimeUnit.SECONDS);
            reporter.close();
            AccessEvent decoded = JsonUtils.fromJson(line, AccessEvent.class);
            assertEquals("product-service", decoded.getAppName());
            assertEquals("product:1", decoded.getKey());
            assertEquals("client-1", decoded.getClientId());
            assertEquals(CacheOperation.GET, decoded.getOperation());
            assertEquals(1, metrics.snapshot().getReportQueued());
            assertEquals(1, metrics.snapshot().getReportSucceeded());
        }
    }

    @Test
    void shouldDropAccessEventWhenQueueIsFull() {
        TmcMetrics metrics = new TmcMetrics();
        AccessReportProperties properties = properties(9, 1);
        properties.setQueueCapacity(1);
        properties.getRsyslog().setConnectTimeoutMillis(1);
        AccessReporter reporter = new AccessReporter(properties, metrics);

        for (int i = 0; i < 1000; i++) {
            reporter.report(event("product:" + i));
        }

        reporter.close();
        assertTrue(metrics.snapshot().getReportDropped() > 0);
    }

    private static AccessReportProperties properties(int port, int flushIntervalMillis) {
        AccessReportProperties properties = new AccessReportProperties();
        properties.setQueueCapacity(16);
        properties.setBatchSize(4);
        properties.setFlushIntervalMillis(flushIntervalMillis);
        properties.getRsyslog().setHost("127.0.0.1");
        properties.getRsyslog().setPort(port);
        properties.getRsyslog().setConnectTimeoutMillis(100);
        return properties;
    }

    private static AccessEvent event(String key) {
        return new AccessEvent(
                "product-service",
                key,
                System.currentTimeMillis(),
                1,
                "client-1",
                CacheOperation.GET
        );
    }

    private static String readOneLine(ServerSocket serverSocket) {
        try (Socket socket = serverSocket.accept();
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.readLine();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
