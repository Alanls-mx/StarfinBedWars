# StarfinBedWars — Classificacao competitiva, Fase 6.1

## Arquitetura

`GameMatch` possui `MatchType.CASUAL` por padrao. O contexto `RANKED` so pode
ser aplicado por `RankedService.authorize(match, issuer)` antes de `IN_GAME`.
O emissor precisa estar habilitado e nomeado em `ranked.yml/authorized-plugins`,
o banco precisa estar operacional e o elenco completo precisa ter pelo menos
duas equipes efetivas. Depois da autorizacao, a equipe e o elenco ficam
fechados. Um jogador que sai antes de abrir a partida cancela a rodada
ranqueada. Partidas casuais nao chamam o processador de MMR.

Esta fase nao habilita nenhum emissor por padrao. O StarfinMinigames ainda
nao emite o contexto competitivo: `/smjoin` e as reservas existentes
continuam casuais. A Fase 6.2 conectara uma reserva confiavel do proxy ao
metodo de admissao. Nenhuma fila ranqueada ou resultado de producao foi simulado.

`RankedService` escuta o inicio e fim reais da partida. Na conclusao com
vitoria ou empate legitimo, captura modalidade, time efetivo de cada jogador,
abandono definitivo, vencedor, servidor, instancia e horario. Partidas
canceladas, interrompidas pelo administrador ou com erro nao geram MMR. Uma
desconexao temporaria mantida pelo `ReconnectService` nao vira abandono;
somente o estado final `ABANDONED` perde o direito a vitoria. A politica
inicial registra derrota para esse participante, mesmo se sua equipe vencer.

O snapshot e gravado em `ranked-pending/<match-id>.properties` por um worker
antes do envio ao MySQL. Depois do commit, o arquivo e removido. Em erro SQL,
ele e reenviado periodicamente e no proximo startup. Os eventos
`COMPLETED`, `RATING_UPDATED`, `DIVISION_CHANGED` e `PLACEMENT_COMPLETED`
saem somente apos commit. `STARTED` representa o inicio real do jogo.

## Formula

O rating representativo de cada equipe e a media aritmetica dos ratings de
seus integrantes. Para cada par de equipes A,B, a expectativa de A e
`1 / (1 + 10^((ratingB-ratingA)/400))`. O resultado e 1 para a equipe
vencedora, 0 para derrota, e 0,5 para empate real. Como nao ha ordem de
eliminacao registrada, equipes derrotadas empatam entre si para esse calculo.
Para cada jogador, `delta = round(K * media(resultado-esperado))` sobre as
equipes adversarias. Integrantes da mesma equipe recebem o mesmo delta salvo
quem abandonou definitivamente, que e calculado como derrota contra todos.
O arredondamento usa `Math.round` do Java; o limite minimo pode reduzir a
perda aplicada. A media evita multiplicar K pelo numero de equipes. Abates,
camas, VIP, kits e habilidades nao entram na formula.

O rating inicial e 1000, o minimo e 0 e K e 24 por padrao. O rating interno
evolui nas cinco partidas de posicionamento, mas a divisao publica fica
`Em posicionamento` ate concluir a quinta. Os perfis sao independentes em
`solo`, `duo`, `trio`, `squad` e `custom`; `custom` vem desabilitado.

Divisoes padrao: Bronze 0+, Prata 900+, Ouro 1100+, Diamante 1300+,
Mestre 1500+ e Lenda 1800+. Nome, cor, ordem, icone e limite sao configuraveis.
Ordem e limites devem crescer estritamente e a primeira divisao deve iniciar
em zero. A divisao e derivada do rating, nunca gravada em coluna separada.

## Persistencia e concorrencia

`sbw_ranked_schema_migrations` registra a migracao aditiva 61.
`sbw_ranked_profiles` usa PK `(player_uuid,mode,context)` e guarda rating,
maior rating, jogos, vitorias, derrotas, empates, posicionamento e ultima
partida. `sbw_ranked_matches` usa PK `match_id`, hash do snapshot e status
`PROCESSED`. `sbw_ranked_results` usa PK `(match_id,player_uuid)` e guarda
rating anterior, delta e rating posterior de cada integrante.

