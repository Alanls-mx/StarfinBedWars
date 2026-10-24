# StarfinBedWars — Melhorias incrementais

Base: `main` @ `281d4479723ac5309593020c5199855cb9ce3ca3` (24/10/2026).

## Etapa 1 — Explosoes e protecao das arenas (implementacao nesta branch)

- As explosoes nas instancias do BedWars so podem destruir blocos registrados como colocados por jogadores da mesma partida.
- Camas e blocos originais do template permanecem protegidos.
- O registro das colocacoes e limpo depois que a explosao efetivamente ocorre.
- Drops vanilla dos blocos explodidos sao desabilitados para evitar duplicacao de defesas.
- Testes unitarios verificam os predicados da politica de explosoes.

**Validacao pendente:** executar `mvn -B verify` em ambiente Java 8 com Spigot/PaperSpigot 1.8.8 e testar TNT na partida, incluindo interacao com listeners externos. Este ambiente de implementacao nao dispoe de Maven nem permite baixar dependencias. Ainda e necessario validar fisica, TNT e Fireball em testes de integracao.

## Proximas etapas (ainda nao implementadas)

1. Mecânica real de TNT e Fireball, impulso/dano corretos e protecao de templates.
2. Gerenciamento de multiplas partidas usando um mesmo template de arena.
3. Persistencia de perfis, kills, final kills, camas, vitorias, XP e historico.
4. Idiomas individuais com contratos reais do StarfinMinigames.
5. Parties e entrada em grupos.
6. Catalogo de itens especiais, Generator Split, anti-AFK e eventos finais.
7. Perfis configuraveis por modalidade, API de add-ons e diagnostico administrativo.
8. Ranked, temporadas, campeonatos e rede.

Nao afirmar que essas etapas estao prontas. Implementar em branches e PRs incrementais, preservar compatibilidade Java 8 / Spigot 1.8.8 e validar cada etapa.

## Correcao de Shop / Upgrade com NPC visivel mas menu inacessivel

- Tratar separadamente os eventos 1.8.8: `PlayerInteractEntityEvent`, `PlayerInteractAtEntityEvent` e `PlayerArmorStandManipulateEvent`.
- Cancelar equipamento do ArmorStand e abrir a GUI no proximo tick, apos o processamento vanilla do clique, evitando abertura seguida de fechamento imediato.
- Aplicar debounce por jogador para evitar duas aberturas quando mais de um evento for disparado no mesmo tick.
- Resolver a loja apenas de NPCs registrados e do mundo da partida, inclusive quando metadados Bukkit sao perdidos durante descarregamento de chunk.
- Validar vinculo partida/equipe, estado de jogador, distancia e mundo novamente antes de abrir o inventario.
- Reparar grupos de NPCs se Villager, corpo equipado ou seus metadados essenciais estiverem invalidos. As linhas de texto nao possuem metadados intencionalmente e NAO devem provocar recriacao repetida.
- Logar reparos de NPCs e falhas de abertura. Testes unitarios validam parsing e isolamento de rotas de Shop/Upgrade.

**Validacao grafica ainda pendente:** dois jogadores de equipes diferentes devem testar varios cliques na cabeca, torso e Villager, com mao vazia e com itens equipados, alem de tentativas de retirar a cabeca/armadura do corpo do NPC. Repetir apos tempo ocioso, descarregar/carregar chunk e reiniciar partidas. Observar logs de reparacao e verificar que menus continuam acessiveis apos varios minutos.

## Etapa de gameplay: Itens especiais e loja Starfin Classica

Nova implementação na branch unica, utilizando como referencia comportamental o BedWars1058. Nenhum codigo GPL foi copiado.

### Itens implementados no codigo

- **Fireball (40 ferros):** item exclusivo identificado por metadata de item, dispara projétil explosivo sem incendio. A protecao do mapa permanece responsabilidade do MatchEngineService.
- **Bridge Egg (2 esmeraldas):** ovo lancado deixa trilha de la na cor da equipe dentro da regiao da arena, limitada a 70 ticks; blocos criados entram no registro de blocos colocados para quebra e explosao.
- **BedBug (40 ferros):** bola de neve invoca um silverfish aliado ao impactar, sem dano a aliados, com tempo de vida de 15 segundos e maximo 3 por equipe.
- **Dream Defender (120 ferros):** clique num piso livre invoca um Iron Golem aliado, maximo 2 por equipe e tempo de vida padrao 30 segundos; mira apenas inimigos da partida.
- **Magic Milk (4 ouros):** protege de disparar/consumir armadilhas inimigas durante 30 segundos, conectado ao TeamUpgradeService.

Os cinco itens sao persistidos no catalogo por migracao nao destrutiva do shop.yml. Cada item de habilidade recebe um lore oculto identificador `StarfinBW`, impedindo que itens vanilla da mesma classe disparem mecanicas personalizadas. Cada efeito pertence a uma GameMatch e e limpo no seu encerramento.

### Interface da loja

- 54 slots, nove abas e faixa de selecao, mantendo a disposicao classica sem reproduzir a arte do BedWars1058.
- Identidade Starfin no titulo, produtos com descricao, quantidade, custo e status de compra legiveis.
- Abas especiais adaptadas, categorias com favoritos e navegacao, painel de acoes inferior.
- A loja antiga continua usando o sistema de favoritos e compras existentes; nenhum core de Shop foi substituido.

### Validacao obrigatoria

O codigo foi enviado via GitHub, mas ainda **nao foi compilado nem validado num servidor** neste fluxo.

1. Rodar `mvn -B verify` com JDK 8 e Spigot 1.8.8.
2. Validar Fireball: impulso, dano entre equipes, destruicao exclusiva de blocos colocados, template protegido.
3. Validar Bridge Egg: trilha sem destruir estruturas, limite da regiao, morte/fim da partida, limpeza de tarefas.
4. Validar BedBug e Defender: alvo correto, nenhuma agressao a aliados, NPC Shop intactos, tempo de vida e sem drops de mob.
5. Validar Magic Milk: armadilha nao dispara durante protecao, mas continua na fila; apos expiracao a proxima invasao deve acionar.
6. Validar loja no cliente: design de categorias, status de compra, favoritos, ausencia de itens duplicados, GUI aberta apos minutos de jogo.
7. Validar migracao do shop.yml original ou personalizado com backups e slots ocupados.

### Ainda nao incluido nesta etapa

- Invisibility Potion com ocultacao de armadura por visualizador.
- Sponge, Pop-Up Tower, Rescue Platform e outros itens de eventos.
- Ajustes finais de knockback em Fireballs, pathfinding dos mobs e balanceamento por modalidade.
- Load test com diversas partidas simultaneas.

Nao chamar esses elementos de completos ate serem efetivamente implementados e testados.

## Correcao de clique 49/45 e mensagens (24/10/2026)

