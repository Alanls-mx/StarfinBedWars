# StarfinBedWars — status de desenvolvimento

**Etapa atual:** Correcoes de apresentacao, NPCs, hologramas e geradores  
**Data:** 24/10/2026

## Ajustes de partida e NPCs apos a validacao

O NPC voltou a usar uma subclasse NMS sem som, colisao ou IA. O registro associa apenas essa subclasse ao ID visual do villager vanilla no protocolo 1.8, e e removido ao desligar o servico. A cada 10 ticks o NPC envia rotacao do corpo e da cabeca para acompanhar o jogador mais proximo. No PaperSpigot 1.8.8 com dois clientes, os quatro NPCs apareceram como `Villager`, o NPC de itens abriu `Loja: Favoritos` e pacotes de rotacao da cabeca foram observados; nenhum pacote de som de villager apareceu durante essa rodada.

Interacoes com cama, mesa de encantamentos, bigorna, crafting, fornalha, alquimia, mecanismos de redstone, baldes, portais, item frames e ArmorStands foram bloqueadas na instancia da partida. Uma segunda barreira cancela entrada na cama e abertura de inventarios vanilla dessas estacoes. No teste noturno, clicar na cama nao colocou o jogador para dormir e a mesa de encantamentos nao abriu; um bau normal abriu. A orientacao do spawn e respawn mantem o yaw salvo e normaliza o pitch para zero, inclusive em arenas antigas: no teste a arena estava salva com pitch -80 e ambos os clientes chegaram aos spawns com pitch 0.

Os geradores so sao criados no evento `STARTED`, apos remover as estruturas de spawn. O primeiro drop agora espera seu intervalo completo. Itens persistidos no template sao removidos ao preparar os spawns e ao carregar chunks durante a espera. Na partida de teste, nao havia drops durante a contagem e o primeiro ferro apareceu apos a abertura. O teste automatizado cobre o agendamento do primeiro recurso e a politica de interacoes do mapa. O JAR final passou em `mvn -B verify -q` com 59 testes sem falhas e reiniciou no PaperSpigot com a arena carregada e sem excecoes no log.

## Correcoes adicionais de 24/10/2026

O NPC anterior era uma subclasse NMS nao registrada como tipo de entidade do protocolo 1.8; o cliente a recebia como `unknown` e mostrava apenas o holograma. O NPC agora usa um `Villager` reconhecido pelo cliente com seletores de IA vazios. Em partida no PaperSpigot 1.8.8, dois clientes receberam quatro entidades `Villager`, e os cliques abriram `Loja: Favoritos` e `Team Upgrades`. O ArmorStand que exibe o bloco do gerador central foi elevado 1,1 bloco para separar o bloco da linha de nivel; as entidades do holograma chegaram aos clientes, mas a aparencia final dessa distancia ainda depende de inspecao visual no cliente do usuario.

Conforme escolha do usuario, nivel 1 de ferro passou de 40 para 80 ticks (4 s) e ouro de 200 para 300 ticks (15 s). No startup do servidor de teste, um `generators.yml` antigo foi migrado para os novos valores, com `generators.yml.pre-slow-lv1.bak`; intervalos personalizados sao mantidos. `scoreboard.footer` passou a alimentar `{footer}` tambem no cabecalho e rodape do TAB; apos reiniciar com o JAR final, um dominio personalizado foi observado em ambos os campos do pacote TAB. A divisao de linhas da sidebar agora repete a cor ativa no sufixo, confirmado no pacote da linha do proximo aprimoramento. `mvn -B verify -q` passou com 57 testes. O Prompt 07 anexado foi lido como contexto; as ranqueadas ainda nao foram implementadas nesta correcao de bugs.

## Validacao complementar de 24/10/2026

No servidor descartavel PaperSpigot 1.8.8 com dois clientes de protocolo 1.8.8, os ArmorStands de hologramas de geradores centrais e lojas apareceram depois que o guardiao de mobs passou a autorizar seu spawn. A interacao com o NPC de itens abriu `Loja: Favoritos`. Uma arena sem posicoes de lojas criou quatro NPCs perto dos spawns das equipes, e o clique no NPC de melhorias abriu `Team Upgrades`. Com `config.yml` personalizado, os clientes receberam a linha de scoreboard `TESTE Duelo de Validacao` e o cabecalho TAB `TESTE TAB`. O cliente colocou la, quebrou a la e encontrou ar no ponto; ao tentar quebrar a pedra original, ela permaneceu. A migracao de configuracao preserva as chaves existentes, adiciona apenas as novas e mantem um backup; seu teste automatizado passou. A ultima compilacao `mvn -B package -q` passou com 53 testes e a arena voltou a carregar apos reinicio do servidor. O teste foi realizado somente na instancia local isolada, sem outros plugins de producao.

