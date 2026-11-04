# Ambiente isolado de homologação — Fase 7.3

## Estado instalado em 2026-11-04

O laboratório está em `C:\Users\alanl\Documents\StarfinPlugins-workspace\StarfinBedWars-phase73-lab`, fora dos checkouts Git e de qualquer instância Pterodactyl. A entrada Minecraft é `127.0.0.1:45577`; o MySQL de laboratório é publicado apenas em `127.0.0.1:33308`. Os únicos volumes Docker usados têm projeto `sbw-phase73-lab` e `sbw-phase73-mysql-data`. O schema integrado é `starfin_phase73`; as suítes usam schemas descartáveis separados. Senhas ficam em `.secrets/` no laboratório, não neste repositório.

| Nó | Runtime | Plugins principais | Rede |
| --- | --- | --- | --- |
| proxy | BungeeCord 26.1, Java 17 | StarfinLogin, StarfinSocial, StarfinMinigames; StarfinCore gerado pelo Login | Entrada somente loopback, redes edge/backend |
| lobby | PaperSpigot 1.8.8, Java 8 | StarfinCore, StarfinLogin, StarfinGuard, StarfinSocial, StarfinMinigames | backend interna |
| bedwars-01 | PaperSpigot 1.8.8, Java 8 | StarfinCore, StarfinGuard, StarfinSocial, StarfinMinigames, StarfinBedWars, Vault, EssentialsX Economy | backend interna |
| bedwars-02 | Igual a bedwars-01 | Cópia independente da arena `1` e do template Speedway | backend interna |
| mysql | MySQL 8.0.46 | Dados de homologação | backend + edge; host somente loopback |

O BungeeCord disponível foi compilado para Java 11; por isso roda em Java 17. Todas as JARs Bukkit e as suítes Maven são Java 8. `probe_lab.py` confirmou protocolo 47, que corresponde ao cliente 1.8.x. O `manifest.json` dentro do laboratório registra os JARs de preparação; `capture-evidence.ps1` registra também `runtime-jars.json`, incluindo o StarfinCore gerado automaticamente pelo proxy. Uma nova compilação não substitui automaticamente as JARs carregadas: capture novo inventário e reinicie apenas entre sessões de teste.

## Segurança e dados

- O proxy usa `ip_forward: true`; os PaperSpigot usam `bungeecord: true` e só escutam no loopback interno do próprio contêiner. Cada backend tem um StarfinGuard em `25566`, segredo exclusivo, lista do IP do proxy e verificação de autenticação para os nós BedWars. O lobby autentica pelo StarfinLogin. O proxy mapeia `lobby`, `bedwars-01` e `bedwars-02` para as portas Guard na rede Docker interna.
- Somente as portas locais 45577 e 33308 são publicadas. O MySQL usa senha exclusiva e TLS `PREFERRED` nos plugins que aceitam esse modo; o BedWars usa `REQUIRED`. Para produção, usar certificados verificados e política de identidade apropriada.
- A arena `1`, o mundo `Speedway` e o template vieram de cópia **somente leitura** de um volume de teste anterior. O original não foi modificado. O `group: casual` foi aplicado apenas às cópias do laboratório. Nenhum arquivo de mundo fictício foi criado.
- `ranked.yml`, `seasons.yml` e `tournaments.yml` permanecem desligados. Não há ainda arena válida Dupla, Trio, Quarteto, `ranked` ou `tournament`. Não renomear a arena Solo para simular capacidade que ela não possui.
- O StarfinCore do laboratório informa validação de licença pendente; StarfinBedWars e APIs iniciaram, mas qualquer recurso dependente da autorização remota precisa de verificação manual antes da homologação. O StarfinLogin Bungee identifica seu coordenador como experimental.

## Reprodução controlada

