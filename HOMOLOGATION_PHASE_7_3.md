# Fase 7.3 — homologação integrada

O Estágio A está preparado e as verificações automáticas descritas em `HOMOLOGATION_RESULTS.md` foram executadas. O Estágio B exige clientes Minecraft 1.8.8 reais, registro individual de evidências e reexecução após correções. Nenhuma aprovação manual é inferida de JUnit, do ping do proxy ou de registros SQL.

## Ordem da sessão guiada

Antes de cada bloco: registrar data, `manifest.json`, jogadores, arena, backend, comandos, resultado esperado/observado e captura. Usar o modelo em `homologation/phase-7-3/TEST_RECORD_TEMPLATE.md`. Para falha, abrir ID em `HOMOLOGATION_BUGS.md` e preservar a execução original. `PASS` só após observar o comportamento descrito; `BLOCKED` quando faltar arena/cliente/serviço; `NOT_RUN` enquanto não tentado.

| Bloco | Preparação e participantes | Ações principais | Aprovação e evidência |
| --- | --- | --- | --- |
| A — inicialização | 1 cliente; proxy/lobby/MySQL e 2 backends ativos | Entrar por `127.0.0.1:45577`, autenticar, consultar `/smnetwork servers`; observar TAB/menus. | Identidade correta, sem entrada direta em backend; logs Login/Guard e catálogo SQL correspondem ao servidor exibido. |
| B — Casual | 2 clientes na arena Solo `1`; depois arenas reais Dupla/Trio/Quarteto com 4/6/8+ conforme equipes | `/smjoin bedwars solo`; seleção de time, contagens, geradores, NPCs, loja, upgrades, cama inimiga, morte/respawn/espectador, vitória, `/sbw leave`, nova rodada. | Cada evento ocorre uma vez, inventário/equipamentos/lobby restaurados, mundo resetado, nenhum recurso duplicado. Captura antes/depois e ID da partida. |
| C — Party e rede | 2–4 clientes; Party no StarfinSocial, backends 01/02; Dupla requer arena própria | `/party create`, `/party invite <jogador>`, aceitar, `/smjoin bedwars duo`; repetir com jogador já no backend, destino cheio/offline e reserva expirada. | Party entra junta no time certo; reserva e chegada correspondem ao mesmo backend; isolamento entre partidas; chat/presença instantâneos sem duplicar. Registrar IDs de reserva e tempos. |
| D — Ranqueadas | Criar arena `ranked` real; habilitar `ranked.yml` e fila proxy só no laboratório; 2+ Solo, 4+ Dupla | `/smranked join solo|duo`, `status`, `leave`; `/sbw ranked profile`, `/smranked admin status`. Testar MMR dentro/fora do limite, expansão, admissão, reconexão/abandono. | Prova competitiva e equipes válidas; MMR antes/depois só para resultado real; casual não altera MMR; kits/habilidades seguem política. SQL de `sbw_ranked_*`, `sm_*`. |
| E — Temporadas | D concluído, temporada descartável configurada em `seasons.yml`, admin e jogadores | `/sbw ranked admin season status|create|schedule|activate|close|finalize ... CONFIRM`; jogar ranqueadas; `/sbw ranked top|history|rewards`. | Ativação/finalização coerentes entre backends/proxy; snapshot, posição, resgate e histórico persistem; nenhuma edição no relógio do sistema. |
| F — Torneios | 2+ arenas reais `tournament`, equipes suficientes, árbitro/espectador; admins | Seguir `H7.2-01`–`H7.2-25` no plano final; `/sbw tournament admin wizard`, inscrições, check-in, chave, MD1/3/5, espectador, final, recompensas. | Jogos e série avançam apenas com resultado confirmado; BYEs e reservas exclusivos; nenhum MMR/estatística casual indevido; journal e auditoria preservados. |
| G — recuperação | Snapshot/backup e IDs antes de cada falha; 2+ clientes em partida | Somente neste laboratório: indisponibilidade MySQL/backend/proxy, desconexão, reinício em jogo, resultado reenviado, torneio pausado/retomado. | Recuperação idempotente, sem vitória fictícia, cobrança dupla, perda de journal ou admissão duplicada. Preservar logs antes/depois. |
| H — desempenho | Hardware e número de clientes/arenas registrados; 2 backends | Medir CPU/RAM/TPS, início, latência `/smjoin`, transferência, admissão, SQL e reset em partidas simultâneas. | Comparar medidas reais por carga/versão, sem declarar limiares ou resultados que não foram medidos. |

