FROM ubuntu:24.04

RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        ca-certificates \
        rsyslog \
        rsyslog-kafka \
    && rm -rf /var/lib/apt/lists/*

EXPOSE 5514/tcp 5514/udp

CMD ["rsyslogd", "-n", "-f", "/etc/rsyslog.conf"]

