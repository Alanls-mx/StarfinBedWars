# Configuracao de arenas

> **Documento legado:** nao siga os comandos de edicao direta no mundo-fonte descritos abaixo. O fluxo suportado usa uma copia isolada e esta documentado em `STARFINBEDWARS_ARENA_GUIDE.md`. Este arquivo permanece apenas para preservar o guia da etapa anterior.

## Importar um template

O mundo-fonte deve ser uma pasta filha direta do diretorio de mundos do servidor e conter `level.dat`. Ele **precisa estar descarregado** para a importacao, porque uma copia de um mundo ativo nao representa um snapshot consistente. A importacao nao apaga nem altera o mundo-fonte.

```text
/sbw arena import <id> <nome-do-mundo>
```

A copia e feita em segundo plano dentro de `plugins/StarfinBedWars/templates/<id>`. O plugin cria um arquivo de arena inicial com duas equipes, `red` e `blue`, ambas com capacidade 4. Para configurar as coordenadas, carregue o mundo-fonte:

```text
/sbw arena loadsource <id>
```

O plugin nao descarrega o mundo-fonte automaticamente. Use o mecanismo de gerenciamento de mundos do servidor, verificando antes que nao haja jogadores nele.

## Definir equipes e posicoes

Crie/atualize uma equipe com um caractere de cor hexadecimal Bukkit (`0`-`9`, `a`-`f`) e capacidade entre 1 e 100:

```text
/sbw arena team <arena> <id-equipe> <nome> <cor> <capacidade>
/sbw arena removeteam <arena> <id-equipe>
```

Va ao mundo-fonte, posicione-se no lobby e grave os pontos:

```text
/sbw arena set <arena> lobby
/sbw arena set <arena> spectator
/sbw arena set <arena> spawn <id-equipe>
```

Para a cama, mire em qualquer um dos dois blocos da cama correta:

```text
/sbw arena set <arena> bed <id-equipe>
```

Configure recursos e lojas com o mundo-fonte carregado e esteja na posicao desejada:

```text
/sbw arena generator add <arena> iron <id-equipe>
/sbw arena generator add <arena> gold <id-equipe>
/sbw arena generator add <arena> diamond
/sbw arena generator add <arena> emerald
/sbw arena generator list <arena>
/sbw arena generator setteam <arena> <gerador> <id-equipe>
/sbw arena generator remove <arena> <gerador>
/sbw arena shop <arena> items
/sbw arena shop <arena> upgrades
```

Geradores de Ferro/Ouro devem ser associados a uma equipe. Os niveis e limites de recursos ficam em `generators.yml`; produtos fisicos ficam em `shop.yml`; melhorias e armadilhas ficam em `upgrades.yml` e `traps.yml`. Os NPCs sao criados na instancia da partida e removidos ao final; configure locais no mundo-fonte, nunca em uma instancia ativa.

As equipes podem ter capacidades assimetricas. A distribuicao de jogadores escolhe primeiro uma equipe com menos integrantes e que ainda tenha espaco. O minimo de jogadores nao pode exceder a capacidade total.

## Validar e habilitar

```text
/sbw arena min <arena> <jogadores>
/sbw arena countdown <arena> <segundos>
/sbw arena validate <arena>
/sbw arena enable <arena>
```

A validacao exige template com `level.dat`, lobby, pelo menos duas equipes, spawn e cama em cada equipe, capacidade total suficiente e pontos pertencentes ao mundo-fonte. Geradores precisam pertencer ao mundo-fonte; Ferro/Ouro tambem precisam de equipe valida. A arena inicia a instancia isolada somente quando o primeiro jogador usa `/sbw join <arena>`.

## Ciclo da partida

Participantes ficam no lobby da instancia durante o countdown. Ao iniciar, sao enviados aos spawns das equipes. A construcao/destruicao de blocos e explosoes ficam bloqueadas, exceto a cama de uma equipe inimiga, que e removida e desativa os respawns daquela equipe. Morte com cama viva retorna o jogador ao spawn em modo protegido por `match.respawn-seconds`; sem cama, o jogador e eliminado e passa a espectador. A ultima equipe elegivel vence; tambem e possivel usar `/sbw spectate <arena>` durante a partida. Geradores, lojas, upgrades e armadilhas ficam isolados ao estado da partida; os recursos sao itens fisicos do inventario.

O `/sbw leave` remove jogadores da espera ou abandona a partida. Uma desconexao durante a partida preserva o lugar por `match.disconnect-grace-seconds`; o jogador pode reconectar no mesmo processo do servidor. Essa tolerancia nao persiste em reinicios.

## Persistencia e restauracao

Cada arena fica em `plugins/StarfinBedWars/arenas/<id>.yml`; o template importado fica em `plugins/StarfinBedWars/templates/<id>/`. Cada partida recebe um mundo `sbw_<uuid>` no diretorio de mundos, com marcador interno de propriedade. O plugin so remove esse mundo depois de validar o nome/marcador e descarrega-lo com sucesso. Se a descarga falhar, o mundo fica intacto e a arena fica bloqueada; nunca ha fallback para apagar a pasta.