## Casos transversais obrigatórios

- Mecânicas: TNT, Fireball, Ender Pearl (dano e som), ponte de ovos, Iron Golem, poções, knockback, limites/void, blocos colocados e camas configuradas. Conferir drops, transferência de recursos por abate, progressão de armadura, downgrade de ferramentas e baús normal/do fim.
- Match Point: manter um ensaio com temporização natural e outro administrativo separado. Capturar dragão/BossBar, destruição progressiva distribuída entre ilhas, raios opcionais, vitória, limpeza e restauração. Não acelerar o ensaio natural.
- Vault: usar apenas contas de teste. Medir saldo antes/depois de compra válida, saldo insuficiente, evolução, compra repetida, reinício e falha de persistência. Exigir ausência de cobrança dupla.
- Kits e habilidades: matriz abaixo; testar nível I–V, modalidade, compra/desbloqueio/seleção, efeito, cooldown, morte/respawn, reconexão e persistência. Dividir entre partidas. Confirmar bloqueio em ranqueadas quando a política exigir.
- Multi-backend: duas arenas `1` são cópias separadas. Testar simultaneidade, inventários, baús, hologramas e resultados isolados. Não afirmar suporte a múltiplos proxies; o laboratório tem um proxy.

## Matriz dos 12 kits — todas as linhas `NOT_RUN`

| ID | Kit | Checklist por execução |
| --- | --- | --- |
| K01 | `builder` | Desbloqueio, compra, I–V, seleção, entrega, respawn sem duplicar, reconexão, modalidade, persistência |
| K02 | `miner` | Mesmo checklist |
| K03 | `lumberjack` | Mesmo checklist |
| K04 | `shears` | Mesmo checklist |
| K05 | `carpenter` | Mesmo checklist |
| K06 | `climber` | Mesmo checklist |
| K07 | `blacksmith` | Mesmo checklist |
| K08 | `archer` | Mesmo checklist |
| K09 | `trapper` | Mesmo checklist |
| K10 | `explorer` | Mesmo checklist |
| K11 | `defender` | Mesmo checklist |
| K12 | `pyromancer` | Mesmo checklist |

## Matriz das 18 habilidades — todas as linhas `NOT_RUN`

Para cada habilidade: desbloqueio/compra, níveis I–V, ativação, cooldown, efeito e duração, morte, reconexão e limpeza; registrar modalidade e custo. As habilidades passivas exigem observar o gatilho correspondente em vez de forçar `/sbw ability use`.

| ID | Habilidade | ID | Habilidade | ID | Habilidade |
| --- | --- | --- | --- | --- | --- |
| A01 | `respawn_boost` | A07 | `momentum` | A13 | `aegis` |
| A02 | `knockback_resist` | A08 | `sentinel` | A14 | `vampire` |
| A03 | `fall_resistance` | A09 | `collector` | A15 | `frenzy` |
| A04 | `fire_resistance` | A10 | `archer` | A16 | `bodyguard` |
| A05 | `projectile_resistance` | A11 | `builder` | A17 | `forge_master` |
| A06 | `miner` | A12 | `merchant` | A18 | `last_stand` |

## Bloqueios atuais e próximo passo

A arena casual Solo permite começar A/B com dois clientes. C/D/E/F necessitam mais clientes e/ou arenas construídas no próprio Minecraft. O licenciamento pendente do StarfinCore deve ser esclarecido antes de aprovar qualquer recurso dependente. A sequência concreta é: entrar com cliente 1.8.8 em `127.0.0.1:45577`, autenticar e executar A; em seguida conectar um segundo cliente e rodar B na arena `1`. Durante a sessão, registrar os cenários efetivamente observados no arquivo de resultados, sem alterar os 25 casos ainda não executados.
