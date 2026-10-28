# Akashic Bellow — Arquitetura

> Backport das camadas negativas (Y < 0) para Minecraft 1.7.10 (Forge 10.13.4.1614),
> desenhado para conviver com um modpack pesado (TEOF 4.1: Crucible, ChunkAPI, EndlessIDs,
> ArchaicFix/Phosphor, Hodgepodge, Angelica/Celeritas, Et Futurum Requiem).

## 1. Por que não CubicChunks

O CubicChunks troca o modelo de armazenamento inteiro (coluna → cubos 16³, mundo infinito na vertical,
geradores "cúbicos", camada de compatibilidade "vanilla generator"). Cada mod que toca chunks,
luz, pacotes ou renderização passa a ver um mundo com outra forma. O histórico dev.1→dev.7 do fork
mostra exatamente isso: corrigir GC de colunas, pacotes JoinGame/Respawn, rastreadores do Angelica,
sessões Bukkit, regeneração de cubos dentro de chunks vanilla… e ainda sobra comportamento aberto.

O objetivo aqui é menor e por isso pode ser mais seguro: **um piso fixo** (`minY`, padrão −64, até −512),
teto vanilla (256) intocado, e **nenhuma mudança no caminho Y ≥ 0**.

## 2. Princípio central: *Deep Path Isolation*

1. A coluna vanilla (`Chunk.storageArrays`, 16 seções, índices 0..15) **não muda de tamanho nem de significado**.
   Centenas de mods fazem `getBlockStorageArray()[y >> 4]` e iteram 0..15 — todos continuam corretos;
   eles apenas não enxergam o subsolo profundo (degradação graciosa, nunca corrupção).
2. As seções profundas ficam num array separado por chunk com 32 posições fixas:
   `slot = (y >> 4) + 32` para `y ∈ [−512, −1]` (`DeepConstants.slot`). Leituras respeitam o piso do mundo.
   São `ExtendedBlockStorage` reais, então EndlessIDs (que estende a EBS via mixin) funciona nelas sem código extra.
3. Os pontos de entrada com Y são desviados **somente quando `minY ≤ y < 0`**:
   * `World`/`ChunkCache`: a guarda vanilla `y < 0` / `y >= 0` é reescrita por expressão (MixinExtras
     `@ModifyExpressionValue` + `@Expression`) para `y < minY`. Abaixo do piso o comportamento é o vanilla.
   * `Chunk`: o acesso `storageArrays[y >> 4]` é redirecionado (`@Redirect` de `array=get`) para o array profundo
     quando `y < 0`. Seções profundas são criadas antes da escrita por um inject no HEAD (`ensureDeepSection`),
     e não por redirecionar o `array=set` (o idioma `DUP_X2` do javac produzia `VerifyError`).
   * TileEntity (`func_150812_a`): inject no HEAD com prioridade 900, antes da checagem fatal do Hodgepodge.
   O método original (ou o `@Overwrite` de outro mod) roda intocado para Y ≥ 0.
4. Todo ponto de injeção é `require = 1` (vários com `allow = 1`): se uma versão diferente de um mod mudar o alvo,
   o jogo **falha no boot** — nunca roda silenciosamente corrompendo o mundo.
5. Invariante: **o heightmap nunca fica negativo**. Céu nunca "enxerga" Y < 0 por heightmap; a luz do céu entra
   no subsolo só por propagação (BFS). Custo: um poço aberto até Y −30 recebe luz decaindo 1/bloco a partir de Y 0
   em vez de 15 cheio. Benefício: zero risco para mods que usam heightmap como índice ou laço `y > 0`.

## 3. Persistência

* **NBT por chunk** via `ChunkAPI ChunkDataManager` (`akashicbellow:deep`), nunca na lista vanilla `Sections`
  (o leitor do ChunkAPI/vanilla faria `array[Y]` com Y negativo → exceção → chunk regenerado).
  ```
  akashicbellow: { deep: { V: 1, Gen: byte, Sections: [ {Y: byte(com sinal), <managers do ChunkAPI>} ] } }
  ```
  Cada seção é serializada por `DataRegistryImpl.writeSubChunkToNBT`, ou seja, pelos mesmos managers que gravam as
  seções vanilla (inclusive os do EndlessIDs) — formato idêntico, IDs estendidos preservados.
