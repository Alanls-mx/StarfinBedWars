# StarfinBedWars

Plugin de BedWars para **Java 8** e **Spigot 1.8.8**. Esta versao implementa arenas em instancias isoladas, engine casual, reconnect local, assistente de configuracao, geradores de recursos, loja classica, NPCs de loja, equipamentos persistentes, upgrades de equipe e armadilhas.

Para o historico dos prompts e o inventario anterior, consulte `STARFINBEDWARS_PROJECT_STATUS.md`. As correcoes do Prompt 6.1 estao em `STARFINBEDWARS_VISUAL_AND_GAMEPLAY_FIXES.md`; a entrega do Prompt 6.2 esta em `STARFINBEDWARS_INVENTORY_AND_VISUALS.md`.

## Requisitos e build

- JDK 8 para compilar e executar.
- Maven 3.6 ou superior.
- Servidor Spigot 1.8.8.
- O artefato local `org.spigotmc:spigot:1.8.8-R0.1-SNAPSHOT`, produzido pelo BuildTools, e necessario para compilar os adaptadores NPC e Title `v1_8_R3`.
- **StarfinCore Spigot 0.3.1+ e obrigatorio** nesta branch: fornece HikariCP e o driver MySQL em runtime, antes do StarfinBedWars iniciar.
- **StarfinMinigames Spigot 0.1.0+ e opcional na Fase 1**: fornece a API publica de descoberta de minigames. Compile `StarfinMinigames` com `mvn -B clean install` antes de compilar BedWars; instale a JAR do novo plugin antes de iniciar BedWars. Sem ela, o gameplay e o MySQL do BedWars continuam ativos, mas a integracao compartilhada fica indisponivel ate reiniciar com o plugin instalado.

```text
mvn -B verify
```

O JAR e `target/StarfinBedWars-1.0.0-SNAPSHOT.jar`. A API Spigot e `provided` e nao e empacotada.

## Gameplay avançado (Prompt 5)

O lobby de espera oferece seleção de kit pela estrela do Nether no slot visual 4 e habilidade pelo frasco de encantamento no slot visual 6. As escolhas são persistidas no MySQL compartilhado. Os cinco kits, quatro habilidades, Generator Split, anti-AFK, limites de equipes e regras por arena são configurados em `plugins/StarfinBedWars/gameplay.yml` e `arenas/<id>.yml`. Split e anti-AFK começam desativados para manter o comportamento das arenas existentes. Comandos, permissões, semântica de cada configuração e roteiro de teste em servidor: [docs/PROMPT5_GAMEPLAY.md](docs/PROMPT5_GAMEPLAY.md).

O ambiente Always é configurado em `plugins/StarfinBedWars/always.yml`: hora, dificuldade, clima e spawn/griefing de mobs por mundo e por arena. As cópias de partidas recebem sempre dia e dificuldade 3 por padrão; mundos comuns permanecem intactos até serem habilitados. Consulte [docs/ALWAYS_ENVIRONMENT.md](docs/ALWAYS_ENVIRONMENT.md) para herança, opções e comandos.

## API compartilhada de minigames (Fase 1)

Com StarfinMinigames presente, BedWars registra `bedwars` no `MinigameRegistry` do Bukkit `ServicesManager`. Arenas, grupos, modalidades, partidas, capacidades, estados e UUIDs dos jogadores sao consultados por snapshots imutaveis. Um adaptador publica eventos genericos a partir de `MatchLifecycleEvent` sem disparar os mesmos eventos internos novamente. O registro e removido no desligamento do BedWars; o StarfinMinigames tambem remove registros quando o plugin proprietario e desabilitado.

Configure `plugins/StarfinMinigames/config.yml` com `server-id` unico por servidor. Se `plugins/StarfinBedWars/network.yml` estiver ativo, use o mesmo `server-id`; divergencia impede a publicacao incorreta do catalogo e preserva o BedWars local. O catalogo MySQL `sbw_network_*`, estatisticas, XP e preferencias permanecem sob responsabilidade do BedWars. O proxy StarfinMinigames cria somente a tabela aditiva `sm_network_reservations`. Consulte o [README do StarfinMinigames](../StarfinMinigames/README.md) para contratos, comandos, ordem de instalacao e limites.

## Estatisticas, perfis, XP e niveis na rede BungeeCord

O modulo de estatisticas usa uma **unica base MySQL compartilhada por todos os servidores Spigot de BedWars** da rede BungeeCord. Cada servidor instala seu proprio StarfinCore Spigot, que carrega e verifica HikariCP 4.0.3 e MySQL Connector/J 8.4.0. Essas bibliotecas sao `provided`: nao entram no JAR do BedWars. O Core disponibiliza as classes; o BedWars gerencia seu pool de conexoes e o encerra ao desligar, conforme o contrato de `docs/SHARED-LIBRARIES.md` do Core. O proxy nao precisa de uma conexao para somar estatisticas.

1. Instale o StarfinCore Spigot da plataforma antes do StarfinBedWars em **cada servidor**. O `plugin.yml` agora exige `StarfinCore`.
2. Inicie uma vez para gerar `plugins/StarfinBedWars/stats.yml`; configure `mysql.host`, `port`, `database`, `username`, `password` e `ssl-mode`.
3. Garanta que todos os servidores BedWars apontem ao **mesmo banco MySQL** com schema acessivel para criar tabelas. Habilite `enabled: true` no `stats.yml` de cada servidor e reinicie.
4. Use `/sbw stats` para ver o perfil em texto, `/sbw perfil` ou `/sbw profile` para abrir o perfil visual e `/sbw nivel` ou `/sbw progress` para acompanhar o nivel. `/sbw stats <jogador|UUID>` consulta qualquer perfil ja registrado na rede; o console deve informar um identificador.

