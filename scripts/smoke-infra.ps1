$ErrorActionPreference = "Stop"

Write-Host "Checking Redis..."
docker compose exec -T redis redis-cli ping

Write-Host "`nChecking etcd..."
docker compose exec -T etcd etcdctl endpoint health --endpoints=http://127.0.0.1:2379

Write-Host "`nChecking Kafka topic..."
$topics = docker compose exec -T kafka kafka-topics.sh --bootstrap-server localhost:9092 --list
if ($topics -notcontains "tmc-access-events") {
    throw "Kafka topic tmc-access-events was not found. Topics: $topics"
}
Write-Host $topics

Write-Host "`nChecking rsyslog TCP port..."
$client = [System.Net.Sockets.TcpClient]::new()
$client.Connect("127.0.0.1", 5514)
$stream = $client.GetStream()
$payload = '{"protocolVersion":"1.0","appName":"tmc-smoke","key":"smoke:key","timestamp":1720000000000,"weight":1,"clientId":"smoke-1","operation":"GET"}' + "`n"
$bytes = [System.Text.Encoding]::UTF8.GetBytes($payload)
$stream.Write($bytes, 0, $bytes.Length)
$stream.Dispose()
$client.Dispose()

Write-Host "`nReading Kafka smoke event..."
$messages = docker compose exec -T kafka kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic tmc-access-events --from-beginning --timeout-ms 8000 2>$null
if (($messages -join "`n") -notmatch "smoke:key") {
    throw "Smoke event was not found in Kafka topic tmc-access-events."
}

Write-Host "Infrastructure smoke check passed."

