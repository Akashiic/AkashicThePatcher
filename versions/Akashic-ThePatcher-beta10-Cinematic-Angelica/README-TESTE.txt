AKASHIC THEPATCHER 1.1.0-beta.10 - CINEMATIC / ANGELICA
========================================================

ARTEFATO
!Akashic-ThePatcher-1.1.0-beta.10-Cinematic-Angelica.jar

SHA-256
3182e7e3d6b51854eae008ff8eb8f346a1d0a8b291740ceff8dab9c5fabc9dab

IMPORTANTE
A beta.10 NAO deriva da beta.9.
Ela deriva diretamente da beta.8 verificada e remove o patch experimental
MPS/Numina que o live test mostrou nao participar da falha.

DIAGNOSTICO ATUAL
- RawScope do Akashic Cinematic Menu foi realmente transformado pelo Angelica 2.2.18.
- RawScope desabilita GL_TEXTURE_2D em todas as unidades e usa GL_ALL_ATTRIB_BITS.
- O mundo branco pode ser state leak/cache-driver desync; ainda precisa live proof.
- O crash das opcoes de video foi confirmado em Angelica/LWJGL2 VSync.

FASE A - MUNDO / STATE FENCE
1. Feche completamente Minecraft e Technic.
2. Remova do mods:
   !Akashic-ThePatcher-1.1.0-beta.9-MPS-Angelica.jar
3. Garanta que NAO exista outra beta do ThePatcher na pasta mods.
4. Coloque:
   !Akashic-ThePatcher-1.1.0-beta.10-Cinematic-Angelica.jar
5. Mantenha TODO o resto igual:
   Angelica 2.2.18
   GTNHLib 0.11.51
   AkashicCinematicMenu 0.7.0-alpha.4
   MPS / Numina
   High Texture Attemption.zip
   configs atuais do Angelica
6. NAO copie ainda phase-b-config/splash.properties.
   Deixe o splash atual com enabled=false.
7. Neste primeiro boot NAO maximize durante a inicializacao.
8. Entre no mesmo servidor e verifique os blocos.

MARCADORES ESPERADOS
[AkashicThePatcher/Cinematic-Angelica] RawScope state fence patched; returns=1
[AkashicThePatcher/Cinematic-Angelica] state fence texture2D cache/driver ... -> true/true
[AkashicThePatcher/Cinematic-Angelica] Angelica VSync fail-safe patched; calls=1

O segundo marcador e especialmente importante.
Ele mostra o estado que o GLSM achava existir e o estado real do driver.
FASE A.2 - OPCOES DE VIDEO
Depois de observar o mundo:
1. Abra as configuracoes de video do Angelica.
2. Altere UMA opcao simples.
3. Aplique.
4. Depois teste VSync separadamente.

Se houver GL_INVALID_ENUM pendente, beta.10 deve registrar algo como:
[AkashicThePatcher/Angelica-VSync] drained pending GL error before VSync: 1280

Se o swap interval falhar, deve registrar:
[AkashicThePatcher/Angelica-VSync] VSync request failed soft ...

O cliente nao deve encerrar por essa excecao.

FASE B - SPLASH / MAXIMIZACAO
Faca esta fase somente depois da Fase A.

1. Copie:
   phase-b-config\splash.properties
   para:
   config\splash.properties
2. Sobrescreva o arquivo existente.
3. Feche/reabra completamente o cliente.
4. Enquanto o Cinematic Loading estiver renderizando, maximize/restaure a janela.
5. Verifique se o layout acompanha a nova resolucao sem cair na tela Mojang branca.
POR QUE ISSO E SEPARADO
O splash atual esta enabled=false porque Forge o auto-desativou depois de um
crash anterior. Com o splash desligado, o Cinematic tem apenas o frame inicial
one-shot; nao existe loop de SplashHooks para redesenhar a geometria apos resize.

ROLLBACK
Remova beta.10 e use:
rollback-beta8\!Akashic-ThePatcher-1.1.0-beta.8.jar

SHA-256 beta.8:
010c3b9f4fdd20528a772904f13238aa836c8af13759aebfe7184ed07beed44b

KILL SWITCHES
Somente state fence:
-Dakashic.cinematicAngelicaStateFence.disable=true

Somente VSync guard:
-Dakashic.angelicaVSyncGuard.disable=true

STATUS
Build deterministico: PASS
Targets especificos: PASS
Regressao herdada beta.8: 16/16 PASS
Auditoria binaria: PASS
Live GPU / mundo branco: PENDENTE
Resize do splash: PENDENTE