Sintoma informado: PaperSpigot 1.8.8 `PlayerConnection.a` dispara
`IndexOutOfBoundsException: Index: 49, Size: 45` quando o cliente manda
`WindowClick` para um slot que nao existe no container ativo no servidor.
A loja Starfin usa 54 slots e o slot 49 era o botao de Favoritos.

### Mitigacao aplicada
- `ClassicShopMenu` agora tem um unico inventario de 54 slots por sessao aberta.
- Mudanca de categoria/pagina, favoritos, compra e atualizacao de preco sao realizadas com `refresh(...)` no MESMO inventario, sem novo `player.openInventory()`.
- Atualizacao e agendada para o tick seguinte, confirmando que o jogador ainda visualiza aquela instancia do inventario.
- Cliques rapidos sao agrupados: no maximo uma tarefa pendente de refresh por jogador; o ultimo pedido prevalece.
- O botao Fechar confere que o inventario ainda e o mesmo antes de fechar.
- O processamento invalido de `WindowClick` acontece dentro do NMS, antes dos eventos Bukkit: esta mitigacao reduz possiveis dessicronizacoes, mas ainda exige teste no cliente PaperSpigot 1.8.8. Se a excecao persistir mesmo sem reaberturas, investigar incompatibilidade de cliente/protocolo/fork do PaperSpigot, sem prometer protecao absoluta no nivel Bukkit.

### Mensagens
- `messages.*` e `visuals.lobby-countdown.*` adicionados a `config.yml`, migrados sem sobrescrever personalizacoes antigas.
- Aviso ao tentar destruir cama da propria equipe: `Voce nao pode destruir sua propria cama!`.
- Entrada com contador atual de jogadores e capacidade da arena; entrada/saida/reconnect configuraveis.
- Contagem do lobby e abertura do spawn tratadas como fases distintas; evento `COUNTDOWN_STARTED` e Titles especificos de lobby e de spawn. Inicio real somente em `STARTED`, sem chat duplicado.
- Morte em Void, inclusive quando acionada por `setHealth(0)` abaixo do limite `voidHeight`: flag por partida para nao depender de `getLastDamageCause`.
- Mensagens configuraveis de quedas, fogo, lava, afogamento, projeteis, explosoes, combate, abate final, respawn e eliminacao definitiva.
- Evento `PLAYER_DIED` para titulo de respawn, sem confundir com `PLAYER_ELIMINATED`.
- Tutorial curto de inicio (configuravel) alem do unico broadcast do engine.
- Testes puros de templates/roteamento de causas e regressao da migracao.

### Validacao pendente (nao foi compilado/executado neste ambiente)
1. Executar `mvn -B verify` (Java 8 / PaperSpigot 1.8.8).
2. Abrir loja, editar favoritos (slot 49), trocar abas e comprar rapido por varios minutos; fechar e reabrir voluntariamente; verificar logs.
3. Clicar no slot 49 apos trocar rapidamente categorias; comprovar que a excecao `Index: 49, Size: 45` nao ocorre.
4. Iniciar lobby 2/8, 3/8; verificar valores corretos e contagens independente do spawn.
5. Cair no Void pela altura de morte e verificar mensagem propria; repetir apos um golpe inimigo para atribuicao ao atacante.
6. Testar morte por queda, explosao e combate; final kills com cama destruida.
7. Tentar quebrar cama da propria equipe com clique esquerdo e direito; confirmar aviso e bloco intacto.
8. Confirmar que o chat de inicio nao esta duplicado e que tutorial/titles aparecem somente na fase correta.
9. Confirmar migracao preservando `messages.own-bed` e outras mensagens personalizadas.

## Correcao de regressao: clique nas categorias sem resultado

Relato: a loja abre, mas clicar em Armaduras, Espadas e outras categorias nao altera os itens exibidos.

Causa provavel identificada na revisao estatica: `refreshLater()` usava
`player.getOpenInventory().getTopInventory() != menu.getInventory()`.
Bukkit/Spigot pode expor wrappers diferentes de `Inventory` para a mesma
janela; a comparacao de referencia causava `return` silencioso, descartando
o clique mesmo com `InventoryClickEvent` processado.

Mudancas:
- A navegacao atualiza exclusivamente a instancia de `ClassicShopMenu`
  associada ao clique, sem depender da igualdade de objetos `Inventory`.
- A funcao auxiliar `ShopInventoryGuard` compara tamanho e `InventoryHolder`
  para operacoes que exigem saber qual janela o jogador esta visualizando,
  como fechar a loja; teste JUnit com wrappers diferentes.
- Atualizacoes sao agendadas ao proximo tick e nao abrem nova janela.
- Removido `player.updateInventory()` das atualizacoes das categorias;
  atualizacoes dos slots da janela sao responsabilidade do Bukkit.
- Quando `general.debug: true`, o log inclui `[Shop] Categoria clicada`
  e `[Shop] Categoria exibida`. Excecoes de renderizacao sao registradas.

**Pendente:** compilar com `mvn -B verify` e testar no cliente PaperSpigot
1.8.8. Abrir Shop, clicar Armaduras, Espadas, Ferramentas, Blocos,
Utilitarios e Especiais, editar favoritos, mudar pagina e comprar. Confirmar
que a GUI muda sem fechar e que nao reaparece `Index: 49, Size: 45`.

## Balanceamento do Ovo de Ponte e Defensor de Sonhos

Solicitado: a ponte feita pelo Ovo de Ponte era fina e o Iron Golem recebia
recuo excessivo durante combates.

### Ovo de Ponte
- Padrao novo: **3 blocos de largura e 2 camadas de espessura** (3x2 por segmento),
  com la da cor da equipe.
- A secao transversal segue o eixo perpendicular ao deslocamento do ovo, com
  preenchimento de lacunas curtas entre os ticks (ate 6 blocos).
- Limites configuraveis: `special-items.bridge-egg.width` (1, 3, 5),
  `thickness` (1-2), `max-blocks` (padrao 480), `max-ticks` (padrao 70).
- Blocos so sao colocados em AIR, em chunks carregados e dentro da regiao MAIN
  da instancia da GameMatch, usando `MatchEngineService.placeSpecialBlock`.
  O mundo template nunca e substituido, e a equipe continua dona dos blocos.
- Geometria movida para `BridgeEggShape`, com testes de orientacao, largura,
  espessura, limites e imutabilidade.

### Iron Golem (Dream Defender)
- Somente o Iron Golem registrado em `SpecialItemService.mobs` recebe
  amortecimento do knockback; Villagers de Shop, monstros naturais e outros
  plugins nao sao afetados.
- Reducao padrao: horizontal `0.75` (25% menor), vertical `0.85` (15% menor).
  Multiplicadores na faixa 0.0 a 1.0, `1.0` restaura o comportamento vanilla.
