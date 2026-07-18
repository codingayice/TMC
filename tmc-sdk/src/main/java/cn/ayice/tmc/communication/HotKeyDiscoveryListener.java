package cn.ayice.tmc.communication;

import cn.ayice.tmc.hotkey.HotKeyManager;
import cn.ayice.tmc.model.HotKeySnapshot;
import cn.ayice.tmc.util.EtcdKeys;
import cn.ayice.tmc.util.JsonUtils;
import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KeyValue;
import io.etcd.jetcd.Watch;
import io.etcd.jetcd.watch.WatchEvent;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.context.SmartLifecycle;

/**
 * SDK 侧热点发现监听器。
 *
 * <p>该组件位于通信模块，负责监听服务端写入 etcd 的 HotKeySnapshot。
 * 它只把合法快照交给 HotKeyManager，不参与 TmcClient 的业务读流程；
 * etcd 抖动、JSON 异常或 watch 中断都只影响热点感知速度，不能影响 Redis 读写。</p>
 */
public class HotKeyDiscoveryListener implements SmartLifecycle, AutoCloseable {

    /**
     * 当前 SDK 所属应用，只监听该应用对应的热点快照路径。
     */
    private final String appName;

    /**
     * etcd 连接配置。
     */
    private final EtcdProperties etcdProperties;

    /**
     * 热点发现行为配置。
     */
    private final HotKeyDiscoveryProperties discoveryProperties;

    /**
     * 本地热点集合管理器，监听器解析快照后只通过它更新内存状态。
     */
    private final HotKeyManager hotKeyManager;

    /**
     * jetcd 客户端，生产环境用于读取和 watch etcd。
     */
    private final Client client;

    /**
     * watch 句柄，关闭 SDK 时需要释放，避免后台连接泄漏。
     */
    private volatile Watch.Watcher watcher;

    /**
     * 最近已经应用过的快照版本，用于避免重复 PUT 导致本地热点集合反复重建。
     */
    private volatile String lastVersion;

    /**
     * 生命周期状态，保证 start/close 幂等。
     */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 独立通信线程池，避免 etcd I/O 占用业务请求线程。
     */
    private final ExecutorService executorService;

    public HotKeyDiscoveryListener(
            String appName,
            EtcdProperties etcdProperties,
            HotKeyDiscoveryProperties discoveryProperties,
            HotKeyManager hotKeyManager
    ) {
        this(
                appName,
                etcdProperties,
                discoveryProperties,
                hotKeyManager,
                buildClient(etcdProperties),
                Executors.newSingleThreadExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "tmc-hotkey-discovery");
                    thread.setDaemon(true);
                    return thread;
                })
        );
    }

    private HotKeyDiscoveryListener(
            String appName,
            EtcdProperties etcdProperties,
            HotKeyDiscoveryProperties discoveryProperties,
            HotKeyManager hotKeyManager,
            Client client,
            ExecutorService executorService
    ) {
        if (isBlank(appName)) {
            throw new IllegalArgumentException("appName must not be blank");
        }
        this.appName = appName;
        this.etcdProperties = etcdProperties;
        this.discoveryProperties = discoveryProperties;
        this.hotKeyManager = hotKeyManager;
        this.client = client;
        this.executorService = executorService;
    }

    /**
     * 创建测试用监听器。
     *
     * <p>测试只验证快照应用逻辑，不连接真实 etcd，因此 client 可以为空。</p>
     */
    static HotKeyDiscoveryListener forTest(String appName, HotKeyManager hotKeyManager) {
        return new HotKeyDiscoveryListener(
                appName,
                new EtcdProperties(),
                new HotKeyDiscoveryProperties(),
                hotKeyManager,
                null,
                Executors.newSingleThreadExecutor()
        );
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        executorService.submit(() -> {
            if (discoveryProperties.isLoadOnStartup()) {
                loadCurrentSnapshot();
            }
            startWatch();
        });
    }

    /**
     * 启动时读取一次当前快照，避免 SDK 在 watch 建立前错过已有热点状态。
     */
    private void loadCurrentSnapshot() {
        if (client == null) {
            return;
        }
        try {
            List<KeyValue> kvs = client.getKVClient()
                    .get(byteSequence(EtcdKeys.hotKeysPath(appName)))
                    .get()
                    .getKvs();
            if (!kvs.isEmpty()) {
                applySnapshotJson(kvs.get(0).getValue().toString(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
            // 启动读取失败只会延迟热点感知，业务读路径继续回 Redis。
        }
    }

    /**
     * 建立 etcd watch。jetcd 回调线程收到事件后只做轻量解析和内存替换。
     */
    private void startWatch() {
        if (client == null || !running.get()) {
            return;
        }
        try {
            watcher = client.getWatchClient().watch(
                    byteSequence(EtcdKeys.hotKeysPath(appName)),
                    this::handleWatchResponse,
                    throwable -> {
                        sleepQuietly(discoveryProperties.getReconnectDelayMillis());
                        if (running.get()) {
                            startWatch();
                        }
                    }
            );
        } catch (RuntimeException ignored) {
            // watch 建立失败只影响热点感知速度，不能影响业务读请求。
        }
    }

    private void handleWatchResponse(io.etcd.jetcd.watch.WatchResponse response) {
        for (WatchEvent event : response.getEvents()) {
            if (event.getEventType() == WatchEvent.EventType.DELETE) {
                handleSnapshotDeleted();
                continue;
            }
            KeyValue keyValue = event.getKeyValue();
            if (keyValue != null) {
                applySnapshotJson(keyValue.getValue().toString(StandardCharsets.UTF_8));
            }
        }
    }

    /**
     * 应用 etcd value 中的热点快照 JSON。
     */
    void applySnapshotJson(String json) {
        try {
            HotKeySnapshot snapshot = JsonUtils.fromJson(json, HotKeySnapshot.class);
            if (snapshot == null || !appName.equals(snapshot.getAppName())) {
                return;
            }
            if (!isBlank(snapshot.getVersion()) && snapshot.getVersion().equals(lastVersion)) {
                return;
            }
            hotKeyManager.updateHotKeySnapshot(snapshot);
            lastVersion = snapshot.getVersion();
        } catch (RuntimeException ignored) {
            // 非法快照直接忽略，等待下一次服务端发布正确快照。
        }
    }

    /**
     * 处理热点快照删除事件。
     */
    void handleSnapshotDeleted() {
        hotKeyManager.clearHotKeys();
        lastVersion = null;
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
            // 关闭失败不影响业务进程退出。
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
