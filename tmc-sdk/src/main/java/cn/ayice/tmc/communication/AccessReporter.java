package cn.ayice.tmc.communication;

import cn.ayice.tmc.model.AccessEvent;
import cn.ayice.tmc.sdk.TmcMetrics;
import cn.ayice.tmc.util.JsonUtils;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class AccessReporter {

    private final AccessReportProperties properties;
    private final TmcMetrics metrics;
    private final BlockingQueue<AccessEvent> queue;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Thread worker;

    private Socket socket;
    private BufferedWriter writer;

    public AccessReporter(AccessReportProperties properties, TmcMetrics metrics) {
        if (properties == null) {
            throw new IllegalArgumentException("properties must not be null");
        }
        if (metrics == null) {
            throw new IllegalArgumentException("metrics must not be null");
        }
        this.properties = properties;
        this.metrics = metrics;
        this.queue = new ArrayBlockingQueue<>(properties.getQueueCapacity());
        this.worker = new Thread(this::runWorker, "tmc-access-reporter");
        this.worker.setDaemon(true);
        if (properties.isEnabled()) {
            this.worker.start();
        }
    }

    public void report(AccessEvent event) {
        if (event == null || !properties.isEnabled() || !running.get()) {
            return;
        }
        try {
            if (queue.offer(event)) {
                incrementSafely(metrics::incrementReportQueued);
            } else {
                incrementSafely(metrics::incrementReportDropped);
            }
        } catch (RuntimeException e) {
            incrementSafely(metrics::incrementReportDropped);
        }
    }

    public void close() {
        if (running.compareAndSet(true, false)) {
            worker.interrupt();
            try {
                worker.join(properties.getFlushIntervalMillis() * 2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            closeSocket();
        }
    }

    private void runWorker() {
        while (running.get() || !queue.isEmpty()) {
            List<AccessEvent> batch = drainBatch();
            if (batch.isEmpty()) {
                continue;
            }
            try {
                writeBatch(batch);
                incrementSafely(() -> metrics.incrementReportSucceeded(batch.size()));
            } catch (RuntimeException e) {
                incrementSafely(() -> metrics.incrementReportFailed(batch.size()));
                closeSocket();
            }
        }
        closeSocket();
    }

    private List<AccessEvent> drainBatch() {
        List<AccessEvent> batch = new ArrayList<>(properties.getBatchSize());
        try {
            AccessEvent first = queue.poll(properties.getFlushIntervalMillis(), TimeUnit.MILLISECONDS);
            if (first == null) {
                return batch;
            }
            batch.add(first);
            queue.drainTo(batch, properties.getBatchSize() - 1);
            return batch;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return batch;
        }
    }

    private void writeBatch(List<AccessEvent> batch) {
        try {
            ensureConnected();
            for (AccessEvent event : batch) {
                writer.write(JsonUtils.toJsonLine(event));
            }
            writer.flush();
        } catch (IOException e) {
            throw new IllegalStateException("write access event to rsyslog failed", e);
        }
    }

    private void ensureConnected() throws IOException {
        if (socket != null && socket.isConnected() && !socket.isClosed()) {
            return;
        }
        RsyslogProperties rsyslog = properties.getRsyslog();
        Socket newSocket = new Socket();
        newSocket.connect(
                new InetSocketAddress(rsyslog.getHost(), rsyslog.getPort()),
                rsyslog.getConnectTimeoutMillis()
        );
        newSocket.setSoTimeout(rsyslog.getWriteTimeoutMillis());
        socket = newSocket;
        writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    private void closeSocket() {
        closeWriter();
        closeCurrentSocket();
    }

    private void closeWriter() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException ignored) {
                // Access reporting must not affect the business read path.
            } finally {
                writer = null;
            }
        }
    }

    private void closeCurrentSocket() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Access reporting must not affect the business read path.
            } finally {
                socket = null;
            }
        }
    }

    private static void incrementSafely(Runnable increment) {
        try {
            increment.run();
        } catch (RuntimeException ignored) {
            // Metrics must never affect the business read path.
        }
    }
}
