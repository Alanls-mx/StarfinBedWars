param([Parameter(Mandatory=$true)][string]$LabPath)
$ErrorActionPreference = 'Stop'
$lab = [System.IO.Path]::GetFullPath($LabPath)
if (-not (Test-Path -LiteralPath (Join-Path $lab '.phase73-lab') -PathType Leaf)) {
    throw "Marcador do laboratorio ausente: $lab"
}
Write-Host "Laboratorio: $lab"
Write-Host 'Estado dos containers:'
docker compose --project-name sbw-phase73-lab --file (Join-Path $lab 'compose.yml') ps
Write-Host 'Portas locais:'
foreach ($port in @(45577, 33308)) {
    $listener = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    Write-Host "  127.0.0.1:$port = $(if ($listener) { 'LISTEN' } else { 'CLOSED' })"
}
Write-Host 'Ultimas mensagens de erro dos nos:'
foreach ($node in @('proxy','lobby','bedwars-01','bedwars-02')) {
    $log = Join-Path $lab "$node/logs/latest.log"
    if (-not (Test-Path -LiteralPath $log)) { Write-Host "  $node`: log ausente"; continue }
    $matches = Select-String -LiteralPath $log -Pattern '(?i)(exception|error|severe|failed|disabled|unavailable)' | Select-Object -Last 12
    Write-Host "  $node`: $($matches.Count) mensagens recentes relevantes"
    foreach ($match in $matches) {
        # A linha pode incluir identificadores operacionais; segredos em configs nunca sao lidos aqui.
        Write-Host "    $($match.LineNumber): $($match.Line)"
    }
}
