# StarfinBedWars — guia de mundos e arenas

Requisitos: Java 8 e Spigot 1.8.8. Os comandos de arena exigem `starfinbedwars.command.arena` ou `starfinbedwars.admin`; `/sbw world` exige `starfinbedwars.command.world` ou `starfinbedwars.admin`.

## Mundos administrativos

`worlds.yml` controla o gerador Void, importacao, exclusao, plataforma de spawn e o limite maximo de volume por regiao. Valores invalidos impedem o plugin de inicializar.

```text
/sbw world create <nome> empty
/sbw world create <nome> platform
/sbw world import <mundo-existente>
/sbw world list
/sbw world info <nome>
/sbw world tp <nome>
/sbw world delete <nome>
/sbw world confirm-delete <nome>
```

`empty` gera chunks sem blocos; `platform` gera um mundo Void e uma plataforma inicial. Para tornar o teleporte a um mundo `empty` seguro, `/sbw world tp` materializa uma plataforma de spawn configurada em `worlds.yml`, preenchendo somente blocos de ar. Mundos importados devem ser pastas diretas do diretorio de mundos do servidor com `level.dat`; importar apenas os registra, sem copiar ou alterar seus arquivos.

A exclusao so pode atingir mundos Void criados pelo plugin, exige confirmacao em ate 30 segundos, valida um marcador de propriedade e recusa mundos principais, importados, usados como origem de arena ou com jogadores. Templates ficam dentro da pasta do plugin e instancias de partida/edicao sao gerenciadas separadamente; nenhum desses caminhos e alvo dos comandos de exclusao.

## Criar um template

O mapa-fonte deve estar descarregado e ser uma pasta direta do diretorio de mundos, com `level.dat`. A importacao faz uma copia assincrona, sem apagar ou modificar a origem:

```text
/sbw arena import <id-arena> <mundo-fonte>
/sbw arena create <id-arena> <mundo-fonte>
```

O template fica em `plugins/StarfinBedWars/templates/<id-arena>/`; a definicao inicial e criada desabilitada com equipes `red` e `blue`. `create` inicia a edicao isolada e teleporta o administrador ao clone assim que a importacao termina; no console, ou usando `import`, o editor pode ser aberto depois com `/sbw arena edit <id>`. O mundo-fonte pode ser carregado para consulta, mas nao deve ser usado para editar os dados da arena:

```text
/sbw arena loadsource <id-arena>
```

## Edicao isolada

Edicao de blocos e pontos ocorre em uma instancia temporaria, copiada do template. Arenas nao podem estar em partida ativa:

```text
/sbw arena edit <arena>
```

O comando tambem aceita a forma `/sbw arena edit start <arena>`. Arenas habilitadas podem ser editadas desde que nao tenham partida em andamento; a engine impede novas entradas durante a sessao. O ponto inicial usa `editspawn`, se configurado, depois o lobby e, por fim, o spawn do mundo. Em mundos Void ou sem piso proximo, o editor recebe voo temporario e a posicao e limitada a altura valida; nenhum bloco permanente e criado por essa sessao.

Configure os pontos abaixo enquanto estiver dentro da copia de edicao. Para definir o ponto de entrada do administrador, use `editspawn`; para a cama, mire em um dos blocos da cama correspondente.

```text
/sbw arena set <arena> editspawn
/sbw arena set <arena> lobby
/sbw arena set <arena> spectator
/sbw arena set <arena> spawn <equipe>
/sbw arena set <arena> respawn <equipe>
/sbw arena set <arena> bed <equipe>
/sbw arena shop <arena> items
/sbw arena shop <arena> upgrades
/sbw arena generator add <arena> iron <equipe>
/sbw arena generator add <arena> gold <equipe>
/sbw arena generator add <arena> diamond
/sbw arena generator add <arena> emerald
```

As equipes e seus limites podem ser definidos com `/sbw arena team <arena> <id> <nome> <cor-hex> <capacidade>`. Ferro e Ouro precisam ser associados a uma equipe. Geradores, lojas e demais pontos sao persistidos com o nome canonico do mundo-fonte da arena, mesmo sendo selecionados na copia temporaria.

Para configurar equipes e pontos usando o fluxo guiado, consulte `STARFINBEDWARS_SETUP_WIZARD_GUIDE.md`. Dentro do clone, escolha primeiro `/sbw setup mode <solo|equipe>`; para Equipe, defina `/sbw setup team-size <2|3|4>`; entao inicie com `/sbw create teams <quantidade>`. O lobby ainda deve ser informado manualmente. O assistente grava lojas por equipe, regioes de geradores, limites/teto/void e coordenadas das estruturas de spawn, preserva lojas globais antigas como fallback e deixa a arena desabilitada para revisao. As estruturas registradas sao removidas somente na copia da partida ao iniciar, antes de teleportar jogadores.