- Amortecimento aplicado no tick seguinte ao dano corpo a corpo, projetil ou
  explosao, preservando o dano e a IA do golem. Eventos de dano cancelados nao
  agendam o amortecimento.
- Uma tarefa pendente por golem, cancelada ao morrer, ao acabar a partida ou
  ao desativar o plugin.

### Configuracao / compatibilidade
As novas opcoes foram adicionadas ao `config.yml` e ao migrador sem sobrescrever
valores personalizados. Foram adicionados testes do novo comportamento.

**Ainda falta homologar em PaperSpigot 1.8.8:** `mvn -B verify`,
jogar o Ovo de Ponte nos eixos X/Z/diagonais, testar nas bordas da arena,
confirmar que todos os blocos sao isolados por GameMatch e apagam quando a
instancia e removida. Comparar recuo de Defender com `0.75/0.85` e
`1.0/1.0`, inclusive Fireball e golpes de jogador. Nao declarar desempenho
ou balanceamento final como comprovados antes desses testes.

## NPCs de outras ilhas: Shop e Upgrades (2026-10-24)

Regra corrigida: um NPC fisicamente colocado em uma ilha NAO pertence
exclusivamente a equipe daquela ilha. Todos os jogadores conectados em
`IN_GAME` na mesma instancia de partida podem interagir com qualquer
Villager/ArmorStand de Shop ou Upgrades dentro do alcance do clique.

- `ShopNpcRoute.canAccess` nao compara mais o `islandTeamId` do NPC
  com a equipe do jogador. Continua exigindo UUID de GameMatch correto e
  um ID valido de equipe do comprador.
- `ShopNpcRoute.getPurchasingTeamId` retorna exclusivamente a equipe
  do jogador visitante. Nunca retorna a equipe do NPC.
- `ShopService.requestShopMenu` passa `purchaserTeamId` para
  `TeamUpgradeService.openShop`, independentemente da ilha onde o
  NPC esta localizado.
- `TeamUpgradeService.onUpgradeClick`, `buyUpgrade` e `buyTrap`
  continuam utilizando o `participant.getTeamId()` de quem clica;
  nenhuma melhoria, armadilha ou upgrade de gerador de uma equipe
  adversaria pode ser comprado acidentalmente.
- `ShopService.purchase` continua usando inventario, saldo de recursos,
  equipamentos permanentes e cor da la do comprador, nunca dados do NPC.
- Permanece proibido interagir com NPC de outra GameMatch, com um
  jogador fora da partida, espectador, desconectado ou distante demais
  do NPC (mais de 6 blocos).
- Testes JUnit de rotas de NPC de mesma/equipe diferente, alvo correto
  da compra e do upgrade, dados invalidos e isolamento entre partidas.

**Pendente validar em PaperSpigot 1.8.8:** testar pelo menos duas equipes:
jogador Azul vai ate a base Vermelha, compra la azul e itens para seu
inventario; compra Sharpness/Protection/Forge/Traps e verifica que
apenas o time Azul e modificado. Confirmar que a Vermelha nao ganha
beneficios e que os NPCs de outras partidas nao podem ser utilizados.

## Desaparecimento progressivo da ponte do Ovo de Ponte

Implementado na mesma branch de desenvolvimento, apos a ponte reforcada de
3 blocos de largura / 2 de espessura.

- O inicio da remocao ocorre **5 segundos depois de o Ovo de Ponte parar
  de construir**, por colisao, limite de distancia/ticks ou orcamento.
- As secoes da ponte somem **na ordem exata em que foram construidas**,
  desde a primeira secao (origem) ate a ultima (ponta).
- Uma secao transversal por passo, a cada 3 ticks (0,15 segundo a 20 TPS),
  sem tarefa Bukkit por bloco. Um scheduler unico atende todos os ovos ativos.
- Configuracoes: `special-items.bridge-egg.decay-delay-seconds` (padrao 5,
  permitido 0–120) e `special-items.bridge-egg.decay-interval-ticks`
  (padrao 3, permitido 1–20).
- A fila de secoes de cada ovo e independente: varios ovos e partidas
  simultaneas nao misturam suas construcoes.
- `BridgeBlockOwnership` preserva a identidade do ovo, da partida e da
  posicao; remover ou substituir blocos manualmente limpa a titularidade.
  A expiracao somente apaga la ainda pertencente aquele ovo e da mesma cor,
  sem drops e sem apagar outros blocos ou estruturas originais.
- Quebra, explosao e substituicao de blocos atualizam os registros de
  protecao do mapa; expiracao remove os registros de blocos desaparecidos.
- Fim de partida, exclusao da instancia e encerramento do plugin cancelam
  construcao e expiracao sem tarefas orfas.
- Testes novos: ordenacao FIFO das secoes, ausencia de secoes vazias,
  isolamento por partida/ovo, protecao de blocos substituidos e migracao
  sem sobrescrever valores anteriores do config.yml.

### Validacao pendente no servidor 1.8.8

Executar `mvn -B verify` com JDK 8, construir ponte com 2 ovos em ilhas
diferentes, aguardar o atraso, observar remocao gradual da origem ate a
ponta; tentar quebrar a ponte e reconstruir la no mesmo lugar antes do
decaimento; verificar que a nova la permanece. Testar projeteis que colidem
cedo, player sai do chunk e fim da partida durante o efeito. Essa verificacao
no servidor ainda nao ocorreu nesta rodada.

## Avisos de limites de arena e descricao curta da loja (24/10/2026)

A imagem do cliente Minecraft 1.8.8 mostrou tooltip de item extrapolando
a tela porque `ClassicShopMenu.refresh` colocava a descricao inteira em
uma unica linha de lore.

- `ShopLoreFormatter` divide descricoes em linhas com maximo de 28 caracteres
  visiveis por padrao, ignorando caracteres de cor, respeitando quebras de
  linha explicitas e preservando as cores nos trechos seguintes.
- `shop.description-line-length` no config.yml controla a largura (18 a 40).
  Nao altera custo, comportamento ou descricoes armazenadas em shop.yml.
- No `MatchEngineService.onMove`, ao aproximar-se do teto configurado da
  arena, aparece mensagem de altura.
- Ao aproximar-se dos limites horizontais X/Z de `ArenaRegion bounds`, aparece
  aviso informando que nao pode fugir do mapa; o bloqueio de movimento
  anterior permanece inalterado.
- `ArenaBoundaryWarnings` controla intervalos independentes por jogador e
  tipo de limite. Configurar em `messages.boundary-warning-cooldown-seconds`
  (padrao 4, aceito 1 a 15).
- Mensagens configuraveis `messages.boundary-height` e
  `messages.boundary-edge` migradas sem sobrescrever personalizacoes antigas.
  Os estados de cooldown sao descartados quando jogador desconecta, partida
  termina ou o plugin desliga.
- Testes adicionados para quebra de textos longos, cores, quebra de palavras,
  proximidade de limites X/Z, supressao de spam e migracao.