* **Tile entities profundas** ficam fora da lista vanilla `Level.TileEntities`: no `ChunkDataEvent.Save` elas vão para
  `Level.AkashicBellowDeepTiles`, e voltam para a lista no início de `AnvilChunkLoader.loadEntities` (caminho síncrono
  e assíncrono). Sem isso, um TE com Y < 0 na lista vanilla derruba o carregamento do chunk sem o mod
  (`storageArrays[-1]`, ou a checagem do Hodgepodge). Chunks gravados por versões anteriores carregam igual e são
  convertidos no próximo save.
* **Versão do formato**: `V` é lido; dados profundos de um formato mais novo (downgrade do mod) não são carregados,
  ficam intactos e são regravados como estavam.
* **Trava por mundo**: o `minY` efetivo de cada dimensão é gravado em `<mundo>/akashicbellow.dat` (mapa `Floors`,
  escrita atômica) no primeiro uso. Mudar a config depois não altera mundos existentes, e uma dimensão travada que
  sai da lista `dimensions` continua com o seu piso (com aviso no log): voltar ao piso 0 apagaria TEs e entidades
  profundas no próximo save. Arquivo corrompido → o servidor recusa subir (melhor que adivinhar um piso e perder
  dados).
* **Desinstalação**: vanilla/ChunkAPI ignoram as tags; o mundo carrega, perdendo o conteúdo abaixo de 0 (entidades
  que estavam lá caem no vazio). A bedrock legada que foi substituída (ver §5) não volta.

## 4. Rede

* Mod obrigatório nos dois lados.
* `ChunkAPI PacketDataManager` (`akashicbellow:deep`) anexa as seções profundas a S21/S26:
  `short minY, int slotMask, depois para cada bit: int len + bloco de DataRegistryImpl.writeToBufferCubic`
  (os mesmos managers "cúbicos" do ChunkAPI/EndlessIDs). Pacote de descarregar e chunks sem dados profundos: vazio.
* Mudanças de bloco em Y < 0 não entram no `PlayerInstance` vanilla (que empacota Y em 8 bits). Fila própria:
  < limiar de agrupamento (Forge `clumpingThreshold`) → S23 individuais (o ChunkAPI já serializa a posição do S23
  num `long` com Y de 12 bits com sinal); ≥ limiar → S21 parcial só com as seções profundas tocadas, mais os
  pacotes de descrição das TileEntities.
* Pacotes vanilla com Y em byte sem sinal são alargados para `short` com sinal:
  `C07PacketPlayerDigging`, `C08PacketPlayerBlockPlacement`, `S28PacketEffect`, `S0APacketUseBed`.
  Auditados e já seguros: S23 (ChunkAPI), S24/S35/S33/C12 (`short`), S25/S29/S36/S0E/S10 (`int`),
  S27 (deslocamentos relativos). S22 nunca é usado para Y < 0.
* `DimensionInfoMessage` (canal `akashicbellow`) informa o `minY` da dimensão no login, troca de dimensão e respawn.
  O handler FML roda na thread do netty, então só registra o valor; ele é aplicado no tick do cliente.
* **Cliente**: ao receber seções profundas, as TileEntities delas são criadas na hora (como o Angelica faz para
  Y ≥ 0 no fim de `fillChunk`). O vanilla as criaria preguiçosamente, mas o meshing do Angelica lê um snapshot do
  mapa e proíbe criação nas threads de trabalho — sem isso baús e outros TESR abaixo de 0 não renderizavam.

## 5. Geração — terreno e retrogen

Estado por chunk (`Gen`): `0 NONE → 1 TERRAIN → 2 DECORATED`.

**TERRAIN** (em `Chunk.onChunkLoad`, servidor, dimensões habilitadas — cobre carga síncrona, assíncrona e geração):
* `minY ≤ y < 0`: bloco profundo (`etfuturum:deepslate` se existir, senão `minecraft:stone`; configurável).
  Nunca sobrescreve um bloco não-ar.
* Piso de bedrock no estilo 1.18 (`verticalGradient("bedrock_floor", aboveBottom(0), aboveBottom(5))`):
  `P(bedrock | y) = clamp(1 − (y − minY)/5, 0, 1)` → 100%, 80%, 60%, 40%, 20%, 0% em minY..minY+5.
  Sorteio **posicional e sem estado** (SplitMix64 de `seed ⊕ pos`), então o resultado não depende da ordem em que
  os chunks são gerados. Medido: 0.810 / 0.597 / 0.387 / 0.194 (esperado 0.8 / 0.6 / 0.4 / 0.2).
