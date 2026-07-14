package cn.ayice.tmc.communication;

import cn.ayice.tmc.hotkey.CaffeineLocalCache;
import cn.ayice.tmc.model.InvalidationEvent;
import cn.ayice.tmc.sdk.TmcMetrics;
import cn.ayice.tmc.util.EtcdKeys;
import cn.ayice.tmc.util.JsonUtils;
import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KeyValue;
import io.etcd.jetcd.Watch;
import io.etcd.jetcd.options.WatchOption;
import io.etcd.jetcd.watch.WatchEvent;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.context.SmartLifecycle;

/**
 * SDK 侧本地缓存失效事件监听器。
 *
 * <p>该组件 watch 当前 app 的失效事件前缀。收到其他客户端发布的合法事件后，
 * 删除本节点 Caffeine 本地缓存；自身事件、其他 app 事件和非法事件都会被隔离处理。</p>
 */
public class InvalidationListener implements SmartLifecycle, AutoCloseable {

    /**
     * 当前业务应用名称。
     */
    private final String appName;

    /**
     * 当前 SDK 实例 ID，用于过滤自身事件。
     */
    private final String clientId;

    /**
     * 失效监听配置。
     */
    private final InvalidationProperties properties;

    /**
     * 本地缓存，监听到其他节点写事件后删除对应 key。
     */
    private final CaffeineLocalCache localCache;

    /**
     * SDK 指标对象。
     */
    private final TmcMetrics metrics;

    /**
     * jetcd 客户端。
     */
    private final Client client;

    /**
     * watch 句柄，关闭时释放。
     */
    private volatile Watch.Watcher watcher;

    /**
     * 生命周期状态，保证 start/close 幂等。
     */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 独立通信线程池，避免 etcd watch 占用业务线程。
     */
    private final ExecutorService executorService;

    public InvalidationListener(
            String appName,
            String clientId,
            EtcdProperties etcdProperties,
            InvalidationProperties properties,
            CaffeineLocalCache localCache,
            TmcMetrics metrics
    ) {
        this(
                appName,
                clientId,
                properties,
                localCache,
                metrics,
                buildClient(etcdProperties),
                Executors.newSingleThreadExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "tmc-invalidation-listener");
                    thread.setDaemon(true);
                    return thread;
                })
        );
    }

    private InvalidationListener(
            String appName,
            String clientId,
            InvalidationProperties properties,
            CaffeineLocalCache localCache,
            TmcMetrics metrics,
            Client client,
            ExecutorService executorService
    ) {
        if (isBlank(appName)) {
            throw new IllegalArgumentException("appName must not be blank");
        }
        if (isBlank(clientId)) {
            throw new IllegalArgumentException("clientId must not be blank");
        }
        this.appName = appName;
        this.clientId = clientId;
        this.properties = properties;
        this.localCache = localCache;
        this.metrics = metrics;
        this.client = client;
        this.executorService = executorService;
    }

    /**
     * 创建测试用监听器。
     */
    static InvalidationListener forTest(
            String appName,
            String clientId,
            CaffeineLocalCache localCache,
            TmcMetrics metrics
    ) {
        return new InvalidationListener(
                appName,
                clientId,
                new InvalidationProperties(),
                localCache,
                metrics,
                null,
                Executors.newSingleThreadExecutor()
        );
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        executorService.submit(this::startWatch);
    }

    /**
     * 建立 prefix watch，监听当前 app 下每一个独立失效事件。
     */
    private void startWatch() {
        if (client == null || !running.get()) {
            return;
        }
        try {
            ByteSequence prefix = byteSequence(EtcdKeys.invalidationEventPrefix(appName));
            watcher = client.getWatchClient().watch(
                    prefix,
                    WatchOption.builder().withPrefix(prefix).build(),
                    this::handleWatchResponse,
                    throwable -> {
                        metrics.incrementInvalidationWatchReconnect();
                        sleepQuietly(properties.getReconnectDelayMillis());
                        if (running.get()) {
                            startWatch();
                        }
                    }
            );
        } catch (RuntimeException e) {
            metrics.incrementInvalidationWatchFailed();
        }
    }

    private void handleWatchResponse(io.etcd.jetcd.watch.WatchResponse response) {
        for (WatchEvent event : response.getEvents()) {
            if (event.getEventType() == WatchEvent.EventType.DELETE) {
                continue;
            }
            KeyValue keyValue = event.getKeyValue();
            if (keyValue != null) {
                applyEventJson(keyValue.getValue().toString(StandardCharsets.UTF_8));
            }
        }
    }

    /**
     * 应用 etcd value 中的失效事件 JSON。
     */
    void applyEventJson(String json) {
        try {
            InvalidationEvent event = JsonUtils.fromJson(json, InvalidationEvent.class);
            if (!isValid(event)) {
                metrics.incrementInvalidationInvalid();
                return;
            }
            if (!appName.equals(event.getAppName())) {
                return;
            }
            if (clientId.equals(event.getClientId())) {
                metrics.incrementInvalidationSelfIgnored();
                return;
            }
            localCache.invalidate(event.getKey());
            metrics.incrementInvalidationReceived();
        } catch (RuntimeException e) {
            metrics.incrementInvalidationInvalid();
        }
    }

    @Override
    public void stop() {
        close();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public void close() {
        if (!running.getAndSet(false)) {
            return;
        }
        closeQuietly(watcher);
        watcher = null;
        closeQuietly(client);
        executorService.shutdownNow();
    }

    private static boolean isValid(InvalidationEvent event) {
        return event != null
                && !isBlank(event.getAppName())
                && !isBlank(event.getKey())
                && event.getOperation() != null
                && !isBlank(event.getClientId())
                && event.getTimestamp() != null
                && event.getTimestamp() > 0
                && !isBlank(event.getEventId());
    }

    private static Client buildClient(EtcdProperties properties) {
        return Client.builder()
                .endpoints(properties.getEndpoints().toArray(new String[0]))
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis()))
                .build();
    }

    private static ByteSequence byteSequence(String value) {
        return ByteSequence.from(value, StandardCharsets.UTF_8);
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
            // 关闭失败不影响应用退出。
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