**Validacao pendente:** compilar `mvn -B verify` no JDK 8 e testar
tooltips no cliente 1.8.8 em diferentes escalas de GUI; testar subida
ate o teto, tentativa de sair pelas bordas da regiao configurada e
confirmar que nenhum aviso fica repetindo continuamente a cada movimento.

## Etapa: Vitoria com celebracao, Dream Defender permanente e Match Point (30 min)

Implementacao na **unica branch ativa**, sem alterar a `main`.

### Comemoracao ao vencer
- `MatchVisualService` inicia show de vitoria no evento `ENDED` somente
  para a equipe vencedora: **4 fireworks a cada 10 ticks**, multicoloridos,
  com efeitos de rastro e brilhos, sons e titulo de vitoria.
- Show continua ate o evento `PLAYERS_RETURNED` (retorno ao lobby), com
  cancelamento ao final da partida, remocao da instancia ou desativacao do plugin.
- Parametros em `visuals.victory.celebration`: enabled,
  fireworks-per-burst, interval-ticks e radius.

### Iron Golem
- `special-items.defender-persistent: true` (padrao): nao desaparece
  automaticamente em 30 segundos, mesmo quando um `config.yml` antigo
  conserva `defender-lifetime-seconds: 30`. Morre normalmente quando
  derrotado ou e removido ao terminar a partida.
- `defender-persistent: false` permite restaurar duracoes especificas
  configurando `defender-lifetime-seconds`.
- Verificacao de piso proprio e piso frontal proximo a cada 4 ticks; evita
  selecao de alvo atraves do vazio. Se houver queda acidental, retorna ao
  ultimo local com piso valido, sem teleportar outros mobs ou jogadores.

### Ultimo abate
- `FinalKillResourcePolicy` evita transferir ferro/ouro/diamante/esmeralda
  da vitima para o inventario do vencedor **quando a eliminacao encerra a
  partida**; abates regulares continuam concedendo recursos.
- Verifica cama destruida, ultimo jogador vivo do time e exatamente
  duas equipes restantes. Testes unitarios cobrem casos positivos/negativos.

### Match Point
- Temporizador iniciado em `STARTED`, ativando Match Point aos 30 minutos
  por padrao (`match-point.after-minutes: 30`).
- Ao ativar, cada cama restante e destruida no modelo e seus blocos sao
  removidos; eventos de cama destruida/alertas continuam sendo enviados.
- Spawna **um EnderDragon por partida**, invulneravel a dano de qualquer
  jogador e com comportamento de voo artificial, desenhando um circuito
  eliptico orientado pelas coordenadas dos spawns das equipes, sempre dentro
  dos limites da arena.
- A cada percurso, remove blocos solidos proximos (max. 18 por sweep
  configuravel) e causa dano aos participantes num raio configuravel.
  Destruicao automatica original de Dragon e desativada, evitando grief sem
  limites; a erosao planejada tambem remove rastreamento de blocos colocados.
- Cada mundo de partida e **uma instancia/clonagem descartavel**. Ao
  terminar, jogadores voltam ao lobby, a instancia e descarregada e excluida
  sem salvar. O template da arena continua intacto. Proxima partida e uma
  nova copia integral, restaurada do template.
- Se o dragao nao for criado, as camas nao sao quebradas. A ativacao pode
  ser tentada novamente, e a falha aparece no console.
- As tarefas e entidades sao removidas ao finalizar ou desativar o plugin.
- Configuracoes em `match-point.*`; testes puros de geometria e
  preservacao de limites do voo.

### NOVO: comando de teste
- `/sbw matchpoint`: quando o administrador esta dentro de uma partida
  em andamento, ativa o Match Point imediatamente.
- `/sbw matchpoint <id-da-arena>`: permite escolher a arena ativa,
  inclusive pelo console; autocomplete mostra apenas arenas em partida.
- Permissao exclusiva `starfinbedwars.command.matchpoint` (padrao OP),
  tambem permitida a `starfinbedwars.admin`. Auditado no log com nome
  do executor, arena e UUID da partida.
- Sem efeitos se nao existir partida em `IN_GAME`, nao existir mundo de
  instancia ou se Match Point ja estiver ativo. O comando percorre exatamente
  o mesmo fluxo da ativacao aos 30 minutos.

### Validacao ainda obrigatoria
A integracao foi aplicada no GitHub, mas **Maven nao esta instalado neste
ambiente e o JAR ainda nao foi compilado/validado em PaperSpigot 1.8.8**.
Executar `mvn -B verify` com JDK 8 e testar:
1. Partida com 2 equipes; executar `/sbw matchpoint` quando iniciar;
   todas as camas quebram, dragao surge, nenhum jogador consegue feri-lo.
2. Testar `/sbw matchpoint arena_id` pelo console e repetir (resposta
   ja ativo), negar usuarios nao-OP e negar arenas sem partida.
3. Validar circuito, velocidade e destruicao em diferentes mapas, incluindo
   altura/limites, danos em jogadores, sem destruir mundos externos.
4. Terminar a partida durante a destruicao; nova partida deve carregar mapa
   limpo do template, com camas intactas e sem EnderDragon residual.
5. Vencer normalmente: dezenas de fireworks ate retorno ao lobby, sem
   acumulo apos cancelamento/final.
6. Invocar Iron Golem com config antigo contendo `defender-lifetime-seconds:
   30` e verificar que permanece vivo; tentar atrai-lo ao Void e com Fireball.
7. Abate comum continua transferindo recursos; ultimo abate de vitoria nao
   transfere recursos ao inventario do vencedor.
8. Confirmar isolamento entre duas partidas simultaneas e performance.

## Correcao da movimentacao do EnderDragon no Match Point (imagem 24/10/2026)

Sintomas observados: o dragao se contorcia perto do alto da arena,
nao percorria as ilhas, apesar de o boss bar "MATCH POINT" aparecer.

### Causa identificada
O codigo executava `dragon.teleport(target)` a cada 10 ticks, enquanto
o motor vanilla do EnderDragon do Minecraft 1.8.8 tentava fazer suas
proprias curvas. O teleporte nao preservava o movimento natural nem
a historico de rotacao/posicao usado para animar cabeca, asas e cauda.
O `MatchPointPath` tambem usava raio de apenas 4 blocos no eixo sem
variacao de spawn, criando uma trajetoria extremamente achatada em mapas
com duas ilhas alinhadas.

### Solucao implementada
- `MatchPointDragonFlightV1_8_R3` controla os DESTINOS da propria IA do
  Minecraft 1.8.8 via campos nativos de `EntityEnderDragon`: `a`, `b`,
  `c` (coordenadas), `bA` (alvo de entidade) e `bw` (escolha aleatoria).
- NAO teletransporta repetidamente. O dragon avanca com velocidade e
  rotacao naturais; corpo, asas e cauda recebem interpolacao correta.