* **Bedrock legada** (Y 0..4, só `Blocks.bedrock`) → bloco profundo, em chunks novos e antigos (configurável),
  abrindo passagem para baixo como a 1.18 fez. Nada mais acima de Y 0 é tocado em chunks antigos.
* Retrogen: num mundo já explorado, **todo chunk antigo recebe o subsolo quando é carregado** — não há "lugar sem
  geração", porque o terreno profundo é por chunk e não depende de vizinhos.

**DECORATED** (minérios/tufo/cascalho): roda para o chunk C quando C e os 8 vizinhos estão carregados com TERRAIN.
Vizinhança 3×3 (como a 1.18), nunca carrega chunk (sem cascata), escreve direto nas seções (sem update de luz:
pedra opaca → minério opaco, luz 0 → 0). Chunks já vistos por jogadores recebem reenvio das seções alteradas.
O intervalo Y ≥ 0 continua sendo da geração vanilla/modded 1.7.10 (sem dobrar o balanceamento do pack); os
recursos são **cortados em Y < 0**.

**Fase de população legada.** Os geradores da 1.7.10 (decorador vanilla e todo `IWorldGenerator` de mod) foram
escritos para um mundo que termina em Y 0: veios e manchas sorteados perto do fundo contavam com a escrita abaixo de 0
ser ignorada. Com o pack inteiro (Mekanism, IndustrialUpgrade, Eln, fósseis, IC2, GC…) isso vazava centenas de minérios
de pedra para Y −1..−16, dentro do deepslate. Enquanto `Chunk.populateChunk` roda (delimitado com `@WrapMethod`,
`try/finally`, aninhamento contado para cascatas), `setBlock`/`setBlockMetadataWithNotify` abaixo de Y 0 mantêm o
comportamento da 1.7.10 (recusados). A geração profunda escreve direto nas seções e não é afetada.

Os avisos "cascading worldgen" do ArchaicFix vistos nos testes aparecem também com a nossa geração desligada
(36 na fase bootstrap do teste) e são emitidos dentro do `populate` vanilla — não vêm deste mod.

## 6. Minérios — porte fiel da 1.18/1.20

`OreFeature.place/doPlace` portado linha a linha (segmento elipsoidal com `size` esferas, eliminação de esferas
contidas, `BitSet` de visita, `discardChanceOnAirExposure`), mais `UniformHeight`, `TrapezoidHeight`
(`i + U[0,m] + U[0,l]`), `CountPlacement`, `InSquare`, `RarityFilter`. Testes unitários verificam a distribuição
das alturas (qui-quadrado) e a forma dos veios.

Alvo: pedra (`stone`) → minério comum; deepslate/tufo → variante deepslate via
`ganymedes01.etfuturum.api.DeepslateOreRegistry` (cobre também minérios de outros mods registrados no EFR).
Sem EFR: minério comum.

| feature (1.20.1) | count | size | discard | altura | parte gerada |
|---|---|---|---|---|---|
| ore_gravel | 14 | 33 | 0 | uniform(bottom, top) | Y<0 |
| ore_tuff | 2 | 64 | 0 | uniform(bottom, 0) | toda |
| ore_iron_middle | 10 | 9 | 0 | trapezoid(−24, 56) | Y<0 |
| ore_iron_small | 10 | 4 | 0 | uniform(bottom, 72) | Y<0 |
| ore_gold (buried) | 4 | 9 | 0.5 | trapezoid(−64, 32) | Y<0 |
| ore_gold_lower | U[0,1] | 9 | 0.5 | uniform(−64, −48) | toda |
| ore_redstone | 4 | 8 | 0 | uniform(bottom, 15) | Y<0 |
| ore_redstone_lower | 8 | 8 | 0 | trapezoid(aboveBottom(−32), aboveBottom(32)) | toda |
| ore_diamond (small) | 7 | 4 | 0.5 | trapezoid(aboveBottom(−80), aboveBottom(80)) | toda |
| ore_diamond_medium | 2 | 8 | 0.5 | uniform(−64, −4) | toda |
| ore_diamond_large | 1/9 | 12 | 0.7 | trapezoid(aboveBottom(−80), aboveBottom(80)) | toda |
| ore_diamond_buried | 4 | 8 | 1.0 | trapezoid(aboveBottom(−80), aboveBottom(80)) | toda |
| ore_lapis | 2 | 7 | 0 | trapezoid(−32, 32) | Y<0 |
| ore_lapis_buried | 4 | 7 | 1.0 | uniform(bottom, 64) | Y<0 |
| ore_copper (EFR) | 16 | 10 | 0 | trapezoid(−16, 112) | Y<0 |
| ore_emerald (montanhas) | 100 | 3 | 0 | trapezoid(−16, 480) | Y<0 |

