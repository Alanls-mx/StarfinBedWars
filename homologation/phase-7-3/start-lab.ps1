param(
    [Parameter(Mandatory=$true)][string]$LabPath,
    [switch]$ConfirmLab
)
$ErrorActionPreference = 'Stop'
$lab = [System.IO.Path]::GetFullPath($LabPath)
if (-not (Test-Path -LiteralPath (Join-Path $lab '.phase73-lab') -PathType Leaf)) {
    throw "Marcador do laboratorio ausente: $lab"
}
if (-not (Test-Path -LiteralPath (Join-Path $lab 'compose.yml') -PathType Leaf)) {
    throw "Compose do laboratorio ausente: $lab"
}
Write-Host "Destino exclusivo: $lab"
Write-Host 'Projeto Docker: sbw-phase73-lab; portas locais 45577 e 33308.'
if (-not $ConfirmLab) {
    throw 'Operacao com estado: execute novamente com -ConfirmLab depois de conferir o destino acima.'
}
docker compose --project-name sbw-phase73-lab --file (Join-Path $lab 'compose.yml') up --detach
if ($LASTEXITCODE -ne 0) { throw 'Falha ao iniciar laboratorio Docker.' }
