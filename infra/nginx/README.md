# TMC Demo Nginx

`tmc-demo.conf` 用于在 Demo 服务器上提供统一访问入口。

部署结构：

```text
用户浏览器 / 压测工具
  -> http://<demo-host>
  -> Nginx:80
  -> tmc-demo-a:8081 / tmc-demo-b:8082
```

Prometheus 仍然分别采集 `8081` 和 `8082`，这样 Grafana 可以聚合展示整体指标，也可以通过 `client_id` 区分两个 SDK 节点。