Com `minY = −64` os parâmetros são exatamente os da 1.20.1. Para outros pisos, âncoras `aboveBottom` acompanham o
piso, âncoras absolutas ficam, e features `uniform(bottom, X)` podem ter a contagem escalada por
`(X − minY + 1)/(X + 64 + 1)`: mais tentativas abaixo de −64, menos acima, com a densidade por volume preservada e o
arredondamento estocástico preservando a esperança. A âncora `top` é a `top()` da 1.18 (Y 319). Como na 1.18, toda
origem sorteada é colocada e só os blocos são cortados (a camada profunda escreve só abaixo de 0); por isso os veios
com origem em Y 0..+6 alcançam a faixa −1..−5.

Medido no teste de servidor (121 chunks, piso −64): ≈27 diamantes/chunk abaixo de 0, distribuição de redstone
crescendo para o fundo, ferro concentrado perto de 0, cobre só no topo da faixa — o perfil da 1.18.

## 7. Luz

* **Vanilla**: o BFS de `World.updateLightByType` usa coordenadas relativas; com os desvios de
  `getSavedLightValue / setLightValue / getBlock / getBlockLightOpacity` e `checkChunksExist` ele atravessa Y=0 sem costura.
* **ArchaicFix Phosphor** (ativo no TEOF): o motor dele empacota Y em 8 bits e indexa
  `getBlockStorageArray()[y >> 4]`. Em mundos com camada profunda, o campo `lightingEngine` que o ArchaicFix adiciona
  ao `World` recebe um `DeepPhosphorEngine` (mesmo algoritmo do Phosphor, Y codificado como `y + 512` em 10 bits,
  bits 52..61). A troca é feita no `WorldEvent.Load` (todas as variantes de construtor de `World`, inclusive o
  servidor integrado), drenando antes as filas do motor antigo e limpando o cache de motor por chunk.
* Detecção do Phosphor no coremod: classe do motor do ArchaicFix presente, Supernova ausente e
  `B:enablePhosphor` não desligado em `config/archaicfix.cfg`.

## 8. Cliente / renderização

* Renderer vanilla (`RenderGlobal`): grade vertical `16 + N` seções com deslocamento, `ChunkCache` com piso `minY`.
  Aplicado só sem Angelica.
* **Angelica/Celeritas**: o Celeritas já trabalha com `minSection/maxSection`. Mixins tardios (`@Pseudo`, só quando o
  mod está carregado) ajustam `AngelicaRenderSectionManager` (minSection, `isSectionVisuallyEmpty`),
  `getMinimumBuildHeight`, `ClonedChunkSection` (limite vertical e leitura da seção), o `WorldSlice` (seções
  profundas e piso da luz) e `CompatMathHelper.clampHeight`.
* Neblina do vazio e partículas de profundidade deslocadas por `minY`.

## 9. Compatibilidade conhecida

