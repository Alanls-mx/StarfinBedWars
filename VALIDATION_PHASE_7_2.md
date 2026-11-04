# Fase 7.2 — validação automatizada e preparação da homologação

## Escopo e estado

Base auditada: StarfinBedWars `580e36f` e StarfinMinigames `f92b907`, ambos em `codex/tournament-phase-7-1`; StarfinSocial API em `codex/party-phase-4-1`. Trabalho nas branches `codex/validation-phase-7-2`, sem merge na `main` e sem instalar JAR em servidor. Arena casual, ranqueada e de torneio continuam desativadas/inalteradas nesta fase. Os 25 cenários Minecraft do [roteiro](STARFINBEDWARS_FINAL_TEST_PLAN.md) continuam `NOT_RUN`.

| Gate | Status | Evidência |
| --- | --- | --- |
| MySQL local 8.0.46, Java 8, BedWars | PASS | `mvn -B -q clean verify -DrequireIntegrationTests=true`: 268 testes, 0 falhas, 0 erros, 0 skips; `python ci/verify_build.py bedwars` PASS. |
| MySQL local 8.0.46, Java 8, Minigames | PASS | `mvn -B -q clean verify -DrequireIntegrationTests=true`: 25 testes, 0 falhas, 0 erros, 0 skips; `python ci/verify_build.py minigames` PASS. |
| StarfinSocial API | PASS | `mvn -B -pl starfinsocial-api -am clean install` em Java 8. A suíte completa do Social não integra o gate 7.2; uma execução exploratória encontrou 5 testes, 2 ignorados por ausência de `SOCIAL_TEST_JDBC`. |
| GitHub Actions BedWars | NOT_RUN | Verificar execução da branch após publicação. |
| GitHub Actions Minigames | NOT_RUN | Verificar execução da branch após publicação. |
| Minecraft 1.8.8 / transporte Bungee / desempenho | NOT_RUN | Roteiro manual, sem simulação nesta fase. |

## Auditoria dos skips iniciais

BedWars: 267 testes, 8 skips por falta das variáveis/banco descartável: `Phase2MySqlIntegrationTest` (1), `VaultPurchaseMySqlIntegrationTest` (1), `RankedMySqlIntegrationTest` (1), `RankedAdmissionMySqlIntegrationTest` (1), `RankedSeasonMariaDbTest` (1) e `TournamentRepositoryIntegrationTest` (3). Minigames: `BungeeDatabaseIntegrationTest`, `RankedQueueIntegrationTest` e `TournamentQueueStoreIntegrationTest` (1 cada). Não eram testes desabilitados por incompatibilidade; `Assume` ocultava a ausência do ambiente na execução comum. Sob `-DrequireIntegrationTests=true`, os helpers exigem configuração e conexão reais, e o parser Surefire rejeita qualquer skip.

## Correções e provas

- Todos os testes JDBC usam schemas isolados com host loopback, prefixo de teste, flag explícita, `SELECT DATABASE()` e a marca `starfin_disposable_test_schema` antes de SQL destrutivo. Nenhuma credencial é versionada. Veja [MYSQL_INTEGRATION_TESTING.md](MYSQL_INTEGRATION_TESTING.md).
- No MySQL descartável, a migração legada de estatísticas/escopo preservou perfil e resultado histórico; duas instâncias abriram a estrutura e registraram resultados sem duplicar XP. Ranqueadas exercitaram ratings e placement concorrentes, política/admissão e temporada. As suítes de proxy exercitaram reservas, fila competitiva e isolamento casual/torneio.
- O teste de torneios cobriu inscrição, confirmação, check-in, chave, substituição, alocação, admissão, empate/replay, MD3, walkover auditado, finalização e resgate único. Duas conexões de proxy disputaram a mesma arena: uma alocação ganhou. Duas instâncias backend disputaram o mesmo resultado: uma gravou. O reenvio após journal persistido e reabertura do repositório aplicou o resultado uma vez. Duas instâncias também reaplicaram `ensureSchema` em paralelo e leram um torneio já persistido.
- Duas falhas apareceram no primeiro teste MySQL real: leitura de títulos quando a tabela opcional de recompensas ranqueadas ainda não existe; fixture de admissão ranqueada sem política do servidor. A leitura agora tolera **somente** a ausência da tabela opcional (SQLState `42S02`, erro `1146`), e a fixture grava a política exigida. O reuso do schema também revelou chave duplicada na fixture; o upsert agora permite reruns sem mascarar dados de produção, pois o guard restringe o destino.
- O shade do Minigames Bungee deixou de copiar `META-INF/versions/**` do driver MySQL: a primeira auditoria encontrou `module-info.class` Java 11, não usado no Java 8. A JAR final contém somente bytecode até versão 52. O verificador confirma `plugin.yml`, `bungee.yml`, classes de torneio/NPC/Match Point, API compartilhada e driver/pool relocados. A checagem Maven existente das classes NMS foi preservada.

## CI, dependências e artefatos

Os dois workflows em `.github/workflows/mysql-validation.yml` usam checkout de StarfinSocial API, Minigames e BedWars na ordem necessária, Java 8, Maven, BuildTools oficial para Spigot 1.8.8, serviço MySQL 8 com health check, nove schemas descartáveis marcados, `-DrequireIntegrationTests=true`, parser Surefire, inspeção de JAR/bytecode e upload de XML/log/JAR mesmo em falha. O Minigames usa três schemas. O CI não depende do Docker local e não publica JAR do Spigot. Dependências `provided` (Spigot, Vault, StarfinCore e API Minigames no backend) mantêm o escopo original; HikariCP e Connector/J permanecem relocados apenas no proxy.

JARs locais verificados: `StarfinBedWars-1.0.0-SNAPSHOT.jar` (SHA-256 `2498EF68ED27DEA57B1FB28FE0C74C14D6C36C01F26FE3961014664BB6B0E125`), `StarfinMinigames-Bungee.jar` (SHA-256 `77F70F095230039FFB876DA69F8EE248193F87BF38E1625AE79F97139D46FC27`). Os hashes mudam se a JAR for reconstruída. Relatórios locais: `target/surefire-reports/` e `starfinminigames-*/target/surefire-reports/`.

## Limites e homologação manual

Os testes com duas conexões JDBC comprovam persistência transacional, não transporte real entre múltiplos proxies. A falha simulada após escrita do journal usa fechamento do repositório antes do commit; queda abrupta do processo e corrupção do arquivo precisam do teste isolado 21. O teste de concorrência não é uma medição de throughput. Nenhum cliente Minecraft ou Pterodactyl foi modificado. Para homologação manual, preparar proxy BungeeCord com StarfinLogin, StarfinSocial e StarfinMinigames; backends Spigot/PaperSpigot 1.8.8 com StarfinCore, Vault/provedor de economia, StarfinMinigames e StarfinBedWars; MySQL **de teste** compartilhado; arenas separadas nos grupos casual, ranked e tournament. Usar credenciais apenas nos YAML privados, `tournaments.enabled: true` somente nos nós de teste, e registrar os 25 cenários. A liberação depende dos gates de CI remotos e da ausência de falhas críticas de persistência.
