# StarfinBedWars — historico e estado do projeto

> **Atualizacao Prompt 05 (23/10/2026):** reconnect local Casual implementado com sessoes por UUID, snapshot temporario, comandos e expiracao. StarfinCore nao e mais dependencia do plugin. O texto historico abaixo pode descrever o estado dos prompts anteriores; consulte `STARFINBEDWARS_RECONNECT_GUIDE.md` e `STARFINBEDWARS_DEVELOPMENT_STATUS.md` para o estado atual.

**Atualizado em:** 23/10/2026  
**Produto:** StarfinBedWars  
**Versao do projeto:** `1.0.0-SNAPSHOT`  
**Plataforma-alvo:** Java 8 e Spigot/Bukkit 1.8.8  
**Build:** Maven  
**Escopo deste documento:** consolidar os prompts e etapas registrados, o que existe no codigo, as correcoes recentes e o que ainda requer implementacao ou validacao.

Este documento descreve o estado do workspace nesta data. Uma funcionalidade planejada nao e considerada implementada apenas por estar mencionada no plano de produto. Os guias operacionais continuam sendo a referencia para executar os comandos no servidor.

## 1. Resumo executivo

O plugin ja possui uma base funcional de BedWars casual: definicoes e validacao de arenas, instancias de mundo isoladas, engine de partidas, equipes, geradores de recursos, lojas fisicas, melhorias e armadilhas. Tambem possui um fluxo administrativo para criar/importar arenas, editar uma copia isolada, configurar os dados de jogo com um assistente por etapas, validar e publicar o template.

O trabalho recente concentrou-se no assistente de setup e na publicacao:

- Dependencias entre etapas do setup foram organizadas para que limites confirmados venham antes de equipes e que cada equipe conclua cama, spawn, lojas e gerador antes da seguinte.
- Geradores de equipe usam uma unica regiao POS1/POS2 para Iron, Gold e Esmeralda. Geradores centrais de Diamante/Esmeralda aceitam varios pontos por cliques direitos e cada ponto recebe holograma.
- As estruturas de spawn Solo e Equipe, retomada/Undo, instrucoes de proximo passo, lobby e fluxo de publicacao receberam correcoes incrementais.
- O holograma central foi elevado. O drop central tambem foi elevado para acompanhar o bloco exibido.
- O prompt recente que mostrou o estado `POS1 do gerador` depois de salvar o lobby levou a uma normalizacao do rascunho: quando todos os geradores centrais estao completos e o lobby existe, o setup avanca para a conclusao em vez de pedir os geradores novamente.
- A publicacao tenta primeiro localizar uma superficie segura e tem um caminho alternativo de voo temporario para mundos sem piso. O estado de voo do administrador e restaurado no retorno.
- A fase de partida foi ajustada para separar o countdown do lobby do countdown de abertura dos spawns; os participantes sao teleportados antes de remover a estrutura e recebem protecao temporaria contra queda.
- A esmeralda de equipe inicia desativada e e habilitada no primeiro Forge. Os intervalos padrao de ferro/ouro de equipe foram aumentados.
- A morte com cama ativa agora aguarda o respawn Bukkit antes de iniciar o tempo protegido; a colocacao de blocos e autorizada apenas para participantes conectados durante `IN_GAME`, dentro da regiao principal.
- A scoreboard agora tem layouts distintos para o servidor principal, lobby da arena e partida em andamento, conforme as tres referencias. O painel principal usa estatisticas zeradas como placeholders, pois nao existe persistencia de perfil; o lobby mostra modo/versao/jogadores/contagem; o painel in-game segue o exemplo: titulo BED WARS, data/ID da arena, temporizador mm:ss do diamante, equipes com estado de cama e indicador VOCE, rodape com marca em duas cores. As linhas sao atualizadas sem recriar todas as equipes em cada tick de refresh.

O estado foi compilado e os testes unitarios passaram com `mvn -B clean verify` (36 testes). Isso nao substitui um teste funcional em um servidor Spigot 1.8.8 real; as ultimas alteracoes de teleporte/publicacao ainda precisam ser exercitadas no ambiente de jogo.

## 2. Historico dos prompts e pedidos executados

