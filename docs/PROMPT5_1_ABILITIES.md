# Prompt 5.1 — habilidades evolutivas

O catálogo fica em `plugins/StarfinBedWars/abilities.yml`. A primeira inicialização copia o arquivo padrão. `/sbw gameplay reload` valida o arquivo inteiro antes de trocar as definições em memória; erro de configuração mantém a versão anterior. Cada habilidade requer **permissão individual**, inclusive quando a categoria é `COMMON`. Categoria, nome, descrição, ícone, modos, nível mínimo, permissão, preços, cooldown, limite de ativações e os cinco valores são editáveis. A arena também pode desabilitar um ID pelas regras de gameplay.

O [abilities.yml completo](../src/main/resources/abilities.yml) contém os 18 exemplos prontos. Para mudar a classificação, edite `category: COMMON` para `SPECIAL` ou o inverso; isso altera a apresentação, sem conceder permissão. Para alterar uma habilidade, edite `prices.unlock` e `prices.level-2` até `level-5`, os valores em `levels.'1'` até `'5'`, `permission`, `enabled`, `cooldown-seconds` ou `minimum-player-level`, conforme o caso. Mantenha os IDs e todas as cinco seções de níveis. Por exemplo:

```yaml
abilities:
  archer:
    enabled: true
    name: "Arqueiro"
    description: "Chance de recuperar uma flecha ao acertar inimigos."
    icon: BOW
    category: SPECIAL
    permission: starfinbedwars.ability.archer
    max-level: 5
    minimum-player-level: 1
    modes: [solo, duo, trio, squad, custom]
    cooldown-seconds: 10
    activation-limit: 0
    prices: {unlock: 2000, level-2: 1000, level-3: 2400, level-4: 5000, level-5: 8000}
    levels:
      '1': {chance-percent: 5}
      '2': {chance-percent: 7.5}
      '3': {chance-percent: 10}
      '4': {chance-percent: 12.5}
      '5': {chance-percent: 15}
```

O trecho ilustra apenas uma entrada; ao editar o arquivo real, preserve as outras 17. Use `/sbw gameplay reload` depois de salvar. O parser recusa campos faltantes, IDs extras, permissões vazias e valores fora dos limites seguros.

| ID | Efeito nos níveis I–V | Gatilho |
| --- | --- | --- |
| `respawn_boost` | 5/10/15/20/25% menos tempo | morte com cama intacta; usa temporizador existente |
| `knockback_resist` | 3/5/7/9/12% menos impulso horizontal | golpe corpo a corpo recebido |
| `fall_resistance` | 5/10/15/20/25% menos dano | queda, nunca void |
| `fire_resistance` | 10/15/20/25/30% menos duração | combustão |
| `projectile_resistance` | 3/5/7/9/12% menos dano | flecha recebida |
| `miner` | 1,5/2/2,5/3/3,5 s de Pressa I | minar defesa próxima de cama inimiga |
| `momentum` | 1/1,5/2/2,5/3 s de Velocidade I | abate válido |
| `sentinel` | raio de 5/6/7/8/9 blocos | inimigo próximo da própria cama |
| `collector` | +0,5/0,75/1/1,25/1,5 bloco | recurso identificado do gerador da partida |
| `archer` | 5/7,5/10/12,5/15% | flecha atinge inimigo; retorna no máximo uma |
| `builder` | 24/21/18/15/12 blocos distintos | construção válida; Velocidade I temporária |
| `merchant` | 2/3/4/5/6% | primeira compra elegível concluída por vida |
| `aegis` | 0,5/1/1,5/1,5/2 corações | respawn válido; absorção temporária |
| `vampire` | 0,5/1/1,5/2/2,5 corações | abate final válido |
| `frenzy` | +2/3/4/6/8% dano | quebra de cama inimiga; bônus temporário corpo a corpo |
| `bodyguard` | 4/5,5/7/8,5/10% | aliado próximo recebe dano; protetor mais próximo prevalece |
| `forge_master` | 2/3/4/5/6% | ciclo do gerador de ferro/ouro do time; limite por minuto |
| `last_stand` | 3/4,5/6/7,5/9% menos dano | própria cama destruída e pouca vida |

O jogador abre o seletor no slot visual 6 do lobby de espera. A lista mostra as 18 habilidades e uma opção para desequipar. Clique em uma habilidade para consultar nível, próximo efeito, preço e permissão; clique em **Equipar** apenas após o desbloqueio. Pode haver uma habilidade equipada por partida. A seleção fica em `sbw_player_preferences.selected_ability`; os níveis por UUID ficam em `sbw_ability_levels` com data de aquisição e atualização. A migração SQL é aditiva. Caches carregam no login e são atualizados periodicamente; concessões administrativas são confirmadas no MySQL antes de aparecerem no cache. Desconectar e reconectar não concede uma nova ativação.

**Economia pendente:** não existe provedor de moedas permanentes no StarfinCore, StarfinMinigames ou BedWars atual. O contrato `AbilityCurrencyProvider` reserva valores com ID de transação e exige confirmação/compensação. O menu informa que compras e melhorias estão indisponíveis; não debita XP nem simula uma compra. `/sbw ability grant <jogador-online> <id> <1..5>` é exclusivo do administrador e eleva o nível de forma monotônica e idempotente para teste. Para testar o efeito, conceda também `starfinbedwars.ability.<id>` no seu plugin de permissões. Retirar a permissão preserva nível e seleção, mas bloqueia o efeito até a permissão voltar.

Os quatro IDs legados de `gameplay.yml` continuam legíveis para seleções anteriores. Eles só podem ativar com a permissão individual configurada ou, se vazia, com `starfinbedwars.ability.legacy.<id>`. O menu novo lista apenas as 18 habilidades evolutivas; desequipe e escolha um ID novo quando quiser migrar.

## Validação

1. Execute `mvn -B clean verify`. Os testes de catálogo e acesso cobrem os 18 IDs, cinco níveis, permissões e bloqueios por estado; as curvas de valores e cálculos de efeito são verificadas separadamente.
2. Use um schema **descartável** `sbw_phase2_test_*` e as variáveis `SBW_TEST_DB_HOST`, `SBW_TEST_DB_PORT`, `SBW_TEST_DB_SCHEMA`, `SBW_TEST_DB_USER`, `SBW_TEST_DB_PASSWORD` para executar `Phase2MySqlIntegrationTest`, que migra o banco e verifica concessão visível entre duas conexões.
3. No servidor 1.8.8, entre na arena com dois jogadores, conceda um ID e a permissão, selecione no frasco e confira seu gatilho. Repita para os cinco níveis, modo e permissão, com cama intacta/destruída conforme o caso. Confira retorno ao lobby, reconexão e reload. Para comprar/evoluir por moeda, a rede precisa primeiro integrar um provedor real.
