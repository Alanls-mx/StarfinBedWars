# Torneios e campeonatos — Fase 7.1

## Estado e arquitetura

Implementação inicial de eliminação simples, desativada por padrão. O StarfinBedWars é a autoridade sobre torneios, elencos, chaves, jogos, resultados e títulos. O StarfinMinigames no proxy escolhe arenas, cria reservas para o elenco inteiro e transfere os jogadores. O StarfinSocial fornece o retrato da Party no momento da inscrição; alterações posteriores na Party não alteram o elenco persistido.

Esta fase não foi instalada nos servidores Pterodactyl e não teve homologação com clientes Minecraft. Habilitar somente após backup, revisão dos grupos de arenas e execução do roteiro manual. O arquivo `tournaments.yml` e `tournaments.enabled` no proxy vêm com `enabled: false`.

## Fluxo

1. Administrador define a proposta em `tournaments.yml` ou usa `/sbw tournament admin wizard`; cria o rascunho com `create <id> CONFIRM`.
2. `schedule` e `open` publicam e abrem as inscrições no período configurado. Capitão solo inscreve diretamente. Nos demais modos, o proxy valida que ele lidera uma Party online com o número exato de membros; cada integrante confirma no BedWars.
3. `close` encerra inscrições. `generate` valida o mínimo de equipes confirmadas, grava seeds e confrontos em uma transação, inclui BYEs e congela a chave. O comando `schedule-match` ajusta a data de um confronto.
4. `start` solicita os jogos prontos. O proxy só aloca uma arena vazia do grupo dedicado quando o elenco completo está online e fez check-in, quando exigido. Cada jogador recebe uma reserva vinculada ao mesmo jogo e à mesma alocação.
5. O backend valida reserva, instância, arena, time e elenco. A partida só abre quando todos os integrantes esperados foram admitidos. Partidas de torneio não atualizam MMR, estatísticas casuais nem recompensas sazonais.
6. O resultado real da partida é gravado em journal local e aplicado de forma idempotente no MySQL. Empate pede novo jogo; erro, cancelamento, queda de backend ou admissão incerta coloca o confronto em revisão. Vitórias atualizam a série MD1/MD3/MD5, avançam a equipe e, após a final, registram campeão e finalista.
7. `finalize` exige final concluída. A operação grava títulos de participante, campeão e finalista de forma idempotente. Jogadores usam `rewards claim` para resgatar e selecionar os títulos no sistema de perfil.

## Configuração

- BedWars: `plugins/StarfinBedWars/tournaments.yml` contém proposta, modalidade, tamanho e limite de equipes (2 a 64), datas UTC, sorteio `RANDOM`/`SEEDED`, seed, série, grupo de arena, check-in, espectadores, títulos e opt-in de kits/habilidades. `statistics.count-as-casual` e `statistics.affect-ranked-mmr` devem permanecer `false`; o parser rejeita `true`.
- Proxy: `plugins/StarfinMinigames/config.yml`, seção `tournaments`, deve usar `enabled: true` e o **mesmo** `arena-group` da proposta publicada no BedWars. O grupo deve ser exclusivo de torneios e conter arenas compatíveis com a modalidade, dois times distintos e capacidade para o elenco completo. O proxy atual atende a um grupo de torneio por instalação.
- Banco: BedWars usa as credenciais MySQL de `stats.yml`. O proxy deve apontar para o mesmo esquema MySQL para ler torneios e gravar reservas. Antes de ativar, confira nome do banco, timezone e permissões de criação/alteração de tabelas. Não copie senhas para documentação ou logs.
- Datas aceitam ISO-8601 UTC nos comandos. A zona `display-timezone` é apenas para exibição. Não mude o grupo de arena enquanto houver torneio ativo.

## Comandos e permissões

Jogador: `/sbw tournament` abre o menu; `list [página]`, `info <id>`, `join <id> [nomeEquipe]`, `confirm <id>`, `checkin <id>`, `leave <id>`, `team <id>`, `bracket <id> [rodada] [página]`, `matches <id> [rodada] [página]`, `spectate <id> [gameUUID]`, `history`, `rewards [claim]`. Alias: `/sbw torneio`. Permissões: `starfinbedwars.tournament.consult`, `.register`, `.spectate`, `.rewards`.

