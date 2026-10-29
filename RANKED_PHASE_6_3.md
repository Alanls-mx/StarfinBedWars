# Fase 6.3 — temporadas competitivas

## Arquitetura e ativação

`RankedService` continua responsável pela admissão, pelo outbox e pelo resultado competitivo. `RankedSeasonRepository` administra o ciclo sazonal em MySQL. `seasons.yml` é somente uma **proposta local de criação**; depois de criada, a temporada, suas datas, regras, divisões e recompensas pertencem ao banco. O proxy StarfinMinigames consulta o mesmo controle central e a versão da política antes de inscrever ou alocar jogadores. StarfinSocial não foi modificado.

`seasons.yml` e `ranked.yml` vêm com `enabled: false`. A migração não cria ou ativa uma temporada automaticamente. A pré-temporada `preseason-1` mantém os perfis existentes e só pode receber novas admissões enquanto o controle sazonal central permanece na versão zero. Depois de iniciar o ciclo sazonal, não há retorno automático à pré-temporada.

Instale versões compatíveis de StarfinMinigames no proxy e StarfinBedWars em **todos** os backends, apontando ao mesmo MySQL. Reinicie os serviços para carregar `seasons.yml`; confira `/sbw ranked admin season status` antes de ativar filas. As instalações desta fase não foram aplicadas aos servidores de produção ou teste compartilhados.

## Estados e operação

| Estado | Entrada | Saída permitida |
|---|---|---|
| DRAFT | `create` | `schedule`, `cancel` |
| SCHEDULED | `schedule` | ativação na data, `activate`, `cancel` |
| ACTIVE | ativação após validação do MySQL | `close` ou fechamento na data final |
| CLOSING | bloqueia filas/admissões; processa resultados antigos | primeira execução de `finalize` |
| FINALIZING | exige backends sem partidas/resultados pendentes | segunda execução de `finalize` |
| FINISHED | snapshot e grants publicados | terminal |
| CANCELLED | cancelamento de DRAFT/SCHEDULED | terminal |

O controle de rede é uma linha bloqueada por `SELECT ... FOR UPDATE`. Ativação e encerramento incrementam `policy_version`. A fila do proxy valida `context`, `season_version`, datas, modo e estado a cada inscrição e alocação, além do heartbeat da instância. Admissão final no backend repete a validação sob bloqueio da mesma linha. Políticas antigas falham fechadas.

Ao encerrar, o proxy deixa de formar partidas e cancela entradas WAITING sem penalidade. Partidas já iniciadas conservam seu `competitiveContext`; o backend aceita resultados delas durante CLOSING. O outbox local é processado antes de o heartbeat anunciar zero pendências. A finalização recusa alocações ainda válidas, partidas ativas, resultados pendentes, heartbeat vencido ou contexto/versão divergentes. Um backend que caiu e não retornou bloqueia o fechamento: o operador deve investigar seu journal `ranked-pending` e o estado das partidas; não há descarte automático nem resultado inventado.

A primeira execução de `finalize <id> CONFIRM` muda CLOSING para FINALIZING. Após os backends publicarem a versão nova, a segunda execução consolida snapshot, grants e FINISHED **na mesma transação**. Repetir a operação em FINISHED não duplica linhas. Um rollback mantém FINALIZING para retentativa.

## Configuração e reset

`closing-grace-minutes` define a janela operacional de observação em CLOSING. Depois dela, `admin season status` pede investigação das pendências; a configuração não força a finalização nem descarta resultados.

`seasons.yml`: `enabled`, `display-timezone`, `ranking-cache-seconds`, `current.id/name/description`, `starts-at`, `ends-at`, `modes`, `placement-required`, `reset`, `rewards`. As datas são persistidas em UTC; só a exibição usa o fuso configurado. `FRESH_START` usa `rating.initial` do `ranked.yml` em contexto novo. `SOFT_RESET` exige `source-season` FINISHED e calcula, para cada jogador e modalidade com snapshot anterior, `base + (MMR final anterior - base) × factor`, arredondado uma vez. Jogadores sem snapshot anterior começam no base. O perfil antigo não é alterado; posicionamento começa em zero no novo contexto.

As divisões Bronze/Prata/Ouro/Diamante/Mestre/Lenda continuam configuradas em `ranked.yml`. Na criação, limites, nomes e cores são copiados para `sbw_ranked_season_divisions`; histórico e notificações confirmadas usam esse registro congelado. Alterações futuras no arquivo não reclassificam snapshots publicados.

Exemplo de título sazonal em `current.rewards.titles` (mantenha `enabled: false` até configurar a temporada real):

```yaml
  - id: season01_gold
    mode: solo              # omitir para todas as modalidades habilitadas
    pt_BR: 'Ouro S1'
    en_US: 'Gold S1'
    es_ES: 'Oro T1'
    source: FINAL           # FINAL ou PEAK
    minimum-rating: 1200
    maximum-position: 100  # 0 = sem limite de posição
    minimum-games: 10
```

As recompensas são somente títulos internos nesta fase. `current.rewards.minimum-games` funciona como mínimo global; o maior entre ele e o mínimo do título prevalece. O posicionamento deve estar concluído. Não há pagamento Vault ou benefício de combate. IDs devem ser únicos entre temporadas e não podem colidir com títulos de XP. Resgates são transacionais e a preferência de título só aceita um grant CLAIMED; os títulos permanecem após a temporada.

## Ranking e histórico