O historico abaixo resume os prompts registrados nesta sessao e nos checkpoints. Nao e uma transcricao literal de cada mensagem: pedidos repetidos e correcoes incrementais foram agrupados por objetivo.

| Etapa registrada | Objetivo solicitado | Resultado no projeto |
|---|---|---|
| Analise inicial do StarfinCore | Inspecionar o Core local/remoto, nao presumir APIs e entregar uma analise antes do desenvolvimento do BedWars. | Analise registrada em `STARFINBEDWARS_ARCHITECTURE_ANALYSIS.md`. A conclusao foi que o Core fornece principalmente autorizacao comercial e nao um sistema de perfis, idioma individual ou persistencia de gameplay. |
| Prompt 01 — fundacoes da engine | Criar a base Java 8/Spigot 1.8.8, dominios de arena/partida, ciclo de vida e engine casual. | Implementada a base do plugin e da engine casual. |
| Prompt 02 — economia e geradores | Adicionar geradores e economia inicial. | Geradores fisicos e configuraveis, niveis, limites de itens, evolucao de forge e eventos relacionados estao na implementacao. |
| Prompt 03.5 — mundos e arenas | Gerenciar mundos Void/importados, regioes e instancias isoladas; preparar a edicao/publicacao. | Servico de mundos, copias isoladas, selecao de regiao, validacao e publicacao transacional documentados nos guias de arena. |
| Prompt 04 — experiencia de partida e administracao | Completar fluxos de arena, menus, scoreboard e ciclo de vida de partida. | Menus paginados, apresentacao/scoreboard e notificacoes de ciclo de vida existem. A personalizacao de HUD permanece limitada. |
| Prompt 4.1 — assistente de arena | Criar setup guiado e retomavel, com times, pontos, limites, Undo e persistencia. | Assistente, rascunhos persistidos, retomada/pausa/cancelamento, Undo e fluxo de setup estao implementados. |
| Prompt 4.2 — limites, sequenciamento e spawn | Exigir limites antes de times, configurar dimensoes/alturas, criar estruturas de spawn por modo e ordenar etapas. | Limites automaticos/manuais confirmaveis, bloqueio de etapas prematuras, footprint Solo/Equipe e fluxo por etapa estao implementados. |
| Correcao de retomada | Ao usar `/sbw arena menu`, continuar de onde o setup parou e informar a proxima acao; diferenciar setup novo de continuacao. | O menu detecta rascunho, arena padrao ou arena preexistente e apresenta a continuacao correspondente. |
| Correcao de spawn | Aplicar o visual tambem ao bloco sob o jogador e teleportar o administrador para fora da estrutura criada. | O layout e o calculo de saida foram ajustados; a implementacao encontra um ponto de superficie proximo a estrutura. |
| Correcao do fluxo entre equipes | Nao iniciar o gerador do proximo time antes da cama; repetir a mesma ordem para cada time. | O fluxo de cada time passa por identidade/cor, cama, spawn, lojas e regiao de gerador antes de avancar. |
| Correcao dos geradores de equipe | Nao pedir recurso para gerador de time; definir Iron, Gold e Esmeralda com a mesma regiao POS1/POS2. | Uma unica regiao registra os geradores de recursos do time. |
| Geradores centrais | Cadastrar Diamante/Esmeralda central por clique direito, aceitar varios pontos e exibir holograma por ponto. | Implementado para pontos centrais sem equipe; o tipo e selecionado pelo chat e os cliques registram pontos individuais. |
| Fluxo de conclusao e lobby | Ao digitar `fim`, confirmar geradores; informar `/sbw arena set <arena> lobby`; apos salvar o lobby, apresentar o passo seguinte. | Mensagens e transicao do assistente foram ajustadas. O rascunho completo nao deve voltar a `GENERATOR_POS1` depois que os geradores e o lobby estao configurados. |
| Holograma e drop central | Elevar o holograma e garantir que o recurso gerado fique sobre o bloco representativo. | Alturas de holograma e drop central ajustadas no setup e na partida. A geometria visual final depende de verificacao no servidor 1.8.8. |
| Publicacao e validacao | Corrigir instrucoes de validate/enable/publish e a falha de destino seguro. | Mensagens de comandos foram esclarecidas; publicacao procura destino seguro, expande a busca e tem fallback de teleporte com voo temporario se nao houver piso. |
| Idiomas dos recursos | Permitir nomes em portugues no setup central. | `diamante` e `esmeralda` sao aceitos junto de `diamond` e `emerald`; instrucoes foram atualizadas. |
| Fluxo da partida e bugs de jogo | Separar contadores do lobby/spawn, impedir queda ao abrir spawn, corrigir geradores, respawn e colocacao de blocos; criar os scoreboards das tres fases conforme referencias. | Countdown do lobby e de abertura usam estados separados; teleporte aos spawns ocorre antes de remover a estrutura, queda tem protecao temporaria, esmeralda de equipe so inicia apos Forge e participantes conectados podem construir em jogo dentro da regiao principal. O respawn protegido e aplicado apos o evento Bukkit de respawn. Scoreboards principal/lobby/in-game possuem conteudos separados; scoreboard in-game agora reflete o layout de referencia com data/arena, diamante mm:ss, status das camas, VOCE e rodape colorido; estatisticas ainda usam zeros placeholder. |

