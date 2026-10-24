# StarfinBedWars — análise de arquitetura e integração

> **Atualizacao de 23/10/2026 (Prompt 05):** este documento preserva a auditoria historica do StarfinCore e suas recomendacoes da epoca. O StarfinBedWars atual removeu o adaptador opcional e a declaracao `softdepend: StarfinCore`. O nucleo compartilhado planejado passou a ser StarfinMinigames; seu codigo/API nao foi encontrado no workspace nem no repositorio acessivel nesta etapa. Portanto, o reconnect implementa somente a recuperacao local Casual, sem afirmar integracao de idioma, armazenamento compartilhado ou transporte BungeeCord. Consulte `STARFINBEDWARS_RECONNECT_GUIDE.md` para o estado atual.

**Data da análise:** 23/10/2026  
**Escopo:** leitura do StarfinCore local e do repositório GitHub autorizado, além da especificação técnica StarfinBedWars v1.1 fornecida como anexo.  
**Resultado:** nenhum código do BedWars foi criado nesta etapa. O StarfinCore e seus arquivos não foram modificados.

## 1. Conclusão executiva

O StarfinCore encontrado **não é uma biblioteca geral de serviços de gameplay**. Na versão mais recente disponível em `main`, ele é principalmente a infraestrutura de identidade da instalação, ativação remota e autorização comercial por produto. Ele não implementa perfis de jogadores, preferências ou idioma por UUID, mensagens compartilhadas para outros plugins, persistência de dados de gameplay, cache genérico, eventos públicos de gameplay, integração com Party ou APIs de lobby.

O principal contrato Java reutilizável no Bukkit é a API de autorização em `com.starfinplugins.core.api`, especialmente `VerifiedAuthorizationService`. A API não deve ser confundida com uma API de jogadores. Além disso, esse contrato não existe na branch remota padrão `develop`; a integração deve, portanto, ser feita contra uma versão do Core efetivamente fixada e distribuída, não contra uma branch mutável.

**Referência de código recomendada para esta análise:** commit `2a0fc1f6f97faf0671f60bbaefeab82f31846967` da branch remota `main`, que coincide com o checkout local. Não foi possível identificar uma release formal ou um artefato publicado versionado; o `pom.xml` declara `0.3.1-SNAPSHOT`. Antes de acoplar o BedWars ao Core, é necessário fixar e testar o JAR/API que será distribuído com ele e confirmar no sistema de licenciamento o identificador autorizado do produto BedWars. A busca no código e documentação local não encontrou `starfin_bedwars`.

As recomendações de módulos, armazenamento, engine e BungeeCord abaixo são **propostas para o StarfinBedWars**, não recursos confirmados do Core.

## 2. Fontes e estado examinados

### Código local

- Fonte encontrada em `C:\Users\alanl\Downloads\StarfinCore`.
- Remote configurado: `https://github.com/Alanls-mx/StarfinCore.git`.
- Checkout atual: branch `main`, commit `2a0fc1f`, igual a `origin/main`; working tree sem alterações.
- A proteção de propriedade do Git impediu os primeiros comandos de leitura. Os comandos seguintes usaram uma exceção `safe.directory` apenas por comando; nenhuma configuração global foi alterada.
- Existem outros worktrees/branches locais. `develop` está cinco commits à frente de `origin/develop`, mas apenas um commit à frente de `origin/main`; os demais branches locais examinados não contêm commits exclusivos em relação a `origin/main`. Não foram usados como referência para a arquitetura atual.

### GitHub

