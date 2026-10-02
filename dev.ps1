<#
.SYNOPSIS
    Launches PlayerEmotes test environments for any Minecraft version / loader.

.DESCRIPTION
    Without parameters an interactive menu asks for the target and mode.

    Modes:
      client   One dev client (username "Dev")
      server   One dedicated dev server (offline mode)
      mp       Dedicated server + two clients (PlayerA, PlayerB) that join it automatically
      clients  Only the two clients, joining an already running server
      showcase Dedicated server + one client that plays every emote in third person

    Every process gets its own window. Clients use run/client and run/client2,
    servers run/server-<mc version>.

.EXAMPLE
    .\dev.ps1
    .\dev.ps1 -Target 1.21.1-fabric -Mode mp
    .\dev.ps1 -Target 1.20.1-forge -Mode client
    .\dev.ps1 -Target 1.21.1-neoforge -Mode mp -Port 25570
#>
param(
    [string]$Target,
    [ValidateSet('client', 'server', 'mp', 'clients', 'showcase')]
    [string]$Mode,
    [int]$Port = 25565,
    # Seconds to wait between starting the two clients (they share the Gradle build)
    [int]$ClientDelay = 20
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$gradle = Join-Path $root 'gradlew.bat'

function Get-Targets {
    $settings = Get-Content (Join-Path $root 'settings.gradle.kts') -Raw
    $targets = @()
    foreach ($m in [regex]::Matches($settings, '(?m)^\s*match\(\s*"([^"]+)"\s*,([^)]*)\)')) {
        $mc = $m.Groups[1].Value
        foreach ($loader in [regex]::Matches($m.Groups[2].Value, '"([^"]+)"')) {
            $targets += "$mc-$($loader.Groups[1].Value)"
        }
    }
    return $targets
}

function Select-FromMenu([string]$title, [string[]]$options) {
    Write-Host ""
    Write-Host $title -ForegroundColor Cyan
    for ($i = 0; $i -lt $options.Count; $i++) {
        Write-Host ("  [{0}] {1}" -f ($i + 1), $options[$i])
    }
    while ($true) {
        $choice = Read-Host "Choose 1-$($options.Count)"
        $index = 0
        if ([int]::TryParse($choice, [ref]$index) -and $index -ge 1 -and $index -le $options.Count) {
            return $options[$index - 1]
        }
        Write-Host "Invalid choice" -ForegroundColor Yellow
    }
}

function Start-GradleWindow([string]$title, [string]$task) {
    # Arguments are quoted: Windows PowerShell would split '-Pmp.port=...' at the dot
    $command = "`$Host.UI.RawUI.WindowTitle = '$title'; Set-Location '$root'; & '$gradle' '$task' '-Pmp.port=$Port' '--console=plain'"
    Start-Process powershell -ArgumentList @('-NoExit', '-NoProfile', '-Command', $command) | Out-Null
    Write-Host "Started $title ($task)" -ForegroundColor Green
}

function Test-PortInUse {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $client.Connect('localhost', $Port)
        return $true
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

function Wait-ForServer([int]$timeoutSeconds = 900) {
    Write-Host "Waiting for the server on localhost:$Port (first start compiles and downloads, this can take a while)..."
    $deadline = (Get-Date).AddSeconds($timeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-PortInUse) { return $true }
        Start-Sleep -Seconds 3
    }
    return $false
}

$targets = Get-Targets
if (-not $Target) {
    $Target = Select-FromMenu 'Which version / loader?' $targets
} elseif ($targets -notcontains $Target) {
    throw "Unknown target '$Target'. Available: $($targets -join ', ')"
}

if (-not $Mode) {
    $modes = @(
        'client  - one client',
        'server  - one dedicated server',
        'mp      - server + two clients (multiplayer test)',
        'clients - two clients joining a running server',
        'showcase - server + client playing every emote in third person'
    )
    $Mode = (Select-FromMenu 'What to launch?' $modes).Split(' ')[0]
}

$project = ":$Target"
switch ($Mode) {
    'client' {
        Start-GradleWindow "Client $Target" "$project`:runClient"
    }
    'server' {
        Start-GradleWindow "Server $Target" "$project`:runServer"
    }
    'mp' {
        if (Test-PortInUse) { throw "Port $Port is already in use (another server running?). Stop it or pass -Port." }
        Start-GradleWindow "Server $Target" "$project`:runMpServer"
        if (-not (Wait-ForServer)) { throw "Server did not come up on port $Port" }
        Start-GradleWindow "PlayerA $Target" "$project`:runMpClientA"
        Start-Sleep -Seconds $ClientDelay
        Start-GradleWindow "PlayerB $Target" "$project`:runMpClientB"
    }
    'showcase' {
        if (-not (Test-PortInUse)) {
            Start-GradleWindow "Server $Target" "$project`:runMpServer"
            if (-not (Wait-ForServer)) { throw "Server did not come up on port $Port" }
        }
        Start-GradleWindow "Showcase $Target" "$project`:runShowcase"
    }
    'clients' {
        Start-GradleWindow "PlayerA $Target" "$project`:runMpClientA"
        Start-Sleep -Seconds $ClientDelay
        Start-GradleWindow "PlayerB $Target" "$project`:runMpClientB"
    }
}
