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
2. As seções profundas ficam num array separado por chunk: `deep[s - minSection]`, onde
   `s = y >> 4 ∈ [minY/16, −1]`. São `ExtendedBlockStorage` reais, então EndlessIDs (que estende a EBS
   via mixin) funciona nelas automaticamente.
3. Cada método com coordenada Y recebe um **fast-path injetado no HEAD**: se `minY ≤ y < 0`, o mod resolve a
   operação inteira e retorna; caso contrário o método original (ou o @Overwrite de outro mod) roda intocado.
   Isso independe da forma do bytecode que outros mods deixaram (Hodgepodge redireciona `World.getBlock`,
   ArchaicFix sobrescreve `Chunk.getSavedLightValue`, etc.). Os mixins usam prioridade acima dos mods-alvo
   para serem aplicados depois deles.
4. Todo ponto de injeção crítico é `require = 1`: se uma versão diferente de um mod mudar o alvo, o jogo
   **falha no boot** — nunca roda silenciosamente corrompendo o mundo.
5. Invariante: **o heightmap nunca fica negativo**. Céu nunca "enxerga" Y < 0 por heightmap; a luz do céu entra
   no subsolo só por propagação (BFS). Custo: um poço aberto até Y −30 recebe luz decaindo 1/bloco a partir de Y 0
   em vez de 15 cheio. Benefício: zero risco para mods que usam heightmap como índice ou laço `y > 0`.

## 3. Persistência

* **NBT por chunk** via `ChunkAPI ChunkDataManager` (`akashicbellow:deep`), nunca na lista vanilla `Sections`
  (o leitor do ChunkAPI/vanilla faria `array[Y]` com Y negativo → exceção → chunk regenerado).
  ```
  akashicbellow: { deep: { V: 1, MinY: int, Gen: byte, Sections: [ {Y: byte(signed), <managers do ChunkAPI>} ] } }
  ```
  Cada seção é serializada por `DataRegistryImpl.writeSubChunkToNBT`, ou seja, pelos mesmos managers que gravam as
  seções vanilla (inclusive os do EndlessIDs) — formato idêntico, IDs estendidos preservados.
* Seções fora do intervalo atual (ex.: config mudou) são mantidas como NBT dormente e regravadas — nada se perde.
* **Trava por mundo**: o `minY` efetivo é gravado em `<mundo>/akashicbellow.dat` no primeiro uso. Mudar a config depois
  não altera mundos existentes (log de aviso). Evita mundos com pisos inconsistentes.
* **Desinstalação**: vanilla/ChunkAPI ignoram a tag; o mundo carrega, perdendo o conteúdo abaixo de 0. A bedrock
  legada que foi substituída (ver §5) não volta. O ChunkAPI mostra a mensagem de desinstalação no carregamento.

## 4. Rede

* Mod obrigatório nos dois lados (handshake FML por versão).
* `ChunkAPI PacketDataManager` (`akashicbellow:deep`) anexa as seções profundas a S21/S26:
  `short minY, int mask, depois para cada bit: byte sectionY + bloco do DataRegistryImpl.writeToBufferCubic`
  (os mesmos managers "cúbicos" do ChunkAPI/EndlessIDs). `forceUpdate && mask==0` (pacote de descarregar) não leva dados.
* Mudanças de bloco em Y < 0 não entram no `PlayerInstance` vanilla (que empacota Y em 8 bits). Fila própria:
  < limiar de agrupamento (Forge `clumpingThreshold`) → S23 individuais (o ChunkAPI já transmite Y com sinal, 12 bits);
  ≥ limiar → S21 parcial só com as seções profundas tocadas.
* Pacotes vanilla com Y em byte sem sinal são alargados para `short` com sinal:
  `C07PacketPlayerDigging`, `C08PacketPlayerBlockPlacement`, `S28PacketEffect`, `S0APacketUseBed`, `S25PacketBlockBreakAnim` (conferir).
* `S3F akashicbellow:dim` informa ao cliente o `minY` de cada dimensão antes dos chunks.

## 5. Geração — terreno e retrogen

Estado por chunk (`Gen`): `0 NONE → 1 TERRAIN → 2 DECORATED`.

**TERRAIN** (em `Chunk.onChunkLoad`, servidor, dimensões habilitadas — cobre carga síncrona, assíncrona e geração):
* `minY ≤ y < 0`: bloco profundo (`etfuturum:deepslate` se existir, senão `minecraft:stone`).
* Piso de bedrock no estilo 1.18 (`verticalGradient("bedrock_floor", aboveBottom(0), aboveBottom(5))`):
  `P(bedrock | y) = clamp(1 − (y − minY)/5, 0, 1)` → 100%, 80%, 60%, 40%, 20%, 0% em minY..minY+5.
  Sorteio **posicional e sem estado** (SplitMix64 de `seed ⊕ pos`), então o resultado não depende da ordem em que
  os chunks são gerados.
