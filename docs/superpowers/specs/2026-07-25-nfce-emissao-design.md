# Emissão de NFC-e — Design

Data: 2026-07-25
Branch: `feature/nfce-emissao`

## Contexto

A Sorveteria Bomcream é MEI (CNPJ 35.242.747/0001-30), registrada no Rio Grande do Sul, optante do Simples Nacional. O dono já obteve o certificado digital A1 (`.pfx`) e confirmou que precisa emitir Nota Fiscal de Consumidor Eletrônica (NFC-e, modelo 65) para as vendas de balcão. O credenciamento junto à SEFAZ-RS (CSC/ID do CSC) ainda não está confirmado — o desenvolvimento deve funcionar inteiramente em ambiente de homologação até essa confirmação.

## Decisão de arquitetura

Integração via gateway fiscal (**Focus NFe**) em vez de integração direta com a SEFAZ, isolada atrás de uma interface (`EmissorFiscalService`) para permitir trocar de provedor ou migrar para integração direta no futuro sem tocar no restante do sistema.

Motivo: para um MEI de baixo volume, o custo de manter uma integração direta com a SEFAZ (assinatura XML, schemas por estado, contingência, geração de DANFE, manutenção contínua conforme mudanças de legislação) supera o custo do plano Focus NFe (Retail, R$ 59,90/mês, 500 NFC-e/mês, R$0,05 por nota excedente, 30 dias de teste grátis). Preços conferidos em [focusnfe.com.br/precos](https://focusnfe.com.br/precos/) em 2026-07-25 — reconfirmar antes de contratar, pois pode mudar.

**Certificado digital**: o upload do `.pfx` e a senha são feitos pelo usuário diretamente no painel da Focus NFe. O cadastro da empresa (CNPJ, endereço, Inscrição Estadual, regime tributário) também é feito uma única vez no painel deles — o backend não armazena esses dados, apenas referencia um token de API por ambiente (homologação/produção).

## Modelo de dados

### `ProdutoEntity` / `ProdutoDTO` (novos campos)

| Campo | Tipo | Observação |
|---|---|---|
| `ncm` | String | Específico por produto, sem valor padrão seguro — preencher manualmente. |
| `cfop` | String | Padrão sugerido `5102` (venda de mercadoria adquirida de terceiros), editável. |
| `csosn` | String | Padrão sugerido `102` (Simples Nacional, sem permissão de crédito), editável. |
| `unidadeComercial` | String | Padrão sugerido `UN`. |

Estes campos já são exercitados pelo teste `ProdutoFiscalMappingTest` (pendente, não commitado) — este teste passa a ser a especificação executável desta parte.

Validar os códigos padrão com o contador antes de emitir em produção — não é responsabilidade do sistema validar a correção fiscal desses códigos.

### Nova coleção `nota_fiscal` (`NotaFiscalEntity`)

| Campo | Tipo |
|---|---|
| `uid` | String (Id) |
| `vendaUid` | String (referência à venda) |
| `status` | Enum: `NAO_EMITIDA`, `PROCESSANDO`, `AUTORIZADA`, `REJEITADA`, `CANCELADA`, `ERRO` |
| `numero` / `serie` | String — atribuídos pela Focus NFe (sem numeração própria no sistema) |
| `chaveAcesso` | String |
| `protocoloAutorizacao` | String |
| `mensagemSefaz` | String — motivo de rejeição/erro quando aplicável |
| `urlDanfe` | String — link para o PDF do cupom, pronto para impressão manual |
| `dataEmissao` | LocalDateTime |
| `dataCancelamento` | LocalDateTime |
| `justificativaCancelamento` | String |

Não há alteração no `VendaEntity`: o campo `cliente` continua texto livre, sem CPF estruturado — NFC-e permite consumidor não identificado, então isso fica fora de escopo.

## Backend

Novo pacote `com.sorveteria.bomcream.vendas.fiscal`:

- `EmissorFiscalService` (interface): `emitir(VendaEntity venda): NotaFiscalEntity`, `consultarStatus(String notaUid): NotaFiscalEntity`, `cancelar(String notaUid, String justificativa): NotaFiscalEntity`.
- `FocusNFeEmissorService implements EmissorFiscalService`: monta o payload JSON da NFC-e a partir dos itens/forma de pagamento da venda, chama a API Focus NFe via HTTP com Basic Auth (token como usuário), mapeia a resposta para `NotaFiscalEntity`.
- `NotaFiscalRepository` (Spring Data MongoDB).
- `NotaFiscalController` em `/v1/notas-fiscais`:
  - `POST /{vendaId}/emitir`
  - `GET /{vendaId}/status` — reconsulta a Focus NFe e atualiza o registro
  - `POST /{vendaId}/cancelar` (body com justificativa)

Configuração via `application.properties`: `focusnfe.token`, `focusnfe.ambiente` (`homologacao` | `producao`, define a base URL da API).

**Mudança necessária em `VendaController`/`VendaService`**: hoje `POST /v1/vendas` responde com corpo vazio (`ResponseEntity.ok().build()`), descartando o `uid` gerado ao salvar. Para permitir emitir a nota logo após finalizar a venda, `VendaService.create` passa a retornar o `VendaDTO` salvo (com `uid` preenchido), e o controller devolve esse corpo na resposta. É uma mudança compatível — o frontend atual ignora o corpo da resposta, então nada quebra.

**Sem webhook**: a Focus NFe processa a emissão de forma assíncrona em alguns casos (`processando_autorizacao`). Como o backend roda em rede local sem URL pública, o sistema não expõe endpoint de callback — o status é atualizado por consulta sob demanda (botão "Verificar status" no frontend chamando `GET /status`).

### Tela de checkout (`add-venda.tsx`) — ação principal

O botão **Emitir Nota Fiscal** fica no mesmo grupo de botões do carrinho, ao lado de **Finalizar Compra** e **Imprimir** ([add-venda.tsx:696-706](../../../../vendas-front/vendas-front/src/components/venda/add-venda.tsx#L696-L706)). Fluxo:

1. Operador clica **Finalizar Compra** → `finalizarVenda()` chama `VendaService.create`, que agora retorna o `uid` da venda salva. Esse `uid` é guardado num novo campo de estado (`lastVendaUid`), separado do carrinho — assim, mesmo `newVenda()` limpando os itens para a próxima venda, o botão de nota fiscal continua sabendo qual venda emitir.
2. **Emitir Nota Fiscal** fica desabilitado até existir `lastVendaUid`; ao clicar, chama `POST /v1/notas-fiscais/{lastVendaUid}/emitir` e mostra o resultado inline, reaproveitando o componente `Alert` já usado para as mensagens de sucesso/erro da venda (mesmo padrão visual de `finalizaAlert`).
3. Se a nota ficar `PROCESSANDO`, o mesmo botão vira **Verificar Status** até resolver; quando `AUTORIZADA`, aparece o link **Ver DANFE** (abre o PDF da Focus NFe em nova aba, para impressão manual do cupom).
4. `lastVendaUid` é limpo quando uma nova venda é iniciada (próximo item adicionado ao carrinho ou `newVenda()`), evitando emitir nota para a venda errada.

### Histórico de vendas (`list-venda.tsx`) — acompanhamento posterior

No painel lateral da venda selecionada, mesma seção "Nota Fiscal" para os casos em que a emissão não foi feita no balcão (SEFAZ fora do ar, esquecimento) ou precisa ser cancelada depois:

- Badge de status (Não emitida / Processando / Autorizada / Rejeitada / Cancelada)
- Botão **Emitir Nota Fiscal** (habilitado quando `NAO_EMITIDA`, `REJEITADA` ou `ERRO` — permite reemitir)
- Botão **Verificar Status** (quando `PROCESSANDO`)
- Link **Ver DANFE** (quando `AUTORIZADA`)
- Botão **Cancelar Nota** (quando `AUTORIZADA`) com campo de justificativa obrigatório

Novos arquivos espelhando o padrão existente: `src/types/nota-fiscal.type.ts`, `src/services/nota-fiscal.service.ts`, compartilhados pelas duas telas.

Nos formulários de produto (`add-produto`/`edit-produto`), novos campos: NCM, CFOP, CSOSN, Unidade Comercial.

## Ambiente e testes

Desenvolvimento e testes inteiramente em `ambiente=homologacao` — a Focus NFe fornece CSC de teste automaticamente nesse ambiente, sem depender do credenciamento real na SEFAZ-RS. A migração para produção é uma troca de configuração (`token` + `ambiente=producao`) depois que o credenciamento/CSC forem confirmados com o contador.

## Fora de escopo (nesta primeira versão)

- Emissão automática ao fechar a venda (decidido: botão manual)
- NF-e modelo 55 (só NFC-e modelo 65)
- Contingência offline customizada (a Focus NFe já trata isso)
- Geração própria de XML/DANFE (usamos o PDF pronto da Focus NFe)
- Integração com impressora térmica
- Webhook de callback
- Captura de CPF/CNPJ do consumidor na venda