Os limites automaticos sao configurados com `/sbw setarena center here`, `/sbw setarena upoversize <largura>x<comprimento>`, `/sbw setarena upheight <blocos>`, `/sbw setarena uplow <blocos>`, `/sbw setarena preview` e `/sbw setarena confirm`. Os marcadores sao virtuais e enviados somente ao administrador. Para selecao manual, use `/sbw setarena pos1` e `/sbw setarena pos2`; ambas as opcoes ficam pendentes ate confirmacao. `tp` teleporta ao centro candidato e `cancel` descarta a proposta. Os padroes configuraveis ficam em `config.yml` na secao `arena-bounds`.

Selecione a regiao principal obrigatoria com o machado identificado por token do plugin:

```text
/sbw arena region <arena> main wand
```

Clique esquerdo e direito nos blocos extremos. Tambem podem ser gravadas regioes opcionais `protection` e `waiting`. Se WorldEdit 6.x estiver instalado, selecione a area no mundo de edicao e use:

```text
/sbw arena region <arena> main worldedit
```

WorldEdit e opcional e nao e dependencia de compilacao. A integracao usa reflexao e falha com mensagem clara se o plugin estiver ausente ou nao for 6.x.

`main` deve pertencer ao mundo-fonte, respeitar `worlds.maximum-region-blocks` e conter lobby, espectador (se definido), spawn, respawn, camas, lojas e geradores configurados. Regioes opcionais tambem ficam limitadas ao mundo-fonte e ao limite de volume. A validacao exige pelo menos duas equipes, lobby, spawn/respawn/cama por equipe, capacidade suficiente, geradores consistentes e template com `level.dat`. O wizard tambem configura limites horizontais, altura maxima e nivel de void por arena.

Antes de publicar, valide os dados e finalize com:

```text
/sbw arena validate <arena>
/sbw arena edit publish <arena>
```

A publicacao recusa arenas invalidas ou em partida, descarrega a copia, cria uma area de staging, troca o template e preserva/restaura o template anterior em caso de falha. O mundo-fonte nunca e sobrescrito. Para descartar as alteracoes:

```text
/sbw arena edit cancel <arena>
```

`/sbw arena exit` fecha todas as sessoes de edicao do administrador, descarta instancias nao publicadas e tenta restaurar a localizacao, modo de jogo e permissao/estado de voo anteriores. `edit cancel` descarta somente a instancia informada. `/sbw arena tp <arena>` visita o mundo-fonte sem iniciar uma sessao e nao e um caminho de edicao. A ferramenta de selecao de regiao e removida do inventario ao concluir/cancelar a selecao ou sair.

Uma edicao interrompida por reinicio nao e retomada: a instancia temporaria marcada sera limpa com seguranca no proximo startup, mantendo o template original. A restauracao de retorno apos desconexao so e mantida na memoria do processo.

## Habilitar e executar

```text
/sbw arena min <arena> <jogadores>
/sbw arena countdown <arena> <segundos>
/sbw arena enable <arena>
/sbw join <arena>
```

`/sbw arena enable` valida novamente o template e toda a configuracao. Arenas invalidas ficam desabilitadas; corrija a definicao e valide antes de abrir partidas. Durante partidas, blocos do mapa seguem protegidos pelas regras da engine casual; a quebra permitida continua restrita a camas inimigas configuradas.

## Persistencia e compatibilidade

- Definicoes YAML ficam em `plugins/StarfinBedWars/arenas/<id>.yml` (versao 7); versoes 1 a 6 continuam sendo lidas. A v5 adiciona modo, centro, dimensoes e alturas relativas dos limites automaticos; a v6 registra os blocos das estruturas de spawn; a v7 registra o respawn separado. Arenas antigas precisam configurar o respawn por equipe antes de ativar.
- Templates e staging de publicacao ficam dentro da pasta do plugin, nunca no fluxo de exclusao de mundos administrativos.
- Instancias de jogo e edicao usam nomes `sbw_<uuid>` e marcadores validados pelo gerenciador de instancias existente.
- A geracao Void implementa os formatos legacy e por secoes requeridos pela API Bukkit 1.8.8, desativa estruturas e nao registra populators.
- Nenhum teste de servidor real foi executado por este guia; comandos Bukkit, mundos e WorldEdit devem ser exercitados em staging Spigot 1.8.8 antes da distribuicao.