- O `MatchPointService` fornece destinos a cada tick e avanca para o
  proximo waypoint quando chega proximo ao atual, ou apos limite de
  tempo para evitar travamento. Selecao de alvo vanilla e cancelada apenas
  para o dragao desta partida.
- `MatchPointPath` nao fica mais achatada com duas ilhas alinhadas:
  mantem trajeto eliptico amplo, distribui distancia nos dois eixos,
  respeita as bordas e reserva margem para o corpo e as asas do dragao.
  Em mapas com ilhas nos quatro cantos, expande o raio da elipse para
  passar perto dos spawns em vez de circular longe das ilhas.
- Altura de voo baseada nos spawns das equipes, com variacao suave no
  eixo vertical dentro do teto configurado e dos limites do mapa.
- `damageNearby` e `destroyNearby` usam a posicao REAL do dragao,
  nao posicao ficticia da rota.
- Invulnerabilidade ampliada aos hits nos `EnderDragonPart`
  (cabeca, asas e cauda), alem da entidade principal.
- Nova opcao `match-point.dragon-waypoint-reach: 14.0`.
  `dragon-angular-speed` passa a controlar o incremento angular
  dos waypoints (nao teleportes).
- Testes de mapa de duas ilhas alinhadas em X e em Z, bordas e
  altitude, alem dos testes existentes.
- Configuracoes anteriores sao preservadas pelo migrador.

**Validacao pendente no PaperSpigot 1.8.8:** executar `mvn -B verify`,
comeca uma partida, usar `/sbw matchpoint`, observar o dragao voando
naturalmente e visitar todas as ilhas, sem se contorcer. Observar
destruicao proxima ao dragon, boss bar, danos aos jogadores,
invulnerabilidade ao acertar a cabeca/asas/cauda, e retornar ao lobby.
Testar mapas 2/4/8 equipes; nenhuma validacao grafica foi feita neste ambiente.

## Match Point: destruicao GLOBAL progressiva e comemoracao maior

Relato: o dragao voa corretamente, mas a destruicao local (`destroyNearby`,
max. 18 blocos/s) nao garante que o mapa inteiro desapareca. Tambem foi
solicitado prolongar um pouco o periodo de fireworks da vitoria.

### Destruicao completa, independente de jogadores
- Nova `MatchPointErosionCursor` percorre **todas as posicoes X/Y/Z** dentro
  de `arena.getBounds()` (fallback MAIN) uma unica vez, agrupadas por
  chunks, de cima para baixo e sem depender de jogadores online/proximos,
  visao do cliente, IA, posição ou velocidade do EnderDragon.
- A varredura comeca quando o Match Point e ativado (automaticamente aos
  30 min ou via `/sbw matchpoint`), depois da criacao do dragao.
- O dragao continua voando e destruindo blocos proximos, enquanto a
  nova onda de destruicao global percorre o restante da arena.
- Configuracao padrao: no maximo **3000 posicoes examinadas por tick** e
  **40 blocos removidos por tick**, configuraveis em:
  `match-point.global-erosion.scan-positions-per-tick` (250 a 8000) e
  `match-point.global-erosion.max-blocks-per-tick` (1 a 120).
- No maximo **um novo chunk carregado por tick** para evitar picos de
  carga; chunks que nao puderem ser carregados (ou estiverem ausentes)
  sao registrados no console e nao sao gerados a partir do nada.
- Todos os materiais normais sao removidos sem drops, inclusive agua,
  decoracoes e blocos de jogadores. `BEDROCK` permanece indestrutivel
  intencionalmente; mundo template jamais e modificado.
- Registros de blocos de jogadores/Ovo de Ponte sao removidos antes de
  apagar cada bloco. A varredura termina automaticamente ao visitar toda
  a regiao, ou e interrompida quando a partida termina. Proxima partida
  restaura o mapa ao clonar novamente o template original.
- Progresso 25/50/75/100% registrado nos logs por partida; o percentual
  e sobre posicoes verificadas, nao uma promessa de quantidade de
  blocos destruidos.
- Testes unitarios adicionais verificam cobertura completa, coordenadas
  negativas, atravessamento de chunks, progressao limitada entre ticks,
  limites verticais e invalidacao de regioes.

### Celebracao de vitoria estendida
- Antes: 100 ticks / 5 segundos ate restaurar jogadores ao lobby.
- Agora: **180 ticks / 9 segundos** para vitoria, configuravel por
  `match.victory-celebration-seconds` (5 a 20).
- Empate ou encerramento sem vencedor continua com os antigos 100 ticks.
- Fogos de artificio ja existentes continuam ate o evento PLAYERS_RETURNED.
- Migracao adiciona novos defaults sem sobrescrever personalizacoes.

### Validacao obrigatoria e limites
Nao houve build/teste ao vivo neste ambiente. Executar `mvn -B verify`
com JDK 8, iniciar partida e `/sbw matchpoint`; observar o dragao
percorrendo a arena e tambem os setores distantes sendo destruidos
gradualmente. Testar arena de varias alturas, chunks nao carregados,
grande volume de blocos, 1 e 2 jogadores ativos, jogadores longe do
dragao e finalizacao durante a destruicao. Ao iniciar uma nova partida,
confirmar mapa totalmente restaurado. Verificar celebracao com fireworks
durante os novos 9 segundos de vitoria.

## Hotfix de compilacao PaperSpigot 1.8.8 — EnderDragon.bA

O usuario compilou com `mvn -B verify` e recebeu um unico erro do compilador
em `MatchPointDragonFlightV1_8_R3.java:25`:
`cannot find symbol: variable bA` no `EntityEnderDragon` de sua API.
O campo `bA` existe em algumas fontes CraftBukkit publicas, mas nao esta
disponivel na distribuicao `patched_1.8.8.jar:git-PaperSpigot-445`
compilada pelo usuario.

Hotfix: remover `handle.bA = null` e documentar essa variacao de NMS.
Continuar direcionando waypoints com `a/b/c` e `bw`, campos que nao foram
apontados como ausentes nessa compilacao. A prevençao de seleçao aleatoria
de alvo permanece no listener de `MatchPointService`.

**A compilacao ainda precisa ser repetida no PC do usuario.** O log de
source/target 8 obsolete e apenas warning do JDK mais novo, nao o motivo
do erro. Nao alegar sucesso do build ate `mvn -B verify` concluir.

## Hotfix Match Point: dragao somente entre duas ilhas, sem destruicao visivel

### Problemas diagnosticados
1. O trajeto era guiado pelos **spawns dos times**, nao pela selecao
   `/sbw setarena pos1` e `pos2`. Em duas ilhas, isso restringia o
   voo ao corredor entre elas.