## 3. Arquitetura e organizacao atual

O codigo esta em `src/main/java/com/starfinplugins/bedwars/`. O projeto usa Maven e compila com `maven.compiler.release=8`; Spigot API 1.8.8 e BungeeCord chat sao dependencias `provided`. JUnit 4 e usado nos testes.

| Pacote | Responsabilidade principal |
|---|---|
| `arena` | Repositorio e validacao de arenas, edicao isolada, selecao de regioes e gerenciamento das instancias/templates. |
| `command` | Comandos administrativos, setup, mundos e comandos de jogador em `BedWarsCommand`. |
| `config` | Leitura e validacao das configuracoes tipadas do plugin. |
| `domain` | Modelos de arena, regioes, equipes, geradores, partidas, participantes, upgrades e armadilhas. |
| `economy` | Transacoes com recursos fisicos e servico de geradores. |
| `event` | Eventos publicos de partida, geracao, compra, upgrades e armadilhas. |
| `integration` | Adaptadores opcionais, incluindo autorizacao do StarfinCore e selecao WorldEdit. |
| `match` | Engine de partidas, apresentacao/scoreboard e snapshots/restauracao de inventario. |
| `menu` | Menus administrativos e de entrada em arenas. |
| `shop` | NPCs, catalogo, Quick Buy e configuracao da loja. |
| `setup` | Assistente, cores Bukkit 1.8 e geometria de plataformas de spawn. |
| `upgrades` | Upgrades e armadilhas de equipe. |
| `world` | Geracao Void, configuracao de mundos e resolucao de locais seguros. |
| `service` | Ciclo de vida dos servicos gerenciados pelo plugin. |

Arquivos centrais para manutencao:

- `StarfinBedWarsPlugin.java`: classe principal Bukkit e inicializacao dos servicos.
- `arena/ArenaService.java`, `ArenaRepository.java`, `ArenaValidator.java` e `WorldInstanceManager.java`: definicao, persistencia, validacao, copia e publicacao de arenas.
- `arena/ArenaEditingService.java`: sessoes de edicao, retorno do administrador, publish/cancel/exit.
- `setup/ArenaSetupWizardService.java`: maquina de estados, mensagens, rascunho, Undo, configuracao dos geradores e transicoes.
- `setup/SpawnPlatformLayout.java`: dimensoes e blocos das estruturas de spawn.
- `economy/generator/ResourceGeneratorService.java`: producao, drops e hologramas durante partidas.
- `command/BedWarsCommand.java`: sintaxe e despacho dos comandos.
- `match/MatchEngineService.java` e `ParticipantInventoryService.java`: ciclo de vida de partidas e estado do jogador.

## 4. Funcionalidades implementadas

### Arenas, mundos e publicacao