- Repositório privado confirmado: [Alanls-mx/StarfinCore](https://github.com/Alanls-mx/StarfinCore).
- Branch padrão remota: `develop`, commit `11dbe6037d43bf8040efd1f527ddcdb06e2edece`, datado de 15/10/2026.
- Branch remota `main`: commit `2a0fc1f6f97faf0671f60bbaefeab82f31846967`, datado de 20/10/2026; coincide com o código local.
- Não foram encontrados tags Git nem releases GitHub. Portanto, não há uma “versão publicada mais recente” formal identificável nesta auditoria; há branches e uma versão Maven `SNAPSHOT`.
- A diferença entre `main` e a branch padrão `develop` é substancial: a comparação exibiu 46 arquivos alterados, incluindo remoção na `develop` de classes Bukkit/Bungee, cliente remoto, identidade, carregador de bibliotecas, mensagens e traduções presentes em `main`. `develop` não contém `VerifiedAuthorizationService` nem `CoreMessages`.
- `develop` também possui trabalho local associado à antiga implementação v2 de licenciamento. Esse histórico não deve ser combinado implicitamente com o fluxo v3 presente em `main`.

### Workspace StarfinBedWars

O workspace contém `.idea`, `StarfinBedWars.iml` e um `src\Main.java` de exemplo IntelliJ (“Hello and welcome!”). Não foram encontrados `pom.xml`, `build.gradle`, `plugin.yml` ou estrutura de plugin Bukkit. O arquivo preexistente não foi alterado.

### Material de produto

O anexo `StarfinBedWars_Plano_Tecnico_Completo_v1.1.md` foi lido como **plano/requisito de produto**, não como evidência de funcionalidades já existentes no Core. Em especial, as referências do plano a `StarfinCore.LanguageService`, perfis e preferências por jogador não foram confirmadas no código.

## 3. Arquitetura atual confirmada do StarfinCore

### Linguagem, plataforma e nomenclatura

- Maven: `groupId` `com.starfinplugins`, `artifactId` `starfincore`, versão `0.3.1-SNAPSHOT`.
- `maven.compiler.release` é `8`.
- Dependências de plataforma: `org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT` e `net.md-5:bungeecord-api:1.8-SNAPSHOT`, ambas `provided`.
- A classe principal Bukkit declarada em `src/main/resources/plugin.yml` é `com.starfinplugins.core.bukkit.StarfinCorePlugin`.
- O mesmo POM produz artefatos shaded classificados como `Spigot` e `Bungee`; a implementação Bungee está em `com.starfinplugins.core.bungee.StarfinCoreBungeePlugin`.
- O README declara que o Bukkit foi executado em PaperSpigot 1.8.8. Não declara suporte a Velocity.
- Convenção observada: `com.starfinplugins.core.<área>`, classes em PascalCase e APIs sob o pacote `api`.

### Organização e responsabilidades reais

| Área | Implementação observada |
|---|---|
| API de autorização | `src/main/java/com/starfinplugins/core/api/AuthorizationService.java`, `AuthorizationState.java`, `ProductAuthorization.java` e `VerifiedAuthorizationService.java`. |
| Plugin Bukkit | `bukkit/StarfinCorePlugin.java`: ciclo de vida, configuração, validação remota, mensagens administrativas, registro de serviço e comando `/starfincore`. |
| Estado/caching | `state/AuthorizationStore.java`: snapshot em memória das autorizações de produtos; não é cache de jogador. |
| Identidade | `identity/InstallationIdentity.java`, `InstallationCredential.java` e `InstallationLinkMarker.java`: identidade/credencial da instalação, não identidade de jogador. |
| Rede/licenciamento | `remote/InstallationClient.java`, `InstallationResult.java` e `SignedGrant.java`: cliente/resultado da autorização remota. |
| Configuração e mensagens | `bukkit/CoreSettings.java`, `bukkit/CoreMessages.java` e `src/main/resources/config.yml`, `lang/pt_BR.yml`, `lang/en_US.yml`. Escopo administrativo do próprio Core. |
| Bibliotecas runtime | `libraries/RuntimeLibraries.java`: baixa artefatos fixos do Maven Central, valida SHA-256 e os adiciona ao classloader do Core em `onLoad`. |
| Bungee | Plugin separado em `bungee/StarfinCoreBungeePlugin.java`; fornece o lado de licenciamento do Core para o proxy, não orquestra partidas. |

Não foram encontradas abstrações de perfil/jogador, `PlayerManager`, repositories de gameplay, API de linguagem por UUID, sistema genérico de banco de dados, eventos públicos de BedWars ou integração com Party/Lobby.

### Contratos e métodos que podem ser usados

- `AuthorizationService#get(String productId)` e `products()` em `api/AuthorizationService.java`.
- `ProductAuthorization#productId()`, `state()`, `validUntil()`, `reasonCode()` e `permitted()` em `api/ProductAuthorization.java`. `permitted()` aceita somente `AUTHORIZED` ou `GRACE`.
- `VerifiedAuthorizationService#protocolVersion()`, `installationId()` e `signedGrant(String productId)` em `api/VerifiedAuthorizationService.java`; estende `AuthorizationService`.
- `StarfinCorePlugin#onEnable()` registra `VerifiedAuthorizationService` no `ServicesManager` Bukkit (versão do protocolo retornada: `1`). `onDisable()` cancela tarefas e limpa o estado de autorização.
- `StarfinCorePlugin#authorizationService()` e `signedGrant(String)` são públicos na classe de implementação; consumidores devem preferir o contrato publicado/registrado, sem fazer cast para a implementação do plugin.
- `StarfinCorePlugin#canStart(Plugin, String)` é uma ponte transitória e explicitamente limitada a `starfin_bans` e ao plugin `StarfinBans`; **não** é um método de autorização genérico para BedWars.
- O comentário de `signedGrant` exige que o consumidor verifique a assinatura Web independentemente. Não usar o grant sem implementar/verificar o protocolo correspondente.

**Compatibilidade de branch:** a branch remota `develop` não contém `VerifiedAuthorizationService`; a integração baseada nesse contrato só é válida se o JAR distribuído for construído da linha `main` (ou de uma versão futura que preserve o contrato).

### Protocolo de autorização e evidência de testes

- `InstallationClient#challenge(...)` e `check(...)` implementam o fluxo remoto v3; a validação exige HTTPS fora do modo local de teste e confere identidade da instalação, produto atribuído, validade, IP/porta e grants assinados. `InstallationClient#download(...)` valida SHA-256 do artefato antes de salvá-lo.
- `SignedGrant#valid(...)` valida assinatura e claims, incluindo product ID, UUID da instalação, porta, IP e expiração. Isso é licenciamento de produto/instalação, não autenticação ou perfil de jogador.
- `InstallationClientTest` cobre o protocolo com um `HttpServer` local e chave efêmera de teste; `AuthorizationStoreTest` cobre estados locais e tolerância de comunicação. O README registra que validação positiva e múltiplas instalações contra API de produção não foram homologadas. Nenhum teste executado nesta auditoria comprova autorização real do produto BedWars.
- `ProductAuthorization#permitted()` considera `GRACE` permitido além de `AUTHORIZED`. A política comercial deve decidir se isso permite iniciar partidas novas; uma falha transitória de licença não deve interromper uma partida em andamento sem uma regra explícita.

### Configuração, mensagens, eventos e ciclo de vida

- `CoreSettings#read(FileConfiguration)` valida somente configuração operacional/licenciamento do Core e aceita `pt_BR`/`en_US` globalmente para o próprio plugin.
- `CoreMessages#load(JavaPlugin, String)`, `text(String, Map<String,String>)` e `send(...)` formatam mensagens do Core. O método recebe um locale para carregar arquivos do Core; não existe lookup de locale por jogador nem uma API de tradução compartilhada documentada.
- `StarfinCorePlugin` implementa `Listener` e registra a si próprio em `onEnable()`. A observação de `PlayerJoinEvent` é usada para notificação administrativa do Core; não foi encontrado um catálogo de eventos públicos do Core.
- O cliente de autorização roda tarefas assíncronas; operações que respondem ao Bukkit retornam à thread principal com o scheduler. O ciclo de vida trata configuração inválida e identidade ausente como falhas de inicialização/desativação.
- Não inferir daí que o Core oferece um framework de eventos, mensagens ou threading para os outros plugins.

### Banco de dados, concorrência e bibliotecas

- `AuthorizationStore` guarda estados de autorização **em memória**, sincroniza os métodos de atualização/leitura e trata `AUTHORIZED`, `GRACE`, falha de comunicação e rejeições confirmadas. Não persiste perfil, estatística ou estado de partida.
- A identidade/credencial do servidor e desafio temporário são arquivos locais sob a pasta de dados do Core; isso não é storage de jogadores.
- `RuntimeLibraries` fornece bibliotecas runtime listadas no código/documentação (HikariCP, SLF4J, SQLite JDBC, MySQL Connector/J e Protocol Buffers), verificadas por hash. `docs/SHARED-LIBRARIES.md` orienta consumidores Starfin a declarar dependência obrigatória do Core e as bibliotecas como `provided`.
- Isso é **distribuição de dependências**, não um `DatabaseService`, repository, schema compartilhado ou pool/connection manager do Core. A compatibilidade real de classloader deve ser testada com o JAR/plataforma distribuído.
- Estado de autorização usa referências `volatile`, `AtomicBoolean` para impedir verificações concorrentes e operações sincronizadas no store. Não há cache genérico nem política de concorrência para entidades BedWars.

### Dependências externas relevantes

Além das APIs Spigot/Bungee, o POM inclui Gson, JUnit 4 e plugins Maven Compiler/Surefire/Shade. A instalação runtime do Core depende de HTTPS ao Maven Central na primeira carga das bibliotecas, conforme `README.md` e `RuntimeLibraries#install(...)`. Não adicionar dependência do BedWars às classes internas do Core só porque estão no mesmo domínio organizacional.

## 4. Diferenças local ↔ GitHub e versão de referência

1. **Checkout local `main` vs GitHub `main`:** mesmo SHA (`2a0fc1f...`), sem alterações locais no working tree. Não há diferenças de código não publicadas nesse checkout.
2. **Branch padrão `develop`:** é mais antiga que `main` e representa uma árvore materialmente diferente, não uma release equivalente. O código necessário para a análise (incluindo `VerifiedAuthorizationService`, as mensagens e a distribuição Bungee/runtime) não está todo presente nela.
3. **Branch local `develop`:** aparece cinco commits à frente de `origin/develop`, mas somente um commit (`a07b098`, alinhamento do JAR de teste) não está em `origin/main`; contém também histórico da linha anterior de licenciamento. Não é a base recomendada para BedWars.
4. **Tags/releases:** nenhuma encontrada. O artefato é `SNAPSHOT`, portanto não há garantia de API binária imutável inferível nesta auditoria.
5. **Base recomendada:** usar `main@2a0fc1f` para entender a implementação local atual e para prototipar uma integração licenciada, condicionada a confirmação do JAR de produção e ao identificador autorizado do produto. Não depender de funcionalidades presentes só nessa branch sem fixar uma versão distribuível. Não usar `develop` como se fosse equivalente.

## 5. Matriz de integração — o que existe e o que não existe

| Sistema | A. Confirmado no Core / B. Reutilização | C. Responsabilidade do BedWars | D. Possível adição futura ao Core | E. Dependências e riscos |
|---|---|---|---|---|
| Perfil e UUID de jogador | Nenhum perfil ou serviço de UUID de jogador. `InstallationIdentity` representa a instalação do servidor. | Criar perfil BedWars próprio identificado por UUID Bukkit; controlar carga/ausência e não usar nome como chave. | Nenhuma adição necessária para perfil específico do jogo. | Não confundir UUID de instalação retornado pela API de autorização com UUID de jogador. |
| Idioma individual | `CoreSettings` tem locale global do Core; `CoreMessages` traduz mensagens do Core. Não há `LanguageService` ou `UUID -> locale`. | Guardar preferência por jogador e resolver idioma no bridge de apresentação; suportar os idiomas previstos no produto e fallback. | Serviço compartilhado por UUID somente se houver requisito transversal confirmado em outros plugins e contrato aprovado. Não é pré-requisito para BedWars. | Não chamar `CoreMessages` como se fosse API de locale por jogador; essa classe não oferece essa semântica. |
| Mensagens/traduções | Arquivos `lang` e fallback existem para mensagens administrativas do Core. | Pacotes `languages/*.yml` próprios, placeholders validados, fallback e renderização por destinatário para chat/menus/HUD. | Nenhuma para mensagens específicas do BedWars. | Chaves, placeholders e ciclo de reload devem pertencer ao BedWars; não compartilhar arquivos de mensagens do Core. |
| Banco de dados | Bibliotecas JDBC distribuídas pelo Core; nenhuma API de persistência de jogadores ou dados do jogo. | Repositories e migrations próprios para SQLite local e MySQL/MariaDB em rede, transações e idempotência de resultados. | Um contrato de DB compartilhado poderia ser estudado depois, mas não é necessário nem encontrado. | Decidir entre dependências `provided` do Core ou distribuição explícita do BedWars após testar classloader e instalação sem duplicação. Não assumir pool/connection já existente. |
| Cache | `AuthorizationStore` sincroniza apenas snapshots de autorização por produto. | Caches delimitados para perfis/leaderboards/arena conforme necessidade, invalidação e consistência definidos pelo domínio. | Nenhuma para cache de gameplay. | Não reutilizar `AuthorizationStore` para estados de jogo. |
| Configurações | YAML/configuração global do Core, validada por `CoreSettings`. | Configuração e recarga versionada por domínio do BedWars, com ruleset congelado para partidas competitivas. | Nenhuma adição necessária. | Não editar nem sobrescrever configuração do Core; segredo de DB fora de versionamento e de logs. |
| Eventos | Listener Bukkit interno para operação do Core; nenhum evento público de partida/perfil. | Eventos BedWars próprios e máquina de estados; publicar contratos estáveis na API pública do produto. | Nenhuma para eventos específicos do BedWars. | Não tratar eventos Bukkit de Core como integração de domínio ou publicar evento cancelável depois de efetivar estado. |
| API compartilhada | API pública limitada a autorização comercial (`AuthorizationService`, `ProductAuthorization`, `AuthorizationState`, `VerifiedAuthorizationService`). | Criar `bedwars-api` com DTOs/serviços imutáveis e operações autorizadas; integrar licenciamento via adaptador separado. | Uma API estável/versionada de autorização e publicação de artefato pode precisar de coordenação, mas não exige expansão de domínio do Core. | `VerifiedAuthorizationService` está ausente em `develop`; fixar o contrato/artefato antes de compilar e distribuir. |
| Party | Nenhum provider/evento de Party encontrado. | Definir `PartyProvider` opcional do lado BedWars, consultado apenas quando plugin compatível estiver ativo; comportamento solo sem dependência. | Não adicionar Party ao Core; Party é produto separado. | API ainda precisa ser definida pelo produto StarfinParty. Validar todos os membros e não dividir grupo silenciosamente. |
| Bans | `canStart(Plugin, String)` contém ponte específica para `StarfinBans`; produto `starfin_bans` aparece no código/documentação. Não há provider geral de punições. | Hook opcional, isolado e sem dependência de compilação obrigatória; padrão claro quando ausente. | Nenhuma adição necessária ao Core. | Não usar `canStart` como lookup de bans nem ampliar o identificador hardcoded; definir API com StarfinBans quando disponível. |
| Lobby | Nenhuma API de Lobby encontrada. | Hooks opcionais de entrada/retorno ao lobby, com fluxo básico funcionando sem plugin. | Nenhuma. | Não tornar menus, teleporte ou partidas dependentes de StarfinLobby. |
| Utilitários e concorrência | Core contém utilitários de identidade/licenciamento, mensagens próprias e instalação de bibliotecas. | Isolar utilitários BedWars e adaptadores Bukkit; toda mutação de mundo/jogador na main thread e SQL/cálculos fora dela. | Nenhuma utilidade de gameplay a adicionar ao Core. | Evitar depender de classes `core.bukkit`, `core.identity`, `core.remote` ou de loaders internos fora dos contratos públicos. |

**Resumo A–E:** o reaproveitamento confirmado é a verificação de autorização comercial do plugin, se a distribuição exigir isso e a versão do Core for fixada. Perfis, idiomas de jogador, persistência de gameplay, Party, Bans genérico e Lobby devem ser implementados como domínios/adaptadores do BedWars ou de seus respectivos produtos. Não há justificativa no código examinado para adicionar esses sistemas ao Core nesta etapa.

## 6. Arquitetura recomendada para StarfinBedWars

### Princípios

- Dependência unidirecional: domínio e aplicação sem Bukkit, BungeeCord ou dependência de StarfinCore; adaptadores na borda.
- Engine não deve conter limite fixo de quatro integrantes. `playersPerTeam` e capacidade vêm de um ruleset validado; não implementar o modo Maluca agora nem expor uma configuração funcional falsa.
- Toda partida tem identidade, estado/transições explícitas, arena isolada e resultado idempotente. Reconnect de jogador é estado de participação separado do estado global da partida.
- Casos competitivos congelam versão do ruleset e registram eventos/auditoria. Um resultado repetido não pode atualizar rating ou premiação mais de uma vez.
- Integrações opcionais são portas/adaptadores substituíveis; o plugin funciona sem Party, Bans ou Lobby.

### Estrutura Maven sugerida

```text
starfin-bedwars/
├── pom.xml                         # parent/aggregator, Java release 8
├── bedwars-api/                    # contratos públicos, DTOs e eventos do produto
├── bedwars-domain/                 # partidas, arenas, times, rulesets, rating/brackets puros
├── bedwars-application/            # casos de uso e orquestração
├── bedwars-storage/                # SQL, repositories, migrations e caches
├── bedwars-spigot/                 # plugin.yml, listeners, comandos, GUIs e mundos
├── bedwars-integration-core/       # adaptador de autorização StarfinCore
├── bedwars-integrations/            # portas/adaptadores opcionais Party, Bans, Lobby, PAPI
├── bedwars-proxy-bungee/            # módulo futuro; somente após escopo de rede validado
└── bedwars-testkit/                 # fixtures e testes de engine/repositories
```

O plano anexo propõe módulos próximos (`bedwars-api`, `domain`, `application`, `infrastructure`, `spigot`, `proxy-bungee`, `integrations`, `testkit`); a estrutura acima conserva essa separação, mas explicita um adaptador Core. Não é necessário criar todos os módulos antes de haver consumidores: pode-se começar com parent, API, domain, application e Spigot, mantendo as fronteiras de pacote e extraindo storage/integrations quando forem implementados.

### Pacotes Java sugeridos

```text
com.starfinplugins.bedwars.api
com.starfinplugins.bedwars.domain.arena
com.starfinplugins.bedwars.domain.match
com.starfinplugins.bedwars.domain.team
com.starfinplugins.bedwars.domain.rules
com.starfinplugins.bedwars.domain.player
com.starfinplugins.bedwars.application.match
com.starfinplugins.bedwars.application.queue
com.starfinplugins.bedwars.application.reconnect
com.starfinplugins.bedwars.application.ranked
com.starfinplugins.bedwars.application.tournament
com.starfinplugins.bedwars.storage
com.starfinplugins.bedwars.spigot
com.starfinplugins.bedwars.integration.core
com.starfinplugins.bedwars.integration.party
com.starfinplugins.bedwars.integration.bans
com.starfinplugins.bedwars.integration.lobby
com.starfinplugins.bedwars.proxy.bungee
```

Não criar tipos/classes agora: esta é a proposta registrada para a próxima etapa, após autorização.

### Dependências entre módulos

```text
bedwars-domain       <- sem Bukkit, SQL ou Core
bedwars-api          <- contratos independentes do servidor
bedwars-application  -> domain + ports/interfaces
bedwars-storage      -> application ports + JDBC
bedwars-spigot       -> application + domain + Bukkit/Spigot 1.8.8
bedwars-integrations -> ports + APIs opcionais de cada plugin
bedwars-integration-core -> API pública fixada do StarfinCore
bedwars-proxy-bungee -> contratos de rede + Bungee API 1.8
```

Não fazer StarfinCore depender de BedWars. Não sombrear/relocar as classes do Core dentro do JAR BedWars; usar a distribuição oficial compatível e declarar dependência Bukkit apenas se esse for o contrato de instalação aprovado. A compilação da API do Core exige coordenadas Maven publicadas ou um artefato local fixado; o repositório examinado não demonstra uma release Maven estável.

## 7. Estratégia de armazenamento, partidas e rede

- **Standalone:** SQLite; **Network:** MySQL/MariaDB, conforme o modo previsto no anexo. Dados próprios em tabelas `sbw_*`, migrations numeradas e UTC.
- Manter tabelas/ownership do BedWars separadas do Core. Modelar ao menos jogadores, estatísticas, arenas, matches, participantes, eventos, ratings/temporadas, torneios e sessões de reconnect conforme a evolução dos módulos.
- Criar índices/uniqueness para `(match_id, player_uuid)` e uma chave de aplicação idempotente de resultados/rating/recompensas. Rating e resultado devem persistir transacionalmente.
- O servidor que hospeda a arena é autoridade do estado de gameplay; o proxy não decide vencedor com base em mensagem do cliente. Para várias instâncias, usar persistência/leases/versões para reservas e reconnect; Redis, se adotado, é sinalização/coordenação e não histórico durável único.
- SQL, I/O de arquivos, cálculos de bracket e operações potencialmente lentas fora da thread principal. Jogador, mundo, inventário, entidade e bloco somente no thread principal do Bukkit.
- Reconnect da primeira versão deve cobrir queda individual enquanto a instância original continua ativa. O Core não fornece snapshot/recovery de partida; recuperação após crash da instância é outro problema e não deve ser prometida pela simples presença do Core.
- A especificação de DB e tabelas no anexo é proposta do produto, ainda não contrato implementado nem validado.

## 8. Compatibilidade Spigot/Bukkit 1.8.8 e Java 8

- `release 8` e `spigot-api 1.8.8-R0.1-SNAPSHOT` são compatíveis com o alvo declarado; compilar e testar contra a API 1.8.8 real, não apenas contra uma API moderna.
- Evitar APIs de Bukkit/Paper introduzidas depois de 1.8.8 e recursos da linguagem/runtime Java posteriores ao Java 8. Manter NMS/reflexão fora do domínio e atrás de adaptadores versionados, somente quando realmente necessário.
- Validar limitações de protocolo legado em scoreboards, nomes de equipes, títulos/action bar, mensagens e itens; testar prefixos/sufixos de até 16 caracteres conforme APIs de scoreboard 1.8.
- Não executar acesso SQL bloqueante em eventos Bukkit. Corrigir no thread principal apenas o estado de jogo/entidades após validar que a partida e a sessão ainda estão ativas.
- BedWars é plugin Bukkit; o JAR Bungee do Core é somente a contraparte de licenciamento do Core. Ele não substitui um módulo de proxy BedWars nem implementa matchmaking/reconnect entre servidores.

## 9. BungeeCord futuro

Manter o módulo proxy separado (`provided` com Bungee API compatível) e sem dependência do domínio em Bungee. Definir contratos de fila, reserva, transferência e confirmação compartilhados; o proxy roteia jogadores e o backend da arena confirma a partida/estado. Usar armazenamento durável e validação de instância saudável para reconnect; não depender exclusivamente de plugin messaging ligado a um jogador para eventos críticos. O plano prevê NETWORK e Bungee, mas o código do Core apenas confirma que existe um artefato Bungee de licenciamento. Não foi encontrada infraestrutura de rede BedWars pronta para reutilizar.

## 10. Riscos, decisões pendentes e mitigação

| Risco | Evidência/efeito | Mitigação recomendada |
|---|---|---|
| API Core incompatível entre branches | `VerifiedAuthorizationService` existe em `main` e não na `develop` remota padrão. | Fixar SHA/versão do JAR; adicionar teste de integração da API na pipeline BedWars antes da distribuição. |
| Sem release formal | Não há tags/releases; Maven declara `SNAPSHOT`. | Solicitar/definir artefato e política de compatibilidade antes de publicar BedWars. |
| Produto BedWars não registrado | Não encontrado identificador `starfin_bedwars`; `canStart` atual é específico de StarfinBans. | Confirmar o product ID canônico no backend/painel e usar o contrato genérico de autorização; não reaproveitar `canStart`. |
| Requisito de idioma atribuído ao Core sem base | Não há locale por jogador nem API `LanguageService`. | Implementar preferências e renderização por UUID no BedWars. Abrir mudança no Core só se existir decisão de produto transversal. |
| Homologação de licença não demonstrada | README não confirma autorização positiva em produção; testes usam serviço HTTP local de teste. | Validar em ambiente de homologação e confirmar produto/contrato antes de impor bloqueio comercial; não alegar suporte de licenciamento validado ainda. |
| Dependências compartilhadas e classloader | Core baixa/verifica bibliotecas e documenta consumidores `provided`; não oferece serviço DB. | Fixar versão do Core e testar carregamento/desativação em Spigot 1.8.8; não assumir pool ou conexão compartilhados. |
| Distribuição/licença do Core | O Core é infraestrutura de autorização de produtos; o plano afirma que será obrigatório/gratuito, mas isso não é comprovado por uma release comercial. | Confirmar modelo de instalação/licença e comportamento offline com o responsável do produto; não depender da verificação online para manter partida em andamento. |
| Party/Bans/Lobby sem contrato de integração generalizado | Hooks não encontrados no Core; ponte de Bans é hardcoded e não há API de lobby. | Adaptadores opcionais separados, contracts acordados com cada produto e fallback funcional sem eles. |
| Recuperação de partida e consistência competitiva | Core não gerencia partidas, snapshots, transações ou reconnect de gameplay. | Implementar no domínio BedWars e tratar falha da instância separadamente da queda individual. |
| Tamanho de equipe acoplado a presets clássicos | Modos planejados começam em até quatro por equipe. | Capacidade por ruleset desde a engine; não implementar Maluca nem impor constante 4 no domínio. |

## 11. Ordem de implementação recomendada

Esta ordem é uma recomendação arquitetural derivada do plano v1.1; não significa autorização para começar a programar nesta etapa.

1. **Resolver artefato/contrato:** confirmar versão de Core distribuída, API consumível, product ID BedWars e política de autorização; registrar teste de compatibilidade.
2. **Fundação do repositório/build:** parent Maven Java 8, módulos mínimos, CI e testes unitários sem Bukkit no domínio.
3. **Contratos e domínio:** regras/capacidades parametrizadas, estados de arena/partida/time, participante, resultado e eventos; sem limite rígido de quatro.
4. **Persistência/testkit:** SQLite, migrations, repositories e idempotência, antes de gravar estatísticas/rating em jogo.
5. **Adaptador Spigot e engine casual:** arena/template/reset, times, cama, respawn, eliminação, vitória e cleanup com testes em Paper/Spigot 1.8.8.
6. **Reconnect casual:** reserva de sessão por UUID+match, política de combate/expiração, restauração segura e testes de repetição/duplicação antes da primeira versão estável.
7. **Sistemas de jogo/UI:** geradores, loja, upgrades, menus, scoreboards, mensagens/idiomas individuais e estatísticas.
8. **Party e integrações opcionais:** apenas depois de especificar APIs reais de cada produto.
9. **Ranked:** matchmaking e rating por equipes após engine casual estável; transação/idempotência, temporadas e auditoria.
10. **Tournaments:** inscrição/check-in, chave e séries BO1/BO3/BO5 sobre resultados confiáveis de partidas.
11. **Bungee/network:** transferências, coordenação e reconnect distribuído com staging multi-servidor.
12. **Comercialização:** benchmark, instalação limpa, documentação, regras/licenciamento, logs, backups e validação das três línguas.

O modo Maluca permanece fora da implementação: apenas evitar decisões estruturais que impeçam capacidade parametrizada e documentar a modalidade como futura.

## 12. Decisão de integração

**Reutilizar agora, quando houver implementação autorizada:** somente o contrato público de autorização comercial do Core, após pinagem de versão compatível e confirmação do produto BedWars. Tratar o serviço ausente/incompatível explicitamente; não fazer cast para `StarfinCorePlugin` nem usar `canStart` como workaround.

**Implementar no StarfinBedWars:** perfis e preferências por UUID, idioma por jogador, mensagens do produto, configurações, domínio de arenas/partidas, banco/cache do jogo, eventos BedWars, reconnect, Party/Bans/Lobby como adaptadores opcionais, e todos os fluxos de partidas e competição.

**Não solicitar alteração ao StarfinCore nesta etapa:** o código não prova que ele ofereça os recursos ausentes, e esses domínios podem ser independentes. Uma API transversal de preferências/idioma só deve ser proposta se for uma decisão explícita para vários produtos, após contrato e compatibilidade definidos.

## 13. Referências do código que sustentam as conclusões

Todos os caminhos a seguir são relativos à raiz `C:\Users\alanl\Downloads\StarfinCore`.

- `pom.xml` — versão `0.3.1-SNAPSHOT`, Java release 8, Spigot 1.8.8, Bungee API e configuração dos artefatos.
- `src/main/resources/plugin.yml` — nome/versão e classe principal Bukkit.
- `README.md` — escopo de licenciamento, instalação, execução reportada em PaperSpigot 1.8.8, ausência de Velocity e limitações/homologação.
- `docs/SHARED-LIBRARIES.md` — bibliotecas compartilhadas, dependência de Core e configuração `provided` recomendada para consumidores.
- `src/main/java/com/starfinplugins/core/api/AuthorizationService.java` — `get` e `products`.
- `src/main/java/com/starfinplugins/core/api/VerifiedAuthorizationService.java` — protocolo, UUID da instalação e grant assinado.
- `src/main/java/com/starfinplugins/core/api/ProductAuthorization.java` — campos e regra `permitted()`.
- `src/main/java/com/starfinplugins/core/api/AuthorizationState.java` — estados de autorização.
- `src/main/java/com/starfinplugins/core/bukkit/StarfinCorePlugin.java` — `onLoad`, `onEnable`, registro Bukkit do serviço, `onDisable`, `authorizationService`, `signedGrant` e `canStart`.
- `src/main/java/com/starfinplugins/core/remote/InstallationClient.java` — `challenge`, `check` e `download` do protocolo de instalação v3 e validação dos produtos/grants.
- `src/main/java/com/starfinplugins/core/remote/SignedGrant.java` — validação de assinatura e claims de autorização.
- `src/main/java/com/starfinplugins/core/remote/InstallationResult.java` — snapshot imutável dos produtos e dados retornados pelo protocolo.
- `src/main/java/com/starfinplugins/core/state/AuthorizationStore.java` — snapshot local sincronizado, tolerância temporária e sem persistência de gameplay.
- `src/main/java/com/starfinplugins/core/bukkit/ActivationChallengeStore.java` — persistência local de desafio temporário de ativação, não de sessão de jogador.
- `src/main/java/com/starfinplugins/core/bukkit/CoreSettings.java` — `read(...)`, validação do idioma global e opções do Core.
- `src/main/java/com/starfinplugins/core/bukkit/CoreMessages.java` — carga de traduções do Core, `text(...)` e `send(...)`, sem busca de idioma por jogador.
- `src/main/java/com/starfinplugins/core/identity/InstallationIdentity.java` — persistência da identidade da instalação.
- `src/main/java/com/starfinplugins/core/libraries/RuntimeLibraries.java` — obtenção/verificação SHA-256 e adição de JARs ao classloader.
- `src/main/java/com/starfinplugins/core/bungee/StarfinCoreBungeePlugin.java` — contraparte de licenciamento Bungee, sem engine BedWars.

---

**Estado da etapa:** relatório concluído; nenhuma implementação do StarfinBedWars iniciada. Aguarda autorização do usuário para a próxima etapa.