| mod | problema | tratamento |
|---|---|---|
| ChunkAPI 0.8.x | dono do NBT/pacotes | dependência obrigatória; managers próprios |
| EndlessIDs | formato de EBS | seções profundas são EBS reais; serialização pelos managers |
| ArchaicFix (Phosphor) | Y em 8 bits | `DeepPhosphorEngine` instalado por mundo |
| Hodgepodge | `MixinChunk` lança erro fatal para TileEntity em Y<0; remove a lista de chunks do cliente | mixin de TE com prioridade 900 resolve antes; instalador de luz tolera lista nula |
| Angelica | min/max de seção fixos; TEs criadas só para Y≥0 | mixins tardios; TEs profundas criadas na recepção |
| EFR | deepslate/minérios | integração por reflexão; camada EFR (Y 0..22) continua dele |
| Crucible/Spigot | `activeChunkSet` limpo no fim de `func_147456_g` | ticks aleatórios profundos injetados depois da chamada a `super.func_147456_g()` |
| Crucible: pistão | o corpo de `canExtend` vai para `canExtend_IntCB` (CraftBukkit devolve o tamanho do empurrão) | variante do mixin escolhida no coremod ao detectar o CraftBukkit |
| Crucible: `PlayerManager` | `chunkWatcherWithPlayers` vira `ConcurrentLinkedQueue` | fila acessada por `IDeepPlayerManager`, com uma implementação para Forge e outra para Crucible |
| Crucible: CraftBukkit | `CraftWorld.getBlockAt`, `CraftChunk.getBlock` e `CraftBlock` fazem `y & 0xFF`: Y −10 virava 246 para todo plugin e todo evento Bukkit | abaixo de 0 a máscara vira −1 (`y & −1 == y`); 0..255 inalterado |
| IC2 2.2.828 | Minerador para em Y 0 (5 comparações com 0); Magnetizador para em Y > 0 | mixins tardios: limites trocados pelo piso do mundo |
| Galacticraft 4.0 | foguetes (`EntitySpaceshipBase`) são destruídos no servidor se `posY < 0` | mixin tardio: só abaixo do piso |
| Crucible: colisão | o Spigot reescreve `getCollidingBoundingBoxes` e começa o Y em `max(k - 1, 0)`: nada colidia abaixo de 0 no servidor (itens, mobs e carrinhos caíam pelo deepslate) | mixin só no Crucible: teste e limite usam o piso |
| Crucible: blocos que caem | o CraftBukkit exige `j > 0` para o pouso: areia e cascalho viravam item abaixo de 0 | mixin só no Crucible: `j > piso` |
| Vanilla: TEs ao enviar chunks | `func_147486_a(x, 0, z, …, 256, …)` coleta os pacotes de descrição só de Y ≥ 0 (placas, spawners, máquinas profundas ficavam no estado padrão no cliente) | limite inferior = piso |
| Vanilla: portais | o `BlockPortal$Size` só desce `while (y > 0)` e o `Teleporter` procura e cria portais de cima até Y 0 | piso nos dois (variante do `Teleporter` para o Crucible, que move as buscas para `findPortal`/`createPortal`) |
| Vanilla/Forge: outros | Enderman só teleporta com `j > 0`; fluidos finitos do Forge somem em `y < 0`; entidades abaixo de 0 saíam e voltavam para o chunk a cada tick (`EnteringChunk` a cada tick) | piso nos testes; linha de entidade limitada a 0 como no `Chunk.addEntity` |
| Cliente vanilla (sem Angelica) | o `RenderGlobal` reserva listas/queries para 16 seções de altura; a grade mais alta estourava em distâncias de renderização altas | reserva para 16 + 32 seções |

Os limites de IC2/GC foram encontrados por varredura de bytecode (comparações de `yCoord`/`posY` com 0 e acessos
diretos a `getBlockStorageArray`) e confirmados pela sonda de compatibilidade (§10), que os reprova sem os patches.
Ocorrências restantes da varredura são inofensivas para uma base profunda: coletor de oxigênio do GC (só conta folhas
em Y ≥ 0), entidades e geradores de dimensões espaciais (sem camada profunda), e falsos positivos.

### 9.1 Plugins Bukkit (servidor de produção)

O Crucible carrega as classes de plugin fora dos transformers do Forge: mixins não as alcançam (o
`AkashicWorldEditEndlessIDsCompat` do pack reescreve o jar do WorldEdit justamente por isso). O Bellow usa um Java
agent, que não altera nenhum arquivo do servidor.

| plugin | situação abaixo de 0 | caminho |
|---|---|---|
| GriefPreventionPlus 13.9 | claims 2D cortados na profundidade global `GriefPrevention.Claims.MaximumDepth` (0 no pack): nada abaixo de 0 é protegido. Os três usos do valor (proteção, baú do auto-claim, pontuação de investimento) só leem a configuração | configuração: `MaximumDepth: -64` (o piso) |
| WorldEdit 6.1.9 | seleções limitadas a Y ≥ 0, `EditSession` recusa Y < 0, máscaras e navegação param em 0 | agent `AkashicBellow-WorldEdit-6.1.9-*-agent.jar` |
| WorldGuard 6.2 | as consultas usam o Y real (correto com o Y corrigido do CraftBukkit); regiões novas vêm das seleções do WorldEdit; as existentes que vão de 0 ao topo não cobrem a camada nova | agent do WorldEdit + `tools/wgdeep.py` para listar as regiões a estender |

