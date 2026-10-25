# Gameplay avançado: kits, habilidades e regras

## Seleção no lobby de espera

Em `WAITING` e `STARTING`, a estrela do Nether ocupa o índice 3 (slot visual 4) e o frasco de encantamento ocupa o índice 5 (slot visual 6). Clique direito abre as GUIs. O frasco é cancelado antes do arremesso. Os dois itens têm marcador interno, não podem ser movidos ou descartados e são removidos no início da partida. A espada e a armadura de jogo só são equipadas em `STARTED`, após a abertura do spawn. Se a contagem for cancelada por saída de jogador, o inventário de espera e seus seletores são reconstruídos. O inventário anterior do jogador é capturado pelo `ParticipantInventoryService` e restaurado ao sair. O assistente de arena não recebe esses itens.

Depois que os jogadores chegam aos spawns das ilhas, uma saída não os devolve ao lobby de espera. As barreiras permanecem fechadas enquanto faltam jogadores; novas entradas são encaminhadas diretamente ao spawn da equipe e a abertura prossegue quando o mínimo da arena é atingido. Se ninguém restar, a instância é encerrada. Jogadores desconectados só podem reconectar enquanto a cama de sua equipe estiver intacta. Destruir essa cama invalida imediatamente as sessões pendentes desse time e libera a condição de vitória quando não houver outro jogador ativo.

As escolhas são salvas por UUID nas colunas `selected_kit` e `selected_ability` da tabela compartilhada `sbw_player_preferences`. A migração é aditiva; as escolhas usam o worker JDBC e o outbox local já existentes. Todos os nós BedWars precisam apontar para o mesmo MySQL em `stats.yml`. Se o ID salvo não existir mais, o jogo usa `default` e `none`, respectivamente. O idioma segue a preferência já existente (`pt_BR`, `en_US`, `es_ES`).

## `gameplay.yml`

Cada kit define `id`, `enabled`, nomes e descrições nos três idiomas, `icon`, `level`, `permission`, `modes`, `start-items`, `respawn-items` e `permanent-tool`. Itens de início são entregues uma vez; os de respawn somente quando listados explicitamente. A ferramenta permanente passa pelo serviço de equipamentos, que preserva as compras e upgrades. Os kits fornecidos são `default` (4 lãs), `builder` (12 lãs), `warrior` (1 maçã dourada), `miner` (picareta de pedra permanente) e `explorer` (1 poção de velocidade). O kit padrão é gratuito e os outros usam níveis 5, 10, 10 e 15.

Cada habilidade define tipo, efeito, duração, cooldown, potência, nível, permissão e modalidades. `surefoot` reduz dano de queda em 20%, `sprinter` concede velocidade por 4 s após abate (30 s de cooldown), `guardian` concede resistência por 3 s após respawn (60 s de cooldown), `dash` concede velocidade por 5 s via `/sbw ability use` (90 s de cooldown). Efeitos são limitados à partida e o cooldown é isolado por jogador e partida.

`generator-split.enabled` e `anti-afk.enabled` vêm desligados para preservar arenas existentes. `ROUND_ROBIN` entrega cada produção inteira a um jogador elegível próximo, alternando jogadores; conserva a quantidade produzida. `SHARED` entrega uma cópia inteira a cada um de até `maximum-recipients` jogadores; multiplica a produção explicitamente. Apenas jogadores conectados do próprio time, na mesma partida e dentro de `radius`, são elegíveis. Excedentes de inventário caem no gerador até o limite configurado. Sem jogador elegível, o item cai no ponto de geração tradicional. Geradores centrais não usam Split.

O anti-AFK distingue lobby de espera e partida, avisa antes de remover e usa a saída normal da engine. Movimento menor que 0,5 bloco ou giro de câmera não renovam a atividade; ações de jogo renovam. `STARTING`, espectadores, administradores isentos por `starfinbedwars.afk.exempt` e jogadores desconectados não são punidos. Configure `waiting-timeout-seconds`, `game-timeout-seconds`, `warning-seconds` e a permissão de isenção.

`custom-teams.maximum-team-size` (padrão 8) e `maximum-total-players` (padrão 64) limitam **novas** configurações de equipe no assistente e no comando de equipe. Arenas antigas não são alteradas ou desativadas apenas por esses limites. A modalidade `custom` já é calculada para capacidades acima de 4.

## Regras por arena e comandos

