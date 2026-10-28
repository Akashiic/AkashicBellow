# Below Core

Camadas negativas do mundo (Y < 0) para **Minecraft 1.7.10** (Forge 10.13.4.1614), no estilo 1.18+:
deepslate, piso de bedrock em gradiente, minérios de deepslate com as distribuições da 1.18/1.20
e retrogen para mundos que já existem.

Status: **beta** (`0.1.0-beta.2`). Veja os riscos abertos em [docs/DESIGN.md §11](docs/DESIGN.md#11-riscos-abertos-beta).

## O que faz

* Estende o mundo para baixo até um piso fixo por dimensão (padrão **−64**, configurável de −16 a **−512**).
  O teto continua 256 e nada acima de Y 0 muda de forma.
* Gera o subsolo em chunks novos **e** em chunks salvos antes da instalação (retrogen no carregamento do chunk).
* Com Et Futurum Requiem: deepslate, tufo, cobre e as variantes deepslate dos minérios (inclusive de outros mods
  registrados no `DeepslateOreRegistry` do EFR). Sem EFR: pedra e minérios comuns.
* Minérios gerados pelo porte linha a linha do `OreFeature` da 1.18 com as alturas/contagens da 1.20.1.

O desenho, as decisões e o motivo de não usar CubicChunks estão em [docs/DESIGN.md](docs/DESIGN.md).

## Requisitos

| | versão |
|---|---|
| Forge | 10.13.4.1614 |
| UniMixins | 0.3.1+ (Mixin 0.8.7, MixinExtras 0.5) |
| GTNHLib | 0.11.40+ (TEOF 4.1 usa 0.11.51) |
| ChunkAPI | 0.8.4+ (obrigatório) |

Servidores: Forge e **Crucible** (testado com o build de produção 1.7.10-staging-67b53034; o Bellow corrige o Y que o
CraftBukkit entregava aos plugins). Para WorldEdit, WorldGuard e GriefPreventionPlus abaixo de 0, veja a seção
"Plugins no Crucible" abaixo.

Opcionais com integração: Et Futurum Requiem, EndlessIDs, ArchaicFix (Phosphor), Hodgepodge, Angelica,
IndustrialCraft 2 (Minerador e Magnetizador alcançam a camada profunda) e Galacticraft (foguetes não são destruídos
abaixo de Y 0). Testado no servidor com IC2, Immersive Engineering e Galacticraft: veja `docs/DESIGN.md` §9–§10.
O mod é obrigatório **no servidor e no cliente**.

## Plugins no Crucible (WorldEdit, WorldGuard, GriefPreventionPlus)

* **WorldEdit 6.1.9**: adicione o agent à linha de comando do servidor, junto dos outros agents
  (`-javaagent:AkashicBellow-WorldEdit-6.1.9-<versão>-agent.jar`; build: `./gradlew -p agent build`). Ele só altera o
  WorldEdit 6.1.9 exato (SHA-256 de cada classe) e não toca em nenhum arquivo; no log aparece
  `[AkashicBellow-WE] WorldEdit 6.1.9 verified`. Sem ele, seleções e edições param em Y 0.
* **GriefPreventionPlus**: `GriefPrevention.Claims.MaximumDepth: -64` (o piso) em `GriefPreventionData/config.yml`;
  com 0 os claims não protegem nada abaixo de Y 0.
* **WorldGuard**: regiões antigas que vão de 0 ao topo não cobrem a camada nova. `tools/wgdeep.py <servidor>` lista
  essas regiões (só leitura) com os comandos para estendê-las (`//pos1`, `//pos2`, `/rg redefine`).

Detalhes em `docs/DESIGN.md` §9.1.

## Configuração (`config/akashicbellow.cfg`)

| opção | padrão | |
|---|---|---|
| `general.minY` | −64 | piso (múltiplo de 16). **Travado por mundo** no primeiro uso em `<mundo>/akashicbellow.dat`. Custo medido: +46% de RAM por chunk em −64, 5× em −512 |
| `general.dimensions` | `0` | dimensões com camada profunda |
| `general.spawnMobsInDeep` | true | spawn natural também abaixo de 0 |
| `worldgen.enabled` | true | gerar o subsolo |
| `worldgen.retrogen` | true | preencher chunks antigos |
| `worldgen.replaceLegacyBedrock` / `legacyBedrockMaxY` | true / 4 | trocar a bedrock antiga (Y 0..4) pelo bloco profundo |
| `worldgen.deepBlock` | `auto` | bloco de preenchimento (`auto` = deepslate do EFR ou pedra) |
| `worldgen.ores` | true | minérios, tufo e cascalho abaixo de 0 |
| `worldgen.scaleBottomAnchoredCounts` | true | para pisos diferentes de −64, manter a densidade por volume |
| `worldgen.oreCountMultiplier` | 1.0 | multiplicador global das contagens |

## Instalação num servidor existente

1. **Backup do mundo.**
2. Instale o jar no servidor e em todos os clientes; ajuste `minY` antes do primeiro boot (depois ele fica travado).
3. Os chunks antigos recebem o subsolo à medida que são carregados (não há pré-geração; um pré-gerador comum
   que carregue os chunks também dispara o retrogen). `/bellow info` mostra o piso e o estado de geração do chunk atual,
   `/bellow stats [raio]` o histograma dos blocos abaixo de 0.

Comandos (op): `/bellow info`, `/bellow stats [raio]`, `/bellow selftest`.

Desinstalar: o mundo continua abrindo, mas tudo abaixo de Y 0 é perdido.

## Downloads (GitHub Actions)

Cada push no `main` roda o workflow **Build**, que gera do mesmo commit o mod e o agent do WorldEdit (companion) e
publica os dois como artifacts do mesmo run, sem zip: `AkashicBellow-<versão>.jar` e
`AkashicBellow-WorldEdit-6.1.9-<versão>-agent.jar`, com `<versão>` = `0.1.0-beta.2-<commit>`. O resumo do run lista o
SHA-256 de cada jar.

## Compilar e testar

```bash
./gradlew build                      # jar em build/libs (defina VERSION=... se não houver tag git)
./gradlew test                       # testes unitários
tools/selftest.sh                    # servidor, 3 fases (cria → gera/verifica → relê do disco numa JVM nova)
tools/selftest.sh -Pbellow.compatRun=true          # idem com EFR + EndlessIDs + ArchaicFix + Hodgepodge
tools/clienttest.sh                                # cliente headless (xvfb), renderer vanilla
tools/clienttest.sh -Pbellow.compatRun=true -Pbellow.angelica=true   # cliente com Angelica
tools/crucibletest.sh <cópia do servidor> [fase]   # Crucible real; apaga world/, só roda numa cópia com .bellow-test-copy
./gradlew -p agent build             # agent do WorldEdit em agent/build/libs
./gradlew -p agent test -Pbellow.weJar=<worldedit-bukkit-6.1.9.jar> -Pbellow.serverJar=<server.jar do Crucible>
tools/wgdeep.py <servidor> [--world world] [--floor -64]   # regiões do WorldGuard que param em Y 0 (só leitura)
```

Num servidor com os mods do pack (jar reobfuscado em `mods/`), adicione
`-Dakashicbellow.selftest=true -Dakashicbellow.selftest.exit=true -Dakashicbellow.selftest.phase=<fase>`:
`compat` e depois `compatverify` (sonda de compatibilidade), `bench` (custo por chunk) ou `chunkload` (chunks forçados
por ticket, sem jogadores: TileEntity, ticks aleatórios e entidades abaixo de 0), num mundo novo.

O teste de cliente precisa de `xvfb-run` e EGL/Mesa (`libegl1 libegl-mesa0 libgles2`).
Os relatórios ficam em `run/server/akashicbellow-selftest-*.txt` e `run/client/akashicbellow-clienttest.txt`;
os screenshots em `run/client/screenshots/`.

## API

`com.akashiic.bellow.api.BellowAPI` (`API_VERSION = 2`):

* Leitura: `getMinY(world)`, `hasDeepLayers(world)`, `getDeepSection(chunk, sectionY)`, para mods que iteram seções
  diretamente e querem enxergar o subsolo.
* **Geradores profundos** (cavernas, aquíferos, estruturas): `registerGenerator(id, IDeepGenerator)` até o postInit.
  O gerador recebe cada chunk novo logo depois do preenchimento e antes dos minérios (`generateTerrain`), a região
  3×3 depois dos minérios (`decorate(IDeepRegion)`) e o chunk já marcado como decorado (`onDecorated`). Chunks cujo
  subsolo foi gerado antes de o gerador ser instalado nunca são entregues a ele. Contrato completo no javadoc de
  `IDeepGenerator` e em [docs/DESIGN.md §5.1](docs/DESIGN.md#51-geradores-profundos-api).
* `resendDeepSections(world, chunk, slotMask)`: reenvia seções escritas diretamente aos jogadores que já receberam o
  chunk.

Para depender da API, declare `required-after:akashicbellow` e confira `BellowAPI.API_VERSION >= 2` em tempo de
execução (a versão do mod carrega o sufixo do commit nos builds de CI).
