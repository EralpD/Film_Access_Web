param(
    [ValidateRange(250, 30000)]
    [int]$PollIntervalMs = 1000
)

$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$mavenWrapper = Join-Path $projectRoot 'mvnw.cmd'

if (-not (Test-Path -LiteralPath $mavenWrapper)) {
    throw "Maven wrapper not found: $mavenWrapper"
}

$watcher = Start-Job -ArgumentList $projectRoot, $PollIntervalMs -ScriptBlock {
    param($root, $interval)

    Set-Location -LiteralPath $root
    $sourceRoot = Join-Path $root 'src/main'
    $wrapper = Join-Path $root 'mvnw.cmd'
    $logDirectory = Join-Path $root 'target'
    $compileLog = Join-Path $logDirectory 'dev-compile.log'
    New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null

    function Get-SourceStamp {
        $files = Get-ChildItem -LiteralPath $sourceRoot -Recurse -File |
            Sort-Object FullName |
            ForEach-Object { "$($_.FullName)|$($_.LastWriteTimeUtc.Ticks)|$($_.Length)" }
        return $files -join "`n"
    }

    $lastStamp = Get-SourceStamp
    while ($true) {
        Start-Sleep -Milliseconds $interval
        $currentStamp = Get-SourceStamp
        if ($currentStamp -eq $lastStamp) {
            continue
        }

        # Let editors finish writing before Maven reads the files.
        Start-Sleep -Milliseconds 500
        $lastStamp = Get-SourceStamp
        & $wrapper -q -DskipTests compile *> $compileLog
        if ($LASTEXITCODE -ne 0) {
            Add-Content -LiteralPath $compileLog -Value "Compilation failed. Fix the error and save again to retry."
        }
    }
}

try {
    Set-Location -LiteralPath $projectRoot
    Write-Host 'Development mode: source changes compile automatically; DevTools restarts the app after Java changes.'
    Write-Host 'Compile output: target/dev-compile.log'
    & $mavenWrapper '-Dspring-boot.run.profiles=dev' 'spring-boot:run'
} finally {
    Stop-Job -Job $watcher -ErrorAction SilentlyContinue
    Remove-Job -Job $watcher -Force -ErrorAction SilentlyContinue
}