Um unico commit InnoDB grava a partida, os perfis e todos os resultados.
Perfis sao criados e bloqueados por UUID em ordem deterministica. Deadlocks
e timeout de lock recebem ate duas retentativas. O ID da partida impede
aplicacao duplicada apos replay ou em outro servidor. Se um ID reaparecer
com snapshot diferente, o processador sinaliza conflito e preserva o
arquivo pendente para auditoria. As tabelas casuais nao sao alteradas e
partidas historicas nao recebem MMR retroativo.

As regras numericas sao congeladas no snapshot pendente para que uma mudanca
de `ranked.yml` nao altere a retentativa de uma partida anterior. O
`context: preseason-1` separa o periodo inicial e prepara temporadas futuras.

## Configuracao, API e comandos

Edite `plugins/StarfinBedWars/ranked.yml`. Ative `enabled` apenas quando a
Fase 6.2 fornecer um emissor configurado em `authorized-plugins`.
`stats.yml` deve continuar conectado ao MySQL compartilhado. Kits e
habilidades comprados sao bloqueados em ranqueadas por padrao em
`advantages.kits` e `advantages.abilities`; casuais mantem a regra anterior.
`history.enabled` e `queries.maximum-results` reservam as consultas da GUI
futura; os registros minimos de resultado sempre sao guardados para
idempotencia.

`RankedApi` esta registrada no Bukkit `ServicesManager`, com consultas
assincronas para perfil, rating, divisao, posicionamento e resultado da
partida. `calculateRatingChanges` e puro e independente de Bukkit.
`RankedLifecycleEvent` publica os eventos para outros plugins. O contrato de
admissao exige um `Plugin` autorizado e a instancia real de `GameMatch`.

- `/sbw ranked` e `/sbw ranked profile [jogador] [modo]`: perfil atual.
- `/sbw ranked status`: configuracao e perfil do jogador.
- `/sbw ranked admin status`: disponibilidade do banco e contexto.
- `/sbw ranked admin inspect <jogador>`: perfis por modalidade; permissao
  `starfinbedwars.ranked.admin` (operadores por padrao).

Mensagens e cores estao em `ranked.yml/messages`. A API e os comandos nao
executam JDBC na thread principal. Nao ha comando para fabricar vitorias.

## Verificacao executada

- `mvn -B clean verify`: Java release 8, testes existentes e testes puros
  de Elo, divisao, posicionamento, tipo da partida e outbox aprovados.
- `RankedMySqlIntegrationTest` foi executado contra schema MariaDB
  descartavel `sbw_ranked_test_61`: migracao repetida, dados casuais
  preservados, cinco posicionamentos, modalidades separadas, partida
  duplicada em outra conexao, duas partidas concorrentes no mesmo perfil,
  abandono, rollback por tabela indisponivel e recuperacao sem duplicacao.
- Nenhuma partida real foi marcada como ranqueada; nao existe emissor de
  admissao da Fase 6.2. Portanto os cenarios de homologacao Solo, Dupla,
  quatro equipes e queda de MySQL durante jogo **continuam pendentes**.

## Roteiro de homologacao da Fase 6.2

Com emissor autorizado, fazer backup de YAML e JAR; instalar a mesma versao
em cada backend; confirmar a migracao 61; realizar partidas Solo, Dupla e
quatro equipes; conferir rating anterior/delta/posterior no MySQL; repetir
o mesmo `match_id`; interromper o MySQL antes do commit e confirmar replay;
executar uma Casual e verificar que `sbw_ranked_*` permanece inalterado.
Validar desistencias e reconexao dentro do prazo. Nao testar queda do banco
de producao sem janela operacional.

## Limites

O arquivo de outbox e escrito pelo worker depois do evento de fim, deixando
uma pequena janela de perda se o processo terminar exatamente entre o fim
e a gravacao local. A Fase 6.2 deve fechar essa janela com um journal
sincrono e leve ou transferencia confiavel do resultado. Um crash completo
do servidor nao restaura partidas em andamento. O historico guarda o
resultado confirmado de cada participante, sem classificacao de segundo ou
terceiro lugar. Torneios, temporadas completas, GUI competitiva e
matchmaking sao fases futuras.