- Importacao de mundos-fonte descarregados para templates; o mundo-fonte nao e editado pelo fluxo normal.
- Instancias temporarias isoladas para edicao e partidas, com nomes/identificadores gerados e marcador de propriedade.
- Edicao por um administrador, protecao da copia contra outros jogadores/explosoes e restauracao do local, modo de jogo e voo do administrador.
- Regioes `main`, `protection` e `waiting`; selecao por ferramenta e adaptador opcional para WorldEdit 6.x.
- Validacao de arena antes da publicacao/habilitacao. A publicacao troca o template com staging e preservacao/restauracao do template anterior em caso de falha.
- Mundos Void `empty` e `platform`, importacao administrativa e exclusao limitada a mundos criados pelo plugin, com validacao de marcador e confirmacao.
- Comandos principais documentados em `README.md` e `STARFINBEDWARS_ARENA_GUIDE.md`.

### Engine casual

- Estados de partida/equipe/participante e ciclo de vida de partida.
- Fila/countdown, entrada e saida de jogadores, espectador, morte/respawn, eliminacao e encerramento.
- Instancia isolada por partida; limites horizontais, teto e nivel de void aplicados aos participantes durante o jogo.
- Protecoes contra interacao/destruicao de blocos, com regras para cama inimiga.
- Snapshot/restauracao de inventario, armadura, experiencia, fome, vida, efeitos, fogo e voo.
- Tres scoreboards por estado: painel principal BED WARS com nivel/progresso/tokens/abates/vitorias placeholder; lobby com mapa/jogadores/estado/modo/versao; in-game com data e ID da arena, contagem real do gerador central de diamante em mm:ss, equipes coloridas e estado de cama, destaque VOCE no time local e rodape `starfinplugins.com.br` em duas cores. As atualizacoes de linhas reutilizam as equipes da sidebar para evitar recriacao a cada refresh.
- Countdown de lobby separado da abertura de spawn; o teleporte ao spawn ocorre antes da remocao das estruturas registradas.
- Protecao temporaria contra queda ao abrir spawn e depois do respawn; respawn Bukkit com cama ativa seguido por janela protegida configuravel.
- Construcao de blocos autorizada para participantes conectados durante a partida dentro da regiao principal; espectadores e fases pre-jogo continuam sem permissao.

### Economia, lojas e upgrades

- Geradores fisicos de Iron, Gold, Diamond e Emerald, com niveis/intervalos/quantidades configuraveis e limite de itens no chao. Padroes de equipe agora iniciam com ferro a cada 2 s e ouro a cada 10 s.
- Gerador de esmeralda de equipe inicia inativo e e ativado no primeiro Forge; geradores centrais nao sao afetados.
- Forge de equipe, eventos de geracao/evolucao e drops coletaveis.
- Quick Buy e catalogo definido em `shop.yml`, com cobranca em recursos fisicos e verificacao/rollback de inventario em falha de entrega.
- NPCs de loja ligados a instancias de partida e removidos ao encerrar.
- Upgrades por equipe, incluindo Sharpness, Protection, Forge e Heal Pool; custos configurados.
- Armadilhas de equipe, fila e acionamento por proximidade de cama, incluindo Alarm, Blindness e Mining Fatigue.
- Eventos para compras, upgrades, evolucao/geracao e armadilhas.

### Assistente de arenas e geradores

- Etapas do setup dependentes: limites da arena antes do modo/equipes; para cada equipe, nome/cor, cama, spawn e lojas, depois gerador do time.
- Selecao de cores por opcoes clicaveis no chat e suporte a cores/blocos legacy 1.8.
- Spawn Solo em footprint 3x3 (interior 1x1) e Equipe 5x5 (interior 3x3), com paredes, piso e teto; Undo pode restaurar blocos ainda iguais aos que o assistente colocou e preserva alteracoes posteriores.
- Configuracao de Iron/Gold/Esmeralda por uma unica regiao POS1/POS2 para cada equipe.
- Geradores centrais de Diamante/Esmeralda por clique direito em bloco, multiplos pontos por tipo, holograma individual e alternancia pelo chat.
- Tipos centrais aceitam `diamond`/`diamante` e `emerald`/`esmeralda`.
- Hologramas centrais de setup e partida mostram bloco, nivel, recurso e contagem. A altura foi aumentada em duas etapas, com o ajuste mais recente elevando em mais 2 blocos sobre a altura que ja existia.
- Rascunhos atomicos, pausa, retomada, cancelamento e Undo persistido. A ferramenta temporaria restaura o item do jogador em pause/quit/shutdown limpo ou login subsequente.
- Mensagens de proxima etapa e instrucoes de validacao/publicacao/habilitacao.

