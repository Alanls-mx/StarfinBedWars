# Prompt 6.1 — auditoria e correcoes

> Registro historico do Prompt 6.1. A etapa 6.2 substituiu o Villager adaptado por um NPC customizado e implementou o sistema de inventarios; consulte `STARFINBEDWARS_INVENTORY_AND_VISUALS.md` para o estado atual.

## Causas identificadas antes das alteracoes

- **NPCs:** `ShopService` cria Villagers vanilla sem desabilitar IA/sons. A tarefa `keepShopsInPlace` teleporta a entidade a cada 10 ticks, escondendo o deslocamento sem impedir pathfinding nem sons. Nao existem hologramas proprios para as lojas.
- **Mobs:** nao ha listener central de spawn para os mundos gerenciados; o controle atual cobre apenas alguns danos e blocos.
- **Hologramas:** o wizard cria ArmorStands no clone de edicao, enquanto `ResourceGeneratorService` cria outros na partida. Os registros em memoria ja sao separados, mas a copia de arquivos do template pode levar entidades persistidas de edicao para o jogo. E preciso evitar isso no ciclo de edicao/publicacao.
- **Geradores:** `generatorLocation` tenta 16 pontos de ar e, se falhar, retorna a posicao central sem exigir que esteja dentro da regiao. O drop vanilla pode adquirir velocidade e sair da regiao. `countTaggedItems` ignora itens rastreados que sairam da regiao, portanto o limite pode ser burlado. Diamante e Esmeralda usam limites de 8/12/16 e 4/6/8, em vez de quatro unidades em todos os niveis. O relogio de `GeneratorInstance.advance` ja avanca mesmo quando cheio.
- **Scoreboard:** existe um quadro por jogador, mas o layout de jogo usa apenas Diamante, ate dez equipes e estados `OK/X`; nao distingue eliminacao nem tem tags de equipe. O gerenciador atual pode coordenar sidebar e TAB sem trocar `Player#setScoreboard`.
- **Cama:** a engine dispara `BED_DESTROYED` uma vez apos `destroyBed`, mas o aviso atual nao inclui destruidor nem title/som para a equipe afetada.
- **TAB e chat:** nao existe implementacao. Mensagens de chat vanilla nao sao isoladas por partida.
- **Respawn:** `TeamDefinition.spawn` representa o spawn inicial dentro da sala temporaria. A engine reutiliza esse mesmo ponto em `PlayerRespawnEvent`; a estrutura e removida no inicio da partida, de modo que o jogador reaparece sem piso e cai. Falta um ponto persistido de respawn por equipe e uma etapa no setup para defini-lo.

## Limites da auditoria

O StarfinMinigames nao foi encontrado no workspace nesta etapa; nenhuma API sua sera presumida. Os documentos de arquitetura do StarfinCore sao historicos e nao descrevem uma dependencia atual do BedWars. As tres imagens mencionadas no prompt nao constam entre os anexos desta mensagem; a hierarquia textual do layout sera usada como referencia. O workspace nao possui `.git`, portanto nao ha status de alteracoes Git a preservar.

## Implementacao e verificacao

- Arena YAML v7 persiste `respawn` separado. O assistente o exige apos criar a sala inicial; o comando manual aceita `respawn` e ambos verificam piso e espaco livres. A validacao impede habilitar arenas sem o novo ponto. Morte e retorno por reconnect usam o ponto de respawn, com busca por piso seguro nas proximidades quando necessario.
- O gerador central passou a usar exatamente a posicao configurada acima do bloco clicado. O deslocamento vertical de 4,2 blocos para a altura do holograma foi removido. Novos geradores centrais do assistente registram a area acima do bloco. O limite efetivo e de quatro unidades em todos os niveis, inclusive com `generators.yml` antigo; itens etiquetados sao contados pela quantidade da pilha, e ciclos cheios continuam avancando.
- Regioes de geradores deixam de recorrer a um ponto fora da regiao quando nao ha ponto seguro. O drop recebe velocidade inicial zero e itens rastreados que escapam de uma regiao configurada sao reposicionados a cada segundo.
- Villagers de loja tem pathfinding/seletores de alvo removidos por adaptador isolado `v1_8_R3`. A tarefa de teleporte a cada 10 ticks foi substituida por verificacao a cada cinco segundos apenas para deslocamentos externos maiores que meio bloco. Cada NPC recebe holograma configuravel; os NPCs e hologramas pertencem ao ciclo da partida. Spawns de criaturas em mundos/regioes gerenciados sao bloqueados salvo operacao autorizada do plugin; criaturas antigas da copia de jogo sao limpas antes da partida.
- O placar por visualizador agora distingue cama intacta, destruida e equipe eliminada. Usa data, mapa, contador real do gerador e marca configuravel. O mesmo scoreboard fornece tags e ordenacao do TAB; cabecalho/rodape sao enviados por adaptador `v1_8_R3`. O chat global e de time e limitado a partida, e o chat de espectadores e isolado.
- O evento autoritativo de destruicao de cama agora produz um unico broadcast com o nome do destruidor quando conhecido, title e som configuraveis apenas para a equipe afetada. O broadcast duplicado da engine foi removido.
- Hologramas do editor usam titulo `EDITOR` e sao removidos antes de publicar o clone como template.
- `mvn clean verify -q` concluiu com sucesso; testes de persistencia/validacao do respawn e do limite central foram adicionados. A verificacao em servidor 1.8.8 real permanece pendente.

## Limites restantes

- A API Spigot 1.8.8 nao expõe `setSilent`; o adaptador atual desativa IA, mas nao suprime com garantia os sons ambientes do Villager.
- O projeto nao possui servico de idioma por jogador, por isso textos de sistema seguem em portugues configuravel, sem traducao individual. A integracao com StarfinMinigames depende de acesso a sua API.
- O contador de eventos do placar mostra o proximo ciclo real do gerador central; evolucoes globais de geradores, evento final e evento de camas ainda nao existem na engine.
- Cabecalho TAB, comportamento de NPCs, hologramas e alertas precisam de teste funcional com clientes em um servidor Spigot 1.8.8.
# Prompt 6.4 — atualização visual

Blocos centrais de Diamante e Esmeralda usam rotação pela pose da cabeça e flutuação calculada a partir da âncora. Quatro ícones pequenos de minério orbitam cada bloco. Os textos ficam acima do conjunto e são atualizados somente quando mudam. Hologramas das camas usam packets individuais por equipe; nenhuma entidade física de cama permanece no mundo. Veja `STARFINBEDWARS_ANIMATED_HOLOGRAMS.md` para configuração e validação.