**Agent do WorldEdit** (`agent/`, build separado: `./gradlew -p agent build`). Entra na linha de comando como os
outros agents do pack (`-javaagent:AkashicBellow-WorldEdit-6.1.9-<versão>-agent.jar`).

* **Contrato com o mod**: no Crucible, o Bellow publica o piso de cada mundo como a propriedade de sistema
  `akashicbellow.floor.<nome do mundo Bukkit>` (em `WorldEvent.Load`). O código injetado lê só essa propriedade
  (`Integer.getInteger`), sem tocar em classes do mod, que o class loader do plugin não enxerga. Sem o mod, ou num
  mundo sem camada profunda, a propriedade não existe e o WorldEdit mantém o piso 0.
* **Tudo ou nada**: quando a primeira classe-alvo é definida, o agent calcula o SHA-256 de todas as 11 classes-alvo
  dentro do jar de onde ela veio. Se qualquer uma diferir do WorldEdit 6.1.9, nenhuma é alterada (um WorldEdit meio
  corrigido selecionaria abaixo de 0 e depois recusaria editar ali). O patch do AkashicWE-EID só reescreve
  `BaseBlock`, que não é alvo, então os hashes valem para o jar já corrigido por ele.
* **Classes já transformadas**: no Crucible, o class loader de plugins roda o remapeador SpecialSource
  (`remap-guava: true` no `cauldron.yml` troca `com/google/common` por `guava10/com/google/common`) antes dos agents,
  então os bytes que chegam ao agent não têm o hash do jar (medido: todas as classes carregadas; o código é idêntico
  ao do jar fora o nome do Guava e a ordem dos atributos). A identidade fica com o jar verificado; em cada classe, cada ponto
  ainda precisa casar exatamente, com a contagem esperada. O log diz `already transformed upstream` nesses casos, e
  `-Dakashicbellow.we.dump=<dir>` grava os bytes recebidos para diagnóstico.
* **Edições**: cada ponto é um padrão exato de instruções com contagem esperada; qualquer divergência rejeita a classe
  e mantém o bytecode original. As edições só trocam a constante 0 (ou 1) pelo piso, ou comparam com o piso em vez de
  com 0, sem novos desvios, de modo que os stack map frames continuam válidos. Pontos alterados:
  `DataValidatorExtent` (todo bloco escrito; piso lido uma vez por `EditSession`), `CuboidRegion` e
  `Polygonal2DRegion` (limites da seleção), `AbstractWorld.getMinimumPoint` (cilindro e esfera),
  `Polygonal2DSelection`/`CylinderSelection` (API Bukkit usada por outros plugins), `//chunk`, `//expand vert`,
  `//removebelow`, `//fill`, `//drain`, `//fixwater`/`//fixlava`, `//cyl`, o traço de mira (`/jumpto`, ferramentas)
  e a navegação (`/ascend`, `/descend`, `/ceil`, `/up`, `/unstuck`).
* **Fora do escopo, de propósito**: `//regen` (copia Y 0..255), restauração de snapshots (seções Anvil 0..15) e os
  laços de neve/grama/degelo, que descem a partir da superfície.
* **Testes**: `./gradlew -p agent test -Pbellow.weJar=<jar do WorldEdit> -Pbellow.serverJar=<server.jar>` aplica
  o patch às 11 classes com a contagem de pontos esperada (também depois do remapeamento do Guava do Crucible), sobe
  uma JVM com o agent para linkar todas
  (verificador de bytecode da JVM) e conferir seleção e validação no piso e abaixo dele, e confere que um jar com
  uma única classe diferente fica intocado.

**GriefPreventionPlus com `CreationRequiresWorldGuardBuildPermission: true`** (desligado no pack): o teste de
sobreposição com o WorldGuard monta uma região de Y 0 ao topo, então não enxerga uma região do WorldGuard que fique
inteira abaixo de 0. Não afeta a proteção dos claims.

## 10. Testes

* **Unitários** (`./gradlew test`): alturas (qui-quadrado), forma dos veios, contagem esperada, empacotamento da luz,
  constantes, gradiente da bedrock — 19 testes.
* **Servidor, 3 fases em JVMs separadas** (`tools/selftest.sh [-Pbellow.compatRun=true]`): cria o mundo com a
  geração desligada (chunks "legados"), religa e verifica retrogen, terreno, blocos/meta/TileEntity, luz, minérios;
  depois uma JVM nova relê do disco. Roda sem mods e com EFR + EndlessIDs + ArchaicFix + Hodgepodge.