### Persistencia e configuracao

- Definicoes de arena em YAML, atualmente schema v6, com leitura retroativa das versoes v1–v5.
- Configuracoes separadas: `config.yml`, `generators.yml`, `shop.yml`, `upgrades.yml`, `traps.yml` e `worlds.yml`.
- Configuracoes invalidas impedem a inicializacao do servico relacionado em vez de ocultar falhas com defaults silenciosos.
- O progresso do assistente e salvo em `setup-wizard/`; a instancia temporaria de mundo nao sobrevive a reinicio abrupto.

## 5. Fluxos de comando relevantes

### Setup inicial

1. Abrir a copia isolada com `/sbw arena edit <id>` (ou criar/importar conforme o guia).
2. Definir e confirmar limites: `/sbw setarena ...`, `/sbw setarena preview` e `/sbw setarena confirm`.
3. Selecionar modo com `/sbw setup mode <solo|equipe>`; em Equipe, escolher capacidade com `/sbw setup team-size <2|3|4>`.
4. Criar equipes com `/sbw create teams <quantidade>`.
5. Seguir as instrucoes no chat para cada equipe e configurar geradores do time antes de prosseguir a proxima.
6. Cadastrar geradores centrais com `diamante`/`diamond` ou `esmeralda`/`emerald`, clicando com botao direito nos blocos. `fim` encerra o cadastro quando os tipos obrigatorios existem.
7. Definir lobby dentro da copia: `/sbw arena set <id> lobby`. Digitar `fim` novamente conclui o setup quando aplicavel.
8. Validar, publicar e habilitar na ordem descrita abaixo.

`/sbw setup status` e `/sbw next` devem mostrar o estado/instrucao atual. Um draft retomado com o lobby e geradores ja prontos e normalizado para a etapa de conclusao, nao para um gerador ja concluido.

### Validar, publicar e habilitar

```text
/sbw arena list
/sbw arena validate <id-real>
/sbw arena edit publish <id-real>
/sbw arena enable <id-real>
```

Use o identificador exibido por `arena list`; nao digite os caracteres `< >` nem um exemplo como `first` a menos que esse seja o ID real. Habilitacao e bloqueada enquanto a arena estiver em modo de edicao. A publicacao exige um destino externo para descarregar o clone: procura primeiro perto do retorno do administrador e nos spawns dos mundos carregados, e pode usar uma posicao elevada com voo temporario quando nao ha piso seguro. Se nao houver outro mundo carregado, a publicacao ainda nao pode descarregar a instancia.

## 6. StarfinCore e integracoes

A auditoria e detalhada em `STARFINBEDWARS_ARCHITECTURE_ANALYSIS.md`. A conclusao confirmada foi que o StarfinCore analisado e infraestrutura de identidade/licenciamento da instalacao; nao fornece um sistema de perfil de jogador, UUID/idioma individual, mensagens de gameplay, banco de dados, cache generico ou engine para reaproveitar.

O BedWars tem adaptador isolado em `integration/core/CoreAuthorizationIntegration.java`. A autorizacao nao e dependencia do gameplay. WorldEdit 6.x e opcional via adaptador reflexivo. Party, Bans e Lobby nao tem integracao funcional confirmada neste projeto; devem continuar opcionais e isoladas se implementadas posteriormente.

## 7. Prometido no plano versus implementado

