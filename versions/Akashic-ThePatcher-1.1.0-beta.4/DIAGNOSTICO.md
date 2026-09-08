# Diagnóstico e limites

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
