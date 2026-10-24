# Validacao em servidor — 24/10/2026

Ambiente: PaperSpigot 1.8.8 build 445, Java 8, arena descartavel `duel`, dois clientes Mineflayer 1.8.8 (`RedTester` e `BlueTester`). Esta validacao foi executada no servidor local da tarefa, nao no servidor do usuario.

## Observado em partida

- Arena carregou apos reinicio e iniciou com dois jogadores; NPCs, hologramas dos geradores e indicadores das camas apareceram como entidades para os clientes.
- Scoreboard transmitiu `§c§lV §fVermelho`.
- Geradores ficaram inativos ate a abertura do spawn e depois soltaram os recursos nos blocos configurados. Ferro nivel 1 foi observado em intervalos de 4 segundos.
- O clique no NPC abriu a loja. O peitoral abriu `Loja: Armaduras` e substituiu os itens abaixo pelas armaduras. As abas `Melhorias: Equipe` e `Melhorias: Armadilhas` tambem abriram.
- O adversario quebrou a cama vermelha. A morte seguinte eliminou o jogador, encerrou a partida e o cliente reapareceu automaticamente fora da instancia, sem ficar na tela de morte.
- Na nova rodada de correcao da cama, um jogador azul iniciou o clique esquerdo na metade oposta da cama vermelha. A cama foi destruida antes do pacote de termino da quebra; os dois blocos passaram a `air` para o cliente. O mesmo clique na cama azul da propria equipe manteve o bloco intacto.
- Com cama ativa, o cliente saiu da tela de morte, ficou em `spectator` por 3 segundos e retornou a `survival` no ponto de respawn da equipe.
- Sem permissao, `&c` foi exibido literalmente no chat; com `starfinbedwars.chat.color`, a cor foi aplicada.
- O comando `/sbw npc skin upgrades AlanLS_` atualizou e salvou a configuracao; o nome foi depois restaurado para `Lurreisper`.
- A migracao acrescentou seis itens ao `shop.yml` existente e criou `shop.yml.pre-extended-catalog.bak`. TNT apareceu no Quick Buy e foi comprado com 4 ouros, resultando em um TNT no inventario.
- Ultimo `mvn -B verify -q`: 59 testes, sem falhas. O ultimo startup nao apresentou excecao do plugin.

## Pendente de validacao visual conjunta

As texturas das cabecas dos NPCs, a animacao do minerio, o enquadramento dos hologramas em uma ilha compacta e a audicao dos sons de compra exigem observacao em um cliente Minecraft grafico. Os clientes automatizados validaram entidades, menus, inventario e transicoes de modo de jogo, mas nao renderizacao ou audio. A comparacao exata com a antiga loja RedeSky depende das capturas ou lista de itens e precos que o usuario vai enviar.
