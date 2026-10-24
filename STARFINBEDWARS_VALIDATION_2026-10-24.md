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
# Prompt 6.4 — validação local adicional

Em PaperSpigot 1.8.8 build 445, dois clientes Mineflayer na arena descartável `duel` receberam quatro ArmorStands de ícones orbitais para Diamante e quatro para Esmeralda. Teleportes registrados alteraram X e Z, enquanto as três linhas de texto mostraram nível, recurso e contador. Em `OWN_TEAM`, RedTester recebeu somente `DEFENDA SUA CAMA!` da cama vermelha e BlueTester somente o indicador da cama azul. `mvn -B verify -q` passou. O teste não teve cliente gráfico e não comprova a aparência do giro, escala ou suavidade. Ainda não foram executados testes de carga com cinco partidas nem validação visual conjunta no servidor do usuário.

Na inspeção gráfica conjunta seguinte, AlanLS_ confirmou que o bloco girava, mas os minérios orbitais não eram visíveis, apesar de seus pacotes de movimento chegarem. A implementação foi alterada para usar itens na mão de ArmorStands invisíveis e raio padrão de 1,05 bloco.

Na segunda captura, os quatro minérios apareceram, mas estavam deitados. O braço do ArmorStand foi inclinado em 90 graus. Na terceira captura, ficaram em pé, porém acima do bloco e sobre o texto. A âncora da mão foi baixada 0,8 bloco; AlanLS_ confirmou que a posição final ficou correta no cliente Minecraft 1.8.8.

AlanLS_ confirmou que `OWN_TEAM` mostrava apenas seu indicador de cama. O cliente BlueTester desconectou e reconectou: recebeu novamente o indicador intacto, sem duplicacao persistente. A cama azul da arena descartavel nao estava fisicamente presente no clone, apesar do ponto salvo; apos recolocar os dois blocos no clone, AlanLS_ a destruiu. O BlueTester recebeu `SUA CAMA FOI DESTRUIDA!` por metadata e depois um pacote de destroy. A ausencia inicial das camas pertence ao template de teste e exige reparo antes de outra partida; nao foi atribuida a nova implementacao de hologramas.
