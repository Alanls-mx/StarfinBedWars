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
- Vault e dependencia de execucao. O gateway consulta o provedor `Economy`
  registrado no ServicesManager; sem plugin de economia, a compra continua
  bloqueada. O ledger MySQL guarda a reserva, debito e confirmacao, com trava
  por jogador/item entre servidores. Falhas ambiguas ficam pendentes para
  verificacao manual em vez de arriscar uma segunda cobranca. Grants
  administrativos fazem testes sem cobranca.

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
- Teste de debito e estorno com um provedor de economia real configurado no
  backend. O teste MySQL descartavel cobre reservas concorrentes.
- Persistencia visual de selecoes diferentes nos cinco modos apos reinicio,
  usando arenas com capacidades de equipe correspondentes.

O StarfinCore no ambiente de teste exibiu `Instalacao sem vinculo`; a rodada
BedWars funcionou mesmo assim. O vinculo depende da ativacao legitima do
StarfinCore pelo proprietario e nao foi alterado nesta fase.

## Integracao Vault (26/10/2026)

- `VaultAPI:1.7` e dependência `provided`; o plugin Vault esta em `depend` no
  `plugin.yml`. A dependência transitiva Bukkit 1.13 da API foi excluida para
  preservar o alvo Spigot 1.8.8.
- `mvn -B clean verify`: 234 testes, 0 falhas, 2 JDBC opcionais ignorados sem
  banco de teste. O teste `VaultPurchaseMySqlIntegrationTest` passou em MariaDB
  11.4 descartavel, incluindo reservas concorrentes, repeticao de ID, commit,
  estorno pendente e grant administrativo durante a compra.
- Vault 1.7.3 oficial e BedWars iniciaram no backend Pterodactyl `f03b4f97`;
  arena `1` e MySQL compartilhado carregaram. EssentialsX 2.19.3 foi instalado
  temporariamente e registrou `Essentials Economy` no Vault.
- O bot `P52KitBot` comprou `builder` I no lobby da arena `1`: saldo EssentialsX
  100000 -> 99000, `sbw_purchase_ledger.status=COMMITTED`,
  `sbw_progression_entitlements` com `KIT|builder|solo=1`. Compra de habilidade
  sem permissao foi bloqueada antes do debito.
- EssentialsX foi retirado do `plugins/`; JAR e dados de teste ficaram
  preservados em `vault-test-archive/` no volume do backend. A JAR final com
  Vault permaneceu instalada e reiniciou com arena e MySQL carregados.

Falta validar o debito de habilidade com um jogador que possua a permissao
especifica, e a economia compartilhada em mais de um backend com o provedor
definitivo. Vault sozinho nao persiste saldos nem fornece transacoes atomicas
entre plugin de economia e MySQL; estados ambiguos ficam bloqueados no ledger
para verificacao manual.
