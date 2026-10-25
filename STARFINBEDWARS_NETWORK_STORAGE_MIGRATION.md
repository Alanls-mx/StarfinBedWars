# Catálogo MySQL/BungeeCord e migração de dados locais

## Análise do estado anterior

O sistema de estatísticas persistia apenas `sbw_profiles` e `sbw_match_results`.
As definições de arena eram mantidas em `plugins/StarfinBedWars/arenas/*.yml` e
os mundos reais em `plugins/StarfinBedWars/templates/<arena>/`. A engine de
partidas só existia em memória no Spigot; portanto o BungeeCord não conseguia
descobrir quais servidores, arenas e partidas estavam disponíveis por SQL.

## Referência técnica: BedWars1058 + BedWarsProxy

Verificação no código oficial do BedWars1058 (branch `master`, 25/10/2026):

- `database/Database.java` define a interface de persistência para estatísticas,
  Quick Buy, níveis/XP e idioma; `MySQL.java` implementa os mesmos contratos que
  `SQLite.java`.
- `MySQL.java` cria `global_stats` (inclui first/last play, kills, final deaths,
  victories, beds, games), `quick_buy_2` (slots do jogador), `player_levels`
  (level, xp, custo do próximo nível) e `player_language` (ISO selecionado).
- As arenas **não são gravadas em tabelas MySQL**: `ArenaConfig.java` e
  `BedWars.java` carregam `Arenas/*.yml` e os mundos locais.
- `lobbysocket/ArenaSocket.java` transmite JSON para o lobby com servidor,
  identificador/nome da arena, status, número/capacidade de jogadores, grupo,
  máximo por equipe e permissão de espectador. O `BedWarsProxy` recebe isso em
  `socketmanager/ArenaSocketTask.java`, atualiza o cache e usa a API do Bungee
  para encaminhar jogadores. A MySQL do proxy é usada sobretudo para dados
  de jogador, não como registro oficial das arenas.
- No plugin de arenas, se a conexão MySQL falhar na inicialização, o código
  retorna para SQLite local. **Não adotamos esse fallback silencioso em rede**:
  poderia criar perfis e progressão divergentes entre servidores.

Assim, o registro SQL de arenas do StarfinBedWars é um recurso **adicional**
à referência, desenhado para consultas do futuro proxy e histórico de
configuração; não é necessário converter arquivos de mundo para registros SQL.
Lacunas planejadas após esta fase: grupo/perfil de arena, reservas/ACK,
sincronização operacional do Quick Buy, escolha de idioma por usuário e o
plugin proxy efetivo.

Fontes oficiais:

- <https://github.com/andrei1058/BedWars1058/blob/master/bedwars-plugin/src/main/java/com/andrei1058/bedwars/database/MySQL.java>
- <https://github.com/andrei1058/BedWars1058/blob/master/bedwars-plugin/src/main/java/com/andrei1058/bedwars/database/Database.java>
- <https://github.com/andrei1058/BedWars1058/blob/master/bedwars-plugin/src/main/java/com/andrei1058/bedwars/lobbysocket/ArenaSocket.java>
- <https://github.com/andrei1058/BedWarsProxy/blob/master/proxy-plugin/src/main/java/com/andrei1058/bedwars/proxy/socketmanager/ArenaSocketTask.java>

## Ativação (em **cada** Spigot BedWars)

1. Instale `StarfinCore` compatível com Spigot 1.8.8, contendo HikariCP,
   MySQL Connector/J e SQLite JDBC como bibliotecas compartilhadas.
2. Configure `stats.yml` com `enabled: true` e **o mesmo MySQL** em toda a rede.
3. Configure `network.yml` com `enabled: true` e um `server-id` **único por
   servidor Spigot**, igual ao nome registrado no BungeeCord (`bedwars-01`,
   `bedwars-02`, etc.). Não reutilize um ID entre processos em execução.
4. Reinicie o servidor. Não copie a mesma pasta `templates` como se fosse uma
   instância de partida: ela contém mundos-template usados para criação local.
5. Valide as tabelas e heartbeats no MySQL, e confira que cada arena aparece
   com status, configurações completas e capacidade correta.

O catálogo publica um snapshot ao iniciar, a cada 5 segundos por padrão e nos
eventos de partida/jogador. Todas as operações de SQL são feitas em um worker
separado, com no máximo um snapshot pendente. Falhas temporárias deixam os
arquivos locais intactos e provocam uma nova tentativa no próximo heartbeat.

## Tabelas para integração futura do proxy