O perfil e identificado pelo UUID (o nome mostrado e atualizado quando o jogador entra). A partida concluida registra partidas, vitorias, derrotas, empates, abates, abates finais, mortes, camas destruidas e XP total. Niveis sao calculados a partir do XP com a curva editavel em `stats.yml`; o nivel inicial e 1. Os valores padrao de XP sao participacao 25, vitoria 100, abate 10, abate final 20 adicionais e cama 40. Partidas canceladas antes ou durante o jogo nao concedem XP. Uma saida definitiva impede receber vitoria da equipe depois de abandonar a partida.

O registro `sbw_match_results` tem chave unica `(match_id, player_uuid)`, e as somas dos perfis ocorrem na mesma transacao InnoDB. Assim, dois servidores ou uma retransmissao do mesmo resultado nao duplicam XP e vitorias. O trabalho JDBC e feito em uma thread dedicada, sem consultas no thread principal do Bukkit. Partidas finalizadas sao gravadas primeiro em `stats-pending/*.properties` e reenviadas a cada 60 s ate que o banco confirme a transacao, inclusive apos reinicio do plugin. **Nao delete esse diretorio quando houver falha de conexao**. Existe uma pequena janela entre o evento de fim da partida e a gravacao assincrona do arquivo local; uma queda brusca nesse exato instante pode perder o resultado. O sistema nao recupera partidas interrompidas por crash nem faz migracao de estatisticas antigas inexistentes.

`stats.yml` vem desativado por padrao para nao impedir que instalacoes existentes iniciem sem credenciais. SQLite local nao e um banco compartilhado para uma rede BungeeCord. Proteja as credenciais e utilize SSL adequado no MySQL; `ssl-mode: REQUIRED` e o padrao. Para isolamento, o usuario SQL deve ter permissoes apenas nas tabelas deste modulo.

## Fase 2: rankings, progressao e recompensas

Os rankings consultam o MySQL compartilhado dos servidores **BedWars**. O geral considera os totais confirmados em `sbw_profiles`; os filtros de modalidade e periodo usam `sbw_match_results`. As metricas sao `wins`, `games`, `kills`, `final_kills`, `deaths`, `final_deaths`, `beds`, `xp`, `level`, `win_rate` e `kd`. Modalidades: `solo`, `duo`, `trio`, `squad` e `custom`. Periodos: `all`, `weekly` e `monthly`, com inicio de semana na segunda-feira e limites em UTC. O ranking de nivel ordena pelo XP confirmado e exibe o nivel derivado; jogadores no mesmo nivel podem aparecer em posicoes diferentes pelo XP. A taxa de vitorias aparece como percentual; K/D divide abates por pelo menos uma morte para evitar divisao por zero.

| Comando | Funcao |
|---|---|
| `/sbw profile` ou `/sbw perfil` | Perfil visual com cabeca, nivel, XP, estatisticas, taxa de vitorias e titulo. |
| `/sbw progress` ou `/sbw nivel` | XP atual, XP restante, ultima partida e marcos de recompensa. |
| `/sbw rewards` | Titulos desbloqueados e bloqueados; clique em um titulo liberado para seleciona-lo. |
| `/sbw top [metrica] [modalidade] [periodo] [pagina]` | Ranking visual com filtros clicaveis, paginacao e posicao propria. Ex.: `/sbw top kills solo weekly 2`. Use `all` para modalidade geral. |

Os subcomandos de jogador seguem a permissao do comando `/sbw` ja existente; `stats <jogador|UUID>` continua disponivel para consulta textual e para o console. Os menus utilizam a preferencia de idioma persistida (`pt_BR`, `en_US`, `es_ES`). Cada pagina de ranking faz consulta em uma thread JDBC dedicada e fica em cache por `rankings.cache-seconds` (padrao 60). Ha limite de 16 pedidos pendentes por instancia e 128 entradas em cache; as consultas usam timeout de 5 segundos. O cache local e invalidado apos um resultado confirmado neste servidor e expira para refletir atualizacoes dos outros servidores. A scoreboard so usa snapshots locais; nao faz SQL a cada atualizacao.

O nivel inicial e 1. Para avancar do nivel `n` ao `n+1`, a curva continua `base-xp + (n-1) * increase-per-level`; o nivel e calculado a partir do XP total sem coluna duplicada. Uma partida pode atravessar varios niveis. O resultado confirmado produz `PlayerProgressionEvent` na thread Bukkit e informa XP da partida, subida de nivel e novos titulos ao jogador online. O XP da ultima partida e lido do resultado persistido. Os novos placeholders do **lobby principal** sao `{total_xp}`, `{xp_remaining}`, `{last_match_xp}`, `{win_rate}`, `{rank_position}` e `{selected_title}`, alem dos anteriores. Os layouts de espera e partida continuam separados.

