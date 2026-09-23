Akashic ThePatcher 1.1.0-beta.9 - MPS / Angelica 2.2.18
=========================================================

ARTEFATO
!Akashic-ThePatcher-1.1.0-beta.9-MPS-Angelica.jar

SHA-256
cefc4f7b13e9c9bdba5f3c2c3c4a534d75a7d22096c6ea012737ef671e885e70

STATUS
Build deterministico + gates estruturais + regressao herdada: PASS.
Homologacao visual real no Forbidden: PENDENTE.

ALVO EXATO
- Angelica 2.2.18
- GTNHLib 0.11.51
- Numina 0.4.1.105
- ModularPowersuits 1.7.10-0.11.1.118-Akashic-QMX.2

O patch e client-side e fail-closed.
Versoes/fingerprints diferentes nao sao alteradas.
COMO TESTAR
1. Feche completamente o Minecraft/Technic.
2. Na pasta mods, REMOVA temporariamente:
   !Akashic-ThePatcher-1.1.0-beta.8.jar
3. Coloque:
   !Akashic-ThePatcher-1.1.0-beta.9-MPS-Angelica.jar
4. Nao mantenha beta.8 e beta.9 juntas.
5. Nao altere MPS, Numina, Angelica, GTNHLib nem resource packs neste A/B.
6. Para o primeiro teste grafico, mantenha config/splash.properties enabled=false.
7. Inicie o cliente e entre no mesmo servidor/local que apresentava blocos
   translucidos/invisiveis.

MARCADOR ESPERADO NO LOG
[AkashicThePatcher/MPS-Angelica] patched Numina RenderState for Angelica 2.2.18; calls=6

Se aparecer ANGELICA_FINGERPRINT_MISMATCH, NUMINA_FINGERPRINT_MISMATCH ou
SHAPE_MISMATCH, o patch falhou fechado e nao modificou RenderState.
O QUE VALIDAR
- blocos vanilla opacos novamente
- blocos modded opacos novamente
- nenhuma textura invisivel/translucida inesperada
- Tinker Table do MPS visivel
- Lux Capacitor visivel
- abrir GUI da Tinker Table
- Power Fist em inventario/mao
- armadura MPS
- menu principal e gameplay sem corrupcao grafica

KILL-SWITCH
Para desativar somente esta compatibilidade sem trocar o JAR:
-Dakashic.mpsAngelica218.disable=true

ROLLBACK
Remova beta.9 e restaure a beta.8 original.
beta.8 SHA-256:
010c3b9f4fdd20528a772904f13238aa836c8af13759aebfe7184ed07beed44b

SPLASH / MENU CINEMATICO
O splash do Forge permanece desabilitado porque o crash anterior gravou
enabled=false. Depois que o render do mundo for aprovado, teste separadamente:
config/splash.properties -> enabled=true
e reinicie completamente o cliente.