| Tabela | Chave | Dados |
|---|---|---|
| `sbw_profiles` | UUID | Estatísticas cumulativas e XP da rede |
| `sbw_match_results` | partida + jogador | Histórico idempotente para não duplicar XP |
| `sbw_network_servers` | `server_id` | Instância, online, slots/ocupação, heartbeat |
| `sbw_network_arenas` | servidor + arena | YAML integral v1–v7, hash, nome, mundo-fonte, modo, times, capacidade, geradores, countdown, status, match ativo, edição e disponibilidade |
| `sbw_network_matches` | UUID da partida | Servidor, arena, estado, jogadores, capacidade, vencedor, motivo, timestamps, encerramento |
| `sbw_network_players` | UUID do jogador | Nome, servidor, instância, papel (`LOBBY`, `PLAYER`, `SPECTATOR`), arena, partida, equipe, heartbeat |
| `sbw_storage_arenas` | servidor + arena | Cópia íntegra de arenas exportadas via migração manual |
| `sbw_storage_documents` | servidor + documento | Configurações e favoritos exportados via migração manual |

As entradas antigas de arenas são **marcadas** como removidas em `deleted_at`,
em vez de apagadas. Partidas encerradas recebem `CLOSED`; perfis e resultados
não são alterados pelo catálogo de rede. O `server_instance` muda em cada
inicialização: uma instância não pode publicar usando o `server-id` de outra
instância viva com heartbeat de menos de 30 segundos.

Para obter arenas realmente online, o futuro proxy deve usar **ambas** as
condições `s.online=1` e `s.last_heartbeat > (epoch_ms_atual - 30000)`,
além de `a.deleted_at=0` e `a.joinable=1`. `joinable` é apenas uma indicação:
o proxy deve implementar reserva/ACK/TTL antes de enviar o jogador, para não
ultrapassar slots sob concorrência.

Consulta exemplo (o proxy deve usar o mesmo relógio de referência para TTL):

```sql
SELECT a.server_id,a.arena_id,a.display_name,a.mode,a.team_count,a.team_size,
       a.players,a.max_players,a.state,a.match_id
FROM sbw_network_arenas a
JOIN sbw_network_servers s ON s.server_id=a.server_id
WHERE s.online=1 AND s.last_heartbeat > ?
  AND a.deleted_at=0 AND a.joinable=1
ORDER BY a.players DESC;
```

## Migração de YAML local para MySQL ou SQLite

A migração é **explícita**, nunca automática. O operador pode executar pelo
console ou in-game (permissão de administrador):

```text
/sbw migrate preview mysql
/sbw migrate apply mysql confirm

/sbw migrate preview sqlite
/sbw migrate apply sqlite confirm
```

- `preview` contabiliza as arenas e documentos locais, sem gravar nada.
- `apply` captura o YAML, gera primeiro um ZIP em
  `plugins/StarfinBedWars/migration-backups/` e então importa num único
  conjunto transacional, com uma cópia SHA-256 para detecção de conflito.
- Se o destino já tiver o mesmo identificador e conteúdo igual, reexecutar
  a migração não duplica nada. Conteúdo **divergente não é sobrescrito**.
- MySQL usa as credenciais de `stats.yml`. SQLite grava
  `plugins/StarfinBedWars/bedwars-migration.sqlite` (somente arquivo local).
- O `server-id` de `network.yml` identifica o conjunto exportado, inclusive
  quando o catálogo online está desativado. Configure um nome exclusivo.
- São exportados arena YAML e configurações/favoritos permitidos. Tokens e
  credenciais (`stats.yml`, `network.yml`) e sessões voláteis de reconexão não
  são exportados. Templates de mapas não cabem nesse modelo SQL e precisam de
  cópia/transferência de diretórios separada.

**Limite atual:** a migração implementada é uma cópia SQL segura de metadados.
O Spigot ainda carrega e edita arenas pelo repositório YAML; os dados
operacionais que o BungeeCord consulta são publicados no catálogo MySQL
quando `network.yml` está habilitado. Não é um modo SQL-authoritative que
recria mundos ou permite compartilhar uma única arena física entre dois
Spigots. SQLite é uma opção local de exportação, **não** um banco da rede.
Uma futura etapa de troca completa de backend precisará de interface de
armazenamento autoritativa, importação/reconciliação de caches por revisão,
publicação atômica de templates e reservas distribuídas pelo proxy.

## Validação em homologação

1. Configure `server-id` diferente em dois servidores Spigot com o mesmo MySQL.
2. Verifique ambos em `sbw_network_servers`, com heartbeat renovado.
3. Crie/edite uma arena; confira YAML integral, hash, modo, capacidade, regiões,
   posições, times e geradores em `sbw_network_arenas`.
4. Entre/saia de partidas; verifique o estado da arena, jogadores registrados,
   UUID do match e seu encerramento; feche um servidor para testar expiração.
5. Tente duplicar um `server-id` em duas instâncias vivas e valide o bloqueio.
6. Rode `preview` e `apply`, confira arquivo ZIP, hashes e cópias SQL; repita
   `apply` sem modificar arquivos e confirme idempotência. Depois modifique
   um YAML e confirme conflito (sem sobrescrita automática).
7. Avalie TPS e latência. Teste queda temporária de MySQL, retomada automática
   e limpeza de entradas obsoletas no heartbeat seguinte.

O proxy `StarfinBedWars-Proxy.jar` e o protocolo distribuído de reservas,
fila e transferências BungeeCord **ainda não fazem parte** desta entrega.
