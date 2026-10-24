# StarfinBedWars — guia do assistente de configuracao de arenas

O assistente e executado por um administrador dentro da copia isolada de edicao. Ele nao modifica o mundo-fonte e nao habilita a arena automaticamente.

## Inicio e recuperacao

1. Inicie a edicao: `/sbw arena edit <arena>`.
2. Configure os limites primeiro. Para limites automaticos, use `/sbw setarena center here` e ajuste as dimensoes/alturas se necessario; para selecao manual, use `/sbw setarena pos1` e `/sbw setarena pos2`. Revise com `/sbw setarena preview` e finalize com `/sbw setarena confirm`. Apos cada comando, o chat informa a proxima acao; ao confirmar, o proximo comando e `/sbw setup mode <solo|equipe>`. O assistente bloqueia a escolha do modo e a criacao de equipes ate os limites estarem confirmados.
3. Escolha o modo: `/sbw setup mode solo` ou `/sbw setup mode equipe`.
4. Para Equipe, escolha a capacidade: `/sbw setup team-size <2|3|4>`. Solo usa capacidade 1 e nao precisa desta etapa.
5. Crie o numero de equipes (2 a 16): `/sbw create teams <quantidade>`.
6. Siga a instrucao atual no chat: informe o nome do time e clique em uma cor; depois selecione cama, sala de spawn, respawn seguro na base e lojas. Cada resposta ou clique avanca para a proxima instrucao. Ao terminar as lojas de cada time, configure Iron e Gold daquele time antes de iniciar o proximo. `/sbw setup status` mostra a etapa e instrucao atual; `/sbw next` repete a instrucao.
7. Configure o lobby manualmente, valide e publique a arena; o assistente nao a habilita automaticamente.

Se ja houver um rascunho, nao escolha outro modo nem crie times novamente: `/sbw setup status` informa o passo real e `/sbw setup resume <arena>` continua. Para reiniciar a configuracao de times, use `/sbw setup cancel <arena>` antes de escolher o modo; as alteracoes ja salvas na arena permanecem.

Ao selecionar uma arena em `/sbw arena menu`, a edicao isolada e aberta e qualquer rascunho do administrador para aquela arena e retomado automaticamente. O chat mostra a etapa e a instrucao atual. Se nao existir rascunho, o chat informa o proximo comando para iniciar a configuracao.

O menu diferencia tres situacoes: rascunho salvo (continua na etapa registrada), arena padrao sem dados (setup do zero, comecando pela escolha do modo) e arena com dados preexistentes (continuidade sem recriar equipes, mostrando o primeiro comando que falta). Se todos os dados estiverem completos e nao houver rascunho, o proximo passo e validar/publicar, nao iniciar outro setup.

Depois de informar o nome, escolha a cor clicando em uma das 16 opcoes coloridas que aparecem no chat. Nao e necessario digitar nome de cor nem codigo `&`.

Comandos de controle:

```text
/sbw setup pause
/sbw setup resume <arena>
/sbw setup cancel <arena>
/sbw setup undo
```

`undo` desfaz a ultima alteracao aplicada pelo assistente: restaurando a definicao persistida da arena e, para a plataforma, os blocos ainda inalterados desde a criacao. O progresso volta a etapa anterior. Blocos que alguem modificou depois sao preservados. Pausar salva o passo atual e restaura o item original do slot 9. Cancelar remove o rascunho, mas mantem as definicoes da arena que ja foram aplicadas. Para retomar apos reiniciar o servidor, inicie uma nova copia com `/sbw arena edit <arena>` e entao use `/sbw setup resume <arena>`. O rascunho e o historico de Undo persistem em `plugins/StarfinBedWars/setup-wizard/`; snapshots do slot usado pela ferramenta sao restaurados em quit, shutdown limpo ou login seguinte.

O mundo temporario do editor nao sobrevive a reinicializacao. A retomada recupera o passo e os dados YAML, nao os blocos alterados no clone. Plataformas e demais alteracoes de mapa devem ser refeitas na nova copia quando necessario.

## Configuracao passo a passo