* **Sonda de compatibilidade com mods reais** (`-Dakashicbellow.selftest.phase=compat`, depois `compatverify` numa JVM
  nova; servidor de produção com o jar reobfuscado): coloca **todo bloco com TileEntity de todos os mods** (cada meta),
  toda entidade registrada e todo multibloco do Immersive Engineering na camada profunda e numa altura de controle com
  vizinhança idêntica; roda 1200 ticks; testa funcionalmente o Minerador e o Magnetizador do IC2; relê do disco.
  Só diferenças entre profundo e controle contam. Resultado com IC2 + IE + Galacticraft + EFR + EndlessIDs +
  Hodgepodge + NEI: 2059/2059 TileEntities iguais, 79/79 entidades (incluindo foguetes T1/T2/T3/carga), 14/18
  multiblocos do IE formados nas duas alturas (os outros 4 não formam pela sonda em nenhuma altura), 1691/1830
  TileEntities relidas byte a byte e as demais 139 mudam também no controle (estado que o próprio mod não salva,
  ex.: `growthPoints` das plantações do IC2). Sem os patches de IC2/GC a mesma sonda reprova 8 itens.
* **Pack TEOF 4.1 inteiro** (servidor Forge 1614, Java 8, ~200 mods; fora só os 8 jars que exigem Crucible/Bukkit e o
  lwjgl3ify, que é de cliente). Mesma sonda, com o controle numa base subterrânea acima de 0 (Y 8+, entidades e
  multiblocos em Y 30) e cada bloco numa cavidade de pedra selada:
  * 4123 blocos com TileEntity (variantes obtíveis), 777 entidades (foguetes GC/ExtraPlanets tier 1–10 incluídos),
    24 multiblocos IE/Immersive Petroleum (16 formados nas duas alturas, 8 a sonda não forma em nenhuma), IC2 funcional.
  * 4026/4123 idênticos na grade densa; os 97 restantes e as 20 entidades sinalizadas foram retestados **isolados**
    (`compatrecheck`, 20 blocos de distância): **97/97 e 20/20 idênticos**. A diferença na grade era interferência entre
    células (fluxo do Thaumcraft, conversão em netherrack, efeitos de área que atravessam paredes), com perdas nos dois
    sentidos (37 no fundo × 48 no controle).
  * Recarga em JVM nova: 3180/3531 idênticos, 287 com chaves que mudam também no controle. Os demais, nenhum
    específico do fundo: 61 ausentes **nas duas alturas** (o `soulJar` do ThaumicHorizons, colocado vazio, grava `null`
    no NBT e derruba o salvamento da coluna de chunk inteira), o refletor de feixe do GC (só resolve o alvo no primeiro
    tick depois de carregar) e listas do OpenComputers gravadas em ordem de hash (comparadas agora como multiconjuntos).
  * Artefatos que a sonda passou a evitar, todos iguais em qualquer altura: metas inexistentes declaradas com
    TileEntity (MFR, ElectriCraft), jogadores falsos (Gadomancy × OpenBlocks), entidades registradas com frequência de
    rastreamento 0 (poções do Witchery) e entidades de efeito criadas sem estado (ChromatiCraft).
* **Crucible de produção** (`tools/crucibletest.sh`; `server.jar` 1.7.10-staging-67b53034, pack inteiro, 66 plugins e os
  dois agents do servidor, linha de comando de produção): self-test completo PASS, incluindo a sonda Bukkit
  (`getBlockAt` mantém Y −10, tipo e luz corretos, `setTypeId` em −11 escreve em −11 e não em 245). O boot com o
  Bellow ficou em ~110 s. Achados do caminho:
  * O Mixin recusou subir enquanto o pistão e a fila do `PlayerManager` não tinham as variantes do Crucible (o
    comportamento desejado de `require = 1`).
  * Com `-Dterminal.jline=false` e a entrada padrão em fim de arquivo, o leitor de console do Crucible enfileira
    comandos vazios até esgotar o heap (2,7 GB em 3 min). O harness usa uma entrada aberta que nunca envia nada; em
    produção a entrada é o console do painel.
  * Num mundo **novo**, os primeiros chunks são gerados dentro do construtor do `World`, antes de o CraftBukkit
    registrar os vizinhos; o `updateLightByType` do Crucible (vindo do PaperSpigot) recusa toda atualização de luz
    neles, em qualquer altura (medido também acima do solo). Mundos existentes carregam o spawn pelo caminho normal.