| Sistema planejado | Estado atual |
|---|---|
| Engine casual e arenas | Implementado, com os limites de plataforma e validacao descritos neste documento. |
| Solo, Duplas, Trios e Quartetos | Estrutura do setup permite Solo e capacidade de equipe 2/3/4. O formato de partidas e limitado ao que a engine casual e as definicoes atuais validam. |
| Equipes configuraveis | Implementado; o modelo aceita capacidade maior que quatro, mas o modo Maluca nao foi implementado. |
| Geradores, lojas, melhorias e armadilhas | Implementacao funcional documentada; validar economia e comportamento em servidor real antes de distribuicao. |
| Scoreboard, menus e mensagens | Basico implementado. Personalizacao visual completa, titulos/actionbar e traducao por jogador nao estao implementados. |
| Estatisticas, progressao, niveis e experiencia persistente | Nao implementado como sistema de produto/persistencia. |
| Ranked, rating, matchmaking e temporadas | Nao implementado. |
| Campeonatos e series BO1/BO3/BO5 | Nao implementado. |
| Reconnect | Janela de desconexao configuravel no processo atual; nao e persistente entre reinicios. |
| Idioma individual via StarfinCore | Nao existe essa API no Core auditado; idioma individual nao implementado. |
| StarfinParty | Integracao futura, nao implementada. |
| BungeeCord | Compatibilidade planejada, nao implementada como fluxo distribuido de partidas. |
| Partidas privadas | Nao implementado como sistema dedicado. |
| Administracao/configuracao | Comandos e arquivos YAML implementados para os sistemas existentes. |
| Cosméticos e conquistas | Nao implementados. |
| Modo Maluca | Explicitamente reservado para futuro; nao implementado. |

## 8. Estado atual, limitacoes e riscos conhecidos

- **Teste de servidor real pendente:** API Java/Maven e testes unitarios nao verificam teleportes, construcao, morte/respawn, fisica de itens, ArmorStand/capacete, menus, eventos e descarga de mundos dentro do Spigot 1.8.8.
- **Publicacao sem mundo externo:** o fallback pode resolver falta de piso, mas nao substitui a necessidade de outro mundo Bukkit carregado para retirar o jogador e descarregar o clone.
- **Rascunho versus mundo temporario:** o YAML do assistente persiste; o clone temporario nao. Retomada apos reinicio exige nova copia e nao recupera alteracoes de blocos nao publicadas no clone anterior.
- **Retorno do administrador:** estado/local de retorno fica em memoria; nao ha garantia de recuperacao apos queda abrupta do servidor.
- **Validacao da arena:** arenas incompletas devem permanecer desabilitadas. `validate` reporta pendencias; nao preenche dados automaticamente.
- **Economia/match state:** recursos, upgrades e snapshots nao sao persistidos em um banco de dados do StarfinCore.
- **Compatibilidade comercial:** API/produto de autorizacao depende da versao distribuida do Core e do identificador oficial do produto; o Core nao deve ser tratado como dependecia de gameplay.
- **Documentacao antiga:** `STARFINBEDWARS_DEVELOPMENT_STATUS.md` registra checkpoints anteriores e pode conter contagens/etapas historicas. Para o estado consolidado nesta data, usar este documento e os guias operacionais atualizados.
- **Workspace sem Git:** esta sessao opera em pasta sem controle Git. Nenhum commit, push, pull, merge ou reset foi executado.

## 9. Verificacao e artefato

Na verificacao completa mais recente:

```text
mvn -B clean verify
BUILD SUCCESS
Tests run: 37, Failures: 0, Errors: 0, Skipped: 0
```

O build produz `target/StarfinBedWars-1.0.0-SNAPSHOT.jar`. A API Spigot e `provided`, portanto deve estar no servidor e nao e empacotada no plugin. Os avisos atuais incluem a API Java 8 marcada como obsoleta pelo JDK mais novo usado no build e chamadas Bukkit deprecated existentes; nao foram falhas de compilacao.

## 10. Documentacao complementar

- `README.md`: instalacao, configuracoes, comandos e visao geral dos sistemas implementados.
- `STARFINBEDWARS_ARENA_GUIDE.md`: importacao de mapas, mundos, edicao isolada, validacao, publicacao e habilitacao.
- `STARFINBEDWARS_SETUP_WIZARD_GUIDE.md`: passo a passo do assistente, limites, retomada, Undo, geradores e publicacao.
- `STARFINBEDWARS_ARCHITECTURE_ANALYSIS.md`: auditoria do StarfinCore e matriz de integracao confirmada/recomendada.
- `STARFINBEDWARS_DEVELOPMENT_STATUS.md`: registro historico de etapas e checkpoints anteriores.