2. O servico escolhia `arena.getBounds()` (regiao de protecao Y=0..255)
   antes da regiao **MAIN** (pos1/pos2 real, com os limites de altura
   utilizados para cadastrar o mapa). Como resultado, a destruicao
   global analisava grandes volumes de ar.
3. A destruicao local examinava blocos proximo da ALTURA do corpo do
   dragao, que frequentemente voa sobre o vazio das ilhas.
4. A altura retornada por `World.getHighestBlockYAt()` no legado
   Spigot 1.8 pode apontar para o ar acima da superficie, e o heightmap
   nao inclui necessariamente alguns blocos transparentes.

### Correcao de voo
- `MatchPointArea.resolve()` escolhe MAIN antes da protecao, mantendo
  compatibilidade com arenas antigas sem MAIN.
- `MatchPointPatrolRoute` monta um percurso serpenteante por toda a area
  pos1/pos2: cobre bordas, cantos e centro independentemente da posicao ou
  quantidade de jogadores, sempre com margem para o tamanho do dragao.
- O sistema continua usando a IA NMS nativa com waypoints, **sem teleporte
  repetido**; ao chegar a um ponto ou apos o limite de ticks passa ao
  seguinte. A distancia entre pontos pode ser ajustada em
  `match-point.dragon-patrol-spacing` (padrao 40, 20..64).
- O console imprime a regiao de voo MAIN (X/Y/Z e quantidade de pontos).

### Correcao da destruicao
- O dragao pode voar acima da ilha, mas `destroyNearby()` agora
  mira o topo real dos blocos na coluna X/Z de sua passagem.
- Em 1.8, caso `getHighestBlockYAt()` retorne ar, procura para
  baixo ate encontrar bloco. Local alternado por passagem evita destruir
  sempre os mesmos blocos em um canto pequeno.
- `MatchPointErosionCursor` percorre TODOS os blocos na regiao MAIN.
  Para reduzir overhead de celulas no ceu sem deixar lacunas, pula SOMENTE
  secoes de chunk (16 blocos de altura) comprovadamente vazias por
  `ChunkSnapshot.isSectionEmpty` — preservando a varredura de
  vidro, la, agua e outros blocos ignorados pelo heightmap.
- Cada bloco apagado remove seu registro de construcao temporaria, sem
  gerar drops. BEDROCK continua preservado.
- Ao menos a cada 10 segundos, o console registra **quantos blocos foram
  efetivamente destruidos** na varredura global e pelo dragao.
- Apenas chunks clonados existentes na instancia da GameMatch sao
  alterados. O mundo template original nunca e destruido e a proxima
  partida inicia novamente de copia limpa.

### Testes / homologacao pendente
- `MatchPointAreaTest`: seleciona MAIN entre pos1/pos2 em vez da regiao
  de protecao Y=0..255 e testa fallback legado.
- `MatchPointPatrolRouteTest`: verifica cobertura dos 4 quadrantes,
  centro, bordas, trajetos com pontos proximos e altitude.
- `MatchPointErosionCursorTest`: verifica salto de secoes comprovadamente
  vazias sem corromper ordem/contagem e cobertura de blocos.
- `PresentationConfigMigratorTest`: confere novas configuracoes.
- **Nao foi executado `mvn -B verify` nem homologacao no PaperSpigot**
  nesta rodada. Rodar a compilacao com Java 8 no ambiente do usuario,
  iniciar partida, usar `/sbw matchpoint` e acompanhar no console
  quantidade de blocos removidos. Testar 2, 4 e 8 ilhas, vidro,
  construcoes fora da vista dos jogadores, restauracao posterior.

## DEFINICAO CORRIGIDA: voo em X, destruicao normal estilo TNT e barra permanente

### Interpretacao final do pedido (substitui as propostas de varredura e explosao em X)
- **O X e a trajetoria do dragao**, NAO o formato dos buracos. O dragao
  percorre os limites MAIN (pos1/pos2) cruzando de um canto ao canto
  oposto; na sequencia percorre a outra diagonal e alterna a direcao
  e a largura da passagem nas proximas voltas.
- `MatchPointPatrolRoute` agora monta trajetos diagonais interpolados
  com waypoints proximos (padrao maximo de 40 blocos entre pontos), sem
  teleportes repetidos, incluindo tres voltas com insets diferentes.
  Isso substitui o zigue-zague em grade anterior.
- **Destruicao e localizada** sob a passagem real do EnderDragon, em
  crateras aproximadamente circulares estilo TNT. `MatchPointBlastFootprint`
  devolve offsets ordenados pela distancia do centro, incluindo os
  eixos e diagonais — NAO o desenho em X da antiga classe removida.
- Defaults mais lentos: 1 impacto a cada **40 ticks (2 segundos)**,
  **ate 8 blocos** no total e **1 camada** de profundidade por coluna,
  raio **3 blocos**. Outro impacto requer deslocamento >= **5 blocos**,
  evitando cavar um buraco enorme se o dragao estiver preso.
- A antiga varredura global que removia rapidamente blocos por toda a
  regiao foi **retirada do servico e eliminada do projeto**, incluindo
  `MatchPointErosionCursor` e seu teste. As opcoes antigas
  `match-point.global-erosion.*` ou `match-point.blocks-per-sweep`
  salvas em configuracoes legadas ficam inertes; o migrador nao destrói
  valores personalizados antigos.
- Qualquer destruicao so pode modificar o mundo temporario da GameMatch.
  Nao destrói o template; outra partida inicia numa nova copia limpa.

### Barra MATCH POINT independente do campo de visao do dragao
- A barra nativa de vida do dragao no Minecraft 1.8.8 depende do tracking
  do mob no cliente (desaparece ao olhar ou andar para longe).
- Novo `MatchPointBossBarV1_8_R3`: entidade Wither invisivel,
  **somente em pacotes para cada cliente** (sem spawn real no mundo).
  A entidade fake e mantida a 25 blocos abaixo do jogador e atualizada
  a cada 10 ticks; a barra MATCH POINT nao depende de estar olhando
  para o dragao ou da distancia de renderizacao.
- As entidades fake sao removidas ao sair da partida, desativar a
  funcionalidade, trocar de mundo, morrer/renascer, desconectar ou
  desativar o plugin. `match-point.bossbar-enabled: true`.
- O dragao real continua invulneravel e usando a IA NMS para voar.
- Testes: `MatchPointPatrolRouteTest` para diagonais alternadas e
  cruzamentos centrais; `MatchPointBlastFootprintTest` verifica cratera
  circular de 29 posicoes (raio 3), incluindo offsets fora do X;
  `PresentationConfigMigratorTest` verifica defaults e preservacao
  de configuracoes customizadas.

**Teste real ainda obrigatorio:** compilar `mvn -B verify` com Java 8
e executar em PaperSpigot 1.8.8. Iniciar Match Point via
`/sbw matchpoint`, conferir voo em X alternado por toda a MAIN, crateras
pequenas e lentas apenas perto do dragao, bossbar ao olhar para outro
lado / em partes distantes da arena, respawn e troca de mundo. Confirmar
retorno ao lobby e instancia nova intacta. Nenhum JAR compilado nesta sessao.

