$ErrorActionPreference = "Stop"

function Assert-Command {
    param([string]$Name)

    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command not found: $Name"
    }
}

Assert-Command java
Assert-Command mvn
Assert-Command docker

Write-Host "Java:"
java -version

Write-Host "`nMaven:"
mvn -version

Write-Host "`nDocker:"
docker --version

Write-Host "`nDocker Compose:"
docker compose version

Write-Host "`nEnvironment check passed."