`rewards.yml` define titulos `TITLE_UNLOCK` nos niveis 5, 10, 25, 40 e 50 (Aprendiz, Guerreiro, Veterano, Mestre e Lenda). IDs devem ser permanentes e unicos; altere os nomes traduzidos sem trocar IDs ja concedidos. Tipos futuros, moedas, comandos de console e permissoes temporarias ainda nao sao executados. O desbloqueio e registrado em `sbw_reward_unlocks` com chave `(player_uuid,reward_id)` e so ocorre quando o XP confirmado atinge o limite. O titulo selecionado fica em `sbw_player_preferences.selected_title`; o banco bloqueia selecao de titulos nao concedidos. A verificacao ao entrar e a cada 60 segundos concede marcos retroativos e recupera desbloqueios pendentes sem duplicatas.

A migracao MySQL e **aditiva e repetivel**: `sbw_match_results.mode` e `sbw_player_preferences.selected_title` sao colunas novas, e `sbw_reward_unlocks` e uma nova tabela. Os indices `(created_at,mode,player_uuid)` e `(mode,created_at,player_uuid)` ajudam os periodos. Os resultados historicos mantem `mode=NULL` quando a modalidade nao pode ser comprovada: entram no geral e ficam fora de modalidades especificas. Outboxes antigos de 8 e 9 campos continuam legiveis; para eles o periodo usa a data do arquivo pendente. Nao apague `stats-pending/` durante a atualizacao. Antes de instalar em outro servidor, preserve um backup do banco e distribua a mesma configuracao de niveis e recompensas em todos os nodes.

Para homologar na rede: confira `/sbw profile`, `progress`, `rewards` e `top`; conclua uma partida em cada modalidade, confira XP, desbloqueio e selecao de titulo no segundo BedWars; reinicie um node, volte e confirme os dados; simule MySQL indisponivel, conclua uma partida e confirme reenvio do outbox apos restaurar a conexao. O build usa `mvn -B clean verify`. O teste `Phase2MySqlIntegrationTest` exige schema descartavel com prefixo `sbw_phase2_test_` e variaveis `SBW_TEST_DB_HOST`, `SBW_TEST_DB_SCHEMA`, `SBW_TEST_DB_USER` e `SBW_TEST_DB_PASSWORD` para testar migracao e concorrencia reais; sem elas ele e ignorado. Notificacoes de partidas ja confirmadas nao sao reenviadas apos um crash entre commit e entrega da mensagem, mas XP e titulos permanecem no MySQL e aparecem nos menus ao reconectar.

## Catalogo de rede e migracao de YAML para banco SQL

`network.yml` controla a publicacao assincrona de servidores, arenas completas (incluindo YAML de equipes, camas, geradores e regioes), partidas e presenca de jogadores nas tabelas `sbw_network_*`. Cada Spigot precisa de um `server-id` unico e estavel, mapeado ao nome real do servidor no BungeeCord; todos usam o MySQL comum de `stats.yml`. O catalogo vem **desativado por padrao** e faz heartbeat periodico. Configure `lobby-server` com o nome Bungee do lobby. O modulo StarfinMinigames-Bungee consulta esse catalogo e grava reservas em `sm_network_reservations`. Na chegada do jogador, o BedWars reivindica a reserva pelo UUID, valida servidor e instancia, solicita a arena selecionada e confirma a reserva apenas no evento real `PLAYER_JOINED`. Timeout, desconexao e falhas liberam a reserva. A entrada local continua disponivel quando o catalogo esta desativado.

Para copiar arenas e configuracoes locais para MySQL ou SQLite com backup ZIP, use `/sbw migrate preview mysql` e depois `/sbw migrate apply mysql confirm` (ou `sqlite`). A importacao e idempotente quando o conteudo e identico e recusa conflitos sem sobrescrever. **Isso exporta os metadados, mas nao substitui o YAML local como fonte do gameplay nem copia os arquivos fisicos de mundos-template.** Veja [STARFINBEDWARS_NETWORK_STORAGE_MIGRATION.md](STARFINBEDWARS_NETWORK_STORAGE_MIGRATION.md) para schema, seguranca, consultas e checklist de testes.

## Instalar e configurar

1. Coloque o JAR em `plugins/` e inicie o servidor.
2. Prepare um mapa BedWars como uma pasta de mundo filha do diretorio de mundos, com `level.dat`.
3. Descarregue esse mundo antes de importar: a engine recusa copiar um mundo ativo para evitar snapshots inconsistentes.
4. Siga `STARFINBEDWARS_ARENA_GUIDE.md` para importar, editar uma copia isolada, selecionar regioes, publicar, validar e habilitar.
5. Para configuracao interativa de equipes e geradores, consulte `STARFINBEDWARS_SETUP_WIZARD_GUIDE.md`.

A migracao adiciona apenas chaves ausentes em `config.yml` e guarda o original em `config.yml.pre-presentation.bak`. Tempos de respawn, altura de hologramas, icones e mensagens explicitamente definidos sao preservados. Se o rodape da scoreboard foi personalizado e o TAB ainda usa exatamente a marca antiga padrao, o TAB passa a usar `{footer}`.

