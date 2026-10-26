# Fase 5.2: validacao de kits por modalidade

## Implementacao

- `kits.yml`: Construtor, Minerador, Lenhador, Cortador, Carpinteiro,
  Escalador, Ferreiro, Arqueiro, Armadilheiro, Explorador, Defensor e
  Incendiario, cada um com cinco valores e precos configuraveis.
- `purchase-settings.yml`: modos e escopos padrao; cada kit e habilidade pode
  sobrescrever o escopo. IDs publicos DOUBLES/TRIOS/QUADS/TEAMS sao mapeados
  para duo/trio/squad/custom sem alterar as arenas existentes.
- Permissoes individuais dos kits sao registradas no `plugin.yml` com valor
  padrao `true` para que jogadores comuns possam usar os direitos adquiridos;
  gestores de permissoes podem revoga-las. Aquisicoes persistem apos revogacao.
- MySQL: `sbw_progression_entitlements`, `sbw_mode_selections`,
  `sbw_schema_migrations` e `sbw_scope_migration_history`. A migracao 52
  importa os niveis antigos de habilidades como GLOBAL sem apagar a origem.
- `GLOBAL` vale nos modos permitidos. `PER_MODE` mantem o nivel de cada modo.
  O nivel efetivo preserva o maior direito entre GLOBAL e o modo, inclusive
  quando o administrador muda um YAML. Selecao e independente por modo.
- Compras mostram precos e permanecem bloqueadas porque a rede ainda nao
  possui servico de moedas. `ProgressionPurchaseGateway` exige ID idempotente,
  nivel anterior e persistencia antes de confirmar uma compra. O gateway ativo
  responde `UNAVAILABLE`. Grants administrativos fazem testes sem cobranca.

## Verificacao executada

| Cenario | Resultado |
| --- | --- |
| `mvn -B clean verify` no Java 8 alvo | 233 testes; 0 falhas; 1 teste de MariaDB opcional ignorado na suite geral |
| Teste MySQL em MariaDB 11.4 descartavel | Passou: migracao legada, dois repositorios, grants GLOBAL/PER_MODE, selecoes e transicoes de escopo |
| Inicializacao no Pterodactyl BedWars `f03b4f97` | Plugin habilitou, arena `1` carregou e MySQL compartilhado conectou |
| Cliente 1.8.8 pelo proxy `127.0.0.1:25577` | Usuario e cliente de teste entraram na arena `1`; rodada iniciou e terminou |
| Menu e tooltip | Usuario confirmou 12 kits, 18 habilidades e textos visiveis sem corte |
| Construtor V em Solo | Usuario recebeu 12 las, de acordo com o nivel V persistido no MySQL |
| Morte com cama intacta | Usuario confirmou que o kit nao reentregou las no respawn |

O teste de `mvn -B clean verify` da suite geral omite o teste JDBC quando as
variaveis `SBW_TEST_DB_*` nao estao configuradas; ele foi executado
separadamente contra um banco descartavel com essas variaveis.

## Proximas verificacoes manuais

- Nivel I e demais kits em partida, incluindo ferramentas de usos limitados,
  bussola do Explorador e regras de colocacao do Armadilheiro/Defensor.
- Permissao revogada e restricoes especificas de arena/modo, inclusive
  Incendiario (desativado por padrao).
- Reconexao durante partida com kit recebido e nova partida em outro servidor.
- Teste de concorrencia de compra e cobranca apos existir provedor monetario;
  hoje nenhuma compra e cobrada ou confirmada.
- Persistencia visual de selecoes diferentes nos cinco modos apos reinicio,
  usando arenas com capacidades de equipe correspondentes.

O StarfinCore no ambiente de teste exibiu `Instalacao sem vinculo`; a rodada
BedWars funcionou mesmo assim. O vinculo depende da ativacao legitima do
StarfinCore pelo proprietario e nao foi alterado nesta fase.