Admin: `/sbw tournament admin wizard [set|next|back|review|confirm]`, `create <id> CONFIRM`, `edit <id> <campo> <valor> CONFIRM`, `schedule|open|close|generate|start|pause|resume|cancel|finalize <id> CONFIRM`, `status|matches|audit <id>`, `seed <id> <teamUUID> <n>`, `schedule-match <id> <matchUUID> <data ISO-8601> CONFIRM`, `replay <id> <matchUUID> CONFIRM`, `substitute <id> <teamUUID> <oldUUID> <newUUID> <justificativa> CONFIRM`, `walkover <id> <matchUUID> <winnerTeamUUID> <justificativa> CONFIRM`. Permissão base `starfinbedwars.tournament.admin` e permissões filhas `.consult`, `.create`, `.configure`, `.start`, `.pause`, `.cancel`, `.resolve`, `.finalize`. `starfinbedwars.tournament.referee` autoriza espectador em torneio privado. Comandos que alteram estado publicamente exigem `CONFIRM` literal; as ações ficam na tabela de auditoria. A substituição exige `allow-substitutions: true`, equipe confirmada, ausência de jogo ou série ativa e confirmação do novo membro com `confirm <id>` antes de ele poder jogar; o capitão não pode ser substituído. O walkover é exclusivamente manual: após o prazo de check-in, exige elenco vencedor completo no check-in, adversário incompleto, nenhum jogo alocado/disputado e justificativa. Ele não registra vitória fictícia de Minecraft nem MMR.

## MySQL e contratos

O BedWars cria `sbw_tournament_schema_migrations`, `sbw_tournaments`, `sbw_tournament_teams`, `sbw_tournament_members`, `sbw_tournament_matches`, `sbw_tournament_games`, `sbw_tournament_game_admissions`, `sbw_tournament_checkin_policy`, `sbw_tournament_checkins`, `sbw_tournament_reward_grants` e `sbw_tournament_operations`. O proxy acrescenta `tournament_game_id` e `tournament_allocation_id` a `sm_network_reservations` via migração aditiva e usa `sm_tournament_arena_leases` para exclusividade. Nenhuma migração remove dados anteriores.

`TournamentRepository` expõe consulta de torneios/equipes/chaves/jogos, inscrição, confirmação, check-in, transições, alocação, admissão, resultado, replay e prêmios. A camada Bukkit executa JDBC em fila de IO, usa snapshot em memória para scoreboard/TAB e só modifica jogadores na thread principal. O proxy aceita somente reservas vinculadas a jogo e alocação ativos. O canal `SMTournament` consulta o StarfinSocial no proxy para validar a Party na inscrição.

O journal `plugins/StarfinBedWars/tournament-pending/` permite repetir aplicação de resultados após falha de MySQL. Estados `REVIEW` exigem inspeção do registro, auditoria e replay administrativo; não há vitória nem walkover automático. O serviço tenta reconectar ao MySQL a cada 10 segundos. Reinício não recria equipes nem chaves: elas são carregadas do banco.

## Interfaces e espectadores

Os inventários Bukkit 1.8.8 mostram lista, detalhes, equipe, chave paginada, histórico e prêmios; cliques em itens de menu não retiram os ícones. Layouts opcionais de scoreboard/TAB estão em `config.yml`. O espectador utiliza o modo já existente no BedWars, respeita `public-spectators`, permissão de árbitro e limite configurado. Nesta primeira versão, `/spectate <id>` seleciona o jogo ativo mais recente **neste backend**; com vários jogos simultâneos, use o UUID do jogo para selecionar outro. Não há transferência de espectador entre servidores nesta fase.

## Testes e limitações

Testes unitários cobrem sorteio reprodutível, BYEs e tamanhos de 2 a 64 equipes, validação de regras perigosas e persistência do journal. Há testes de integração para inscrição, confirmação, substituição, chave, alocação, admissão, empate/replay, série, walkover manual, final e prêmios, além de reserva em lote no proxy. Uma versão anterior dos testes de integração executou em MySQL 8.0 descartável; **os testes SQL da revisão final foram ignorados em 03/11/2026 porque o daemon Docker local estava indisponível**. Reexecutá-los é obrigatório antes da homologação. Executar `mvn -B clean verify` no StarfinMinigames antes do StarfinBedWars; os testes SQL só executam quando as variáveis `SM_TEST_MYSQL_*` e `SBW_TOURNAMENT_TEST_PORT` apontam explicitamente para o banco descartável. Sem elas, JUnit os marca como ignorados, portanto um build verde isolado não comprova persistência.

Limites conhecidos: `automatic-walkover: true` é rejeitado para evitar vitórias fabricadas em falhas técnicas; a decisão manual por ausência depende de check-in verificável e não pode resolver um jogo em revisão técnica. A substituição exige a permissão administrativa de resolução e foi projetada para elencos fora de uma série ativa; ainda precisa de homologação em jogo real. Não há placar global de torneios, premiação monetária, convite automático da Party, transferência interservidores para espectadores ou homologação visual. A criação e a execução de uma competição real dependem da configuração conjunta dos dois plugins e de arenas dedicadas. Consulte `STARFINBEDWARS_FINAL_TEST_PLAN.md` para a homologação manual pendente.