## Prompt 6.3 — resultado em servidor

Ambiente descartavel: PaperSpigot 1.8.8 build 445, Java 8, porta local 25566, arena `duel` em template isolado, dois clientes Mineflayer usando protocolo 1.8.8. O teste nao alterou o servidor de producao do usuario. `mvn package -q` concluiu com todos os testes; o JAR esta em `target/StarfinBedWars-1.0.0-SNAPSHOT.jar`.

- Arena YAML carregada em tres reinicios; o arquivo principal e seu backup permaneceram no diretorio de teste.
- Partida iniciou com duas equipes. Tres diamantes do inventario externo sumiram no lobby e voltaram apos o encerramento.
- Ferro e ouro surgiram no piso da regiao de gerador de cada equipe. Diamante e esmeralda surgiram sobre os blocos centrais, separados dos hologramas. Uma camada de neve no ponto de esmeralda inicialmente bloqueou a producao; a verificacao agora aceita decoracoes atravessaveis selecionadas, e a segunda partida produziu esmeralda sem aviso de ponto invalido.
- Quatro NPCs foram vistos nas posicoes das lojas; clique no NPC de itens abriu `Loja: Favoritos` e no de melhorias abriu `Team Upgrades`. Nao houve duplicacao observada no teste. Imobilidade, silencio e limpeza apos muitas partidas nao foram medidos automaticamente.
- Em cronograma acelerado **somente na copia descartavel** (15, 30, 45, 60 s), os clientes receberam os avisos Diamante II, Esmeralda II, Diamante III e Esmeralda III. Os padroes distribuidos continuam 360, 720, 1080 e 1440 s em `generator-events.yml`. A scoreboard mostrou o proximo upgrade e a contagem por equipe; pacotes TAB mostraram cabecalho de marca e prefixos `[V]`/`[A]`.
- Clique do cliente no capacete equipado foi recusado e a armadura permaneceu. Shift, arrastar, troca numerica, descarte e inventario criativo foram cobertos no codigo, mas nao tiveram clientes de teste dedicados nesta rodada.
- Quebrar a propria cama nao a removeu. Quebrar a cama adversaria produziu o aviso e removeu ambas as metades na instancia. Matar um jogador com cama viva o levou ao `respawn` configurado (`-6.5, 121, 0.5`), distinto do spawn inicial (`-7.5, 121, 0.5`). Matar o adversario sem cama o eliminou, encerrou a partida e declarou a equipe vencedora.
- Setup em copia de edicao apresentou somente uma varinha no quinto slot (slot de protocolo 40), abriu `Setup da arena` por clique direito no ar, pausou pelo menu e restaurou os tres diamantes externos. Um rascunho incompleto deixa a arena invalida ate terminar ou cancelar o setup, comportamento esperado.

Correcoes principais: regiao de gerador de equipe definida sobre piso agora e convertida em ponto de drop acima do piso; pontos centrais aceitam neve fina e plantas atravessaveis; scheduler de upgrades e independente por `GameMatch`; NPCs sao criados separadamente com retry limitado; armadura e inventario de espera protegidos; varinha e inventario original persistidos no rascunho; clique no ar da varinha e tratado mesmo quando a interacao vanilla vem cancelada; quebra de cama inimiga e aceita mesmo se protecoes anteriores cancelarem o evento; scoreboard e TAB possuem configuracao classica. O teste usou apenas este plugin; conflitos com protecoes e menus de outros plugins devem ser verificados na instalacao final. Prompt 7 nao foi iniciado.

## Prompt 05 — resultado verificado

- Reconnect local no mesmo processo para partidas Casual: sessao por UUID com prazo absoluto, estado de conexao separado do estado de vida, validacao de partida/equipe/mundo, retorno automatico ou por menu/comando e expiracao centralizada.
- Snapshot temporario de inventario, armadura, vida, fome, efeitos e localizacao da partida; o inventario original continua no servico anterior. Restauracao substitui o estado uma vez; upgrades atuais sao reaplicados.
- Combat tag PvP e protecao basica contra queda no void; desconexao em combate entra no fluxo de morte/respawn. A engine atual retem itens na morte.
- Metadados de sessoes salvos assincronamente em YAML; sessoes de processos antigos sao invalidadas na inicializacao, pois partidas/mundos nao sao restaurados apos crash.
- `/reconnect`, `/sbw reconnect [status]`, menu de confirmacao e comandos administrativos de consulta/invalidacao com permissao e confirmacao.
- Dependencia opcional de StarfinCore removida do descriptor, da inicializacao e da configuracao; o adaptador antigo foi removido. O historico abaixo descreve etapas anteriores e nao a integracao atual.
- `mvn -B clean verify`: 43 testes aprovados e JAR gerado. Smoke test em PaperSpigot 1.8.8 confirmou inicializacao, comandos de status e encerramento limpo.

