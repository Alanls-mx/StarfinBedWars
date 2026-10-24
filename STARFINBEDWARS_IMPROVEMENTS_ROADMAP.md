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
