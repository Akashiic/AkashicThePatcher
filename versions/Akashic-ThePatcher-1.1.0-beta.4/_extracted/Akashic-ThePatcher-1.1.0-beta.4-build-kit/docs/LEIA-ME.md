# Akashic-ThePatcher 1.1.0-beta.4

Build para homologação isolada. Não foi executada uma sessão gráfica completa pelo proxy.

## Instalação
Feche completamente o cliente. Faça backup e substitua somente `!Akashic-ThePatcher-1.1.0-beta.3.jar` pelo JAR beta.4 desta pasta, na instância Forbidden.
Não mantenha duas versões do ThePatcher. Mantenha o Dimensional Doors original, CubicChunks dev.6, RegionLib, Angelica e os demais mods.
Esta correção é client-side. Não exige modificar Crucible, Velocity, Paper ou instalar um novo javaagent.
O Akashic-RGB-Transport beta.2 anterior não mudou. Não coloque esse agente em mods.
Não apague mundos, registries, configurações ou caches.

## Controle
A nova opção é `module.dimDoorsClientSyncGuard` em `config/akashic-thepatcher.properties`, habilitada quando ausente.
Para desativar somente este módulo em um diagnóstico controlado, defina false e reinicie. O crash original poderá voltar.

## Teste jogável
Repita lobby -> forbidden-1 -> lobby, mantendo F3, inventário e movimentação funcionando.
No Forbidden, aproxime-se das portas, confira estados visuais e interação. Repita após reconectar.
Verifique no log os alvos DimDoors instalados e eventual mensagem de vínculo adiado/reaplicado.
Expiração/overflow de vínculos deve ser investigado; não equivale a sincronização concluída.

## Reversão
Com o jogo fechado, retire beta.4 e restaure apenas beta.3. Nenhuma migração de mundo é feita por este módulo.