| Chave | Padrao | Uso |
|---|---:|---|
| `general.debug` | `false` | Diagnostico geral reservado. |
| `match.respawn-seconds` | `3` | Tempo em modo espectador apos morte com cama ativa, antes de voltar ao respawn da equipe; intervalo 0-30. |
| `match.spawn-opening-seconds` | `10` | Tempo independente no spawn fechado, depois do countdown do lobby; intervalo 1-60. |
| `arena-bounds.default-width` | `280` | Largura horizontal padrao dos limites automaticos. |
| `arena-bounds.default-depth` | `280` | Comprimento horizontal padrao dos limites automaticos. |
| `arena-bounds.default-up-height` | `50` | Altura acima do centro automatico. |
| `arena-bounds.default-down-depth` | `50` | Profundidade abaixo do centro automatico. |
| `arena-bounds.maximum-dimension` | `1000` | Limite por dimensao horizontal; o volume tambem respeita `worlds.maximum-region-blocks` (25 milhoes por padrao). |
| `bed-alert.*` | textos e som em portugues | Broadcast unico por cama, title e som para a equipe atingida. |
| `shop-hologram.*` | titulos e subtitulos em portugues | Textos e altura dos hologramas de loja. |
| `shop-npc.items-skin`, `shop-npc.upgrades-skin` | `AlanLS_`, `Lurreisper` | Nome da conta usado na cabeca com skin dos NPCs; tambem pode ser alterado por comando. No Minecraft 1.8, o visual e um corpo de armor stand com a cabeca do jogador, e a textura depende da disponibilidade da skin dessa conta no cliente. |
| `bed-hologram.*` | `OWN_TEAM` | Indicador virtual por jogador sobre a cama propria; visibilidade, textos, altura e prazo apos destruicao configuraveis. |
| `generator-holograms.*` | 4 icones, orbita de 1,05 bloco | Bloco central giratorio/flutuante, minerios em orbita, texto e limites de atualizacao/visualizacao. |
| `purchase-sounds.*` | `NOTE_PLING`, `LEVEL_UP` | Sons de compra da loja e das melhorias. |
| `match-chat.format` | tags, nome e mensagem | Formato dos canais de chat na partida. |
| `tab.header`, `tab.footer` | marca configurada | Cabecalho e rodape do TAB na partida; `{footer}` usa `scoreboard.footer`, ou pode ser substituido por texto literal. |
| `tab.lobby.enabled`, `tab.lobby.header`, `tab.lobby.footer` | habilitado, marca configurada | Exibe o TAB do BedWars no lobby e restaura esse cabecalho ao sair da partida. Desative se outro plugin controlar o TAB do lobby. |
| `tab.skins.enabled`, `tab.skins.aliases`, `tab.skins.fallback-player` | habilitado, sem aliases nem fallback | Usa a textura assinada do perfil conectado ou consulta o perfil oficial pelo nome. Para um nome pirata sem skin publica, associe o nick a uma conta com skin em `aliases`; o fallback e opcional. |
| `tab.enabled`, `tab.team-prefix`, `tab.team-prefixes.*`, `tab.show-spectators`, `tab.spectator-prefix` | habilitado, prefixos por equipe | Exibicao e identificacao dos jogadores no TAB. |
| `scoreboard.title`, `scoreboard.footer` | `BED WARS`, marca | Titulo e rodape do placar da partida. Para usar seu dominio, altere `scoreboard.footer`; o TAB e os layouts com `{footer}` exibem esse texto. |
| `scoreboard.enabled`, `scoreboard.preset` | habilitado, `CLASSIC` | Controla a sidebar e o modelo visual. |
| `scoreboard.layouts.lobby`, `scoreboard.layouts.in-game`, `scoreboard.layouts.ending` | linhas classicas | Linhas por fase, na ordem definida no arquivo; aceitam cores `&` e placeholders documentados no `config.yml`. |
| `scoreboard.team-line`, `scoreboard.own-marker`, `scoreboard.eliminated-display` | formato classico | Aparencia das linhas de equipes. |
| `scoreboard.team-status` | `BED` | Indicador de equipes: `BED`, `ALIVE`, `TEXT` ou `COMPACT`. |
| `shop-npc.fallback-to-team-spawn` | `true` | Cria NPCs perto do spawn quando a arena nao tem pontos de loja salvos. |
| `chests.ender-mode` | `MATCH_SHARED` | Escopo do bau do fim: `MATCH_SHARED`, `TEAM_SHARED` ou `PLAYER_PRIVATE`. |
| `equipment.armor-mode` | `CLASSIC` | Armadura `CLASSIC` ou `INVERTED`, fixada por partida. |
| `equipment.sword-minimum`, `equipment.tool-minimum` | `WOOD` | Piso do downgrade de equipamentos adquiridos: `WOOD` ou `STONE`. |
| `shop.category-order`, `shop.category-icons.*` | Favoritos, Blocos, Combate, Armaduras, Ferramentas, Arcos, Poções, Utilidades | Abas da loja de seis linhas. Configurações antigas com `SWORDS` e `SPECIAL` continuam aceitas. |
| `next-match.enabled` | `true` | Após a partida, abre `Jogar novamente?` para os jogadores que permaneceram até o fim. O olho procura outra arena do mesmo modo; o papel abre o seletor de arenas. |
| `messages.shop.*`, `messages.upgrades.*` | textos coloridos em portugues | Feedback de compra, falha e nomes das melhorias. `{item}`, `{upgrade}` e `{level}` aceitam cores `&`; reaplique a cor apos o placeholder quando necessario. |
| `starfinbedwars.chat.color` | `op` | Permite codigos `&` no chat da partida. Sem essa permissao, os codigos aparecem literalmente. |
| `visuals.*` | titulos, sons e particulas | Contagem real de abertura, inicio e resultado da partida. |

O reconnect usa `reconnect.yml`: habilitacao, modo `AUTOMATIC` ou `CONFIRMATION`, prazo Casual de 120 segundos e combat tag de 15 segundos. Consulte `STARFINBEDWARS_RECONNECT_GUIDE.md`. Chaves antigas `match.disconnect-grace-seconds` e `integrations.starfin-core.*` em instalacoes existentes nao sao mais utilizadas e podem ser removidas.

