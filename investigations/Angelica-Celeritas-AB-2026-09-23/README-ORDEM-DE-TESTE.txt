ANGELICA / CELERITAS - A/B WHITE BLOCKS - 2026-09-23
====================================================

OBJETIVO
Isolar a causa dos blocos brancos/translucidos sem misturar variaveis.

REGRA CARDINAL
Antes de CADA perfil:
- fechar completamente Minecraft/Technic;
- restaurar os arquivos de 00-backup-current;
- aplicar SOMENTE o perfil sendo testado;
- iniciar novamente;
- nao abrir a GUI de video do Angelica durante esses testes.

THEPATCHER
Para estes testes use a baseline beta.8:
!Akashic-ThePatcher-1.1.0-beta.8-BASELINE.jar
SHA-256:
010c3b9f4fdd20528a772904f13238aa836c8af13759aebfe7184ed07beed44b

Remova beta.9/beta.10 da pasta mods. Elas nao corrigiram o sintoma e nao devem
ficar como variavel extra durante o A/B do terrain renderer.

TESTE 1 - COMPACT VERTEX FORMAT OFF
Copiar:
01-compact-vertex-off\config\angelica-options.json
para:
<Forbidden>\config\angelica-options.json

Unica alteracao relevante:
"use_compact_vertex_format": false

Reiniciar completamente e entrar no MESMO servidor/mesma area.
Se corrigir:
o alvo e o layout/packing de vertices do Celeritas.
Nao continuar para os outros perfis.

Se NAO corrigir:
restaurar 00-backup-current antes do teste 2.

TESTE 2 - MIPMAP LEVEL 0
Copiar:
02-mipmap-zero\options.txt
para:
<Forbidden>\options.txt

Unica alteracao:
mipmapLevels:4 -> mipmapLevels:0

O arquivo foi modificado byte a byte para preservar tamanho e encoding.

Se corrigir:
o alvo passa a ser a interacao mipmap / TextureMap / ParallelMipMap / Angelica.

Se NAO corrigir:
restaurar 00-backup-current antes do teste 3.

TESTE 3 - IRIS OFF
Copiar:
03-iris-off\config\angelica-modules.cfg
para:
<Forbidden>\config\angelica-modules.cfg

Unica alteracao:
B:enableIris=false

Requer restart completo porque muda mixins de startup.
Nenhum shader pack precisa estar ativo para os mixins Iris existirem quando
enableIris=true.

Se corrigir:
investigar a integracao Iris/FFP/Celeritas.
TESTE 4 - VAO OFF
Somente se 1-3 falharem.

Copiar os DOIS arquivos de:
04-vao-off\config\

para:
<Forbidden>\config\

Alteracoes da mesma feature:
"use_vertex_array_objects": false
B:enableVAO=false

TESTE 5 - DSA OFF
Somente se 1-4 falharem.

Copiar:
05-dsa-off\config\angelica-modules.cfg

Alteracao:
B:enableDSA=false

TESTE 6 - THREADED CHUNK BUILDING OFF
Somente se 1-5 falharem.

Copiar:
06-threaded-off\config\angelica-modules.cfg

Alteracao:
B:enableThreadedChunkBuilding=false

MENU / SPLASH - TESTE SEPARADO
O seu client atual CONTINUA com:
config\splash.properties
enabled=false

Enquanto isso estiver false, o Akashic Cinematic Menu so consegue apresentar
o frame inicial one-shot; o loop SplashHooks que recalcula geometria por frame
nao roda. Portanto maximizar durante bootstrap continuara parecendo quebrado.

Depois de terminar o A/B do mundo, copiar:
07-splash-on\config\splash.properties
para:
<Forbidden>\config\splash.properties

e testar resize/maximize em um boot separado.

EVIDENCIA DO ULTIMO LOG
- beta.10 carregou e executou seus dois patches.
- o mundo continuou branco.
- GL error 1280 ja estava pendente antes da chamada de VSync.
- cache GLSM texture2D=true e driver=false NAO e por si so bug: essa textura
  e ffpStateOnly e Angelica pode emula-la no shader.
- Celeritas sortAndRender faz bind explicito do atlas antes do terrain pass.
Logo, a investigacao correta agora e vertex/mipmap/Iris/VAO/DSA/threading.
