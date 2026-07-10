param(
    [ValidateSet("Local", "Remote")]
    [string]$Mode = "Local",

    [string]$Topic = "tmc-access-events",
    [string]$RsyslogHost = "127.0.0.1",
    [int]$RsyslogPort = 5514,
    [int]$TimeoutMs = 8000,

    [string]$ComposeFile = "docker-compose.yml",

    [string]$AppHost = "",
    [string]$AppUser = "",

    [string]$KafkaHost = "",
    [string]$KafkaUser = "",
    [string]$KafkaContainer = "tmc-kafka"
)

$ErrorActionPreference = "Stop"

function Assert-Command {
    param([string]$Name)

    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command not found: $Name"
    }
}

function New-SshTarget {
    param(
        [string]$User,
        [string]$HostName
    )

    if ([string]::IsNullOrWhiteSpace($HostName)) {
        throw "SSH host must not be empty."
    }
    if ([string]::IsNullOrWhiteSpace($User)) {
        return $HostName
    }
    return "$User@$HostName"
}

function Invoke-DockerCompose {
    param([string[]]$Arguments)

    $dockerArgs = @("compose")
    if (-not [string]::IsNullOrWhiteSpace($ComposeFile)) {
        $dockerArgs += @("-f", $ComposeFile)
    }
    $dockerArgs += $Arguments
    & docker @dockerArgs
}

function New-SmokePayload {
    param(
        [string]$SmokeKey,
        [string]$ClientId
    )

    $event = [ordered]@{
        appName = "tmc-smoke"
        key = $SmokeKey
        timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
        weight = 1
        clientId = $ClientId
        operation = "GET"
    }

    return ($event | ConvertTo-Json -Compress) + "`n"
}

function Send-RsyslogEvent {
    param(
        [string]$HostName,
        [int]$Port,
        [string]$Payload
    )

    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $client.Connect($HostName, $Port)
        $stream = $client.GetStream()
        try {
            $bytes = [System.Text.Encoding]::UTF8.GetBytes($Payload)
            $stream.Write($bytes, 0, $bytes.Length)
            $stream.Flush()
        } finally {
            $stream.Dispose()
        }
    } finally {
        $client.Dispose()
    }
}

function Send-RemoteRsyslogEvent {
    param(
        [string]$HostName,
        [string]$User,
        [int]$Port,
        [string]$Payload
    )

    $target = New-SshTarget -User $User -HostName $HostName
    $encodedPayload = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($Payload))
    $python = "import base64,socket; p=base64.b64decode('$encodedPayload').decode(); s=socket.create_connection(('127.0.0.1',$Port),timeout=5); s.sendall(p.encode()); s.close()"
    $remoteCommand = "python3 -c `"$python`""
    & ssh $target $remoteCommand
}

function Assert-LocalInfrastructure {
    Write-Host "Checking Redis..."
    Invoke-DockerCompose @("exec", "-T", "redis", "redis-cli", "ping")

    Write-Host "`nChecking etcd..."
    Invoke-DockerCompose @("exec", "-T", "etcd", "etcdctl", "endpoint", "health", "--endpoints=http://127.0.0.1:2379")

    Write-Host "`nChecking Kafka topic..."
    $topics = Invoke-DockerCompose @("exec", "-T", "kafka", "kafka-topics.sh", "--bootstrap-server", "localhost:9092", "--list")
    if ($topics -notcontains $Topic) {
        throw "Kafka topic $Topic was not found. Topics: $topics"
    }
    Write-Host $topics
}

function Assert-RemoteKafkaTopic {
    param(
        [string]$HostName,
        [string]$User,
        [string]$ContainerName
    )

    $target = New-SshTarget -User $User -HostName $HostName
    Write-Host "`nChecking remote Kafka topic on $target..."
    $remoteCommand = "docker exec $ContainerName kafka-topics.sh --bootstrap-server localhost:9092 --list"
    $topics = & ssh $target $remoteCommand
    if ($topics -notcontains $Topic) {
        throw "Kafka topic $Topic was not found on $target. Topics: $topics"
    }
    Write-Host $topics
}

function Read-LocalKafkaMessages {
    Invoke-DockerCompose @(
        "exec", "-T", "kafka",
        "kafka-console-consumer.sh",
        "--bootstrap-server", "localhost:9092",
        "--topic", $Topic,
        "--from-beginning",
        "--timeout-ms", "$TimeoutMs"
    ) 2>$null
}

function Read-RemoteKafkaMessages {
    param(
        [string]$HostName,
        [string]$User,
        [string]$ContainerName
    )

    $target = New-SshTarget -User $User -HostName $HostName
    $remoteCommand = "docker exec -i $ContainerName kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic $Topic --from-beginning --timeout-ms $TimeoutMs"
    & ssh $target $remoteCommand 2>$null
}

function Assert-SmokeMessage {
    param(
        [string[]]$Messages,
        [string]$SmokeKey,
        [string]$ClientId
    )

    $joined = $Messages -join "`n"
    foreach ($expected in @("tmc-smoke", $SmokeKey, $ClientId, "GET")) {
        if ($joined -notmatch [regex]::Escape($expected)) {
            throw "Smoke event field was not found in Kafka topic $Topic. Missing: $expected"
        }
    }
}

$now = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$smokeKey = "smoke:key:$now"
$clientId = "smoke-$now"
$payload = New-SmokePayload -SmokeKey $smokeKey -ClientId $clientId

if ($Mode -eq "Local") {
    Assert-Command docker
    Assert-LocalInfrastructure

    Write-Host "`nWriting smoke event to rsyslog TCP $RsyslogHost`:$RsyslogPort..."
    Send-RsyslogEvent -HostName $RsyslogHost -Port $RsyslogPort -Payload $payload

    Write-Host "`nReading Kafka smoke event..."
    $messages = Read-LocalKafkaMessages
    Assert-SmokeMessage -Messages $messages -SmokeKey $smokeKey -ClientId $clientId
} else {
    Assert-Command ssh

    if ([string]::IsNullOrWhiteSpace($AppHost)) {
        throw "Remote mode requires -AppHost."
    }
    if ([string]::IsNullOrWhiteSpace($KafkaHost)) {
        throw "Remote mode requires -KafkaHost."
    }

    Assert-RemoteKafkaTopic -HostName $KafkaHost -User $KafkaUser -ContainerName $KafkaContainer

    $appTarget = New-SshTarget -User $AppUser -HostName $AppHost
    Write-Host "`nWriting smoke event on $appTarget to local rsyslog TCP 127.0.0.1:$RsyslogPort..."
    Send-RemoteRsyslogEvent -HostName $AppHost -User $AppUser -Port $RsyslogPort -Payload $payload

    Write-Host "`nReading Kafka smoke event from $KafkaHost..."
    $messages = Read-RemoteKafkaMessages -HostName $KafkaHost -User $KafkaUser -ContainerName $KafkaContainer
    Assert-SmokeMessage -Messages $messages -SmokeKey $smokeKey -ClientId $clientId
}

Write-Host "`nInfrastructure smoke check passed. key=$smokeKey"
