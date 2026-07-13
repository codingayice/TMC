package cn.ayice.tmc.server.hotkey;

import cn.ayice.tmc.model.HotKeySnapshot;
import cn.ayice.tmc.server.config.EtcdProperties;
import cn.ayice.tmc.server.metrics.TmcServerMetrics;
import cn.ayice.tmc.util.EtcdKeys;
import cn.ayice.tmc.util.JsonUtils;
import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.options.PutOption;
import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 服务端热点快照发布器。
 *
 * <p>HotKeyMappingScheduler 负责识别热点，本类只负责把识别结果写入 etcd。
 * 发布失败只记录指标并返回，不能抛回调度器，否则一次 etcd 抖动会影响后续滑窗推进。</p>
 */
public class HotKeyPublisher implements AutoCloseable {

    /**
     * 服务端 etcd 发布配置。
     */
    private final EtcdProperties properties;

    /**
     * 服务端指标，用于观察热点快照发布是否健康。
     */
    private final TmcServerMetrics metrics;

    /**
     * jetcd 客户端，由 Spring 托管生命周期。
     */
    private final Client client;

    /**
     * 测试专用写入钩子。生产环境为空，直接走 jetcd client。
     */
    private final BiConsumer<String, String> testPutAction;

    /**
     * 测试专用删除钩子。生产环境为空，直接走 jetcd client。
     */
    private final Consumer<String> testDeleteAction;

    public HotKeyPublisher(Client client, EtcdProperties properties, TmcServerMetrics metrics) {
        this(properties, metrics, client, null, null);
    }

    private HotKeyPublisher(
            EtcdProperties properties,
            TmcServerMetrics metrics,
            Client client,
            BiConsumer<String, String> testPutAction,
            Consumer<String> testDeleteAction
    ) {
        this.properties = properties;
        this.metrics = metrics;
        this.client = client;
        this.testPutAction = testPutAction;
        this.testDeleteAction = testDeleteAction;
    }

    /**
     * 创建测试用发布器。
     */
    static HotKeyPublisher forTest(
            EtcdProperties properties,
            TmcServerMetrics metrics,
            BiConsumer<String, String> putAction,
            Consumer<String> deleteAction
    ) {
        return new HotKeyPublisher(properties, metrics, null, putAction, deleteAction);
    }

    /**
     * 发布当前 app 的最新热点快照。
     */
    public void publish(HotKeySnapshot snapshot) {
        try {
            if (snapshot == null || isBlank(snapshot.getAppName())) {
                throw new IllegalArgumentException("snapshot appName must not be blank");
            }
            String path = EtcdKeys.hotKeysPath(snapshot.getAppName());
            String value = JsonUtils.toJson(snapshot);
            put(path, value);
            metrics.incrementHotKeyPublishSucceeded();
        } catch (Exception e) {
            metrics.incrementHotKeyPublishFailed();
        }
    }

    /**
     * 删除某个 app 的热点快照。
     */
    public void delete(String appName) {
        try {
            deletePath(EtcdKeys.hotKeysPath(appName));
            metrics.incrementHotKeyDeleteSucceeded();
        } catch (Exception e) {
            metrics.incrementHotKeyDeleteFailed();
        }
    }

    @Override
    public void close() {
        if (client != null) {
            client.close();
        }
    }

    private static ByteSequence byteSequence(String value) {
        return ByteSequence.from(value, StandardCharsets.UTF_8);
    }

    /**
     * 执行真实写入或测试写入。
     */
    private void put(String path, String value) throws Exception {
        if (!properties.isEnabled()) {
            return;
        }
        if (testPutAction != null) {
            testPutAction.accept(path, value);
            return;
        }
        long leaseId = client.getLeaseClient().grant(properties.getHotKeyLeaseTtlSeconds()).get().getID();
        client.getKVClient()
                .put(byteSequence(path), byteSequence(value), PutOption.builder().withLeaseId(leaseId).build())
                .get();
    }

    /**
     * 执行真实删除或测试删除。
     */
    private void deletePath(String path) throws Exception {
        if (!properties.isEnabled()) {
            return;
        }
        if (testDeleteAction != null) {
            testDeleteAction.accept(path);
            return;
        }
        client.getKVClient().delete(byteSequence(path)).get();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