* **Plugins abaixo de 0** (mesmo servidor, com o agent do WorldEdit como terceiro agent e, só na cópia de teste,
  `MaximumDepth: -64` no GriefPreventionPlus): self-test PASS. O piso chega aos plugins (`akashicbellow.floor.world=-64`);
  o WorldEdit seleciona até −64 (não 0), um `EditSession` preenche 3×11×3 = 99 blocos de −64 a −54 e recusa −65; uma
  região do WorldGuard em −62..−44 é encontrada por consulta dentro dela e não acima; um claim do GriefPreventionPlus é
  achado na superfície e em Y −59. Todas as 7 classes-alvo carregadas na sessão chegaram ao agent já remapeadas pelo
  Crucible (§9.1) e foram corrigidas com a contagem exata de pontos.
* **Custo** (`-Dakashicbellow.selftest.phase=bench`): 529 chunks novos, servidor de produção com EndlessIDs:

  | | sem camada | −64 | −512 |
  |---|---|---|---|
  | heap por chunk | 87 KB | 127 KB | 438 KB |
  | geração por chunk | 10,6 ms | 11,0 ms | 16,7 ms |
  | pacote de chunk | 4,0 KB | 5,2 KB | 9,8 KB |
  | disco por chunk | 6,8 KB | 7,7 KB | 11,7 KB |
* **Cliente headless** (`tools/clienttest.sh [-Pbellow.compatRun=true -Pbellow.angelica=true]`, via `xvfb-run` e
  Mesa llvmpipe): mundo singleplayer, sala iluminada em `minY + 24`, tocha de parede colocada como jogador, baú;
  compara cliente × servidor (blocos, meta, TE, luz) e tira screenshots em `run/client/screenshots/`.

## 11. Riscos abertos (beta)

* Crucible: o servidor de produção foi testado (§10), sem jogadores reais conectados; os plugins foram exercitados
  pelas suas APIs, não por comandos de jogador. Proteção abaixo de 0 exige o agent do WorldEdit na linha de comando,
  `MaximumDepth` no piso no GriefPreventionPlus e as regiões antigas do WorldGuard estendidas (§9.1).
  `//regen` e restauração de snapshots continuam limitados a Y ≥ 0.
* Memória (medida, §10): −64 custa +40 KB por chunk carregado (+46%); −512 custa +351 KB (5×). Com distância de
  visão 10 (441 chunks por jogador isolado) são ≈ +18 MB por jogador em −64 e ≈ +150 MB em −512, no servidor e no
  cliente. Recomendado: −64 (padrão) a −128; −512 só com RAM sobrando e poucos jogadores espalhados.
* Spawn natural: a altura é sorteada uniformemente entre o piso e o topo do chunk, como na 1.18. Em −64 isso é o
  comportamento da 1.18; em −512 as tentativas na superfície ficam ≈ 7× mais raras que na 1.7.10.
* Mods que iteram `getBlockStorageArray()` não veem as seções profundas (por design): scanners, quarries e
  minimapas de outros mods param em Y 0 até ganharem suporte via `BellowAPI`. O Distant Horizons do pack usa altura
  fixa 0..256: as LODs não incluem a camada profunda (invisível da superfície de qualquer forma).
* A sonda testa comportamento de servidor. O visual de máquinas específicas no cliente (TESR) foi validado só pelo
  baú (mesmo caminho de renderização).
* Limites vanilla que ficam como estão (o heightmap nunca desce abaixo de 0, §2): `canBlockSeeTheSky` é falso abaixo
  de 0, então beacons abaixo de 0 não acendem e mortos-vivos não queimam num poço aberto; árvores e cogumelos
  gigantes só crescem com `y >= 1`, cipós param de descer em Y 1; `/setspawn` e `/setworldspawn` aceitam Y 0..256.
* Retrogen de minérios de outros mods que roda fora de `Chunk.populateChunk` (por exemplo a do CoFH, por tick) não
  passa pela guarda de §5 e pode deixar veios antigos abaixo de 0. No Crucible, os ticks aleatórios profundos usam o
  `growthOdds` do Spigot deixado pelo último chunk processado.
* Cavernas profundas: projeto separado.