Exemplo para um nick offline sem perfil oficial: `tab.skins.aliases.MeuNick: AlanLS_`. A busca ocorre fora da thread principal, com cache e limites de tempo; se a conta nao tiver textura publica assinada ou o servico estiver indisponivel, o cliente usa sua skin padrao. O UUID e o nome no TAB continuam os do jogador conectado. Ao voltar da partida, o plugin tambem devolve o inventario, os efeitos, o modo de jogo, o voo e as velocidades anteriores.

Os arquivos `generators.yml`, `shop.yml`, `upgrades.yml`, `traps.yml` e `worlds.yml` sao criados na primeira inicializacao. Eles definem niveis/limites de geradores, itens e Quick Buy, custos de melhorias, efeitos/limites das armadilhas e politicas de mundos/regioes. Intervalos antigos padrao do nivel 1 de ferro (40 ticks) e ouro (200 ticks) sao atualizados para 80 e 300 ticks no primeiro startup desta versao; valores personalizados permanecem, e o original fica em `generators.yml.pre-slow-lv1.bak`. O catalogo da loja acrescenta os blocos, armas, arcos, poções e utilidades das capturas quando seus IDs e slots estao livres; preserva preços existentes e cria `shop.yml.pre-extended-catalog.bak`. As melhorias acrescentam Pressa, segundo nível de Afiação e segundo nível de Fadiga; migrações de valores padrão guardam `upgrades.yml.pre-reference-shop.bak` e `traps.yml.pre-reference-shop.bak`. Valores invalidos impedem a inicializacao do respectivo servico, em vez de habilitar funcionalidades com defaults implicitos. `WorldEdit` 6.x e opcional e nao e incluído como dependencia.

Na loja, a primeira linha seleciona a categoria e os itens abaixo mudam para o submenu escolhido no mesmo inventário. Shift-clique adiciona ou remove um item da compra rápida; clique simples compra com som de confirmação. As melhorias de equipe e armadilhas aparecem em uma janela de três linhas, com custos e níveis no tooltip. Os itens e preços são configuráveis nos arquivos YAML; os preços ausentes nas capturas usam os valores definidos no catálogo. O balde de água comprado pode colocar água dentro da região MAIN; a água flui somente para ar dentro dos limites e não altera blocos originais. Ao terminar uma partida, os jogadores que permaneceram recebem o menu `Jogar novamente?`; arenas que falharem ao preparar a próxima partida são ignoradas nessa tentativa.

## Comandos

| Comando | Permissao | Descricao |
|---|---|---|
| `/sbw help`, `/sbw version`, `/sbw status` | status exige `starfinbedwars.command.status` | Ajuda, versao e estado do plugin. |
| `/sbw help <setup\|arena\|world\|player>` | nenhuma | Ajuda contextual por categoria. |
| `/sbw menu` | nenhuma | Abre a lista paginada de arenas habilitadas. |
| `/sbw arena list` | nenhuma | Lista arenas configuradas e habilitadas/desabilitadas. |
| `/sbw arena menu` | `starfinbedwars.command.arena` | Abre o painel paginado; clicar em uma arena inicia edicao isolada. |
| `/sbw arena create <id> <mundo-descarregado>` | `starfinbedwars.command.arena` | Cria o template e, quando chamado por um jogador fora de partida, inicia a edicao isolada automaticamente. |
| `/sbw arena import <id> <mundo-descarregado>` | `starfinbedwars.command.arena` | Copia assincronamente um mundo descarregado para um template. |
| `/sbw arena loadsource <arena>` | `starfinbedwars.command.arena` | Carrega o mundo-fonte para consulta; edicoes devem ser feitas na copia temporaria. |
| `/sbw arena tp <arena>` | `starfinbedwars.command.arena` | Visita o mundo-fonte sem entrar na copia de partida. |
| `/sbw arena edit <arena>` | `starfinbedwars.command.arena` | Inicia uma copia de edicao; arenas habilitadas podem ser editadas fora de partidas. |
| `/sbw arena edit <publish\|cancel> <arena>` | `starfinbedwars.command.arena` | Publica o template validado ou descarta a instancia dessa arena. |
| `/sbw arena exit` | `starfinbedwars.command.arena` | Encerra as sessoes de edicao do administrador e restaura o ponto de retorno. |
| `/sbw arena region <arena> <main\|protection\|waiting> <wand\|worldedit\|clear>` | `starfinbedwars.command.arena` | Persiste regioes; WorldEdit 6.x e opcional. |
| `/sbw arena team <arena> <id> <nome> <cor-hex> <capacidade>` | `starfinbedwars.command.arena` | Cria/atualiza equipe dentro dos limites de `gameplay.yml`. |
| `/sbw arena removeteam <arena> <id>` | `starfinbedwars.command.arena` | Remove equipe se restarem pelo menos duas. |
| `/sbw arena set <arena> lobby\|spectator\|editspawn\|spawn\|respawn\|bed [equipe]` | `starfinbedwars.command.arena` | Salva pontos na copia de edicao; `respawn` exige piso seguro e `bed` exige mirar em um bloco de cama. |

