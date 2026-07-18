package cn.ayice.tmc.communication;

import cn.ayice.tmc.enums.CacheOperation;
import cn.ayice.tmc.model.InvalidationEvent;
import cn.ayice.tmc.util.EtcdKeys;
import cn.ayice.tmc.util.JsonUtils;
import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.options.PutOption;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * SDK 侧本地缓存失效事件发布器。
 *
 * <p>业务写 Redis 成功后，TmcClient 会先删除当前节点本地缓存，再调用本类把
 * InvalidationEvent 写入 etcd。发布失败只记录指标，不抛给业务写路径。</p>
 */
public class InvalidationReporter implements AutoCloseable {

    /**
     * 当前业务应用名称，用于生成应用隔离的 etcd 路径。
     */
    private final String appName;

    /**
     * 当前 SDK 实例 ID，其他实例收到事件时依赖它过滤自身事件。
     */
    private final String clientId;

    /**
     * 失效广播配置。
     */
    private final InvalidationProperties properties;

    /**
     * jetcd 客户端，生产环境用于写入失效事件。
     */
    private final Client client;

    /**
     * 测试写入钩子。生产环境为空，直接走 jetcd。
     */
    private final BiConsumer<String, String> testPutAction;

    public InvalidationReporter(
            String appName,
            String clientId,
            EtcdProperties etcdProperties,
            InvalidationProperties properties
    ) {
        this(appName, clientId, properties, buildClient(etcdProperties), null);
    }

    /**
     * 供测试子类使用的构造器。
     */
    protected InvalidationReporter(
            String appName,
            String clientId,
            InvalidationProperties properties
    ) {
        this(appName, clientId, properties, null, null);
    }

    private InvalidationReporter(
            String appName,
            String clientId,
            InvalidationProperties properties,
            Client client,
            BiConsumer<String, String> testPutAction
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
        this.client = client;
        this.testPutAction = testPutAction;
    }

    /**
     * 创建测试用发布器。
     */
    static InvalidationReporter forTest(
            String appName,
            String clientId,
            InvalidationProperties properties,
            BiConsumer<String, String> putAction
    ) {
        return new InvalidationReporter(appName, clientId, properties, null, putAction);
    }

    /**
     * 发布一个写操作导致的本地缓存失效事件。
     */
    public void report(String key, CacheOperation operation) {
        try {
            if (!properties.isEnabled() || !properties.isReportEnabled()) {
                return;
            }
            if (isBlank(key)) {
                throw new IllegalArgumentException("key must not be blank");
            }
            if (operation == null || !operation.isWriteOperation()) {
                throw new IllegalArgumentException("operation must be write operation");
            }
            String eventId = UUID.randomUUID().toString();
            InvalidationEvent event = new InvalidationEvent(
                    appName,
                    key,
                    operation,
                    clientId,
                    System.currentTimeMillis(),
                    eventId
            );
            put(EtcdKeys.invalidationEventPath(appName, eventId), JsonUtils.toJson(event));
        } catch (RuntimeException ignored) {
            // 失效事件发布失败属于写后旁路失败，不向业务写路径传播。
        }
    }

    private void put(String path, String value) {
        if (testPutAction != null) {
            testPutAction.accept(path, value);
            return;
        }
        try {
            long leaseId = client.getLeaseClient().grant(properties.getEventTtlSeconds()).get().getID();
            client.getKVClient()
                    .put(byteSequence(path), byteSequence(value), PutOption.builder().withLeaseId(leaseId).build())
                    .get();
        } catch (Exception e) {
            throw new IllegalStateException("write invalidation event failed", e);
        }
    }

    @Override
    public void close() {
        if (client != null) {
            client.close();
        }
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

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
