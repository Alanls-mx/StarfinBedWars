# Reconnect do StarfinBedWars

## Estado implementado

O reconnect funciona **na mesma instancia em execucao** de uma partida Casual. Ao sair durante `IN_GAME`, a engine preserva o UUID, partida, arena, equipe, estado de vida e prazo absoluto. `PlayerStateSnapshot` captura o inventario temporario do BedWars, armadura, slot ativo, vida, fome, saturacao, efeitos e ultima localizacao. O snapshot anterior a partida continua separado em `ParticipantInventoryService`.

O prazo padrao e 120 segundos, inclusive no instante limite. O temporizador central verifica vencimentos a cada segundo, mas a tentativa tambem valida o relogio no momento da entrada. Varias tentativas nao ampliam o prazo. Expiracao ou invalidacao marca abandono, libera a participacao ativa e reavalia a vitoria. Um jogador ausente continua reservando sua equipe ate esse momento.

No login pendente, o jogador e colocado no local anterior a partida, em espera, sem os itens temporarios expostos. Ao retornar, o servico valida UUID, partida ativa, equipe original e mundo carregado. O teleporte usa a ultima posicao se ainda houver piso seguro; caso contrario usa o spawn da equipe. O snapshot substitui o inventario temporario integralmente uma unica vez. As melhorias atuais da equipe sao reaplicadas sem adicionar novos itens. O scoreboard existente e atualizado pelo servico de apresentacao. Se o teleporte falhar, a sessao volta a estar disponivel ate o prazo.

Se a partida terminar durante a ausencia, a sessao e invalidada. No proximo login do mesmo processo, o inventario original salvo por `ParticipantInventoryService` e restaurado; o retorno ao lobby usa a localizacao anterior quando ela ainda esta disponivel. Itens temporarios de BedWars nao sao entregues apos o fim da partida.

## Vida, combate e cama

`ConnectionState` distingue conectado, desconectado, restaurando e abandonado. `GameParticipant` preserva o estado de vida anterior durante a ausencia. Uma desconexao durante espera de respawn retorna ao ciclo de respawn; se a cama caiu nesse intervalo, ocorre eliminacao. Um jogador que saiu vivo e fora de combate pode retornar vivo mesmo apos a cama ser destruida, mas nao recebe novo direito de respawn.

Um dano PvP corpo a corpo ou por projetil marca os dois participantes por 15 segundos. Sair dentro dessa janela conta como morte para o estado de reconnect. Cair para baixo de Y=5 com queda acumulada tambem conta como morte. O inventario segue a politica atual da engine, que **mantem os itens na morte**. Nenhuma kill e atribuida automaticamente pelo logout. A deteccao de risco ambiental e limitada: outros riscos, como fogo ou explosoes, ainda nao tem politica especifica de combat logout.

## Configuracao

`plugins/StarfinBedWars/reconnect.yml` e criado na primeira inicializacao:

| Chave | Padrao | Faixa/efeito |
|---|---:|---|
| `reconnect.enabled` | `true` | `false` transforma quit em abandono imediato. |
| `reconnect.mode` | `AUTOMATIC` | `AUTOMATIC` ou `CONFIRMATION`. |
| `reconnect.grace-seconds` | `120` | Inteiro de 1 a 3600. |
| `reconnect.combat.tag-seconds` | `15` | Inteiro de 0 a 120. |
| `reconnect.combat.disconnect-counts-as-death` | `true` | Aplica morte ao logout em combate. |

No modo `CONFIRMATION`, uma mensagem e um menu com Retornar, Informacoes e Voltar aparecem no login. O botao Retornar usa o mesmo metodo que `/reconnect`. Todas as mensagens atuais sao em portugues.

## Comandos e permissao

- `/reconnect` e `/sbw reconnect`: tenta recuperar a propria sessao; nenhuma permissao especial. O comando dedicado `reconnect` nao tem alias e pode conflitar com outros plugins que registrem o mesmo nome; `/sbw reconnect` permanece disponivel.
- `/sbw reconnect status`: arena, equipe, prazo e estado da propria sessao.
- `/sbw reconnect sessions` e `/sbw reconnect inspect <uuid>`: exigem `starfinbedwars.command.reconnect.admin` (operadores por padrao).
- `/sbw reconnect invalidate <uuid> confirm`: exige a mesma permissao, confirmacao textual e registra a acao no log.

## Persistencia, reinicio e integracoes

`reconnect-sessions.yml` guarda metadados sem inventarios. As escritas sao sequenciais, assincronas e substituem o arquivo por troca atomica quando o sistema de arquivos permite. Na inicializacao, sessoes antigas sao contadas e invalidadas, pois a engine nao restaura a partida nem o mundo apos reinicio ou crash. Snapshots de inventario e do estado anterior a partida vivem somente na memoria do processo; por isso **nao ha recuperacao pos-crash**.

O codigo local do StarfinMinigames nao estava disponivel nesta etapa e nenhuma API compartilhada foi inventada. Sera necessario um contrato de idioma por UUID para mensagens individuais, um armazenamento/roteamento de sessoes para rede, e um transporte de proxy para BungeeCord. Ranked e campeonatos ainda nao existem na engine; os prazos sugeridos de 90 e 180 segundos dependem dessas modalidades e nao sao opcoes ativas. Kits e habilidades tambem nao existem para integrar.

`ReconnectLifecycleEvent` publica `CREATED`, `ATTEMPTED`, `COMPLETED`, `REJECTED`, `EXPIRED`, `INVALIDATED` e `RESTORE_FAILED` como eventos nao cancelaveis. `COMPLETED` ocorre somente depois da restauracao. O evento ja existente `MatchLifecycleEvent.PLAYER_RECONNECTED` atualiza apresentacao e upgrades.
Companheiros da mesma equipe recebem avisos de desconexao, retorno e expiracao em portugues.

## Verificacao e limites

`mvn -B clean verify` compilou para Java 8 com 43 testes aprovados. Um servidor PaperSpigot 1.8.8 local iniciou o plugin, registrou o servico `reconnect`, respondeu a `/sbw status` e `/sbw reconnect sessions`, e desligou normalmente. Nao havia arena configurada nem dois clientes para validar o fluxo real de desconexao, inventario, combat log, GUI e vitoria em partida. Esses cenarios precisam ser testados antes da distribuicao.

Para diagnosticar, consulte o log por UUID de jogador e partida: criacao, conclusao, expiracao, invalidacao e falhas de escrita sao registrados sem inventarios. Se o mundo original nao existir, a tentativa e recusada; se o arquivo de metadados estiver invalido, e ignorado e o log registra a causa.
