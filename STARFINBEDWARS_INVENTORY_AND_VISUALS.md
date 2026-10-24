# Prompt 6.2: inventarios e apresentacao

## Implementado

- O drop manual e bloqueado somente para participantes conectados na propria partida ativa. Fora da partida, o evento segue o comportamento normal.
- Na morte valida, recursos de Ferro, Ouro, Diamante e Esmeralda passam ao assassino de outra equipe; excedentes caem no ponto da morte. Sem assassino valido, os recursos caem no local. O inventario de jogo e limpo antes do respawn. A saida em combate tratada como morte segue a mesma rotina.
- Baus normais do clone da partida comecam vazios e mantem o conteudo durante a partida. O Bau do Fim usa inventario isolado por partida, equipe ou jogador, conforme `chests.ender-mode`. O conteudo termina com a instancia.
- O equipamento usa niveis logicos por participante. No modo `CLASSIC`, capacete e peitoral sao couro da cor da equipe; calca e botas usam o nivel comprado. `INVERTED` troca essas posicoes. Armadura permanece apos morte; espada, machado e picareta adquiridos descem um nivel por morte ate o piso configurado. Upgrades coletivos sao aplicados aos itens reconstruidos.
- A loja usa seis linhas, categorias, favoritos persistidos, separadores, paginas, descricao opcional em `shop.yml` e os mesmos pagamentos e eventos do `ShopService`. Catalogos antigos com quatro pecas de armadura expõem apenas a entrada de calca como compra do nivel completo; a compra de couro inicial e removida. Ordem e icones das categorias ficam em `config.yml`.
- A sidebar usa WAITING, STARTING, IN_GAME, espectador e ENDING, com countdowns reais. `scoreboard.team-status` escolhe o indicador de equipes e as linhas sao limitadas para 1.8.8. Titles com prioridade mostram a abertura, inicio, cama destruida, eliminacao e resultado; sons e particulas configuraveis acompanham os eventos.
- O Villager de loja e um NPC customizado NMS `v1_8_R3`, imovel, silencioso e associado a partida, com o holograma separado.

## Validacao e limites

`mvn clean verify` compila, executa 47 testes unitarios e gera `target/StarfinBedWars-1.0.0-SNAPSHOT.jar`. Um smoke test com PaperSpigot 1.8.8 build 445 iniciou e desligou o plugin sem erro. Teste manual com cliente ainda e necessario para confirmar interacao, Titles, particulas, morte e aparencia dos NPCs. As tres imagens citadas no prompt nao estavam entre os arquivos recebidos; o layout usa as instrucoes textuais.

A API StarfinMinigames nao esta disponivel neste workspace. Textos e preferencias ainda sao locais ao BedWars, sem idioma por perfil. O plugin nao depende do StarfinCore. Sessoes de partida nao sobrevivem a reinicio do servidor; restaure os inventarios originais de jogadores offline antes de desligar o plugin em ambiente de producao.