- Para cada equipe, o assistente solicita nome, escolha interativa de cor no chat, cama existente, spawn inicial, respawn separado sobre piso solido e posicoes das lojas de itens e melhorias. Solo cria uma sala com footprint 3x3 e interior oco de 1x1; Equipe cria footprint 5x5 e interior oco de 3x3. Ambas usam vidro colorido, piso, paredes de 3 blocos de altura e teto plano. Blocos de ar sao substituidos; o bloco de piso central sob o jogador tambem e substituido pelo vidro da equipe. O Undo registra os blocos alterados e restaura os que permanecerem intactos. Depois que a estrutura e salva, o editor e teleportado para uma superficie segura fora dela.
- As coordenadas, o respawn e a cor dos blocos gerados sao persistidos com a definicao da arena (schema v7). Ao comecar a partida, a estrutura e removida apenas na copia isolada daquela partida, antes do teleporte dos jogadores; durante o estado `STARTING`, o listener da engine cancela dano, inclusive dano de queda.
- Os limites confirmados sao pre-requisito para selecionar o modo e criar equipes. Se forem limites automaticos, POS1/POS2 e alturas sao calculados e persistidos por `/sbw setarena confirm`; se forem manuais, marque ambos antes de confirmar. Para rascunhos antigos que ja avancaram sem limites, conclua essa etapa antes de seguir.
- Configure `altura-do-teto altura-do-void`, por exemplo `120 0`, somente se o wizard pedir essa etapa; o teto aceita 1–255 e o void 0–255.
- Ao concluir as lojas de um time, selecione com a varinha POS1 (clique esquerdo) e POS2 (clique direito) uma unica vez para configurar juntos Iron, Gold e Esmeralda do time, antes de continuar para o proximo time. Depois dos times, digite `diamond`/`diamante` ou `emerald`/`esmeralda` e clique com o botao direito no bloco de cada ponto central desejado; cada clique registra um gerador independente e exibe seu holograma. Pode cadastrar varios de cada tipo, alternando o tipo pelo chat. Depois configure o lobby e digite `fim`. Geradores dos times usam uma regiao amostrada; os centrais geram no bloco escolhido.
- O lobby ainda deve ser configurado no clone com `/sbw arena set <arena> lobby`. Espectador e opcional. O assistente valida cama, spawn, respawn, lojas, geradores, lobby, template e regiao principal antes de concluir.

## Limites automaticos

Na copia de edicao, os limites podem ser preparados sem alterar o mapa e so sao persistidos ao confirmar:

```text
/sbw setarena center here
/sbw setarena upoversize 280x280
/sbw setarena upheight 50
/sbw setarena uplow 50
/sbw setarena preview
/sbw setarena tp
/sbw setarena confirm
```

Ao definir o centro, o sistema calcula imediatamente POS1 (canto inferior) e POS2 (canto superior) a partir das dimensoes e alturas. `/sbw setarena preview` marca POS1 com vidro azul na coordenada minima e POS2 com vidro vermelho na coordenada maxima, e informa as coordenadas no chat; os marcadores sao enviados apenas ao jogador e nao gravam blocos no mundo. Se uma altura levar os limites para fora de Y=0..255, o comando e recusado e informa os valores maximos possiveis para aquele centro. A configuracao padrao fica em `config.yml` (`arena-bounds.default-width`, `default-depth`, `default-up-height`, `default-down-depth` e `maximum-dimension`). O volume calculado tambem respeita `worlds.maximum-region-blocks`. Tambem e possivel marcar `pos1` e `pos2` manualmente; as duas formas ficam pendentes ate `/sbw setarena confirm`. `/sbw setarena cancel` descarta a proposta.

Ao concluir, a arena continua desabilitada. Revise e publique o clone com:

```text
/sbw arena validate <arena>
/sbw arena edit publish <arena>
/sbw arena enable <arena>
```

Nao habilite enquanto a copia de edicao estiver ativa: o comando agora informa a publicacao necessaria. O wizard apresenta a sequencia validacao, publicacao e habilitacao ao concluir. Geradores centrais usam holograma com bloco flutuante, nivel, recurso e contagem regressiva durante a partida.

Use o ID real exibido por `/sbw arena list` nos comandos; nao digite `<arena>` nem um nome de exemplo literalmente. A publicacao procura primeiro um piso seguro perto do local de retorno do administrador e depois nos spawns dos mundos carregados, para permitir publicar mesmo quando o spawn padrao nao tem piso.

Durante partidas, o limite horizontal impede sair dos limites configurados, o teto limita a altura e cair abaixo do nivel de void usa o fluxo de morte/respawn existente. Espectadores e jogadores fora da partida nao recebem esses limites.

## Persistencia e compatibilidade

O schema de arena v6 armazena a estrutura gerada de spawn, alem dos campos de limites automaticos introduzidos na v5. Arquivos v1–v5 continuam sendo lidos; lojas globais continuam funcionando como fallback quando uma equipe nao tem posicao especifica.

O assistente e uma ferramenta administrativa; a capacidade de equipe continua independente e aceita ate 100 jogadores no modelo, sem limitar a arquitetura futura do modo Maluca. Nenhum teste com servidor Spigot 1.8.8 real foi executado.
