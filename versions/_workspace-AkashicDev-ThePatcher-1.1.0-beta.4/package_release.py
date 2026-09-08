from pathlib import Path
import json,hashlib,shutil,zipfile
r=Path(__file__).resolve().parent;e=r/'evidence';a=json.loads((e/'final-audit.json').read_text())
jar=r/'build/!Akashic-ThePatcher-1.1.0-beta.4.jar';sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
assert sha(jar)==a['sha256'] and all(x['exit']==0 for x in a['checks'])
dest=Path.home()/'Desktop/Akashic-ThePatcher-1.1.0-beta.4';stage=dest.with_name(dest.name+'-preparando')
assert not dest.exists() and not stage.exists(),'Refusing to overwrite a delivery'
stage.mkdir();shutil.copy2(jar,stage/jar.name);shutil.copy2(e/'final-audit.json',stage/'AUDITORIA.json')
readme='''# Akashic-ThePatcher 1.1.0-beta.4

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
'''
(stage/'LEIA-ME.md').write_text(readme,encoding='utf-8')
diagnosis='''# Diagnóstico e limites

O crash de 08/09/2026 às 03:13:45 ocorre no cliente: PocketManager.getDimensionData, linha 494 do Dimensional Doors 2.2.6, chamado pelo watcher da atualização S35 de TileEntityDimDoor.
O mapa dimensionData está nulo. A atualização de NBT acontece antes da notificação que falha.
O módulo preserva a leitura de NBT e intercepta somente a notificação de criação do vínculo. As duas consultas de dimensão preservam a semântica de retorno nulo quando o mapa ainda não existe.
A notificação é adiada até o mapa nativo estar carregado e conectado, com a dimensão de origem presente. Não cria raízes fictícias e não chama PocketManager.load antecipadamente para pular o snapshot.
A reprodução usa o watcher original. A fila guarda referências fracas a mundo, tile e conexão; elimina estado antigo, tile substituída e duplicatas pendentes. Limites: 512 notificações, 64 consultas por tick e expiração após 600 ticks, verificada ao examinar cada entrada.
Quando falta informação do servidor, não se anuncia sincronização concluída: expiração e overflow são registrados. A correção evita esta exceção; não promete resolver toda divergência de dimensões ou todo caminho de portal.

## Preservação
94 das 96 classes da beta.3 permanecem byte a byte idênticas. O coordenador recebe um hook de registro e a classe principal recebe versão e um método adicional de lifecycle. Todos os módulos anteriores permanecem empacotados.
O build é uma sobreposição binária reproduzível sobre a beta.3 de hash fixo, não uma recompilação completa de todos os fontes históricos. Os fontes novos, a base exata e ferramentas acompanham o kit.

## Natureza dos testes
São executados os binários reais de DimDoors, NBT, S35, Compactor e watcher. O mundo e a indicação de lado FML são fixtures controladas. Isso não equivale a executar o Minecraft completo, GPU, proxy e modpack real.
O defeito original é reproduzido antes da correção. O ensaio corrigido valida NBT, adiamento, leitura do snapshot, reaplicação nativa e descarte de mundo anterior.
A presença do novo guard não substitui as correções independentes do CubicChunks dev.6.
'''
(stage/'DIAGNOSTICO.md').write_text(diagnosis,encoding='utf-8')
summary=f'''# Resultado — beta.4

JAR: `{jar.name}`
SHA-256: `{a['sha256']}`
Classes: {a['classes']}; bytecode Java 8 (major 52).
Classes antigas preservadas: {a['base_classes_unchanged']}/{a['base_classes_total']}.
Duas compilações limpas produziram o mesmo SHA-256.
Validação: {len(a['checks'])} etapas registradas com código de saída zero; as etapas incluem compilação e testes e não representam sessões Minecraft.
O ensaio nativo e a fila também executaram no Java 8 do cliente.

Aprovado somente para homologação isolada. Cliente gráfico e rota autenticada pelo proxy não foram executados nesta bateria.
Modpack ativo e produção não foram substituídos.
Detalhes e saídas completas estão em AUDITORIA.json e evidencias.zip.
'''
(stage/'RESULTADO.md').write_text(summary,encoding='utf-8')
with zipfile.ZipFile(stage/'Akashic-ThePatcher-1.1.0-beta.4-build-kit.zip','w',zipfile.ZIP_DEFLATED) as z:
    chosen=[]
    for directory in ['src','tools','stubs','base','lib']:
        chosen.extend(p for p in (r/directory).rglob('*') if p.is_file() and not p.name.endswith('-sources.jar'))
    chosen.extend(r/name for name in ['build.py','finish_checks.py','package_release.py'])
    for p in sorted(chosen):
        assert p.suffix.lower() not in ['.ttf','.otf','.woff','.woff2']
        z.write(p,p.relative_to(r).as_posix())
    for name in ['LEIA-ME.md','DIAGNOSTICO.md','RESULTADO.md']:z.write(stage/name,'docs/'+name)
with zipfile.ZipFile(stage/'Akashic-ThePatcher-1.1.0-beta.4-evidencias.zip','w',zipfile.ZIP_DEFLATED) as z:
    for p in sorted(e.glob('*')):
        if p.is_file() and p.suffix in ['.log','.json','.txt']:z.write(p,p.name)
for p in stage.glob('*.zip'):
    with zipfile.ZipFile(p) as z:assert z.testzip() is None
(stage/'SHA256SUMS.txt').write_text('\n'.join(sha(p)+'  '+p.name for p in sorted(stage.iterdir()) if p.is_file())+'\n')
assert sha(stage/jar.name)==a['sha256']
stage.rename(dest)
result={'path':str(dest),'jar':str(dest/jar.name),'sha256':a['sha256'],'status':'isolated-homologation-only','classes':a['classes'],'checks':a['checks']}
(e/'delivery.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
print('DELIVERY_VERIFIED='+json.dumps(result),flush=True)
