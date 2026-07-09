# etcd

etcd is used as the coordination layer for hot key publishing and invalidation broadcast.

Planned key prefixes:

```text
/tmc/hotkeys/{appName}
/tmc/invalidation/{appName}/{eventId}
/tmc/config/{appName}
```

Useful commands:

```powershell
docker compose exec etcd etcdctl endpoint health --endpoints=http://127.0.0.1:2379
docker compose exec etcd etcdctl get /tmc --prefix --endpoints=http://127.0.0.1:2379
```

