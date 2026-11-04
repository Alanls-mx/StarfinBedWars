param(
    [Parameter(Mandatory=$true)][string]$LabPath,
    [switch]$ConfirmLab
)
$ErrorActionPreference = 'Stop'
$lab = [System.IO.Path]::GetFullPath($LabPath)
if (-not (Test-Path -LiteralPath (Join-Path $lab '.phase73-lab') -PathType Leaf)) { throw "Laboratorio nao reconhecido: $lab" }
$destination = Join-Path $lab ('evidence\' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
Write-Host "Destino da captura: $destination"
if (-not $ConfirmLab) { throw 'Captura escreve evidencias no laboratorio. Reexecute com -ConfirmLab.' }
New-Item -ItemType Directory -Path $destination -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $destination 'screenshots') -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $destination 'videos') -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $lab 'manifest.json') -Destination (Join-Path $destination 'manifest.json')
$jarInventory = foreach ($node in @('proxy','lobby','bedwars-01','bedwars-02')) {
    Get-ChildItem -LiteralPath (Join-Path $lab $node) -Filter '*.jar' -Recurse -File | ForEach-Object {
        [pscustomobject]@{ path = $_.FullName.Substring($lab.Length + 1); sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant() }
    }
}
$jarInventory | ConvertTo-Json -Depth 3 | Out-File -LiteralPath (Join-Path $destination 'runtime-jars.json') -Encoding utf8
$compose = Join-Path $lab 'compose.yml'
docker compose --project-name sbw-phase73-lab --file $compose ps | Out-File -LiteralPath (Join-Path $destination 'containers.txt') -Encoding utf8
docker stats --no-stream --format '{{.Name}} {{.CPUPerc}} {{.MemUsage}}' | Select-String 'sbw-phase73-lab-' | Out-File -LiteralPath (Join-Path $destination 'resources.txt') -Encoding utf8
python (Join-Path $PSScriptRoot 'probe_lab.py') | Out-File -LiteralPath (Join-Path $destination 'proxy-status.json') -Encoding utf8
$query = 'SELECT server_id,online,online_players,ROUND((UNIX_TIMESTAMP()*1000-last_heartbeat)/1000,1) AS heartbeat_age_s FROM sbw_network_servers ORDER BY server_id; SELECT server_id,arena_id FROM sbw_network_arenas ORDER BY server_id; SELECT COUNT(*) AS social_tables FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name LIKE "ss\_%"; SELECT COUNT(*) AS matchmaking_tables FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name LIKE "sm\_%";'
docker exec sbw-phase73-lab-mysql-1 sh -c 'MYSQL_PWD="$(cat /run/secrets/mysql_root)" mysql -N -uroot starfin_phase73 -e "$1"' sh $query | Out-File -LiteralPath (Join-Path $destination 'catalog.txt') -Encoding utf8
@"
Data: $(Get-Date -Format o)
Ambiente: $lab
JARs: manifest.json
JARs presentes no momento da captura: runtime-jars.json (inclui gerados automaticamente).
Logs integrais permanecem nos diretorios proxy/ e <node>/logs/ do laboratorio.
Nao anexe configuracoes com senhas ou segredos.
STATUS: NOT_RUN para todos os cenarios com cliente Minecraft ate registro individual.
"@ | Out-File -LiteralPath (Join-Path $destination 'README.txt') -Encoding utf8
Write-Host "Captura concluida: $destination"
