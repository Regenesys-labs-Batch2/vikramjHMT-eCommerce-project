[CmdletBinding()]
param(
    [switch]$Build
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot

if ([string]::IsNullOrWhiteSpace($env:JWT_SECRET) -or $env:JWT_SECRET.Length -lt 32) {
    throw "JWT_SECRET must be set in the environment and contain at least 32 characters."
}

$definitions = @(
    @{ Name = "identity"; Directory = "identity-service"; Artifact = "identity-service"; Port = 8081; Environment = @{} },
    @{ Name = "catalog"; Directory = "catalog-service"; Artifact = "catalog-service"; Port = 8082; Environment = @{} },
    @{ Name = "inventory"; Directory = "inventory-service"; Artifact = "inventory-service"; Port = 8083; Environment = @{} },
    @{ Name = "cart"; Directory = "cart-service"; Artifact = "cart-service"; Port = 8084; Environment = @{ CATALOG_URL = "http://localhost:8082" } },
    @{ Name = "payment"; Directory = "payment-service"; Artifact = "payment-service"; Port = 8086; Environment = @{} },
    @{ Name = "order"; Directory = "order-service"; Artifact = "order-service"; Port = 8085; Environment = @{
            CART_URL = "http://localhost:8084"
            INVENTORY_URL = "http://localhost:8083"
            PAYMENT_URL = "http://localhost:8086"
        } },
    @{ Name = "platform-ui"; Directory = "platform-ui"; Artifact = "platform-ui"; Port = 8090; Environment = @{
            IDENTITY_URL = "http://localhost:8081"
            CATALOG_URL = "http://localhost:8082"
            INVENTORY_URL = "http://localhost:8083"
            CART_URL = "http://localhost:8084"
            ORDER_URL = "http://localhost:8085"
            PAYMENT_URL = "http://localhost:8086"
        } }
)

# This repository has no platform-ui module; skip any definition whose folder is absent.
$definitions = @($definitions | Where-Object {
        $present = Test-Path (Join-Path $root $_.Directory)
        if (-not $present) { Write-Warning "Skipping $($_.Name): folder '$($_.Directory)' not found." }
        $present
    })

function Get-ServiceJar {
    param(
        [hashtable]$Definition
    )

    $target = Join-Path (Join-Path $root $Definition.Directory) "target"
    if (-not (Test-Path $target)) {
        throw "Missing build directory: $target. Build $($Definition.Name) first."
    }

    $jars = @(Get-ChildItem -Path $target -Filter "$($Definition.Artifact)-*.jar" -File |
        Where-Object { $_.Name -notlike "original-*" } |
        Sort-Object LastWriteTime -Descending)
    if ($jars.Count -eq 0) {
        throw "Missing executable JAR for $($Definition.Name) in $target. Run Maven package first."
    }
    return $jars[0].FullName
}

if ($Build) {
    foreach ($definition in $definitions) {
        Write-Host "Building $($definition.Name)..."
        Push-Location (Join-Path $root $definition.Directory)
        try {
            & mvn -B -ntp -q package
            if ($LASTEXITCODE -ne 0) {
                throw "Maven build failed for $($definition.Name)."
            }
        } finally {
            Pop-Location
        }
    }
}

$processes = @()
foreach ($definition in $definitions) {
    $jar = Get-ServiceJar $definition
    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = "java"
    $startInfo.Arguments = "-jar `"$jar`""
    $startInfo.WorkingDirectory = Join-Path $root $definition.Directory
    $startInfo.UseShellExecute = $false
    $startInfo.EnvironmentVariables["SERVER_PORT"] = [string]$definition.Port
    foreach ($key in $definition.Environment.Keys) {
        $startInfo.EnvironmentVariables[$key] = [string]$definition.Environment[$key]
    }

    $process = [System.Diagnostics.Process]::Start($startInfo)
    if ($null -eq $process) {
        throw "Could not start $($definition.Name)."
    }
    $processes += $process
    Write-Host ("Started {0} on http://localhost:{1} (PID {2})" -f $definition.Name, $definition.Port, $process.Id)
}

Write-Host ""
Write-Host "Open http://localhost:8090 for the Commerce Control Center."
Write-Host "The browser defaults to Demo mode; switch to Live API after the services are healthy."
Write-Host "Stop the listed Java processes with Stop-Process -Id <PID> when finished."