* **Bedrock legada** (Y 0..4, só `Blocks.bedrock`) → bloco profundo, em chunks novos e antigos (configurável),
  abrindo passagem para baixo como a 1.18 fez. Nada mais acima de Y 0 é tocado em chunks antigos.
* Isto responde à pergunta original: num mundo com 25 mil blocos explorados, **todo chunk antigo recebe o subsolo
  quando é carregado** — não há "lugar sem geração", porque a geração profunda é por chunk e não depende de vizinhos.

**DECORATED** (minérios/tufo/cascalho): roda para o chunk C quando C e os 8 vizinhos estão carregados com TERRAIN.
Vizinhança 3×3 (como a 1.18), nunca carrega chunk (sem cascata), escreve direto nas seções (sem update de luz:
pedra opaca → minério opaco, luz 0 → 0). Chunks já vistos por jogadores recebem reenvio das seções alteradas.
O intervalo Y ≥ 0 continua sendo da geração vanilla/modded 1.7.10 (sem dobrar o balanceamento do pack); os
recursos são **cortados em Y < 0**.

## 6. Minérios — porte fiel da 1.18/1.20

`OreFeature.place/doPlace` portado linha a linha (segmento elipsoidal com `size` esferas, eliminação de esferas
contidas, `BitSet` de visita, `discardChanceOnAirExposure`), mais `UniformHeight`, `TrapezoidHeight`
(`i + U[0,m] + U[0,l]`), `CountPlacement`, `InSquare`, `RarityFilter`.

Alvo: pedra (`stone`) → minério comum; deepslate/tufo → variante deepslate via
`ganymedes01.etfuturum.api.DeepslateOreRegistry.getOre(block, meta)` (cobre também minérios de outros mods
registrados no EFR). Sem EFR: minério comum.

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

Com `minY = −64` os parâmetros são exatamente os da 1.20.1. Para pisos mais fundos, âncoras `aboveBottom` descem
junto, âncoras absolutas ficam, e features `uniform(bottom, X)` podem ter a contagem escalada por
`(X − minY + 1)/(X + 64 + 1)` (densidade por volume preservada; arredondamento estocástico preserva a esperança).

## 7. Luz

* **Vanilla**: o BFS de `World.updateLightByType` usa coordenadas relativas; com os fast-paths de
  `getSavedLightValue / setLightValue / getBlock / getBlockLightOpacity` e `checkChunksExist` ele atravessa Y=0 sem costura.
* **ArchaicFix Phosphor** (ativo no TEOF): o motor dele empacota Y em 8 bits (`y << 52`, flag no bit 60) e indexa
  `getBlockStorageArray()[y >> 4]`. Em mundos com camada profunda, `getLightingEngine()` passa a devolver o
  `DeepLightingEngine` do Bellow: mesmo algoritmo do Phosphor, Y codificado como `y − minY` em 10 bits
  (bits 52..61, flag de estouro no 62), luz inicial em filas por nível. Um só motor, sem costura em Y=0.

## 8. Cliente / renderização

* Renderer vanilla (`RenderGlobal`): grade vertical `16 + N` seções com deslocamento, `ChunkCache` com piso `minY`.
  Aplicado só sem Angelica.
* **Angelica/Celeritas**: o Celeritas já trabalha com `minSection/maxSection`. Mixins tardios ajustam
  `AngelicaRenderSectionManager.create` (minSection), `getMinimumBuildHeight`, `isSectionVisuallyEmpty`
  e a cópia de seções do `WorldSlice` para usar as seções profundas.
* Neblina do vazio / partículas de profundidade deslocadas por `minY`.

## 9. Compatibilidade conhecida

| mod | problema | tratamento |
|---|---|---|
| ChunkAPI 0.8.x | dono do NBT/pacotes | dependência obrigatória; managers próprios |
| EndlessIDs | formato de EBS | seções profundas são EBS reais; serialização pelos managers |
| ArchaicFix (Phosphor) | Y em 8 bits | `DeepLightingEngine` |
| Hodgepodge | `MixinChunk` lança erro fatal para TileEntity em Y<0 | mixin tardio em `Compat.isCubicChunksPresent` → limites corretos |
| Angelica | min/max de seção fixos | mixins tardios |
| EFR | deepslate/minérios | integração por reflexão; camada EFR (Y 0..22) continua dele |
| Crucible/Bukkit | ver §10 | |

## 10. Riscos abertos (beta)

* Crucible: os pontos Bukkit (`CraftWorld.getBlockAt`, eventos com Y) ainda precisam de teste em servidor Crucible real.
* Renderização com Angelica precisa de validação visual no cliente (não testável no ambiente de CI deste projeto).
* Memória: cada seção ≈ 10 KB (mais com EndlessIDs). −64 adiciona 4 seções/chunk; −512 adiciona 32. Recomendo −64 ou −128.
* Cavernas profundas: projeto separado (API `DeepTerrainStage` prevista para o mod de cavernas registrar carvers).
