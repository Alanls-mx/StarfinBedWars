# Ambiente Always por mundo e arena

O arquivo `plugins/StarfinBedWars/always.yml` controla hora, dificuldade, clima e regras de mobs. O plugin aplica as regras aos mundos carregados e às cópias descartáveis das arenas enquanto estiver ativo. Alterações manuais feitas em jogo nos campos controlados são corrigidas a cada segundo.

## Herança

1. `defaults`: valores disponíveis para todos os contextos, mas `enabled: false` mantém mundos comuns intactos por padrão.
2. `worlds.<nome>`: altera um mundo carregado pelo nome. O mesmo ajuste é herdado pelas arenas que usam esse mundo como origem.
3. `arena-defaults`: altera todas as cópias de partidas. No arquivo inicial, `enabled: true` aplica sempre dia, dificuldade 3, clima limpo e desativa spawn natural e griefing de mobs nas arenas.
4. `arenas.<id>`: sobrescreve apenas a arena indicada. A regra é aplicada à cópia de cada partida, sem modificar o template publicado ou o mundo de origem.

Exemplo:

```yaml
defaults:
  enabled: false
  time: DAY
  difficulty: 3
  weather: CLEAR
  mob-spawning: false
  mob-griefing: false
  allow-monsters: false
  allow-animals: false
arena-defaults:
  enabled: true
worlds:
  Speedway:
    enabled: true
    time: 1000
arenas:
  '1':
    time: DAY
    difficulty: 2
    weather: RAIN
    mob-spawning: unchanged
```

`time: DAY` mantém o ciclo durante o dia e volta a 1000 ticks quando começa a noite; `NIGHT` mantém o ciclo noturno. Um inteiro de `0` a `23999` fixa a hora e desativa `doDaylightCycle` enquanto a regra estiver ativa. `difficulty` aceita `0` a `3` ou `PEACEFUL`, `EASY`, `NORMAL`, `HARD`. `weather` aceita `CLEAR`, `RAIN`, `THUNDER`. Os quatro campos de mobs aceitam `true`/`false`. Use `unchanged` em qualquer campo para remover uma regra herdada; em `time`, `difficulty` e `weather`, `off` também é aceito. Para desativar todas as regras de um mundo ou arena, use `enabled: false`.

`mob-spawning` controla a gamerule `doMobSpawning`; `mob-griefing` controla `mobGriefing`. `allow-monsters` e `allow-animals` controlam as flags de spawn natural do mundo. NPCs criados pelo plugin não dependem de spawn natural. Ao recarregar ou desativar o plugin, dificuldade, clima, flags e gamerules que ele alterou são restaurados ao valor observado antes da ativação; a hora não é retrocedida.

Use `/sbw always status` para ver a regra e o estado real do mundo onde você está, `/sbw always status <mundo>` para outro mundo carregado, `/sbw always status arena:<id>` para a configuração efetiva da arena e `/sbw always reload` após editar o arquivo. Esses comandos exigem `starfinbedwars.command.always` ou `starfinbedwars.admin`. Um `always.yml` inválido é rejeitado sem substituir as regras que já estão em execução.

## Verificação no servidor

1. Entre em uma partida da arena e use `/sbw always status`. Confirme `arena=<id>`, `time=DAY`, `difficulty=HARD` e `weather=CLEAR`.
2. Use `/time set night`, `/difficulty easy` e `/weather rain` na cópia de teste; após um segundo, confirme pelo status que as regras voltaram aos valores configurados.
3. Configure um mundo comum em `worlds`, recarregue e confira seu status. Desative a regra e confira que os valores anteriores retornaram.
4. Mude apenas `arenas.<id>`, recarregue e inicie outra partida. Confirme que o mundo de origem e outras arenas não receberam o override.