**Nao concluido:** teste real com dois jogadores e arena, idioma individual, modos Ranked/Campeonato, recuperacao pos-crash, BungeeCord e integracao StarfinMinigames (codigo/API nao disponivel nesta auditoria). Veja `STARFINBEDWARS_RECONNECT_GUIDE.md`.

## Historico anterior

## Etapas anteriores

- Auditoria local/remota do StarfinCore registrada em `STARFINBEDWARS_ARCHITECTURE_ANALYSIS.md`.
- Fundacao Java 8 / Spigot 1.8.8 e engine casual de arenas implementadas nos Prompts 01 e 02.
- O StarfinCore confirmado oferece autorizacao comercial; nao fornece perfil, idioma por jogador, economia ou persistencia de gameplay. O Core nao foi modificado.

## Implementado no Prompt 03.5

- Gerador Void compativel com os formatos legacy/por secoes da API Spigot 1.8.8 e configuracao tipada em `worlds.yml`, com modos `empty` e `platform`.
- Servico de mundos administrativos com importacao externa nao destrutiva, registro persistente, teleportes com destino seguro e exclusao em duas etapas limitada a mundos criados pelo plugin com marcador validado.
- Regioes `main`, `protection` e `waiting` persistidas no schema de arena v2; arenas v1 continuam carregaveis, mas precisam configurar a regiao principal antes de validar/habilitar.
- Selecao de regiao por ferramenta com token individual ou adaptador reflexivo opcional para WorldEdit 6.x, sem dependencia de compilacao.
- Modo de edicao em copia isolada; outros jogadores e explosoes nao podem alterar a copia. Publicacao exige validacao, arena inativa, unload e troca de template via staging com rollback.
- Guia de operacao `STARFINBEDWARS_ARENA_GUIDE.md`; `ARENA_SETUP.md` marcado como legado e README atualizado.
- Geradores fisicos de Ferro, Ouro, Diamante e Esmeralda com niveis configuraveis, intervalo/quantidade, limite de itens no chao, upgrades de forge e eventos Bukkit.
- Persistencia YAML de geradores e locais das duas lojas por arena; validacao de mundo e equipe; comandos administrativos para adicionar, listar, remover, associar equipe e posicionar lojas.
- Quick Buy configuravel em `shop.yml`, com itens padrao, cobranca em recursos fisicos e verificacao previa de capacidade do inventario. Falhas de entrega restauram o snapshot do inventario e nao cobram.
- NPCs de loja criados por instancia de partida, com protecao contra dano/alvo e limpeza no encerramento. Compras so sao aceitas de participante conectado na instancia correspondente.
- Upgrades por equipe e partida: Sharpness, Protection, Forge e Heal Pool; custos e niveis definidos em `upgrades.yml`.
- Fila de armadilhas por equipe, com Alarm, Blindness e Mining Fatigue, custos/efeitos/limites em `traps.yml` e evento `TeamTrapTriggeredEvent`.
- Eventos publicos de compra, upgrade, evolucao/geracao de gerador e acionamento de armadilha.
- Snapshot e restauracao de inventario, armadura, experiencia, fome, saude, efeitos, fogo e voo de participantes; itens sao retidos durante morte/respawn.
- Guias, README e status atualizados.

## Implementado no Prompt 04 ate aqui

