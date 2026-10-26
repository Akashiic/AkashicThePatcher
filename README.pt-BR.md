# AkashicThePatcher (pt-BR)

Pacote de coremods de correção de bugs, **client-side**, para o modpack **Forbidden Series** (Minecraft **1.7.10**): coremod Forge + Mixins que corrigem crashes, tempo de inicialização e incompatibilidades de outros mods em tempo de carregamento de classe.

Este repositório é o **histórico completo registrado** do projeto, reconstruído a partir dos arquivos do autor: **45 builds**, de `1.1.0-beta.3` (08/09/2026) até `1.1.0-beta.42-MiraiShaderBridge` (26/10/2026, a atual), com checkpoints, auditorias, ferramentas, código-fonte (quando existe), as investigações por trás de cada build e a procedência de cada arquivo.

> English version: [README.md](README.md). A documentação em `docs/` está em inglês.

## Por onde começar

| Quero... | Leia |
|---|---|
| Entender a história | [docs/HISTORY.md](docs/HISTORY.md) |
| Ver o que cada versão mudou (mais nova primeiro) | [docs/CHANGELOG.md](docs/CHANGELOG.md) |
| Ver qual build descende de qual (provado por hash) | [docs/LINEAGE.md](docs/LINEAGE.md) |
| Entender código e modelo de build | [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) |
| Continuar o trabalho | [docs/HANDOFF.md](docs/HANDOFF.md) |
| Saber o que é desconhecido ou não resolvido | [docs/OPEN-ISSUES.md](docs/OPEN-ISSUES.md) |
| Saber de onde vieram os arquivos e o que ficou de fora | [docs/PROVENANCE.md](docs/PROVENANCE.md) |

## Qual JAR instalar?

O mais novo: `!Akashic-ThePatcher-1.1.0-beta.42-MiraiShaderBridge.jar` (SHA-256 `245e67cbf8f82e4395a9706c21bbee2ebd94911774600aa1e06f188adc0415df`). A beta.42 é a beta.41 mais **shaders do Angelica com o Akashic: Horizons of The Mirai** (o fork de Distant Horizons do pack): o Iris do Angelica passa a detectar o Mirai e a desenhar o terreno distante (LODs) com os programas DH do shaderpack, o Mirai desenha os LODs translúcidos adiados (água distante), e no Java 8 o Angelica encontra os programas de `world0/` dos shaderpacks em `.zip`, como a BSL (antes a BSL não rodava nenhum programa no Java 8). Ela também **inclui a correção do elytra do Et Futurum** (Akashic-ChromaPlayerApiPreloadFix 1.0.0, antes um JAR separado; remova esse JAR). Um único JAR carrega tudo da beta.38 à beta.42 (conferido entrada por entrada na auditoria do build). A beta.41 é a beta.40 mais duas correções de crash: o DragonAPI não quebra mais (`ClassCastException ... WaystonesAlternateResourcePack`) nos blocos com TESR do ChromatiCraft/RotaryCraft quando o pacote "Modernity Textures" do Waystones está selecionado, e depois de um crash recuperado pelo crash handler do CoreTweaks o estado de render do Angelica/Iris é resetado, então o jogo não quebra de novo com `GbufferPrograms in weird state` no próximo contorno de bloco. A beta.40 é a beta.39 mais a correção dos **itens invisíveis (NEI, inventário, mão) com o Angelica 2.2.24+**: o agrupador de upload do atlas de itens não sobrescreve mais o atlas que o Angelica já enviou. Mantém tudo da beta.37 (proteção contra o crash C2 do Forestry dentro do JAR, funciona no Technic, SKLauncher e outros) e faz o pack funcionar com o **Angelica 2.2.24+** (testado com o 2.2.37): corrige em tempo de carga a integração de fontes do NecroTempus com o Angelica (o crash de inicialização `NoClassDefFoundError: BatchingFontRenderer`) e reativa a correção de sublinhado em espaços do módulo RGB no Angelica 2.2.37. **Não instale a beta.38**: no cliente real a correção do NecroTempus dela é ignorada (o Mixingasm do UniMixins não confia no transformer); a beta.39 é a versão que funciona, verificada num cliente real headless. **Não** instale os dois builds marcados `REJECTED` (beta.28) nem os builds de diagnóstico, a menos que esteja reproduzindo uma investigação. Outros requisitos do pack estão em [docs/HANDOFF.md](docs/HANDOFF.md).

## Ressalvas honestas

* **As datas são as de modificação dos JARs**; o histórico git é uma *reconstrução*: um commit por build, em ordem cronológica, e não o histórico real de desenvolvimento.
* As betas 1, 2, 5 e 6 não existem no disco. O código-fonte do núcleo (`com.akashic.thepatcher.*`) não está disponível; a classe alterada da beta.35 só existe descompilada.
* O bug do mundo branco/translúcido que motivou as betas 9-28 **não tem correção registrada**.
* Binários de terceiros (Angelica, Forestry, GTNHLib, pack Technic, derivado do cliente Minecraft) e tudo que é privado (configurações do launcher, endereços de servidor, credenciais) **não** foram incluídos - ver [docs/PROVENANCE.md](docs/PROVENANCE.md) e `data/excluded.tsv`.
* **Nenhuma licença foi escolhida ainda**; por padrão todos os direitos ficam reservados ao autor.
