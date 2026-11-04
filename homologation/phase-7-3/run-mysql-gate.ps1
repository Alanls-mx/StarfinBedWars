param(
    [Parameter(Mandatory=$true)][string]$LabPath,
    [Parameter(Mandatory=$true)][string]$JavaHome,
    [switch]$ConfirmLab
)
$ErrorActionPreference = 'Stop'
$lab = [System.IO.Path]::GetFullPath($LabPath)
if (-not (Test-Path -LiteralPath (Join-Path $lab '.phase73-lab') -PathType Leaf)) { throw "Laboratorio nao reconhecido: $lab" }
Write-Host "Destino exclusivo dos testes SQL: $lab / schema sbw_*_test_local"
if (-not $ConfirmLab) { throw 'Os testes modificam apenas schemas descartaveis. Reexecute com -ConfirmLab.' }
$container = 'sbw-phase73-lab-mysql-1'
$running = docker inspect $container --format '{{.State.Running}}' 2>$null
if ($LASTEXITCODE -ne 0 -or $running -ne 'true') { throw "MySQL do laboratorio parado: $container" }
$schemas = @(
    'sbw_phase2_test_local','sbw_vault_test_local','sbw_ranked_test_local',
    'sbw_ranked_test_admission_local','ranked63_test_local','sbw_tournament_test_local',
    'sbw_proxy_test_local','sbw_ranked_test_proxy_local','sbw_tournament_proxy_test_local'
)
$sql = New-Object System.Text.StringBuilder
foreach ($schema in $schemas) {
    [void]$sql.AppendLine("CREATE DATABASE IF NOT EXISTS $schema;")
    [void]$sql.AppendLine("CREATE TABLE IF NOT EXISTS $schema.starfin_disposable_test_schema (marker VARCHAR(40) NOT NULL);")
    [void]$sql.AppendLine("INSERT INTO $schema.starfin_disposable_test_schema SELECT 'phase72-disposable-v1' WHERE NOT EXISTS (SELECT 1 FROM $schema.starfin_disposable_test_schema);")
    [void]$sql.AppendLine("GRANT ALL ON $schema.* TO 'starfin_lab'@'%';")
}
[void]$sql.AppendLine('FLUSH PRIVILEGES;')
$sql.ToString() | docker exec -i $container sh -c 'MYSQL_PWD="$(cat /run/secrets/mysql_root)" mysql -uroot'
if ($LASTEXITCODE -ne 0) { throw 'Falha ao preparar schemas descartaveis.' }
$password = [System.IO.File]::ReadAllText((Join-Path $lab '.secrets\mysql-lab.txt')).Trim()
$env:JAVA_HOME = [System.IO.Path]::GetFullPath($JavaHome)
if (-not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe') -PathType Leaf)) { throw "Java nao encontrado: $env:JAVA_HOME" }
$env:Path = (Join-Path $env:JAVA_HOME 'bin') + [System.IO.Path]::PathSeparator + $env:Path
foreach ($prefix in @('SBW_TEST_DB','SBW_VAULT_TEST','SBW_RANKED_TEST_DB','SBW_RANKED_TEST','SBW_TOURNAMENT_TEST','SM_TEST_MYSQL')) {
    [Environment]::SetEnvironmentVariable($prefix + '_HOST','127.0.0.1')
    [Environment]::SetEnvironmentVariable($prefix + '_PORT','33308')
    [Environment]::SetEnvironmentVariable($prefix + '_USER','starfin_lab')
    [Environment]::SetEnvironmentVariable($prefix + '_PASSWORD',$password)
}
$env:SBW_TEST_DB_SCHEMA='sbw_phase2_test_local'
$env:SBW_VAULT_TEST_SCHEMA='sbw_vault_test_local'
$env:SBW_RANKED_TEST_DB_SCHEMA='sbw_ranked_test_local'
$env:SBW_RANKED_ADMISSION_DB_SCHEMA='sbw_ranked_test_admission_local'
$env:SBW_RANKED_TEST_SCHEMA='ranked63_test_local'
$env:SBW_TOURNAMENT_TEST_SCHEMA='sbw_tournament_test_local'
$env:SM_TEST_MYSQL_DATABASE='sbw_proxy_test_local'
$env:SM_RANKED_TEST_MYSQL_DATABASE='sbw_ranked_test_proxy_local'
$env:SM_TOURNAMENT_TEST_MYSQL_DATABASE='sbw_tournament_proxy_test_local'
$repos = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$mini = (Resolve-Path (Join-Path $repos '..\StarfinMinigames')).Path
Write-Host 'Executando gate MySQL do StarfinMinigames...'
Push-Location $mini
try {
    mvn -B -q clean verify '-DrequireIntegrationTests=true'
    if ($LASTEXITCODE -ne 0) { throw 'Gate Minigames falhou.' }
    python ci/verify_build.py minigames
    if ($LASTEXITCODE -ne 0) { throw 'Verificador de JAR Minigames falhou.' }
} finally { Pop-Location }
Write-Host 'Executando gate MySQL do StarfinBedWars...'
Push-Location $repos
try {
    mvn -B -q clean verify '-DrequireIntegrationTests=true'
    if ($LASTEXITCODE -ne 0) { throw 'Gate BedWars falhou.' }
    python ci/verify_build.py bedwars
    if ($LASTEXITCODE -ne 0) { throw 'Verificador de JAR BedWars falhou.' }
} finally { Pop-Location }
Write-Host 'Gates MySQL e verificacoes de JAR concluidos sem falha.'
