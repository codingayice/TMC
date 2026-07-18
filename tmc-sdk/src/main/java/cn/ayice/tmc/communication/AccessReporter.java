package cn.ayice.tmc.communication;

import cn.ayice.tmc.model.AccessEvent;
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

/**
 * SDK 访问事件上报器。
 *
 * <p>业务线程调用 {@link #report(AccessEvent)} 时只做非阻塞入队；
 * 后台 daemon 线程负责批量把事件作为 JSON line 写入 rsyslog。
 * 该组件是热点探测旁路，任何异常都不能影响业务读请求。</p>
 */
public class AccessReporter {

    /**
     * 上报配置，包括队列容量、批量大小和 rsyslog 地址。
     */
    private final AccessReportProperties properties;

    /**
     * 有界队列。rsyslog 不可用或写入变慢时，队列满会丢弃事件而不是拖慢业务线程。
     */
    private final BlockingQueue<AccessEvent> queue;

    /**
     * 后台线程运行标记。
     */
    private final AtomicBoolean running = new AtomicBoolean(true);

    /**
     * 负责真正写 rsyslog 的后台线程。
     */
    private final Thread worker;

    /**
     * 当前 TCP socket。写失败后会关闭，下次批量写入时重新连接。
     */
    private Socket socket;

    /**
     * 当前 socket 对应的字符输出流。
     */
    private BufferedWriter writer;

    public AccessReporter(AccessReportProperties properties) {
        if (properties == null) {
            throw new IllegalArgumentException("properties must not be null");
        }
        this.properties = properties;
        this.queue = new ArrayBlockingQueue<>(properties.getQueueCapacity());
        this.worker = new Thread(this::runWorker, "tmc-access-reporter");
        this.worker.setDaemon(true);
        if (properties.isEnabled()) {
            this.worker.start();
        }
    }

    /**
     * 上报单条访问事件。
     *
     * <p>使用 {@code offer} 而不是 {@code put}，保证业务线程永远不会因为上报队列满而阻塞。</p>
     */
    public void report(AccessEvent event) {
        if (event == null || !properties.isEnabled() || !running.get()) {
            return;
        }
        try {
            queue.offer(event);
        } catch (RuntimeException ignored) {
            // 上报队列异常属于旁路失败，业务读路径不感知。
        }
    }

    /**
     * 停止后台线程并关闭 socket。
     */
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

    /**
     * 后台消费循环。
     *
     * <p>关闭时会继续处理队列中剩余事件，尽量减少正常停机时的事件丢失。</p>
     */
    private void runWorker() {
        while (running.get() || !queue.isEmpty()) {
            List<AccessEvent> batch = drainBatch();
            if (batch.isEmpty()) {
                continue;
            }
            try {
                writeBatch(batch);
            } catch (RuntimeException ignored) {
                closeSocket();
            }
        }
        closeSocket();
    }

    /**
     * 从队列中取出一个批次。
     *
     * <p>先 poll 等待第一条事件，再 drainTo 追加同批其他事件，避免空转。</p>
     */
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

    /**
     * 将一个批次写入 rsyslog。
     */
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

    /**
     * 确保当前有可用 socket。
     *
     * <p>连接采用懒加载：只有真正需要写事件时才连接 rsyslog。写失败后关闭 socket，
     * 下一批事件会重新连接，便于 rsyslog 短暂重启后自动恢复。</p>
     */
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

    /**
     * 关闭当前 writer 和 socket。
     */
    private void closeSocket() {
        closeWriter();
        closeCurrentSocket();
    }

    /**
     * 安全关闭 writer。
     */
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

    /**
     * 安全关闭 socket。
     */
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

}