O Top ranqueado é separado de `/sbw top` casual. Consultas usam SQL assíncrono, paginação (até 100 por pedido) e cache local de duração controlada. A ordem é: rating atual, peak, vitórias, taxa de vitórias, partidas e UUID; todos em ordem decrescente, exceto UUID crescente. Jogadores sem posicionamento completo não aparecem no Top público. Em FINISHED, o Top e a posição individual vêm somente do snapshot. Participação sem posicionamento permanece no histórico pessoal com posição zero e `eligible=0`.

O menu permite alternar temporada, modalidade e divisão. O filtro de divisão preserva a posição geral da modalidade. O perfil mostra vitórias, derrotas, taxa de vitórias, maior MMR, posicionamento e progresso até a próxima divisão. A tela de modalidades apresenta um resumo persistido de cada perfil.

Snapshots preservam MMR final, peak, divisões da época, jogos, vitórias, derrotas, empates, posição e elegibilidade. `/sbw ranked matches` consulta os resultados competitivos confirmados do contexto atual, sem misturar partidas casuais. O cache de Top é invalidado após resultado confirmado neste servidor e em mudança de versão; expira para refletir outros servidores.

## Comandos, menus e API

- `/sbw ranked`: menu principal de 54 slots.
- `/sbw ranked profile [jogador] [modo]`, `status`: comandos textuais anteriores preservados.
- `/sbw ranked season`, `top`, `rewards`, `history`, `divisions`, `matches`: menus de temporada, Top, resgate, histórico sazonal, divisões e partidas recentes.
- `/sbw ranked admin season list|status|info <id>|create <id>|schedule <id>`: inspeção e preparação.
- `/sbw ranked admin season activate|close|finalize|cancel <id> CONFIRM`: mutações com confirmação explícita. `create` utiliza `current.id` e regras validadas do `seasons.yml` carregado na inicialização.
- `/sbw ranked admin rewards inspect <jogador>`: auditoria de até 50 concessões persistidas; requer `starfinbedwars.ranked.admin.rewards.inspect` ou permissão geral. Como o título é interno e a concessão/resgate são transacionais, não há entrega externa a repetir por `retry`.

As mutações exigem `starfinbedwars.ranked.admin` ou `starfinbedwars.ranked.admin.season.<ação>`. Os menus seguem `pt_BR`, `en_US` e `es_ES`, bloqueiam clique, shift-click, hotbar swap e arraste para o topo. Todas as views têm 54 slots; respostas assíncronas só atualizam a mesma instância ainda aberta. O menu de títulos de XP também lista títulos sazonais resgatados.

`RankedApi` mantém os métodos 6.1/6.2 e adiciona futures para temporada ativa, temporada por ID, lista, Top, posição, histórico, recompensas, resgate e resultados por jogador. `RankedSeasonLifecycleEvent` é emitido após commit em STARTED, CLOSING, FINISHED e REWARD_CLAIMED. O resultado da partida só informa MMR e divisão após commit do MySQL; enquanto pendente informa que está em processamento. Promoção/rebaixamento usam divisões da temporada; title e som de promoção são configuráveis em `ranked.yml/notifications`.

## Scoreboard e TAB

Placeholders opcionais: `{ranked_season}`, `{ranked_mode}`, `{ranked_division}`, `{ranked_rating}`, `{ranked_position}`, `{ranked_placement}`, `{ranked_season_remaining}`. Informações indisponíveis aparecem como `-`. `config.yml` oferece `scoreboard.ranked.enabled`, `scoreboard.ranked.lobby-enabled`, layouts `ranked-lobby`, `ranked-waiting`, `ranked-in-game`, `ranked-ending`, além de `tab.ranked.enabled/header/footer`. Todos começam desativados. O refresh visual só lê cache; o worker JDBC o atualiza de forma assíncrona.

## MySQL e migração

A migração aditiva 63 preserva todas as tabelas e perfis anteriores. Novas tabelas: `sbw_ranked_season_control`, `sbw_ranked_seasons`, `sbw_ranked_season_divisions`, `sbw_ranked_season_snapshots`, `sbw_ranked_season_rewards`, `sbw_ranked_reward_grants`, `sbw_ranked_season_operations`. `sbw_ranked_policy` recebe `season_version`, `pending_results` e `active_matches`. Chaves primárias e índices sustentam estado, histórico, Top e auditoria. Nenhuma migração apaga `preseason-1`.

## Validação e limites

Passaram `mvn -B clean verify` em BedWars e Minigames, os testes unitários de configuração e o teste JDBC da transição completa em schemas descartáveis **MariaDB 11.4** e **MySQL 8.0**. O teste cobre recusa de segunda temporada ativa, resultado processado após fechamento, idempotência por match, bloqueio por pendência, snapshot imutável, filtro de divisão, desempate, paginação, histórico de não posicionados, `SOFT_RESET` e resgate repetido. StarfinSocial não foi alterado.

Homologação manual posterior: menus no cliente 1.8.8; promoção/rebaixamento em partidas reais; troca de temporada em vários backends; fechamento com partidas ativas; recompensa vista e selecionada pelo jogador; vários clientes e falhas reais de proxy/MySQL. Não há resultado aprovado para esses cenários ainda. A recuperação de um backend perdido é intencionalmente conservadora e exige auditoria operacional; a Fase 6.4 pode exercitar falhas reais e ajustar índices/limites com volume de produção. Nenhuma temporada foi ativada automaticamente.