- O trajeto em X alterna entre uma passagem ampla sobre os cantos
  definidos por pos1/pos2 e uma passagem cruzada mais estreita sobre
  os spawns quando as duas ilhas estao alinhadas nos eixos X ou Z.
  Assim o voo ainda explora o mapa inteiro, mas tambem atinge as ilhas
  reais; nao tenta forcar a destruicao de toda a area ao mesmo tempo.

## Hotfix: NoClassDefFoundError ShopNpcFactoryV1_8_R3 ao desligar

### Relato observado no PaperSpigot-445 / Java 8u504
O usuario enviou stack trace de `onDisable`:
`java.lang.NoClassDefFoundError:
com/starfinplugins/bedwars/shop/npc/ShopNpcFactoryV1_8_R3`,
originado em `ShopService.stop(ShopService.java:125)`, cujo
`ShopNpcFactoryV1_8_R3.unregisterType()` era incondicional.

**O arquivo-fonte ShopNpcFactoryV1_8_R3.java ESTÁ presente no repositorio
e o pom.xml usa empacotamento JAR Maven padrao.**
Portanto nao remover os NPCs ou substituir por NPC vanilla como "correcao":
a falha indica que a classe nao estava acessivel ao classloader do plugin
NO JAR CARREGADO, e a causa exata depende da instalacao. Causas plausiveis:
JAR implantado incompleto/diferente ou substituido enquanto o servidor
ainda o utilizava. O stack trace isolado nao determina a causa.

### Protecoes implementadas
- `ShopService.stop()` protege o unregister contra
  `RuntimeException | LinkageError`, registra mensagem e stack trace
  explicando que o JAR deve ser reinstalado com o servidor desligado, e
  continua a limpeza dos mapas e tarefas do Shop.
- `ServiceLifecycle.stop()` captura `LinkageError` por servico, assim
  uma falha de carregamento NMS nao impede outros servicos de liberar
  mundos, inventarios, timers etc. Na inicializacao, `start()`
  tambem garante shutdown de servicos iniciados se houver LinkageError.
- `StarfinBedWarsPlugin.verifyBundledClasses()` roda antes de
  inicializar servicos, usando `Class.forName(..., false, classloader)`
  nas classes NMS essenciais: `ShopNpcFactoryV1_8_R3`,
  `ShopNpcVillager`, `MatchPointDragonFlightV1_8_R3` e
  `MatchPointBossBarV1_8_R3`; se faltar, nao inicializa metade do plugin
  e registra erro claro de pacote incompleto ou incompativel.
- `pom.xml` adiciona fase `maven-antrun-plugin:run` em `verify` para
  conferir que as quatro classes estao PRESENTES no JAR final, nao apenas
  no diretorio compilado. Build falha com mensagem especifica se faltar.
- Teste `ServiceLifecycleLinkageTest` simula
  `NoClassDefFoundError` em stop e start; verifica ordem de limpeza,
  agregacao dos erros e liberacao de todos os servicos.

### Implantacao segura no Windows
1. Executar `stop` no console e aguardar o processo Java ENCERRAR
   completamente (nao apenas ficar na tela inicial).
2. Em CMD, na raiz do projeto:
   `git fetch origin`,
   `git switch improvement/phase-1-explosion-protection`,
   `git pull --ff-only origin improvement/phase-1-explosion-protection`,
   `mvn -B verify`.
3. Conferir conteudo do JAR:
   `jar tf target\\StarfinBedWars-1.0.0-SNAPSHOT.jar | findstr
   /C:"ShopNpcFactoryV1_8_R3.class"`. O comando deve imprimir a
   entrada da classe dentro do archive.
4. Copiar `target\\StarfinBedWars-1.0.0-SNAPSHOT.jar` para
   `plugins\\` do servidor, removendo JARs antigos/duplicados
   da mesma versao; conferir que os hashes dos arquivos origem/destino
   sao identicos, se necessario via `certutil -hashfile <arquivo> SHA256`.
5. Iniciar o servidor do ZERO; jamais copiar/substituir o JAR de um
   plugin que ja esta carregado, e nao usar `/reload`.

### Estado da validacao
Alteracoes enviadas ao GitHub mas nao compiladas/testadas neste ambiente.
Ainda executar `mvn -B verify` e um ciclo real de startup/partida/stop
no PaperSpigot-445 com o JAR novo. Caso o problema continue com classe
presente no JAR, recolher as PRIMEIRAS mensagens de erro durante
a inicializacao e confirmar o checksum do JAR em `plugins/`.

## Match Point: priorizar ilhas/centro + explosoes realmente quebrando blocos

**Relato de teste no PaperSpigot 1.8.8:** dragao voa entre bordas e
cantos de MAIN, mesmo com ilhas no meio, e os efeitos de explosao
aparecem sem destruicao perceptivel de construcoes.

### Diagnostico no codigo
- `MatchPointPatrolRoute` gerava seus quatro vertices a partir de
  `MAIN` (pos1/pos2), mesmo quando a maior parte do cuboide era Void.
  Uma fase passava pelas bases, mas tres fases voltavam as bordas.
- `dragon-waypoint-reach` tinha tolerancia de 14 blocos (ou mais que
  raio 3 do blast), fazendo o dragao desistir da aproximacao antes de
  chegar perto das plataformas.
- A explosao usava `world.getHighestBlockYAt(x,z)` e descia no maximo
  **12 blocos**: em ilhas flutuantes isso frequentemente nao alcança o
  primeiro bloco real embaixo da altura do dragao.
- Nao havia log discriminando explosoes reais de tentativas em colunas
  vazias ou chunks descarregados.

### Correcao implementada
- `MatchPointPatrolRoute` calcula um **X centrado no conjunto dos
  spawns das equipes**, com margem curta e cruzamento central em cada
  diagonal. `MAIN` agora e usada para CLAMP/seguranca; sem coordenadas
  de ilhas validas, ha fallback para o cuboide. A rota inclui waypoints
  EXATOS de cada spawn por fase, alternando a ordem das visitas.
- O alcance de waypoint passa a no maximo **5 blocos em runtime**,
  inclusive para arquivos antigos com valor 14. Novo default: 4.
  Timeout de waypoint aumentado a 700 ticks para evitar pular destinos
  longos antes de o dragao alcança-los.
- `MatchPointSurfaceSearch` examina todos os valores Y entre minimo de
  MAIN e a altura REAL do dragao + 4; nao usa o heightmap do vanilla e
  detecta vidro, la, terra e estruturas flutuantes em qualquer altura.
  Ainda remove no maximo os 8 blocos por explosao e ate 1 por coluna
  (defaults). Nao apaga o mundo template ou blocos BEDROCK.