| `/sbw arena generator add <arena> <iron\|gold\|diamond\|emerald> [equipe]` | `starfinbedwars.command.arena` | Cria gerador na copia de edicao. Ferro/Ouro precisam de equipe para validar. |
| `/sbw arena generator list <arena>` | `starfinbedwars.command.arena` | Lista geradores configurados. |
| `/sbw arena generator remove <arena> <id>` | `starfinbedwars.command.arena` | Remove gerador persistido. |
| `/sbw arena generator setteam <arena> <gerador> <equipe>` | `starfinbedwars.command.arena` | Associa gerador de Ferro/Ouro a uma equipe. |
| `/sbw arena shop <arena> <items\|upgrades>` | `starfinbedwars.command.arena` | Salva posicao da loja na copia temporaria de edicao. |
| `/sbw npc skin <items\|upgrades> <nome-do-jogador>` | `starfinbedwars.command.arena` | Define e salva a skin da cabeca do NPC, atualizando os NPCs ativos. |
| `/sbw setup mode <solo\|equipe>` | `starfinbedwars.command.arena` | Seleciona o formato depois que POS1/POS2 e os limites da arena forem confirmados. |
| `/sbw setup team-size <2..maximo>` | `starfinbedwars.command.arena` | Define a capacidade das equipes antes de cria-las; limite em `gameplay.yml`. |
| `/sbw create teams <quantidade>` | `starfinbedwars.command.arena` | Cria equipes e inicia o assistente por etapas; exige limites confirmados. |
| `/sbw setup pause` | `starfinbedwars.command.arena` | Salva e pausa o passo atual do assistente. |
| `/sbw setup resume <arena>` | `starfinbedwars.command.arena` | Retoma um rascunho apos entrar novamente na copia de edicao. |
| `/sbw setup cancel <arena>` | `starfinbedwars.command.arena` | Remove o rascunho; configuracoes ja aplicadas permanecem. |
| `/sbw setup undo` | `starfinbedwars.command.arena` | Desfaz a ultima alteracao aplicada pelo assistente; a plataforma tambem restaura blocos que nao foram alterados depois. |
| `/sbw setup status`, `/sbw next` | `starfinbedwars.command.arena` | Mostra o passo atual e repete sua instrucao contextual. |
| `/sbw setarena <center here\|pos1\|pos2\|upoversize\|upheight\|uplow\|tp\|preview\|confirm\|cancel>` | `starfinbedwars.command.arena` | Prepara limites automaticos ou selecao manual; previews sao virtuais e os limites so sao salvos ao confirmar. |
| `/sbw arena min <arena> <jogadores>` | `starfinbedwars.command.arena` | Define o minimo de jogadores. |
| `/sbw arena countdown <arena> <segundos>` | `starfinbedwars.command.arena` | Configura countdown entre 1 e 600 segundos. |
| `/sbw arena validate\|enable\|disable <arena>` | `starfinbedwars.command.arena` | Valida e controla a disponibilidade de novas partidas. |
| `/sbw world <list\|create\|import\|info\|tp\|delete\|confirm-delete>` | `starfinbedwars.command.world` | Gerencia Void worlds e registra mundos externos sem alteracao destrutiva. |
| `/sbw join <arena>` | nenhuma | Entra na fila/countdown e depois na partida. |
| `/sbw spectate <arena>` | nenhuma | Assiste uma partida em andamento. |
| `/sbw leave` | nenhuma | Sai da partida ou do modo espectador. |
| `/sbw chat <global\|team\|spectator>` | nenhuma | Escolhe um canal da propria partida; espectadores usam apenas o canal de espectadores. |
| `/reconnect`, `/sbw reconnect` | nenhuma | Tenta retornar a partida Casual local pendente. |
| `/sbw reconnect status` | nenhuma | Exibe arena, equipe, prazo e estado da propria sessao. |
| `/sbw reconnect sessions`, `/sbw reconnect inspect <uuid>` | `starfinbedwars.command.reconnect.admin` | Consulta sessoes ativas. |
| `/sbw reconnect invalidate <uuid> confirm` | `starfinbedwars.command.reconnect.admin` | Invalida uma sessao, registra auditoria e remove a vaga ativa. |

O assistente de setup pede um ponto de respawn separado para cada equipe logo apos criar a sala inicial. Configure-o na base, fora da sala temporaria, sobre piso solido e com dois blocos livres acima. O yaw salvo define a direcao de frente; o pitch e zerado no spawn e no respawn, inclusive para arenas antigas que foram configuradas olhando para baixo. Arenas antigas precisam desse ponto em cada equipe antes de serem validadas e ativadas novamente.

As definicoes de arena ficam em `plugins/StarfinBedWars/arenas/<id>.yml`. A cada gravacao, o plugin valida o YAML e mantem a versao mais recente em `<id>.yml.bak`; ao iniciar, restaura automaticamente o arquivo principal se ele estiver ausente e registra a recuperacao no log. A primeira inicializacao desta versao tambem cria backup de arquivos de arena anteriores. Se o arquivo principal existir mas estiver invalido, ele e preservado para diagnostico. Os blocos alterados em uma copia de edicao so entram no template apos `/sbw arena edit publish <id>`; uma sessao de edicao nao publicada ainda e descartada ao sair ou desligar o servidor.

Durante partidas, geradores entregam itens fisicos conforme `generators.yml`. O NPC de itens abre a loja classica de seis linhas, com categorias e favoritos salvos por jogador em `shop-favorites.yml`. O pagamento usa os recursos do inventario. O NPC de melhorias abre as opcoes configuradas em `upgrades.yml` e `traps.yml`. Upgrades e armadilhas pertencem a equipe da partida; armadilhas disparam quando um inimigo entra na area da cama. O plugin restaura inventario, armadura, experiencia, efeitos e atributos salvos ao sair ou encerrar a instancia. Na morte, Ferro, Ouro, Diamante e Esmeralda passam ao assassino valido, ou caem no local se nao houver assassino. O equipamento persistente e reconstruido no respawn e as demais posses da partida sao perdidas.