Os scripts ficam em `homologation/phase-7-3/` e aceitam caminho absoluto. `prepare_lab.py` exige `inputs/` e `.secrets/` já copiados para um diretório externo e recusa sobrescrever nós existentes. `inputs/` deve conter `BungeeCord.jar`, `server.jar`, `StarfinCore-Spigot.jar`, `Vault.jar`, `EssentialsX-2.19.3.jar`, `spigot.yml`, `arena-1.yml`, `Speedway/`, `arena-template-1/`, `paper-cache/` e `core-libraries/`; estes arquivos são obtidos de backups de teste autorizados, nunca de produção automaticamente. `.secrets/mysql-root.txt` e `.secrets/mysql-lab.txt` contêm senhas aleatórias exclusivas, com pelo menos 32 caracteres. O laboratório atual já está preparado.

```powershell
$lab = 'C:\Users\alanl\Documents\StarfinPlugins-workspace\StarfinBedWars-phase73-lab'
python .\homologation\phase-7-3\probe_lab.py
.\homologation\phase-7-3\diagnose-lab.ps1 -LabPath $lab
.\homologation\phase-7-3\capture-evidence.ps1 -LabPath $lab -ConfirmLab
.\homologation\phase-7-3\run-mysql-gate.ps1 -LabPath $lab -JavaHome 'C:\caminho\para\jdk-8' -ConfirmLab
.\homologation\phase-7-3\stop-lab.ps1 -LabPath $lab -ConfirmLab
.\homologation\phase-7-3\start-lab.ps1 -LabPath $lab -ConfirmLab
```

`start-lab.ps1`, `stop-lab.ps1`, `run-mysql-gate.ps1` e `capture-evidence.ps1` mostram o destino e exigem `-ConfirmLab`. O diagnóstico e o probe são somente leitura. Em Linux, com o mesmo diretório do laboratório e Docker Compose: `docker compose -p sbw-phase73-lab -f /caminho/compose.yml ps` é diagnóstico; `up -d`/`down` mudam estado e devem ser executados apenas após conferir projeto e destino. O arquivo Compose gerado usa caminhos relativos ao laboratório.

## Identificadores e parâmetros

`StarfinMinigames` no proxy usa `network-id: phase73-lab`, `lobby-server: lobby`, mapeamento `bedwars-01`→`bedwars-01` e `bedwars-02`→`bedwars-02`, mesmo schema MySQL que os backends. `StarfinMinigames` Spigot e `StarfinBedWars/network.yml` usam IDs únicos iguais aos nomes de servidor. `instance-id` é gerado por inicialização e publicado no catálogo; não deve ser copiado entre backends. O heartbeat é de 5 segundos e o proxy ignora registros com mais de 30 segundos. Reservas casuais têm prazo de 15 segundos; a Party usa janela de admissão de 20 segundos. Os grupos `ranked` e `tournament` ficam exclusivos. A política competitiva e de temporada permanece desativada até arenas e clientes existirem.

## Preparação de novas arenas reais

Dentro do cliente 1.8.8, em uma cópia de edição do próprio laboratório, use `/sbw arena edit <id>` ou crie a arena pelo assistente, confirme POS1/POS2 com `/sbw setarena ... confirm`, selecione `/sbw setup mode equipe` e `/sbw setup team-size <2..4>` conforme a modalidade. Configure duas ou mais equipes reais com `/sbw arena team <id> <equipe> <nome> <cor-hex> <capacidade>`, seus `spawn`, `respawn` e `bed` com `/sbw arena set <id> ...`, geradores/lojas e regiões. Execute `/sbw arena validate <id>`, `/sbw arena edit publish <id>` e `/sbw arena group <id> casual|ranked|tournament`, seguido de `/sbw arena enable <id>`. Confirme no MySQL que o catálogo publica modalidade, grupo e capacidade corretos antes de ativar filas. Para torneios simultâneos, prepare mais de uma arena `tournament` independente. Consulte o README para a sequência completa do assistente; não reutilize a mesma instância entre jogos.

## Evidências e limpeza

`capture-evidence.ps1` cria uma pasta datada em `<lab>\evidence\`, com manifesto, estado dos contêineres, ping do protocolo 47, resumo do catálogo e pastas para capturas/vídeos. Os logs brutos permanecem em cada nó do laboratório; revise antes de compartilhar. O volume MySQL é preservado ao parar o Compose. Nunca use `down -v` ou limpeza recursiva sobre o laboratório ou sobre servidores Pterodactyl sem um plano separado.
