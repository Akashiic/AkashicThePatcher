# AkashicThePatcher (pt-BR)

Pacote de coremods de correção de bugs, **client-side**, para o modpack **Forbidden Series** (Minecraft **1.7.10**): coremod Forge + Mixins que corrigem crashes, tempo de inicialização e incompatibilidades de outros mods em tempo de carregamento de classe.

Este repositório é o **histórico completo registrado** do projeto, reconstruído a partir dos arquivos do autor: **38 builds**, de `1.1.0-beta.3` (08/09/2026) até `1.1.0-beta.35-BackhandLifecycleFix` (25/09/2026, a que está em uso), com checkpoints, auditorias, ferramentas, código-fonte (quando existe), as investigações por trás de cada build e a procedência de cada arquivo.

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

O mais novo: `!Akashic-ThePatcher-1.1.0-beta.35-BackhandLifecycleFix.jar` (SHA-256 `c48d3ec3b1ef35f907baca47cd78ead48730b4cd556592e529c0140d7a869c17`). **Não** instale os dois builds marcados `REJECTED` (beta.28) nem os builds de diagnóstico, a menos que esteja reproduzindo uma investigação. O pack também precisa de argumentos de JVM fora do JAR (ver [docs/HANDOFF.md](docs/HANDOFF.md)).

## Ressalvas honestas

* **As datas são as de modificação dos JARs**; o histórico git é uma *reconstrução*: um commit por build, em ordem cronológica, e não o histórico real de desenvolvimento.
* As betas 1, 2, 5 e 6 não existem no disco. O código-fonte do núcleo (`com.akashic.thepatcher.*`) não está disponível; a classe alterada da beta.35 só existe descompilada.
* O bug do mundo branco/translúcido que motivou as betas 9-28 **não tem correção registrada**.
* Binários de terceiros (Angelica, Forestry, GTNHLib, pack Technic, derivado do cliente Minecraft) e tudo que é privado (configurações do launcher, endereços de servidor, credenciais) **não** foram incluídos - ver [docs/PROVENANCE.md](docs/PROVENANCE.md) e `data/excluded.tsv`.
* **Nenhuma licença foi escolhida ainda**; por padrão todos os direitos ficam reservados ao autor.