Durante uma partida, cada jogador recebe uma scoreboard propria com dados da sua `GameMatch`. As linhas do preset `CLASSIC` sao configuraveis por fase em `scoreboard.layouts`; o proximo upgrade temporizado e o estado das equipes sao atualizados na partida. O TAB aceita cabecalho, rodape, prefixos e visibilidade de espectadores em `config.yml`; ao sair, o scoreboard anterior e restaurado. Os upgrades centrais seguem `generator-events.yml` por partida (padrao: Diamante II em 6 min, Esmeralda II em 12 min, Diamante III em 18 min e Esmeralda III em 24 min). O countdown do lobby e o countdown de abertura sao independentes: ao fim do lobby, os jogadores vao para seus spawns ainda fechados; apos `match.spawn-opening-seconds`, as estruturas sao removidas e a partida comeca. O inventario externo e salvo e limpo ao entrar no lobby, e restaurado ao sair. O painel administrativo e de entrada usam menus paginados e encaminham as acoes aos servicos existentes.

Durante `IN_GAME`, participantes conectados podem colocar e quebrar blocos colocados pelos jogadores na regiao principal da arena; espectadores e jogadores em fases pre-jogo nao podem construir. Os blocos originais do mapa continuam protegidos, e a cama inimiga configurada pode ser quebrada. Camas nao podem ser usadas para dormir; encantamento, bigorna, crafting, fornalha, alquimia e outros mecanismos do mapa nao abrem ou acionam. Baus normais e do fim continuam utilizaveis. Com cama propria ativa, o jogador reaparece no `respawn` separado da equipe em estado protegido e volta ao modo de sobrevivencia apos `match.respawn-seconds`. A queda e temporariamente protegida ao abrir as estruturas de spawn e apos o respawn.

Geradores de equipe produzem ferro a cada 4 segundos e ouro a cada 15 segundos no nivel inicial (intervalos acelerados por upgrades). A esmeralda de equipe fica desativada ate o primeiro Forge; geradores centrais de esmeralda nao dependem de Forge. Nenhum gerador da partida produz antes da abertura dos spawns; o primeiro recurso aguarda o intervalo completo apos a abertura. Itens soltos copiados do template sao removidos durante a espera. Os intervalos continuam configuraveis em `generators.yml`.

Geradores centrais respeitam limite efetivo de quatro unidades no chao, inclusive quando um `generators.yml` antigo ainda declara um limite maior. O tempo do ciclo continua passando enquanto estao cheios. Recursos com regiao configurada sao produzidos somente em pontos validos dessa regiao; itens que escapam dela sao reposicionados a cada segundo. Novos geradores centrais do assistente usam o bloco clicado como ancora e registram uma pequena regiao acima dele.

Configure e confirme POS1/POS2 antes de escolher o modo ou criar equipes: `/sbw setarena center here` para calcular automaticamente ou `/sbw setarena pos1` e `/sbw setarena pos2` para marcar manualmente, depois `/sbw setarena confirm`. O plugin bloqueia as etapas seguintes enquanto os limites nao estiverem salvos. Depois, siga modo, capacidade de equipe e criacao das equipes nessa ordem. Depois de informar o nome de cada time, o assistente apresenta as 16 cores como opcoes clicaveis no chat; a escolha aplica tambem a cor de bloco correspondente, sem exigir codigo `&`. Apos configurar cama, spawn, respawn e lojas de cada time, marque uma vez POS1 e POS2 da regiao do gerador para criar juntos os geradores de Iron, Gold e Esmeralda daquele time. Ao final, digite `diamond` ou `emerald` e clique com o botao direito no bloco de cada gerador central; o minerio nasce sobre esse bloco, enquanto o holograma fica acima apenas para informar o nivel e o ciclo. O `/sbw setup status` exibe a etapa e instrucao reais. O assistente salva equipes, cores de bloco, lojas por equipe, regioes de geradores, limites e blocos das estruturas de spawn no schema YAML v7, mantendo leitura de arenas v1–v6. Solo usa footprint 3x3 com interior 1x1; Equipe usa 5x5 com interior 3x3. As estruturas registradas somem somente na copia isolada quando a partida inicia; o estado `STARTING` cancela dano antes do teleporte. Rascunhos e historico de Undo sobrevivem a reinicios, mas o clone temporario nao: reabra o editor para retomar; alteracoes de bloco nao publicadas no clone antigo nao sao recuperadas. O lobby deve ser configurado separadamente e o wizard nao habilita a arena.

Ao concluir o setup, valide e publique a copia antes de habilitar: `/sbw arena validate <arena>`, `/sbw arena edit publish <arena>`, depois `/sbw arena enable <arena>`. A publicacao confere os blocos fisicos das camas; se um template antigo estiver incompleto, a partida tambem e recusada antes de admitir jogadores, com uma mensagem indicando a equipe e as coordenadas a corrigir. Geradores centrais mostram um bloco flutuante, quatro icones orbitais e nivel/recurso/contagem regressiva. Os indicadores das camas sao enviados individualmente aos clientes. Consulte `STARFINBEDWARS_ANIMATED_HOLOGRAMS.md`.