- Quando um impacto realmente remove blocos, o efeito visual e o som
  ocorrem no local do BLOCO destruido. Ao nao encontrar terreno, nao
  cria explosao fantasma; log periodico detalha coordenadas, limites de
  altura e chunks nao disponiveis. Processamento das buscas vazias e
  limitado a uma tentativa por intervalo de 40 ticks para evitar lag.
- Logs tambem registram total de blocos efetivamente destruidos na
  instancia a cada 32 blocos.

### Testes adicionais
- `MatchPointPatrolRouteTest`: quatro ilhas agrupadas em -40/+40 num
  mapa de -200/+200; trajetoria deve passar pelo centro e priorizar
  ilhas, nunca ir aos cantos vazios. Em mapa com duas equipes no eixo X,
  nao usa extremos vazios do eixo Z e visita as duas bases.
- `MatchPointSurfaceSearchTest`: acha blocos em Y=90 quando o teto e
  Y=180, reconhece blocos transparentes, respeita limites e pisos
  negativos/vazios.

**Ainda pendente** `mvn -B verify` e validacao real no PaperSpigot-445:
entrar numa partida, usar `/sbw matchpoint`, observar voo sobre as
ilhas e centro; conferir crateras reais na ilha e log `blocos
destruidos de verdade`. Testar todos os layouts existentes, inclusive
mapas de 2 e 4 equipes, protecao do template e recompilacao limpa.

## Match Point: rotas por TODOS os blocos do mapa, metas 5/8 minutos e raios (24/10/2026)

O usuario confirmou que as crateras da passagem do EnderDragon funcionam,
mas as rotas continuam demasiado focadas nas ilhas com cama. Requereu
destruicao configuravel de 80% do mapa ate 5 minutos e 100% em 8 minutos,
independentemente da quantidade de jogadores, alem de raios opcionais
QUE REALMENTE destroem blocos, ligados por padrao.

### Mudancas implementadas
- `MatchPointMapIndex` percorre, incrementalmente na thread do servidor,
  os snapshots dos chunks presentes na MAIN delimitada por pos1/pos2.
  A indexacao considera TODOS os blocos presentes (exceto AIR e BEDROCK)
  no inicio do evento, incluindo ilhas SEM camas, ilhas centrais, vidro,
  la, plataformas, geradores etc. Pula apenas secoes de chunks vazias
  e limita leituras a 7000 posicoes por tick e 1 novo chunk por tick
  por padrao, sem gerar chunks ausentes.
- `structuralHotspots(24)` identifica chunks com mais construcao e
  distribui pontos de visita por estruturas separadas. Depois de
  concluir a indexacao, a rota em X passa pelos spawns das equipes,
  pelos geradores centrais/diamante/esmeralda e pelos centros das
  principais construcoes detectadas, sem depender dos times participantes.
  Os limites da MAIN continuam sendo o limite de seguranca.
- `MatchPointDemolitionSchedule` define curva linear em dois trechos,
  baseada na QUANTIDADE INICIAL DE BLOCOS DESTRUTIVEIS, nunca no
  volume de ar: 80% apos 5 minutos, 100% apos 8 minutos.
  `match-point.demolition.partial-minutes: 5`,
  `partial-percent: 80`, `complete-minutes: 8`.
  O relogio comeca no momento da ativacao, via timer ou `/sbw matchpoint`.
- Remocao programada `advanceDemolition` busca blocos em chunks com
  construcoes, priorizando a proximidade do dragao, com limite de 220
  blocos por tick por padrao (max configuravel 1400). Pode compensar
  atraso da indexacao. Os impactos continuam na instancia temporaria
  e o template permanece intacto.
- Progresso na consola com blocos originais processados, removidos, e
  inacessiveis; se chunks falharem ou o limite/taxa TPS nao permitirem
  a meta no horario, emite aviso de atraso, sem prometer falsos 100%.
  `MapIndex.retryLast` evita registrar como removido um bloco cuja
  instancia nao pode ser carregada ou cujo tipo nao pode ser apagado.
  Em chunks inacessiveis ja durante a indexacao, informa quantos
  ficaram de fora, nao os declara destruidos.
- `match-point.lightning.enabled: true` por padrao. A cada 60 ticks
  (configuravel), escolhe impacto entre os blocos realmente removidos
  no progresso programado e executa `strikeLightningEffect` visual.
  O plugin quebra AINDA MAIS blocos da estrutura em raio configuravel
  (3 blocos, ate 24 blocos por raio), sem ativar dano, incendio ou
  eletricidade vanilla fora da area. Apos desativar com `enabled: false`,
  a curva 5/8 minutos e a destruicao do dragao continuam independentes.
  O impacto nao gera efeito se nenhum bloco local adicional foi removido.
- As crateras menores criadas pelo proprio voo continuam ativadas; a
  aceleracao controlada por metas atua em blocos reais mapeados, nao
  varre cuboides inteiros de ar, sem depender de jogadores proximo.
- Configuracoes adicionadas sem substituir valores personalizadas em
  `PresentationConfigMigrator`:
  `match-point.demolition.{partial-minutes,partial-percent,complete-minutes,survey-positions-per-tick,max-blocks-per-tick}`;
  `match-point.lightning.{enabled,interval-ticks,radius,max-blocks-per-strike}`.
- Testes: `MatchPointDemolitionScheduleTest` (metas e customizacao),
  `MatchPointMapIndexTest` (ilhas centrais SEM camas, vidro, exclusao
  de bedrock, retry nao falseia %), extensao dos testes do migrador
  (default lightning ativo, config lightning desligado), e testes do
  trajeto ja presentes.

### Limites operacionais e homologacao
As metas de 5/8 minutos dependem de TPS, quantidade de blocos,
limite maximo configurado por tick, chunks acessiveis e tempo da
indexacao. Elas sao alvos reais medidos; o codigo nao faz promessas
impossiveis de 100% se um chunk falhar ou o servidor estiver sobrecarregado.
A estatistica e relativa aos blocos ORIGINAIS indexados quando Match
Point inicia, nao as construes adicionadas depois por jogadores.
Mundo instancia descartado ao terminar jogo -> mapa template restaurado
na proxima partida.

**A ser validado no servidor do usuario:** `mvn -B verify` com Java 8,
`/sbw matchpoint`, observar rota cruzando centro, plataformas sem camas
e outros geradores; testar raios enabled:true/false, destruicao real ao
lado das ilhas, atingir ~80% em 5min e ~100% em 8min com a populacao de
blocos do mapa real. Se ficar atrasado, ajustar
`demolition.max-blocks-per-tick` com monitoramento de TPS; conferir
limites da MAIN, chunks, logs de indexacao e restauracao apos nova partida.
Nao ha Maven/nem homologacao PaperSpigot nesta sessao.