As regras são opcionais em `arenas/<id>.yml` (`rules.generator-split`, `rules.anti-afk`, `rules.respawn-seconds`, `rules.kits`, `rules.abilities`). Ausência herda o valor global. Altere apenas quando a arena estiver fora de partida:

```text
/sbw arena rule <arena> split <on|off|inherit>
/sbw arena rule <arena> afk <on|off|inherit>
/sbw arena rule <arena> respawn <0..30|inherit>
/sbw arena rule <arena> kits <default,builder,...|inherit>
/sbw arena rule <arena> abilities <surefoot,dash,...|none|inherit>
```

`kits` deve incluir `default`. `/sbw kit list` e `/sbw ability list` consultam os IDs; `/sbw kit` e `/sbw ability` abrem a GUI no lobby de espera. `/sbw ability use` aciona a habilidade ativa durante a partida. A permissão `starfinbedwars.command.gameplay.admin` (ou `starfinbedwars.admin`) dá acesso a `/sbw gameplay reload|diagnose|inspect <jogador>|cooldowns <jogador>`. O reload valida um novo arquivo antes de substituir o catálogo em memória. Os efeitos e escolhas de uma partida iniciada continuam restritos ao ciclo dessa partida.

## Roteiro de homologação em servidor

1. Com MySQL habilitado, entrar na arena e conferir os slots 4 e 6. Tentar mover, descartar e arremessar o frasco; abrir os dois menus e selecionar opções liberadas e bloqueadas.
2. Iniciar uma partida com dois jogadores; conferir remoção dos seletores, aplicação do kit e habilidade, uso de `/sbw ability use`, cooldown, abate, morte, três segundos como espectador e respawn. Confirmar que não há item consumível duplicado.
3. Sair e confirmar inventário/efeitos/modo de jogo originais; reiniciar o servidor e confirmar que as seleções permanecem. Repetir em outro nó BedWars da mesma rede MySQL.
4. Ativar Split em uma arena de teste, colocar dois aliados no gerador e um inimigo próximo, conferir alternância e isolamento. Repetir sem aliados. Ativar `SHARED` só para verificar a multiplicação documentada e o limite.
5. Reduzir temporariamente os tempos anti-AFK em ambiente de teste, observar aviso, movimentar-se mais de 0,5 bloco para cancelar, e repetir até a remoção. Confirmar isenção de administrador, espectador e setup.
6. Criar uma arena descartável com cinco ou mais jogadores por equipe; conferir capacidade, spawns e scoreboard. Testar `arena rule`, recarga de configuração e retorno por reconnect.

`mvn -B clean verify` valida código, configuração, serialização de arenas e testes unitários. A homologação visual e os passos multiplayer acima dependem de clientes Minecraft 1.8.8 conectados ao servidor real de teste; registre separadamente o que foi observado.

### Validação conjunta de 25/10/2026

- No servidor Pterodactyl BedWars `f03b4f97`, com proxy `127.0.0.1:25577`, o jogador confirmou os seletores nos slots 4/6 e os dois menus. Confirmou ausência da espada no spawn fechado, restauração dos seletores após cancelamento no lobby e entrega da espada e do kit apenas ao abrir o spawn.
- Após o início da abertura, o jogador de teste saiu e o jogador remanescente confirmou que permaneceu na própria ilha. O catálogo MySQL marcou a arena como `OPENING`, `players=1`, `joinable=1`. A nova entrada do jogador de teste passou pelo proxy, chegou direto ao spawn da ilha vaga e a partida abriu. O jogador confirmou entrega dos 12 blocos do Construtor e ativação da Arrancada.
- A seleção `builder`/`dash` foi observada na tabela `sbw_player_preferences`. Com o jogador de teste desconectado, sua cama foi destruída durante a janela de reconexão; o jogador confirmou encerramento imediato. O registro MySQL fechou como `VICTORY` para `team1` antes do prazo da sessão.
- Os limites de nível de Construtor e Arrancada foram reduzidos para 1 somente no `gameplay.yml` do servidor de teste para permitir esta verificação com o perfil de nível 1. O arquivo de produção do repositório mantém os níveis 5 e 15.

Ainda requer inspeção manual específica: morte e respawn com cama intacta, cooldown, restauração completa de inventário, Generator Split com aliados, anti-AFK, arena com mais de quatro jogadores por equipe e sincronização entre dois nós BedWars. Os testes automatizados cobrem regras de domínio e persistência, mas não substituem essas verificações em jogo.