`starfinbedwars.admin`, `starfinbedwars.command.arena` e `starfinbedwars.command.world` sao concedidas a operadores por padrao. Os comandos que exigem jogador nao funcionam pelo console.

## Seguranca e limites atuais

- O mundo-fonte nunca e usado como instancia de jogo; cada partida recebe uma copia identificada por UUID.
- Edicoes de mapas acontecem em copias temporarias; publicar substitui o template de forma transacional e nao altera o mundo-fonte.
- Mundos importados sao somente registrados, nao sao alterados nem elegiveis para exclusao. Somente mundos Void criados pelo plugin podem ser removidos apos confirmacao e validacao de marcador.
- Copia de arquivos e assincrona; carga/descarga de mundos e operacoes Bukkit ocorrem na thread principal.
- Instancias de jogo/edicao exigem nome gerado e marcador de propriedade validado; mundos administrativos exigem registro, tipo permitido e confirmacao. Se a descarga falhar, o diretorio nao e apagado e a arena permanece bloqueada.
- `uid.dat`, `session.lock`, `playerdata`, `stats` e `advancements` nao sao copiados para a instancia.
- Colocacao e permitida somente a participantes conectados durante `IN_GAME` e dentro da regiao principal. Blocos colocados nessa partida podem ser quebrados; os blocos originais do mapa permanecem protegidos, exceto pela cama inimiga configurada. Explosoes nao alteram blocos; as mudancas permitidas ocorrem apenas na copia isolada da partida, removida ao final.
- Arenas invalidadas no startup ficam desativadas; nada e corrigido automaticamente no arquivo de origem.
- O mundo-fonte pode ser carregado para consulta. `/sbw arena edit <id>` cria uma copia temporaria; somente `/sbw arena edit publish <id>` atualiza o template, preservando o mundo original.

Ainda nao implementados: ranked, temporadas, torneios, recuperacao de partidas apos reinicios, StarfinParty, cosmeticos e conquistas. O adaptador de proxy BungeeCord cobre reservas individuais BedWars; Party e reserva em lote nao fazem parte desta fase. O modo Maluca nao esta disponivel; a capacidade dinamica das equipes nao fixa o jogo em quatro jogadores por equipe.

Os NPCs de loja usam villager registrado no protocolo 1.8 com objetivos de caminho desativados; o clique abre a loja BedWars. Quando pontos de loja nao foram salvos, o fallback cria NPCs perto dos spawns; configure os pontos pelo setup para posiciona-los nas bancadas. Textos por idioma individual e integracao com StarfinMinigames dependem de uma API compartilhada disponivel. Em 23/10/2026, uma partida completa com dois clientes de protocolo 1.8.8 em PaperSpigot 1.8.8 confirmou entrada, lobby limpo, NPCs e menus, geradores de equipe e centrais, evolucao temporizada, protecao de armadura, respawn separado, quebra da cama inimiga, eliminacao, vitoria, restauracao do inventario e carregamento da arena apos reinicio. Em 24/10/2026, outra partida no servidor isolado confirmou os hologramas centrais e de loja, a abertura dos NPCs de itens e melhorias, a personalizacao da sidebar e do TAB, o fallback dos NPCs sem posicao salva e a quebra de la colocada com preservacao da pedra original. Uma validacao adicional do protocolo confirmou os NPCs como `Villager` visivel, o texto de dominio personalizado no TAB, a cor preservada ao dividir linhas da sidebar e a migracao dos intervalos iniciais dos geradores. O assistente de setup foi testado com varinha unica no quinto slot, menu por clique no ar e pausa pelo menu. Veja `STARFINBEDWARS_DEVELOPMENT_STATUS.md` para o escopo exato do teste; um servidor de producao com outros plugins ainda exige verificacao propria.

Para homologacao de rede, instale StarfinCore, StarfinMinigames-Spigot e StarfinBedWars no backend, e StarfinMinigames-Bungee no proxy. Configure o mesmo MySQL nos dois lados, `server-id` coerente no Spigot e o mapeamento autorizado no proxy. Teste `/smnetwork servers`, `/smjoin bedwars solo`, lotacao, destino offline, desconexao, reconexao e retorno ao lobby. Preserve backup das JARs e YAML para rollback; o schema existente nao e recriado.

## Organizacao

```text
src/main/java/com/starfinplugins/bedwars/
├── arena/       # persistencia, validacao e instancias de mundo
├── command/     # comandos administrativos e de jogador
├── config/      # configuracao tipada
├── domain/      # modelos Bukkit-independentes de arena e partida
├── economy/     # transacoes de inventario e geradores fisicos
├── event/       # eventos de ciclo de vida da partida
├── integration/ # adaptadores opcionais, isolados do dominio
│   └── worldedit/ # adaptador opcional sem dependencia de compilacao
├── match/       # engine e listeners Bukkit
├── reconnect/   # sessoes, snapshots, politica e metadados locais
├── menu/        # menus paginados de jogo e administracao
├── shop/        # catalogo, NPCs e Quick Buy
├── upgrades/    # upgrades e armadilhas de equipe
├── service/     # ciclo de vida dos servicos
├── setup/       # assistente interativo e mapeamento de cores 1.8
└── world/       # geracao Void e gerenciamento administrativo de mundos
```

O relatorio historico da auditoria do StarfinCore permanece em `STARFINBEDWARS_ARCHITECTURE_ANALYSIS.md`; o progresso e as verificacoes estao em `STARFINBEDWARS_DEVELOPMENT_STATUS.md`.
