# Resultados de homologação — Fase 7.3

Atualizado em 2026-11-04. Use apenas `NOT_RUN`, `PASS`, `FAIL` ou `BLOCKED` para cenários. Nenhum teste manual com Minecraft 1.8.8 foi executado nesta fase até esta atualização; o Estágio B está `NOT_RUN`.

Captura local sem credenciais: `C:\Users\alanl\Documents\StarfinPlugins-workspace\StarfinBedWars-phase73-lab\evidence\20261104-132353` (manifesto, inventário de JARs, catálogo MySQL, contêineres, uso de recursos sem jogadores e status do protocolo 47). Esses dados estão fora do Git.

## Identidade e escopo

| Repositório | Branch auditada | Commit de origem | Mudança nesta fase |
| --- | --- | --- | --- |
| StarfinBedWars | `codex/homologation-phase-7-3` criada de `codex/validation-phase-7-2` | `b747ebe` | scripts e documentação de homologação; código de gameplay intacto |
| StarfinMinigames | `codex/validation-phase-7-2` | `02fec6c` | nenhuma |
| StarfinSocial | `codex/party-phase-4-1` | `0b58deb` | nenhuma |

O `manifest.json` do laboratório registra SHA-256 das JARs de preparação, incluindo StarfinLogin, Guard, Core Bukkit, Vault, EssentialsX, PaperSpigot e BungeeCord. O `runtime-jars.json` da captura datada acrescenta JARs gerados no startup, como StarfinCore do proxy. A compilação do gate local ocorreu depois da cópia das JARs; o inventário do laboratório, e não o artefato Maven recriado depois, identifica esta sessão.

## Estágio A — verificações executadas

| ID | Verificação | Estado | Evidência e limite |
| --- | --- | --- | --- |
| A-01 | Dependências e JARs presentes | PASS | Manifesto com commits/SHA-256; BedWars carregou StarfinCore, Vault, StarfinMinigames e provedor EssentialsX nos dois backends. |
| A-02 | MySQL 8 isolado | PASS | MySQL 8.0.46 no Compose, schema `starfin_phase73`, portas locais. Não toca o banco Pterodactyl. |
| A-03 | StarfinSocial JDBC completo | PASS | `mvn -B -q clean verify` com `SOCIAL_TEST_JDBC` no schema descartável `ss_phase73_test`: 22 testes, zero falhas/erros/skips. |
| A-04 | Gate StarfinMinigames 7.2 | PASS | `mvn -B -q clean verify -DrequireIntegrationTests=true` e `ci/verify_build.py minigames`: 25 testes, zero falhas/erros/skips; 3 suítes JDBC obrigatórias e JARs Java 8. |
| A-05 | Gate StarfinBedWars 7.2 | PASS | Mesmo comando e `ci/verify_build.py bedwars`: 268 testes, zero falhas/erros/skips; 6 suítes JDBC obrigatórias, 450 classes na JAR. |
| A-06 | Proxy Bungee e protocolo 1.8 | PASS | `probe_lab.py`: `127.0.0.1:45577`, protocolo 47, BungeeCord 1.8.x–26.x, 0 jogadores. Isto valida status ping, não ingresso/autenticação. |
| A-07 | Inicialização dos serviços | PASS | Lobby `StarfinLogin READY`; proxy `StarfinSocial MySQL pronto` e `StarfinMinigames Catalogo MySQL pronto`; BedWars `Inicializacao concluida` em ambos. |
| A-08 | Descoberta de dois backends | PASS | MySQL mostra `bedwars-01` e `bedwars-02` online, um registro da arena `1` por backend, heartbeat observado em torno de 4 s. Isto valida publicação, não reserva/transferência com jogador. |
| A-09 | Migrações integradas | PASS | Schema integrado contém tabelas `sbw_*`, 13 `ss_*` e 8 `sm_*`; `ss_schema_migrations` contém 3 registros. Contagem não substitui teste de transação. |
| A-10 | Guard e portas internas | PASS | Só 45577 e 33308 publicados em loopback; Paper escuta loopback interno; o proxy alcançou TCP `27881` dos três Guards, cada um com segredo próprio. Admissão real ainda requer cliente. |
| A-11 | CI anterior dos commits de origem | PASS | BedWars [run 41754069867](https://github.com/Alanls-mx/StarfinBedWars/actions/runs/41754069867), Minigames [run 41753440045](https://github.com/Alanls-mx/StarfinMinigames/actions/runs/41753440045). A branch nova contém apenas scripts/docs até aqui; não atribuir o CI anterior a commit posterior. |

Avisos não bloqueantes observados: EssentialsX tentou consultar `webapi.essentialsx.net` sem Internet na rede Docker interna; Vault registrou o hook de Essentials Economy. O StarfinCore registrou autorização remota pendente no laboratório; qualquer recurso que dependa dela fica `BLOCKED` até validação autorizada. O primeiro start falhou no proxy por Java 8 incompatível com essa JAR Bungee e a configuração SQL inicial foi incompatível com o método de autenticação MySQL; os ajustes foram feitos só no laboratório e estão no gerador versionado. Consulte `HOMOLOGATION_BUGS.md`.

## Estágio B — testes com clientes Minecraft

| Bloco | Estado | Pré-requisito principal |
| --- | --- | --- |
| A — checagem visual da entrada e menus | NOT_RUN | Cliente Minecraft Java 1.8.8 e conta de teste |
| B — Casual Solo e mecânicas | NOT_RUN | 2 jogadores reais; arena `1` já disponível |
| C — Party, reservas e dois servidores | NOT_RUN | 2–4 jogadores de teste, Party; Dupla exige arena própria |
| D — Ranqueadas | BLOCKED | Criar arenas `ranked`, habilitar políticas apenas no laboratório, clientes suficientes |
| E — Temporadas | BLOCKED | Concluir D, criar temporada descartável |
| F — Torneios `H7.2-01`–`H7.2-25` | BLOCKED | Arenas `tournament` independentes, equipes/árbitro/espectador, política de teste |
| G — recuperação controlada | NOT_RUN | Snapshot/backup do laboratório e clientes durante partida |
| H — desempenho real | NOT_RUN | Quantidade de clientes/partidas e janela de medição definida |

Os 25 cenários de torneio mantêm seus IDs e estado individual `NOT_RUN` no plano final. `BLOCKED` na tabela acima significa ausência de pré-requisito para iniciar o bloco, não falha do produto. Nenhuma compra Vault, jogo, MMR, temporada, torneio, reset, transferência ou latência de gameplay foi medida com cliente nesta fase.

## Registro de evidências futuras

Use `homologation/phase-7-3/TEST_RECORD_TEMPLATE.md` para cada execução. Rode `capture-evidence.ps1` antes/depois das sessões; guarde capturas e vídeos na pasta datada do laboratório. Registre IDs da reserva/partida, versão exata do manifesto, logs e SQL sem credenciais. Ao corrigir um defeito, mantenha o FAIL original e acrescente uma nova execução PASS após repetir o cenário afetado.
