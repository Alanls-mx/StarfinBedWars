# StarfinBedWars — perfis e estatisticas para BungeeCord

## Contrato com StarfinCore

Esta implementacao usa as bibliotecas compartilhadas do **StarfinCore Spigot 0.3.1+**:
HikariCP 4.0.3, MySQL Connector/J 8.4.0 e SLF4J. Elas sao carregadas e
verificadas pelo Core no `onLoad`. O `plugin.yml` do BedWars exige o Core;
as dependencias Maven sao `provided`, sem empacotar drivers no plugin.

O Core disponibiliza **classes**, nao um `DataSource` unico. Cada instancia
do BedWars cria e fecha seu proprio pool; todos apontam ao mesmo banco MySQL.
Uma instalacao BungeeCord correta precisa encaminhar o UUID real de cada
jogador aos servidores Spigot. UUIDs diferentes fragmentam perfis.

## Modelo de dados

| Tabela | Chave | Finalidade |
|---|---|---|
| `sbw_profiles` | `uuid` | Nome recente, partidas, resultados, abates, mortes, camas, XP total |
| `sbw_match_results` | `(match_id, player_uuid)` | Ledger imutavel de partidas concluidas e protecao contra duplicacao |

- Os contadores sao incrementados apenas quando uma nova chave de resultado e inserida.
- A insercao do ledger e a soma do perfil ocorrem na mesma transacao InnoDB.
- Varios servidores podem atualizar o mesmo jogador sem sobrescrever o contador do outro.
- O nivel e derivado de `total_xp` e de `stats.yml`, com inicio no nivel 1.
- Partidas canceladas nao sao contabilizadas. Vitorias, derrotas e empates sao distintos.
- Quem abandonou nao recebe vitoria posterior do time. Abate final concede XP de abate mais bonus.
- A atualizacao do nome nao muda a chave UUID e nunca zera os contadores.
- O sistema contabiliza participantes e eventos validados pelo engine, sem fazer consultas SQL no thread Bukkit.

## Resiliencia e limites

No fim de uma partida, o worker de estatisticas grava um arquivo atomico em
`plugins/StarfinBedWars/stats-pending/<match-uuid>.properties`. So depois tenta
aplicar a transacao no MySQL e excluir o arquivo. Falhas deixam o arquivo
intacto para retry a cada 60 s e na proxima inicializacao.

O arquivo de fila e local a cada servidor e **deve ser preservado em backups**.
Nao se deve copiar a mesma instancia de arena nem forcar um novo UUID de
partida como tentativa de retry. O backend SQL protege a repeticao da mesma
chave original, nao partidas reconstruidas artificialmente com outro UUID.

A fila e preparada de forma assincrona; uma falha abrupta entre o evento
`ENDED` e a persistencia do arquivo pode perder o resultado. Nao ha
recuperacao de partidas em andamento apos crash, nem recalculo retroativo
de eventos que ocorreram antes da ativacao deste modulo.

## Roteiro de homologacao (dois servidores Spigot + um proxy)

1. Instalar StarfinCore Spigot com as bibliotecas compartilhadas em cada node.
2. Configurar `stats.yml` em ambos com o mesmo banco MySQL, credenciais de
   usuario restrito e `enabled: true`. Manter o arquivo fora de commits com senha real.
3. Reiniciar os dois nodes. Verificar a criacao das tabelas e log de conexao.
4. Entrar com o mesmo UUID pelos dois servidores, consultar `/sbw stats` e
   conferir nivel, XP e identificador sem dados fragmentados.
5. Concluir uma partida com cama destruida, abates comuns e finais, uma
   vitoria e uma derrota. Comparar os contadores e a formula de XP configurada.
6. Repetir a entrega do mesmo `(match_id, player_uuid)` em homologacao e
   confirmar que `sbw_profiles` nao ganha nova partida, vitoria ou XP.
7. Derrubar temporariamente o banco durante o fim de uma partida: confirmar
   arquivo pendente; religar MySQL; aguardar retry; conferir uma unica soma.
8. Interromper e reiniciar o servidor com arquivo pendente: verificar replay
   sem duplicacao; testar leitura no outro node apos a confirmacao.
9. Verificar scoreboard, menus, outros plugins, latencia, TPS e desligamento
   com sessao MySQL ativa. Validar SSL no ambiente real.

## Validacao nesta implementacao

Os testes JUnit das classes puras verificam XP/limites de nivel, atribuicao
de kills/camas/vitorias e serializacao do outbox. A compilacao Maven completa,
o carregamento das dependencias do StarfinCore e o teste MySQL/Bungee precisam
ser repetidos em um ambiente com Spigot 1.8.8, Maven e acesso aos artefatos.