- Teleporte administrativo `/sbw arena tp <id>` ao mundo-fonte, com selecao de piso seguro em mapas comuns e verificacao de plataforma em Void; `/sbw arena create <id> <mundo>` inicia o clone isolado e teleporta o administrador quando a copia termina.
- Arenas habilitadas podem ser editadas quando nao estao em partida; a reserva do editor bloqueia novas entradas sem alterar a configuracao de disponibilidade.
- Persistencia v3 de `edit-spawn`, mantendo leitura de esquemas v1/v2 e validando que o ponto pertence ao mundo-fonte.
- Retorno de sessoes de edicao: localizacao original, modo de jogo e estado de voo sao preservados; `/sbw arena exit` encerra todas as sessoes do administrador e descarta alteracoes nao publicadas.
- Voar temporariamente em mundos Void durante edicao, sem criar uma plataforma permanente; destinos de teleporte e publicacao requerem pontos seguros.
- Menus paginados de arenas para entrada e administracao; o menu administrativo inicia a copia de edicao e o menu de jogo chama a fila/engine existente.
- Scoreboard lateral por participante com arena, estado, jogadores online, equipes e camas; notificacoes de entrada/saida, countdown, inicio, cama, eliminacao e resultado.
- Sem API de idioma por jogador no StarfinCore: textos visuais estao em portugues e nao alegam uma integracao individual inexistente.

## Ainda pendente no Prompt 04

- Menus de jogador/admin ainda sao os fluxos basicos de listar arenas/abrir edicao; faltam navegacao de espectadores e configuracao visual completa.
- Scoreboard nao tem layout configuravel, countdown numerico, titulos/actionbar ou mensagens traduzidas por jogador.
- A localizacao de retorno e o estado das sessoes administrativas existem apenas em memoria e nao sobrevivem a reinicio abrupto.
- Teleportes/menus/scoreboards precisam de validacao manual em servidor real Spigot 1.8.8 antes de distribuicao.

## Implementado no Prompt 4.1

- Assistente de arena restrito ao proprietario da sessao de edicao; captura nomes/cores no chat e seleciona camas, spawns e lojas com ferramenta temporaria.
- Fluxo de selecao de limites da arena e regioes POS1/POS2; configuracao de teto/void e dos geradores iron/gold por equipe e diamond/emerald compartilhados.
- Rascunhos persistidos atomicamente por arena em `setup-wizard/`, com pausa, retomada e cancelamento. O slot temporario e restaurado no cancelamento, pause, quit, shutdown limpo e recuperacao no login apos uma queda abrupta.
- Schema de arena v4 para cor de bloco (16 valores de dye 1.8), lojas por equipe, regioes de geradores e limites horizontais/alturas. Versoes 1–3 continuam sendo lidas e lojas globais servem de fallback.
- Geradores amostram posicoes de ar nas regioes configuradas sem alterar o timer/economia existente; itens da regiao respeitam o limite agregado do respectivo gerador.
- Engine aplica limites horizontais, teto e void apenas a participantes conectados daquela partida; void segue o fluxo de morte/respawn existente. Editores e espectadores ficam fora dessa regra.
- Guia operacional em `STARFINBEDWARS_SETUP_WIZARD_GUIDE.md`.

## Implementado no Prompt 4.2 nesta etapa

- A criacao do setup agora exige selecao previa do modo Solo/Equipe e, para Equipe, capacidade 2/3/4; a quantidade de times e escolhida em uma etapa separada.
- Salas de spawn usam footprint 3x3 com interior 1x1 para Solo e 5x5 com interior 3x3 para Equipe, com piso, paredes de 3 blocos e teto. Somente ar e substituido e o Undo rastreia os blocos exatos.
- A definicao persistida (schema v6) guarda tipo/cor e coordenadas dos blocos gerados. Na partida, somente a copia isolada perde esses blocos, durante `STARTING` e antes do teleporte; o dano e cancelado nesse estado.
- Comandos de setup e ajuda foram reorganizados em etapas, com status e instrucao contextual para cada passo.
- O fluxo identifica limites principais ja confirmados por `/sbw setarena`, pula as etapas POS1/POS2/altura duplicadas e direciona o wizard aos geradores. `setup status` agora repete a instrucao exata da etapa ativa e informa como retomar/cancelar um rascunho pausado; erros de ordem indicam a acao correta.
- `/sbw setup undo` registra a definicao YAML anterior e o estado original dos blocos criados; permite desfazer alteracoes de configuracao do wizard, voltando a etapa anterior. Blocos modificados posteriormente sao preservados. Undo e persistido no rascunho.
- `/sbw setarena` aceita centro automatico, dimensoes, altura superior/inferior, selecao manual POS1/POS2, teleport, preview virtual, confirmacao transacional e cancelamento. Nenhum limite candidato altera a arena antes de `confirm`.
- Marcar o centro calcula POS1 e POS2 automaticamente. O preview mostra POS1 na altura minima em vidro azul e POS2 na altura maxima em vidro vermelho, com coordenadas no chat; atualizacoes sao individuais e nao gravam marcadores no mundo. Alturas fora de Y=0..255 sao recusadas com os limites validos para o centro.
- Os padroes (280x280, +50/-50) e dimensao maxima (1000) estao em `config.yml`; o volume efetivo respeita `worlds.maximum-region-blocks` (25 milhoes por padrao).
- Persistencia de arena v5 guarda modo, centro, dimensoes e alturas; leitura retroativa v1-v4 preservada.
- Corrigido o despacho de `/sbw menu`, que estava aninhado no tratamento de `/sbw status`.

