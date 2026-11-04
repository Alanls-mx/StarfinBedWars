# Testes MySQL descartáveis — Fase 7.2

## Pré-requisitos e ordem

Use Java 8, Maven, Git e MySQL 8.0 em `127.0.0.1`. O BedWars também precisa do Spigot 1.8.8/NMS instalado no Maven local pelo [BuildTools oficial](https://www.spigotmc.org/wiki/buildtools/) (`java -jar BuildTools.jar --rev 1.8.8`). Compile primeiro a API do `StarfinSocial` com `mvn -B -pl starfinsocial-api -am clean install`, depois `StarfinMinigames` com `mvn -B clean install -DrequireIntegrationTests=true` e enfim `StarfinBedWars` com `mvn -B clean verify -DrequireIntegrationTests=true`. Os workflows executam essa ordem; não dependem do Docker do desenvolvedor. A suíte completa do StarfinSocial fica fora do escopo obrigatório desta fase; seus testes JDBC de amizade e Party usam configuração própria e não são acionados pelo CI 7.2.

`mvn -B clean verify` sem a propriedade obrigatória executa a suíte unitária e deixa as suítes JDBC ignoradas. O gate completo exige `-DrequireIntegrationTests=true`; falta de configuração, banco indisponível ou schema inseguro causa falha. `SBW_INTEGRATION_TESTS=1` e `SM_INTEGRATION_TESTS=1` permitem executar JDBC localmente sem a propriedade, mas o gate do CI usa sempre a propriedade.

## Banco descartável

Exemplo local: `docker run --rm -d --name sbw-mysql-test -p 127.0.0.1:33307:3306 -e MYSQL_ALLOW_EMPTY_PASSWORD=yes mysql:8.0`. Aguarde `docker exec sbw-mysql-test mysqladmin ping -h 127.0.0.1 -uroot`. Em Linux ou Windows PowerShell, crie os schemas e a marca antes dos testes:

```sql
CREATE DATABASE sbw_phase2_test_local;
CREATE TABLE sbw_phase2_test_local.starfin_disposable_test_schema (marker VARCHAR(40) NOT NULL);
INSERT INTO sbw_phase2_test_local.starfin_disposable_test_schema VALUES ('phase72-disposable-v1');
```

Repita para `sbw_vault_test_local`, `sbw_ranked_test_local`, `sbw_ranked_test_admission_local`, `ranked63_test_local`, `sbw_tournament_test_local`, `sbw_proxy_test_local`, `sbw_ranked_test_proxy_local` e `sbw_tournament_proxy_test_local`. Exemplo de execução: `docker exec -i sbw-mysql-test mysql -uroot < schemas.sql` no Linux; no PowerShell, `Get-Content -Raw schemas.sql | docker exec -i sbw-mysql-test mysql -uroot`. O CI cria os nove schemas com sufixo `_ci` em serviço MySQL 8.0 efêmero.

Configure as variáveis abaixo apenas no processo do teste. `HOST=127.0.0.1`, `PORT=33307`, `USER=root`, `PASSWORD=` no exemplo Docker local. Não salve senha em arquivo versionado.

| Grupo | Variáveis | Schema de exemplo |
| --- | --- | --- |
| Estatísticas | `SBW_TEST_DB_HOST/PORT/SCHEMA/USER/PASSWORD` | `sbw_phase2_test_local` |
| Vault | `SBW_VAULT_TEST_HOST/PORT/SCHEMA/USER/PASSWORD` | `sbw_vault_test_local` |
| Ranqueada | `SBW_RANKED_TEST_DB_HOST/PORT/SCHEMA/USER/PASSWORD` | `sbw_ranked_test_local` |
| Admissão ranqueada | mesmas variáveis, mais `SBW_RANKED_ADMISSION_DB_SCHEMA` | `sbw_ranked_test_admission_local` |
| Temporadas | `SBW_RANKED_TEST_HOST/PORT/SCHEMA/USER/PASSWORD` | `ranked63_test_local` |
| Torneios | `SBW_TOURNAMENT_TEST_HOST/PORT/SCHEMA/USER/PASSWORD` | `sbw_tournament_test_local` |
| Proxy | `SM_TEST_MYSQL_HOST/PORT/DATABASE/USER/PASSWORD` | `sbw_proxy_test_local` |
| Fila ranqueada do proxy | mesmas variáveis, mais `SM_RANKED_TEST_MYSQL_DATABASE` | `sbw_ranked_test_proxy_local` |
| Fila de torneio do proxy | mesmas variáveis, mais `SM_TOURNAMENT_TEST_MYSQL_DATABASE` | `sbw_tournament_proxy_test_local` |

Exemplo PowerShell: `$env:SBW_TOURNAMENT_TEST_HOST='127.0.0.1'; $env:SBW_TOURNAMENT_TEST_PORT='33307'; $env:SBW_TOURNAMENT_TEST_SCHEMA='sbw_tournament_test_local'; $env:SBW_TOURNAMENT_TEST_USER='root'; $env:SBW_TOURNAMENT_TEST_PASSWORD=''; mvn -B test '-Dtest=TournamentRepositoryIntegrationTest' -DrequireIntegrationTests=true`. Em Bash, use `export NOME=valor` para cada variável antes de `mvn`.

## Proteção e diagnóstico

Cada suíte verifica, **antes do primeiro `DROP` ou fixture**, a flag explícita, host loopback, prefixo de schema, bloqueio de nomes de produção, `SELECT DATABASE()` e a marca `starfin_disposable_test_schema`. A marca deve ser criada somente em schema efêmero. Mesmo um túnel local para um banco de produção é proibido. Nunca use schemas `starfin`, `minecraft`, `production` ou equivalentes. Apenas os nove schemas efêmeros criados para esta execução podem ser apagados, depois de confirmar o contêiner/host correto. Os testes não apagam o schema inteiro.

Os relatórios estão em `target/surefire-reports/TEST-*.xml` e, no Minigames, em `*/target/surefire-reports/`. `python ci/verify_build.py bedwars` ou `python ci/verify_build.py minigames` falha se faltar suíte crítica, houver skip/falha, JAR incompleto ou bytecode acima de Java 8. Para falha de conexão, confira o health check, porta, variáveis e marca; para migração, use schema novo e leia o XML da suíte; para falha de empacotamento, confira BuildTools e dependências `provided`. O [workflow do BedWars](.github/workflows/mysql-validation.yml) e o workflow equivalente do Minigames aceitam `workflow_dispatch` no GitHub Actions e publicam relatórios e logs mesmo em caso de falha.
