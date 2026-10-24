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