## Limites atuais do Prompt 4.2

- O rascunho e o progresso YAML sobrevivem a reinicios, mas o clone temporario de edicao nao: reabra a edicao antes de retomar; alteracoes de blocos nao publicadas no clone anterior nao sao recuperadas.
- O wizard exige que o administrador configure o lobby manualmente e deixe a arena desabilitada para revisar/publicar.
- O gerador por regiao tenta uma posicao de ar aleatoria e usa o ponto central como fallback; a acessibilidade dos locais deve ser validada no servidor.
- O historico pode restaurar configuracoes apos retomada, mas nao modifica blocos de um clone descartado pelo servidor. Acoes administrativas fora do wizard alteram a definicao e bloqueiam o Undo para evitar reverter mudancas posteriores.
- `/sbw help <categoria>`, `/sbw setup status` e `/sbw next` foram adicionados; a revisao completa de mensagens e argumentos ainda pode evoluir.
- A ferramenta e as operacoes de blocos ainda precisam ser testadas em servidor real Spigot 1.8.8. Nenhum servidor de staging foi iniciado.

## Limites e decisoes

- Quick Buy e uma lista definida pelo administrador em `shop.yml`; personalizacao individual persistida pelo jogador nao esta implementada.
- O catalogo padrao inclui blocos, armas, ferramentas, arcos, flechas, consumiveis e pecas de armadura. Os itens sao entregues no inventario; equipar pecas de armadura e responsabilidade do jogador.
- A fila de armadilhas e acionada quando um inimigo entra na area de 8 blocos da cama da equipe. Heal Pool aplica Regeneration a aliados conectados na mesma area.
- Recursos e estado de upgrades sao locais a cada `GameMatch`; nao sao gravados no StarfinCore nem em banco permanente.
- Snapshots de inventario existem apenas em memoria. Reconnect continua limitado ao processo atual; nao e garantido apos reinicio/queda abrupta.
- Posicoes de loja sao opcionais no validador. Arena sem posicao configurada simplesmente nao cria aquele NPC.
- O teleporte administrativo para um mundo `empty` materializa uma plataforma configurada no spawn para evitar queda no vazio.
- Nenhuma operacao de exclusao administrativa pode atingir templates, instancias `sbw_<uuid>`, mundos externos importados ou mundos referenciados como origem de arenas.
- O custo do forge melhora um nivel cada gerador de Ferro/Ouro associado a equipe, respeitando o maximo de niveis configurado para aquele recurso.
- NPCs e fluxos Bukkit, drop/pickup, menus, efeitos e mundos precisam de validacao em servidor real Spigot 1.8.8; nenhum servidor de staging foi iniciado nesta etapa.

## Fora do escopo

Ranked, rating, temporadas, torneios, reconnect persistente, StarfinParty, StarfinBans, StarfinLobby, BungeeCord, estatisticas/progressao, cosméticos, conquistas e modo Maluca. O modo Maluca nao foi desenvolvido.

## Verificacao

- `mvn -B clean verify` concluido com sucesso apos as alteracoes atuais: 32 testes aprovados e JAR produzido com compilacao `release 8`.
- Os testes cobrem dominio, geometria das salas de spawn, limites automaticos e persistencia YAML v6/legada. A validacao nao substitui teste em servidor real Spigot 1.8.8.
- Nenhum commit ou push foi realizado; o workspace segue folder-backed e sem Git.
# Prompt 6.4 — hologramas animados (24/10/2026)

Geradores centrais agora mostram quatro minérios em órbita X/Z, bloco com pose de rotação e flutuação opcional. O contador segue o relógio da produção. Indicadores de cama passaram a entidades virtuais enviadas por jogador, com padrão `OWN_TEAM`, prazo de aviso após destruição e limpeza por partida. Configuração e limites em `config.yml`; migração preserva textos existentes. Build e teste de dois clientes no PaperSpigot 1.8.8 passaram. A inspeção gráfica com cliente Minecraft e o teste de carga com cinco partidas continuam pendentes. Detalhes em `STARFINBEDWARS_ANIMATED_HOLOGRAMS.md`.
