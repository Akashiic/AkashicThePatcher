Akashic The Patcher 1.1.0-beta.9
MPS / Numina / Angelica 2.2.18 compatibility candidate

STATUS
- Build determinístico: PASS
- Regressões herdadas da beta.8: 17/17 PASS
- Replay do transformer: 6/6 chamadas PASS
- Ordem antes/depois do redirecionador Angelica: PASS
- Fingerprint real Java 8: PASS
- Fingerprint real Java 21 (MR21): PASS
- Fingerprint incorreto: fail-closed PASS
- Kill-switch: bytes originais PASS
- Teste visual dentro do Forbidden: PENDENTE
- Produção aprovada: NÃO

ALVO EXATO
- Angelica 2.2.18
- GTNHLib 0.11.51
- Numina 0.4.1.105
- ModularPowersuits 1.7.10-0.11.1.118-Akashic-QMX.2

O QUE FOI MESCLADO
A beta.9 mantém a beta.8 e adiciona um transformer CLIENT-ONLY que atua
somente em net.machinemuse.numina.render.RenderState.
São interceptados somente estes seis scopes:
- on2D
- off2D
- blendingOn
- blendingOff
- glowOn
- glowOff

O patch substitui apenas os glPushAttrib/glPopAttrib desses métodos por
uma ponte explícita de estado compatível com o GLStateManager do Angelica.
O restante das chamadas OpenGL continua sendo redirecionado normalmente
pelo Angelica.

O patch NÃO rebaixa o Angelica e NÃO altera globalmente o GLStateManager.

SEGURANÇA / FAIL-CLOSED
O transformer só ativa quando encontra fingerprints exatos do Numina
0.4.1.105 e de uma das variantes oficiais Java 8/17/21 do
GLStateManager do Angelica 2.2.18.

Versões diferentes ficam inalteradas.

Kill-switch JVM:
-Dakashic.mpsAngelica218.disable=true

TESTE RECOMENDADO
1. Feche completamente o Minecraft/Technic.
2. Faça backup de !Akashic-ThePatcher-1.1.0-beta.8.jar.
3. Remova a beta.8 da pasta mods. Não mantenha beta.8 e beta.9 juntas.
4. Coloque !Akashic-ThePatcher-1.1.0-beta.9.jar na pasta mods.
5. Mantenha Angelica 2.2.18, GTNHLib 0.11.51, Numina .105 e MPS QMX.2.
6. Para o PRIMEIRO teste, mantenha config/splash.properties enabled=false.
   Isso isola o problema do mundo do problema do splash/menu.
7. Inicie o cliente e procure no log:
   [AkashicThePatcher/MPS-Angelica] patched Numina RenderState for Angelica 2.2.18; calls=6
8. Entre no mesmo servidor e verifique:
   - blocos vanilla e modded opacos/corretos
   - Tinker Table
   - Lux Capacitor
   - inventário
   - Power Fist
   - mudança de dimensão/servidor

MENU CINEMÁTICO / SPLASH
O crash anterior fez o Forge escrever:
config/splash.properties -> enabled=false

A beta.9 deliberadamente NÃO força esse valor de volta para true, porque
essa flag é também um mecanismo de segurança do Forge.

Depois que o teste visual do mundo passar, altere separadamente:
enabled=true

e faça outro restart completo para validar o AkashicCinematicMenu.

ROLLBACK
- Remova beta.9 e restaure beta.8; ou
- use -Dakashic.mpsAngelica218.disable=true para desabilitar apenas
  o módulo MPS/Angelica da beta.9.

SHA-256 do candidato:
695d6bd9681070bba8bc8e80bc9bc89d9ac02b2b524725dcbc30dc024e116740
