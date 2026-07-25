# NFC-e Emission Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the sorveteria's operator emit a NFC-e (Nota Fiscal de Consumidor Eletrônica) for a sale, from a button placed at checkout, using Focus NFe as the fiscal gateway.

**Architecture:** A new `fiscal` package in the Spring Boot backend exposes a persistence-and-orchestration layer (`NotaFiscalService`/`NotaFiscalEntity`) on top of a gateway port (`EmissorFiscalService`), implemented today by `FocusNFeEmissorService` (HTTP calls to Focus NFe, no local certificate/XML handling). The React frontend gets one new shared component, `NotaFiscalPanel`, mounted both at checkout (`add-venda.tsx`, next to "Finalizar Compra") and in the sales history side panel (`list-venda.tsx`).

**Tech Stack:** Java 11, Spring Boot 2.5.2, Spring Data MongoDB, Lombok, ModelMapper, JUnit 5 + Mockito + Spring's `MockRestServiceServer`/`MockMvc` (all already on the test classpath via `spring-boot-starter-test`, no new backend dependency). React 17 + TypeScript, MUI v5, `@testing-library/react` (already a dependency, no new frontend dependency). External: Focus NFe REST API (`https://homologacao.focusnfe.com.br` / `https://api.focusnfe.com.br`, `/v2/nfce`), HTTP Basic Auth (token as username, blank password).

## Global Constraints

- Spec: [docs/superpowers/specs/2026-07-25-nfce-emissao-design.md](../specs/2026-07-25-nfce-emissao-design.md) — read it if anything here is ambiguous.
- Manual emission only — no automatic emission when a sale is finalized.
- No webhook endpoint — status is refreshed only on demand (`GET /v1/notas-fiscais/{vendaId}/status`), because the backend runs on a local network without a public URL.
- The Focus NFe API token is a secret. It must never be hardcoded or committed — read it from an environment variable with an empty default (`${FOCUSNFE_TOKEN:}`).
- Certificate upload and Focus NFe company registration happen in the Focus NFe dashboard, by the user, outside this codebase. Nothing in this plan touches the `.pfx` file.
- CFOP/CSOSN/NCM values entered by the user are trusted as-is; this system does not validate their fiscal correctness.
- NFC-e cancellation is only valid within 30 minutes of authorization, and Focus NFe requires `justificativa` to be 15–255 characters — enforce the minimum length client-side before calling the API.
- Existing test conventions: backend tests are plain JUnit 5 (+ Mockito where needed), no `@SpringBootTest`/real MongoDB required for anything in this plan (MongoDB is only needed to run the full app, not these tests). Frontend tests use `@testing-library/react`, run via `CI=true npm test -- --watchAll=false --testPathPattern=<name>`.

---

## Task 1: Produto fiscal fields (backend)

**Files:**
- Modify: `vendas/src/main/java/com/sorveteria/bomcream/vendas/repository/entity/ProdutoEntity.java`
- Modify: `vendas/src/main/java/com/sorveteria/bomcream/vendas/controller/dto/ProdutoDTO.java`
- Test: `vendas/src/test/java/com/sorveteria/bomcream/vendas/service/ProdutoFiscalMappingTest.java` (already exists, untracked — this task makes it compile and pass)

**Interfaces:**
- Produces: `ProdutoEntity`/`ProdutoDTO` gain `ncm`, `cfop`, `csosn`, `unidadeComercial` (all `String`) — every later task that reads a product's fiscal data (Task 3's `FocusNFeEmissorService`) depends on these exact getter names (`getNcm()`, `getCfop()`, `getCsosn()`, `getUnidadeComercial()`).

- [ ] **Step 1: Confirm the existing pending test currently fails to compile**

Run: `cd vendas && mvnw.cmd test -Dtest=ProdutoFiscalMappingTest`
Expected: BUILD FAILURE — compilation error, `ProdutoEntity.builder()` has no method `ncm(String)` (the field doesn't exist yet).

- [ ] **Step 2: Add the fiscal fields to `ProdutoEntity`**

```java
package com.sorveteria.bomcream.vendas.repository.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "produto")
public class ProdutoEntity {
    @Id
    private String uid;
    private String nome;
    private BigDecimal valor;
    private String tipoMedida;
    private String categoria;
    private String ncm;
    private String cfop;
    private String csosn;
    private String unidadeComercial;
    private LocalDateTime create = LocalDateTime.now();
}
```

- [ ] **Step 3: Add the same fields to `ProdutoDTO`**

```java
package com.sorveteria.bomcream.vendas.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProdutoDTO {
    private String uid;
    private String nome;
    private BigDecimal valor;
    private String tipoMedida;
    private String categoria;
    private String ncm;
    private String cfop;
    private String csosn;
    private String unidadeComercial;
}
```

- [ ] **Step 4: Run the test again to confirm it passes**

Run: `cd vendas && mvnw.cmd test -Dtest=ProdutoFiscalMappingTest`
Expected: BUILD SUCCESS, 1 test run, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/sorveteria/bomcream/vendas/repository/entity/ProdutoEntity.java src/main/java/com/sorveteria/bomcream/vendas/controller/dto/ProdutoDTO.java src/test/java/com/sorveteria/bomcream/vendas/service/ProdutoFiscalMappingTest.java
git commit -m "Add fiscal fields (NCM/CFOP/CSOSN/unidade comercial) to Produto"
```

---

## Task 2: `POST /v1/vendas` returns the created venda (backend)

**Files:**
- Modify: `vendas/src/main/java/com/sorveteria/bomcream/vendas/service/VendaService.java`
- Modify: `vendas/src/main/java/com/sorveteria/bomcream/vendas/controller/VendaController.java`
- Test: Create `vendas/src/test/java/com/sorveteria/bomcream/vendas/service/VendaServiceTest.java`

**Interfaces:**
- Consumes: `VendaRepository.save(VendaEntity): VendaEntity` (existing, from `PagingAndSortingRepository`).
- Produces: `VendaService.create(VendaDTO): VendaDTO` (previously returned `void`) — the returned DTO's `uid` is what the frontend (Task 6) will use to call the nota fiscal endpoints.

- [ ] **Step 1: Write the failing test**

```java
package com.sorveteria.bomcream.vendas.service;

import com.sorveteria.bomcream.vendas.controller.dto.VendaDTO;
import com.sorveteria.bomcream.vendas.repository.VendaRepository;
import com.sorveteria.bomcream.vendas.repository.entity.VendaEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VendaServiceTest {

    @Mock
    private VendaRepository repository;

    @Test
    void createReturnsTheGeneratedUid() {
        VendaService service = new VendaService(repository, new ModelMapper());

        VendaDTO input = VendaDTO.builder()
                .caixa("caixa1")
                .itens(new ArrayList<>())
                .valorDesconto(BigDecimal.ZERO)
                .valorTotal(BigDecimal.TEN)
                .valorPago(BigDecimal.TEN)
                .valorTroco(BigDecimal.ZERO)
                .formaPagamento("Dinheiro")
                .create(LocalDateTime.of(2026, 7, 25, 10, 0))
                .build();

        VendaEntity saved = VendaEntity.builder()
                .uid("venda-123")
                .caixa("caixa1")
                .itens(new ArrayList<>())
                .valorDesconto(BigDecimal.ZERO)
                .valorTotal(BigDecimal.TEN)
                .valorPago(BigDecimal.TEN)
                .valorTroco(BigDecimal.ZERO)
                .formaPagamento("Dinheiro")
                .build();

        when(repository.save(any(VendaEntity.class))).thenReturn(saved);

        VendaDTO result = service.create(input);

        assertEquals("venda-123", result.getUid());
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd vendas && mvnw.cmd test -Dtest=VendaServiceTest`
Expected: BUILD FAILURE — compilation error, `VendaService.create` returns `void` so `result` can't be assigned.

- [ ] **Step 3: Change `VendaService.create` to return the saved DTO**

In `VendaService.java`, replace:

```java
    public void create(VendaDTO dto) {
        repository.save(mapper.map(dto, VendaEntity.class));
    }
```

with:

```java
    public VendaDTO create(VendaDTO dto) {
        VendaEntity saved = repository.save(mapper.map(dto, VendaEntity.class));
        return converterEntityToDTO(saved);
    }
```

- [ ] **Step 4: Update the controller to return that body**

In `VendaController.java`, replace:

```java
    @PostMapping
    public ResponseEntity create(@RequestBody VendaDTO dto) {
        service.create(dto);
        return ResponseEntity.ok().build();
    }
```

with:

```java
    @PostMapping
    public ResponseEntity create(@RequestBody VendaDTO dto) {
        return ResponseEntity.ok(service.create(dto));
    }
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd vendas && mvnw.cmd test -Dtest=VendaServiceTest`
Expected: BUILD SUCCESS, 1 test run, 0 failures.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/sorveteria/bomcream/vendas/service/VendaService.java src/main/java/com/sorveteria/bomcream/vendas/controller/VendaController.java src/test/java/com/sorveteria/bomcream/vendas/service/VendaServiceTest.java
git commit -m "Return created venda (with uid) from POST /v1/vendas"
```

---

## Task 3: Focus NFe gateway client (backend)

**Files:**
- Create: `vendas/src/main/java/com/sorveteria/bomcream/vendas/fiscal/NotaFiscalStatus.java`
- Create: `vendas/src/main/java/com/sorveteria/bomcream/vendas/fiscal/ResultadoEmissaoFiscal.java`
- Create: `vendas/src/main/java/com/sorveteria/bomcream/vendas/fiscal/EmissorFiscalService.java`
- Create: `vendas/src/main/java/com/sorveteria/bomcream/vendas/fiscal/FocusNFeEmissorService.java`
- Create: `vendas/src/main/java/com/sorveteria/bomcream/vendas/fiscal/FiscalConfig.java`
- Modify: `vendas/src/main/resources/application.properties`
- Test: Create `vendas/src/test/java/com/sorveteria/bomcream/vendas/fiscal/FocusNFeEmissorServiceTest.java`

**Interfaces:**
- Consumes: `ProdutoEntity.getNcm()/getCfop()/getCsosn()/getUnidadeComercial()` (Task 1), `VendaEntity.getUid()/getItens()/getFormaPagamento()/getValorTotal()/getCreate()`, `ItemVendaEntity.getProduto()/getQuantidade()/getValorItem()`.
- Produces: `EmissorFiscalService` interface with `emitir(VendaEntity): ResultadoEmissaoFiscal`, `consultarStatus(String referencia): ResultadoEmissaoFiscal`, `cancelar(String referencia, String justificativa): ResultadoEmissaoFiscal`. `ResultadoEmissaoFiscal` has getters/setters for `status` (`NotaFiscalStatus`), `numero`, `serie`, `chaveAcesso`, `protocoloAutorizacao`, `mensagemSefaz`, `urlDanfe`. `NotaFiscalStatus` enum: `NAO_EMITIDA, PROCESSANDO, AUTORIZADA, REJEITADA, CANCELADA, ERRO`. Task 4 (`NotaFiscalService`) consumes all of this.

- [ ] **Step 1: Write the failing test**

```java
package com.sorveteria.bomcream.vendas.fiscal;

import com.sorveteria.bomcream.vendas.repository.entity.ItemVendaEntity;
import com.sorveteria.bomcream.vendas.repository.entity.ProdutoEntity;
import com.sorveteria.bomcream.vendas.repository.entity.VendaEntity;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FocusNFeEmissorServiceTest {

    @Test
    void emitirEnviaOPayloadEInterpretaAAutorizacao() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        FocusNFeEmissorService service = new FocusNFeEmissorService(
                restTemplate, "TOKEN123", "homologacao", "35242747000130");

        ProdutoEntity produto = ProdutoEntity.builder()
                .uid("p1").nome("Sorvete 500ml").valor(new BigDecimal("15.00"))
                .ncm("21050010").cfop("5102").csosn("102").unidadeComercial("UN")
                .build();

        ItemVendaEntity item = ItemVendaEntity.builder()
                .produto(produto).quantidade(BigDecimal.ONE).valorItem(new BigDecimal("15.00"))
                .build();

        VendaEntity venda = VendaEntity.builder()
                .uid("venda-1")
                .itens(List.of(item))
                .valorTotal(new BigDecimal("15.00"))
                .formaPagamento("Dinheiro")
                .create(LocalDateTime.of(2026, 7, 25, 10, 0))
                .build();

        server.expect(requestTo("https://homologacao.focusnfe.com.br/v2/nfce?ref=venda-1"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", org.hamcrest.Matchers.startsWith("Basic ")))
                .andExpect(jsonPath("$.items[0].cfop").value("5102"))
                .andExpect(jsonPath("$.items[0].ncm").value("21050010"))
                .andExpect(jsonPath("$.formas_pagamento[0].forma_pagamento").value("01"))
                .andRespond(withSuccess(
                        "{\"status\":\"autorizado\",\"numero\":\"12\",\"serie\":\"1\","
                                + "\"chave_nfe\":\"CHAVE123\",\"caminho_danfe\":\"https://focusnfe/danfe/1\"}",
                        MediaType.APPLICATION_JSON));

        ResultadoEmissaoFiscal resultado = service.emitir(venda);

        assertEquals(NotaFiscalStatus.AUTORIZADA, resultado.getStatus());
        assertEquals("CHAVE123", resultado.getChaveAcesso());
        assertEquals("https://focusnfe/danfe/1", resultado.getUrlDanfe());
        server.verify();
    }

    @Test
    void mapeiaFormasDePagamentoParaOsCodigosDaFocusNFe() {
        FocusNFeEmissorService service = new FocusNFeEmissorService(
                new RestTemplate(), "TOKEN123", "homologacao", "35242747000130");

        assertEquals("01", service.mapearFormaPagamento("Dinheiro"));
        assertEquals("02", service.mapearFormaPagamento("Credito"));
        assertEquals("03", service.mapearFormaPagamento("Debito"));
        assertEquals("12", service.mapearFormaPagamento("PIX"));
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd vendas && mvnw.cmd test -Dtest=FocusNFeEmissorServiceTest`
Expected: BUILD FAILURE — compilation error, none of `FocusNFeEmissorService`/`ResultadoEmissaoFiscal`/`NotaFiscalStatus` exist yet.

- [ ] **Step 3: Create the status enum**

```java
package com.sorveteria.bomcream.vendas.fiscal;

public enum NotaFiscalStatus {
    NAO_EMITIDA,
    PROCESSANDO,
    AUTORIZADA,
    REJEITADA,
    CANCELADA,
    ERRO
}
```

- [ ] **Step 4: Create the gateway's result value object**

```java
package com.sorveteria.bomcream.vendas.fiscal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResultadoEmissaoFiscal {
    private NotaFiscalStatus status;
    private String numero;
    private String serie;
    private String chaveAcesso;
    private String protocoloAutorizacao;
    private String mensagemSefaz;
    private String urlDanfe;
}
```

- [ ] **Step 5: Create the gateway port interface**

```java
package com.sorveteria.bomcream.vendas.fiscal;

import com.sorveteria.bomcream.vendas.repository.entity.VendaEntity;

public interface EmissorFiscalService {
    ResultadoEmissaoFiscal emitir(VendaEntity venda);

    ResultadoEmissaoFiscal consultarStatus(String referencia);

    ResultadoEmissaoFiscal cancelar(String referencia, String justificativa);
}
```

- [ ] **Step 6: Create the Focus NFe implementation**

```java
package com.sorveteria.bomcream.vendas.fiscal;

import com.fasterxml.jackson.databind.JsonNode;
import com.sorveteria.bomcream.vendas.repository.entity.ItemVendaEntity;
import com.sorveteria.bomcream.vendas.repository.entity.ProdutoEntity;
import com.sorveteria.bomcream.vendas.repository.entity.VendaEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FocusNFeEmissorService implements EmissorFiscalService {

    private final RestTemplate restTemplate;
    private final String token;
    private final String ambiente;
    private final String cnpjEmitente;

    public FocusNFeEmissorService(RestTemplate restTemplate,
                                   @Value("${focusnfe.token}") String token,
                                   @Value("${focusnfe.ambiente}") String ambiente,
                                   @Value("${focusnfe.cnpj-emitente}") String cnpjEmitente) {
        this.restTemplate = restTemplate;
        this.token = token;
        this.ambiente = ambiente;
        this.cnpjEmitente = cnpjEmitente;
    }

    @Override
    public ResultadoEmissaoFiscal emitir(VendaEntity venda) {
        String url = baseUrl() + "/v2/nfce?ref=" + venda.getUid();
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(montarPayload(venda), headers());
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, request, JsonNode.class);
        return interpretarResposta(response.getBody());
    }

    @Override
    public ResultadoEmissaoFiscal consultarStatus(String referencia) {
        String url = baseUrl() + "/v2/nfce/" + referencia;
        HttpEntity<Void> request = new HttpEntity<>(headers());
        ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, request, JsonNode.class);
        return interpretarResposta(response.getBody());
    }

    @Override
    public ResultadoEmissaoFiscal cancelar(String referencia, String justificativa) {
        String url = baseUrl() + "/v2/nfce/" + referencia;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("justificativa", justificativa);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers());
        ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.DELETE, request, JsonNode.class);
        ResultadoEmissaoFiscal resultado = interpretarResposta(response.getBody());
        resultado.setStatus(NotaFiscalStatus.CANCELADA);
        return resultado;
    }

    private String baseUrl() {
        return "producao".equalsIgnoreCase(ambiente)
                ? "https://api.focusnfe.com.br"
                : "https://homologacao.focusnfe.com.br";
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(token, "");
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    Map<String, Object> montarPayload(VendaEntity venda) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("cnpj_emitente", cnpjEmitente);
        payload.put("data_emissao", venda.getCreate()
                .atZone(ZoneId.of("America/Sao_Paulo"))
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        payload.put("presenca_comprador", "1");
        payload.put("modalidade_frete", "9");
        payload.put("local_destino", "1");
        payload.put("items", montarItens(venda));
        payload.put("formas_pagamento", montarFormasPagamento(venda));
        return payload;
    }

    private List<Map<String, Object>> montarItens(VendaEntity venda) {
        List<Map<String, Object>> itens = new ArrayList<>();
        int numero = 1;
        for (ItemVendaEntity item : venda.getItens()) {
            ProdutoEntity produto = item.getProduto();
            Map<String, Object> itemPayload = new LinkedHashMap<>();
            itemPayload.put("numero_item", numero++);
            itemPayload.put("codigo_produto", produto.getUid());
            itemPayload.put("descricao", produto.getNome());
            itemPayload.put("ncm", produto.getNcm());
            itemPayload.put("cfop", produto.getCfop());
            itemPayload.put("unidade_comercial", produto.getUnidadeComercial());
            itemPayload.put("quantidade_comercial", item.getQuantidade());
            itemPayload.put("valor_unitario_comercial", produto.getValor());
            itemPayload.put("valor_bruto", item.getValorItem());
            itemPayload.put("icms_origem", "0");
            itemPayload.put("icms_situacao_tributaria", produto.getCsosn());
            itens.add(itemPayload);
        }
        return itens;
    }

    private List<Map<String, Object>> montarFormasPagamento(VendaEntity venda) {
        Map<String, Object> forma = new LinkedHashMap<>();
        forma.put("forma_pagamento", mapearFormaPagamento(venda.getFormaPagamento()));
        forma.put("valor", venda.getValorTotal());
        List<Map<String, Object>> formas = new ArrayList<>();
        formas.add(forma);
        return formas;
    }

    String mapearFormaPagamento(String formaPagamento) {
        switch (formaPagamento) {
            case "Dinheiro":
                return "01";
            case "Credito":
                return "02";
            case "Debito":
                return "03";
            case "PIX":
                return "12";
            default:
                throw new IllegalArgumentException(
                        "Forma de pagamento não suportada para NFC-e: " + formaPagamento);
        }
    }

    private ResultadoEmissaoFiscal interpretarResposta(JsonNode json) {
        return ResultadoEmissaoFiscal.builder()
                .status(mapearStatus(textoOuNulo(json, "status")))
                .numero(textoOuNulo(json, "numero"))
                .serie(textoOuNulo(json, "serie"))
                .chaveAcesso(textoOuNulo(json, "chave_nfe"))
                .protocoloAutorizacao(textoOuNulo(json, "protocolo_autorizacao"))
                .mensagemSefaz(textoOuNulo(json, "mensagem_sefaz"))
                .urlDanfe(textoOuNulo(json, "caminho_danfe"))
                .build();
    }

    private String textoOuNulo(JsonNode json, String campo) {
        JsonNode node = json.get(campo);
        return node == null || node.isNull() ? null : node.asText();
    }

    private NotaFiscalStatus mapearStatus(String status) {
        if (status == null) {
            return NotaFiscalStatus.ERRO;
        }
        switch (status) {
            case "autorizado":
                return NotaFiscalStatus.AUTORIZADA;
            case "processando_autorizacao":
                return NotaFiscalStatus.PROCESSANDO;
            case "erro_autorizacao":
                return NotaFiscalStatus.REJEITADA;
            case "cancelado":
                return NotaFiscalStatus.CANCELADA;
            default:
                return NotaFiscalStatus.ERRO;
        }
    }
}
```

**Note on field names:** `protocolo_autorizacao` and `mensagem_sefaz` are best-effort names based on Focus NFe's documented response shape for `status`/`chave_nfe`/`caminho_danfe` (confirmed) — Task 7 includes a manual call against the real homologação sandbox specifically to confirm or correct these two field names once a Focus NFe account exists.

- [ ] **Step 7: Create the `RestTemplate` bean**

```java
package com.sorveteria.bomcream.vendas.fiscal;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class FiscalConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }
}
```

- [ ] **Step 8: Add the configuration properties**

Append to `vendas/src/main/resources/application.properties`:

```properties
focusnfe.token=${FOCUSNFE_TOKEN:}
focusnfe.ambiente=${FOCUSNFE_AMBIENTE:homologacao}
focusnfe.cnpj-emitente=35242747000130
```

- [ ] **Step 9: Run the test to verify it passes**

Run: `cd vendas && mvnw.cmd test -Dtest=FocusNFeEmissorServiceTest`
Expected: BUILD SUCCESS, 2 tests run, 0 failures.

- [ ] **Step 10: Commit**

```bash
git add src/main/java/com/sorveteria/bomcream/vendas/fiscal/NotaFiscalStatus.java src/main/java/com/sorveteria/bomcream/vendas/fiscal/ResultadoEmissaoFiscal.java src/main/java/com/sorveteria/bomcream/vendas/fiscal/EmissorFiscalService.java src/main/java/com/sorveteria/bomcream/vendas/fiscal/FocusNFeEmissorService.java src/main/java/com/sorveteria/bomcream/vendas/fiscal/FiscalConfig.java src/main/resources/application.properties src/test/java/com/sorveteria/bomcream/vendas/fiscal/FocusNFeEmissorServiceTest.java
git commit -m "Add Focus NFe gateway client for NFC-e emission"
```

---

## Task 4: Nota fiscal persistence + orchestration (backend)

**Files:**
- Create: `vendas/src/main/java/com/sorveteria/bomcream/vendas/fiscal/NotaFiscalEntity.java`
- Create: `vendas/src/main/java/com/sorveteria/bomcream/vendas/fiscal/NotaFiscalRepository.java`
- Create: `vendas/src/main/java/com/sorveteria/bomcream/vendas/fiscal/NotaFiscalService.java`
- Test: Create `vendas/src/test/java/com/sorveteria/bomcream/vendas/fiscal/NotaFiscalServiceTest.java`

**Interfaces:**
- Consumes: `EmissorFiscalService.emitir/consultarStatus/cancelar` (Task 3), `VendaRepository.findById(String): Optional<VendaEntity>` (existing).
- Produces: `NotaFiscalService.emitir(String vendaId): NotaFiscalEntity`, `consultarStatus(String vendaId): NotaFiscalEntity`, `cancelar(String vendaId, String justificativa): NotaFiscalEntity` — Task 5 (`NotaFiscalController`) calls these three methods directly.

- [ ] **Step 1: Write the failing test**

```java
package com.sorveteria.bomcream.vendas.fiscal;

import com.sorveteria.bomcream.vendas.repository.VendaRepository;
import com.sorveteria.bomcream.vendas.repository.entity.VendaEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotaFiscalServiceTest {

    @Mock
    private NotaFiscalRepository notaFiscalRepository;

    @Mock
    private EmissorFiscalService emissorFiscalService;

    @Mock
    private VendaRepository vendaRepository;

    @Test
    void emitirCriaUmaNotaQuandoNaoExisteRegistroAnterior() {
        NotaFiscalService service = new NotaFiscalService(notaFiscalRepository, emissorFiscalService, vendaRepository);

        VendaEntity venda = VendaEntity.builder().uid("venda-1").build();
        when(vendaRepository.findById("venda-1")).thenReturn(Optional.of(venda));
        when(notaFiscalRepository.findByVendaUid("venda-1")).thenReturn(Optional.empty());
        when(emissorFiscalService.emitir(venda)).thenReturn(
                ResultadoEmissaoFiscal.builder()
                        .status(NotaFiscalStatus.AUTORIZADA)
                        .chaveAcesso("CHAVE123")
                        .urlDanfe("https://focusnfe/danfe/1")
                        .build());
        when(notaFiscalRepository.save(any(NotaFiscalEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NotaFiscalEntity resultado = service.emitir("venda-1");

        assertEquals("venda-1", resultado.getVendaUid());
        assertEquals(NotaFiscalStatus.AUTORIZADA, resultado.getStatus());
        assertEquals("CHAVE123", resultado.getChaveAcesso());
        assertEquals("https://focusnfe/danfe/1", resultado.getUrlDanfe());
    }

    @Test
    void cancelarRegistraAJustificativaEAData() {
        NotaFiscalService service = new NotaFiscalService(notaFiscalRepository, emissorFiscalService, vendaRepository);

        NotaFiscalEntity notaExistente = NotaFiscalEntity.builder()
                .uid("nota-1").vendaUid("venda-1").status(NotaFiscalStatus.AUTORIZADA)
                .build();
        when(notaFiscalRepository.findByVendaUid("venda-1")).thenReturn(Optional.of(notaExistente));
        when(emissorFiscalService.cancelar("venda-1", "Erro no valor do item"))
                .thenReturn(ResultadoEmissaoFiscal.builder().status(NotaFiscalStatus.CANCELADA).build());
        when(notaFiscalRepository.save(any(NotaFiscalEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NotaFiscalEntity resultado = service.cancelar("venda-1", "Erro no valor do item");

        assertEquals(NotaFiscalStatus.CANCELADA, resultado.getStatus());
        assertEquals("Erro no valor do item", resultado.getJustificativaCancelamento());
        assertEquals("nota-1", resultado.getUid());
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd vendas && mvnw.cmd test -Dtest=NotaFiscalServiceTest`
Expected: BUILD FAILURE — `NotaFiscalEntity`, `NotaFiscalRepository`, `NotaFiscalService` don't exist yet.

- [ ] **Step 3: Create the Mongo entity**

```java
package com.sorveteria.bomcream.vendas.fiscal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "nota_fiscal")
public class NotaFiscalEntity {
    @Id
    private String uid;
    private String vendaUid;
    private NotaFiscalStatus status;
    private String numero;
    private String serie;
    private String chaveAcesso;
    private String protocoloAutorizacao;
    private String mensagemSefaz;
    private String urlDanfe;
    private LocalDateTime dataEmissao;
    private LocalDateTime dataCancelamento;
    private String justificativaCancelamento;
}
```

- [ ] **Step 4: Create the repository**

```java
package com.sorveteria.bomcream.vendas.fiscal;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface NotaFiscalRepository extends MongoRepository<NotaFiscalEntity, String> {
    Optional<NotaFiscalEntity> findByVendaUid(String vendaUid);
}
```

- [ ] **Step 5: Create the orchestration service**

```java
package com.sorveteria.bomcream.vendas.fiscal;

import com.sorveteria.bomcream.vendas.repository.VendaRepository;
import com.sorveteria.bomcream.vendas.repository.entity.VendaEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class NotaFiscalService {
    private final NotaFiscalRepository notaFiscalRepository;
    private final EmissorFiscalService emissorFiscalService;
    private final VendaRepository vendaRepository;

    public NotaFiscalEntity emitir(String vendaId) {
        VendaEntity venda = vendaRepository.findById(vendaId)
                .orElseThrow(() -> new RuntimeException("Venda não encontrada"));

        NotaFiscalEntity nota = notaFiscalRepository.findByVendaUid(vendaId)
                .orElseGet(() -> NotaFiscalEntity.builder()
                        .vendaUid(vendaId)
                        .status(NotaFiscalStatus.NAO_EMITIDA)
                        .build());

        ResultadoEmissaoFiscal resultado = emissorFiscalService.emitir(venda);
        aplicarResultado(nota, resultado);
        nota.setDataEmissao(LocalDateTime.now());
        return notaFiscalRepository.save(nota);
    }

    public NotaFiscalEntity consultarStatus(String vendaId) {
        NotaFiscalEntity nota = notaFiscalRepository.findByVendaUid(vendaId)
                .orElseThrow(() -> new RuntimeException("Nota fiscal não encontrada para esta venda"));

        ResultadoEmissaoFiscal resultado = emissorFiscalService.consultarStatus(vendaId);
        aplicarResultado(nota, resultado);
        return notaFiscalRepository.save(nota);
    }

    public NotaFiscalEntity cancelar(String vendaId, String justificativa) {
        NotaFiscalEntity nota = notaFiscalRepository.findByVendaUid(vendaId)
                .orElseThrow(() -> new RuntimeException("Nota fiscal não encontrada para esta venda"));

        ResultadoEmissaoFiscal resultado = emissorFiscalService.cancelar(vendaId, justificativa);
        aplicarResultado(nota, resultado);
        nota.setDataCancelamento(LocalDateTime.now());
        nota.setJustificativaCancelamento(justificativa);
        return notaFiscalRepository.save(nota);
    }

    private void aplicarResultado(NotaFiscalEntity nota, ResultadoEmissaoFiscal resultado) {
        nota.setStatus(resultado.getStatus());
        nota.setNumero(resultado.getNumero());
        nota.setSerie(resultado.getSerie());
        nota.setChaveAcesso(resultado.getChaveAcesso());
        nota.setProtocoloAutorizacao(resultado.getProtocoloAutorizacao());
        nota.setMensagemSefaz(resultado.getMensagemSefaz());
        nota.setUrlDanfe(resultado.getUrlDanfe());
    }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `cd vendas && mvnw.cmd test -Dtest=NotaFiscalServiceTest`
Expected: BUILD SUCCESS, 2 tests run, 0 failures.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/sorveteria/bomcream/vendas/fiscal/NotaFiscalEntity.java src/main/java/com/sorveteria/bomcream/vendas/fiscal/NotaFiscalRepository.java src/main/java/com/sorveteria/bomcream/vendas/fiscal/NotaFiscalService.java src/test/java/com/sorveteria/bomcream/vendas/fiscal/NotaFiscalServiceTest.java
git commit -m "Add nota fiscal persistence and orchestration service"
```

---

## Task 5: Nota fiscal REST controller (backend)

**Files:**
- Create: `vendas/src/main/java/com/sorveteria/bomcream/vendas/fiscal/CancelarNotaFiscalDTO.java`
- Create: `vendas/src/main/java/com/sorveteria/bomcream/vendas/fiscal/NotaFiscalController.java`
- Test: Create `vendas/src/test/java/com/sorveteria/bomcream/vendas/fiscal/NotaFiscalControllerTest.java`

**Interfaces:**
- Consumes: `NotaFiscalService.emitir/consultarStatus/cancelar` (Task 4).
- Produces: `POST /v1/notas-fiscais/{vendaId}/emitir`, `GET /v1/notas-fiscais/{vendaId}/status`, `POST /v1/notas-fiscais/{vendaId}/cancelar` (body `{"justificativa": "..."}"`) — Task 6's `nota-fiscal.service.ts` calls these three routes with these exact paths and methods.

- [ ] **Step 1: Write the failing test**

```java
package com.sorveteria.bomcream.vendas.fiscal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NotaFiscalControllerTest {

    @Mock
    private NotaFiscalService service;

    @Test
    void emitirRetornaANotaAutorizada() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new NotaFiscalController(service)).build();

        NotaFiscalEntity nota = NotaFiscalEntity.builder()
                .uid("nota-1").vendaUid("venda-1").status(NotaFiscalStatus.AUTORIZADA)
                .chaveAcesso("CHAVE123").build();
        when(service.emitir("venda-1")).thenReturn(nota);

        mockMvc.perform(post("/v1/notas-fiscais/venda-1/emitir"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTORIZADA"))
                .andExpect(jsonPath("$.chaveAcesso").value("CHAVE123"));
    }

    @Test
    void statusConsultaPelaVenda() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new NotaFiscalController(service)).build();

        NotaFiscalEntity nota = NotaFiscalEntity.builder()
                .uid("nota-1").vendaUid("venda-1").status(NotaFiscalStatus.PROCESSANDO).build();
        when(service.consultarStatus("venda-1")).thenReturn(nota);

        mockMvc.perform(get("/v1/notas-fiscais/venda-1/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSANDO"));
    }

    @Test
    void cancelarEnviaAJustificativaAoServico() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new NotaFiscalController(service)).build();

        NotaFiscalEntity nota = NotaFiscalEntity.builder()
                .uid("nota-1").vendaUid("venda-1").status(NotaFiscalStatus.CANCELADA).build();
        when(service.cancelar("venda-1", "Erro no valor do item")).thenReturn(nota);

        String body = new ObjectMapper().writeValueAsString(
                CancelarNotaFiscalDTO.builder().justificativa("Erro no valor do item").build());

        mockMvc.perform(post("/v1/notas-fiscais/venda-1/cancelar")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADA"));
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd vendas && mvnw.cmd test -Dtest=NotaFiscalControllerTest`
Expected: BUILD FAILURE — `NotaFiscalController`/`CancelarNotaFiscalDTO` don't exist yet.

- [ ] **Step 3: Create the cancellation request DTO**

```java
package com.sorveteria.bomcream.vendas.fiscal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelarNotaFiscalDTO {
    private String justificativa;
}
```

- [ ] **Step 4: Create the controller**

```java
package com.sorveteria.bomcream.vendas.fiscal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/notas-fiscais")
@RequiredArgsConstructor
public class NotaFiscalController {
    private final NotaFiscalService service;

    @PostMapping("/{vendaId}/emitir")
    public ResponseEntity emitir(@PathVariable String vendaId) {
        try {
            return ResponseEntity.ok(service.emitir(vendaId));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    @GetMapping("/{vendaId}/status")
    public ResponseEntity status(@PathVariable String vendaId) {
        try {
            return ResponseEntity.ok(service.consultarStatus(vendaId));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    @PostMapping("/{vendaId}/cancelar")
    public ResponseEntity cancelar(@PathVariable String vendaId, @RequestBody CancelarNotaFiscalDTO dto) {
        try {
            return ResponseEntity.ok(service.cancelar(vendaId, dto.getJustificativa()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd vendas && mvnw.cmd test -Dtest=NotaFiscalControllerTest`
Expected: BUILD SUCCESS, 3 tests run, 0 failures.

- [ ] **Step 6: Run the full backend test suite before moving to the frontend**

Run: `cd vendas && mvnw.cmd test`
Expected: BUILD SUCCESS (note: `VendasApplicationTests` requires a local MongoDB running on `localhost:27017`, per the project's existing setup — start it first if it isn't already running).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/sorveteria/bomcream/vendas/fiscal/CancelarNotaFiscalDTO.java src/main/java/com/sorveteria/bomcream/vendas/fiscal/NotaFiscalController.java src/test/java/com/sorveteria/bomcream/vendas/fiscal/NotaFiscalControllerTest.java
git commit -m "Add nota fiscal REST endpoints (emitir/status/cancelar)"
```

---

## Task 6: `NotaFiscalPanel` shared component (frontend)

**Files:**
- Create: `vendas-front/vendas-front/src/types/nota-fiscal.type.ts`
- Create: `vendas-front/vendas-front/src/services/nota-fiscal.service.ts`
- Create: `vendas-front/vendas-front/src/components/venda/nota-fiscal-panel.tsx`
- Test: Create `vendas-front/vendas-front/src/components/venda/nota-fiscal-panel.test.tsx`

**Interfaces:**
- Consumes: `http` default export from `../../http-common` (existing axios instance).
- Produces: `NotaFiscalDTO` type (`vendaUid`, `status: "NAO_EMITIDA"|"PROCESSANDO"|"AUTORIZADA"|"REJEITADA"|"CANCELADA"|"ERRO"`, `urlDanfe`, etc.), `NotaFiscalService.emitir(vendaId): Promise`, `.status(vendaId): Promise`, `.cancelar(vendaId, justificativa): Promise`, and `<NotaFiscalPanel vendaUid={string | null} />` — Tasks 7 and 8 import and render this component.

- [ ] **Step 1: Write the failing test**

```tsx
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import NotaFiscalPanel from './nota-fiscal-panel';
import NotaFiscalService from '../../services/nota-fiscal.service';

jest.mock('../../services/nota-fiscal.service');

const mockedService = NotaFiscalService as jest.Mocked<typeof NotaFiscalService>;

describe('NotaFiscalPanel', () => {
  afterEach(() => {
    jest.clearAllMocks();
  });

  it('disables the emit button when there is no venda yet', () => {
    render(<NotaFiscalPanel vendaUid={null} />);
    expect(screen.getByRole('button', { name: 'Emitir Nota Fiscal' })).toBeDisabled();
  });

  it('emits the note and shows the DANFE link once authorized', async () => {
    mockedService.emitir.mockResolvedValue({
      data: { vendaUid: 'venda-1', status: 'AUTORIZADA', urlDanfe: 'https://focusnfe/danfe/1' },
    } as any);

    render(<NotaFiscalPanel vendaUid="venda-1" />);
    fireEvent.click(screen.getByRole('button', { name: 'Emitir Nota Fiscal' }));

    await waitFor(() => {
      expect(screen.getByRole('link', { name: 'Ver DANFE' })).toHaveAttribute(
        'href',
        'https://focusnfe/danfe/1'
      );
    });
    expect(mockedService.emitir).toHaveBeenCalledWith('venda-1');
  });

  it('requires a justificativa of at least 15 characters to cancel', async () => {
    mockedService.emitir.mockResolvedValue({
      data: { vendaUid: 'venda-1', status: 'AUTORIZADA', urlDanfe: 'https://focusnfe/danfe/1' },
    } as any);

    render(<NotaFiscalPanel vendaUid="venda-1" />);
    fireEvent.click(screen.getByRole('button', { name: 'Emitir Nota Fiscal' }));
    await waitFor(() => screen.getByRole('link', { name: 'Ver DANFE' }));

    fireEvent.click(screen.getByRole('button', { name: 'Cancelar Nota' }));
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar Cancelamento' }));

    expect(await screen.findByText(/pelo menos 15 caracteres/)).toBeInTheDocument();
    expect(mockedService.cancelar).not.toHaveBeenCalled();
  });
});
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd vendas-front/vendas-front && CI=true npm test -- --watchAll=false --testPathPattern=nota-fiscal-panel`
Expected: FAIL — `Cannot find module './nota-fiscal-panel'`.

- [ ] **Step 3: Create the type**

```ts
export type NotaFiscalStatusType =
  | "NAO_EMITIDA"
  | "PROCESSANDO"
  | "AUTORIZADA"
  | "REJEITADA"
  | "CANCELADA"
  | "ERRO";

export default interface NotaFiscalDTO {
  uid?: string | null;
  vendaUid: string;
  status: NotaFiscalStatusType;
  numero?: string | null;
  serie?: string | null;
  chaveAcesso?: string | null;
  protocoloAutorizacao?: string | null;
  mensagemSefaz?: string | null;
  urlDanfe?: string | null;
  dataEmissao?: string | null;
  dataCancelamento?: string | null;
  justificativaCancelamento?: string | null;
}
```

- [ ] **Step 4: Create the service**

```ts
import http from "../http-common";
import NotaFiscalDTO from "../types/nota-fiscal.type";

class NotaFiscalService {

  emitir(vendaId: string) {
    return http.post<NotaFiscalDTO>(`/notas-fiscais/${vendaId}/emitir`);
  }

  status(vendaId: string) {
    return http.get<NotaFiscalDTO>(`/notas-fiscais/${vendaId}/status`);
  }

  cancelar(vendaId: string, justificativa: string) {
    return http.post<NotaFiscalDTO>(`/notas-fiscais/${vendaId}/cancelar`, { justificativa });
  }

}

export default new NotaFiscalService();
```

- [ ] **Step 5: Create the component**

```tsx
import { useEffect, useState } from "react";
import { Alert, Box, Button, TextField } from "@mui/material";
import NotaFiscalDTO from "../../types/nota-fiscal.type";
import NotaFiscalService from "../../services/nota-fiscal.service";

type Props = {
  vendaUid: string | null;
};

export default function NotaFiscalPanel({ vendaUid }: Props) {
  const [nota, setNota] = useState<NotaFiscalDTO | null>(null);
  const [carregando, setCarregando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const [justificativa, setJustificativa] = useState("");
  const [mostrarCancelamento, setMostrarCancelamento] = useState(false);

  useEffect(() => {
    setNota(null);
    setErro(null);
    setMostrarCancelamento(false);
    setJustificativa("");
  }, [vendaUid]);

  function emitir() {
    if (!vendaUid) {
      return;
    }
    setCarregando(true);
    setErro(null);
    NotaFiscalService.emitir(vendaUid)
      .then((response) => {
        setNota(response.data);
        setCarregando(false);
      })
      .catch(() => {
        setErro("Não foi possível emitir a nota fiscal. Tente novamente.");
        setCarregando(false);
      });
  }

  function verificarStatus() {
    if (!vendaUid) {
      return;
    }
    setCarregando(true);
    setErro(null);
    NotaFiscalService.status(vendaUid)
      .then((response) => {
        setNota(response.data);
        setCarregando(false);
      })
      .catch(() => {
        setErro("Não foi possível consultar o status da nota fiscal.");
        setCarregando(false);
      });
  }

  function cancelar() {
    if (!vendaUid || justificativa.trim().length < 15) {
      setErro("A justificativa precisa ter pelo menos 15 caracteres.");
      return;
    }
    setCarregando(true);
    setErro(null);
    NotaFiscalService.cancelar(vendaUid, justificativa)
      .then((response) => {
        setNota(response.data);
        setCarregando(false);
        setMostrarCancelamento(false);
      })
      .catch(() => {
        setErro("Não foi possível cancelar a nota fiscal.");
        setCarregando(false);
      });
  }

  return (
    <Box sx={{ display: "flex", flexDirection: "column", gap: 1 }}>
      {erro && <Alert severity="error">{erro}</Alert>}

      {(!nota || nota.status === "REJEITADA" || nota.status === "ERRO") && (
        <Button
          variant="contained"
          color="primary"
          disabled={!vendaUid || carregando}
          onClick={emitir}
        >
          Emitir Nota Fiscal
        </Button>
      )}

      {nota && nota.status === "PROCESSANDO" && (
        <Button variant="outlined" color="primary" disabled={carregando} onClick={verificarStatus}>
          Verificar Status
        </Button>
      )}

      {nota && nota.status === "AUTORIZADA" && (
        <>
          <Button
            variant="outlined"
            color="primary"
            component="a"
            href={nota.urlDanfe ?? undefined}
            target="_blank"
            rel="noreferrer"
          >
            Ver DANFE
          </Button>
          {!mostrarCancelamento ? (
            <Button variant="outlined" color="error" onClick={() => setMostrarCancelamento(true)}>
              Cancelar Nota
            </Button>
          ) : (
            <Box sx={{ display: "flex", flexDirection: "column", gap: 1 }}>
              <TextField
                label="Justificativa do cancelamento"
                value={justificativa}
                onChange={(e) => setJustificativa(e.target.value)}
                helperText="Mínimo de 15 caracteres"
                multiline
              />
              <Button variant="contained" color="error" disabled={carregando} onClick={cancelar}>
                Confirmar Cancelamento
              </Button>
            </Box>
          )}
        </>
      )}

      {nota && nota.status === "CANCELADA" && <Alert severity="info">Nota fiscal cancelada.</Alert>}
    </Box>
  );
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `cd vendas-front/vendas-front && CI=true npm test -- --watchAll=false --testPathPattern=nota-fiscal-panel`
Expected: PASS, 3 tests passed.

- [ ] **Step 7: Commit**

```bash
git add src/types/nota-fiscal.type.ts src/services/nota-fiscal.service.ts src/components/venda/nota-fiscal-panel.tsx src/components/venda/nota-fiscal-panel.test.tsx
git commit -m "Add NotaFiscalPanel component for emitting/tracking NFC-e"
```

(Run from `vendas-front/vendas-front/` — that repo is separate from `vendas/`.)

---

## Task 7: Produto fiscal fields (frontend forms)

**Files:**
- Modify: `vendas-front/vendas-front/src/types/produto.type.ts`
- Modify: `vendas-front/vendas-front/src/components/produto/add-produto.tsx`
- Modify: `vendas-front/vendas-front/src/components/produto/edit-produto.tsx`

**Interfaces:**
- Consumes: none new.
- Produces: `ProdutoDTO` gains `ncm`, `cfop`, `csosn`, `unidadeComercial` (all `string`) — matches Task 1's backend fields exactly (same JSON keys, since `ModelMapper` maps `ProdutoDTO`↔`ProdutoEntity` by field name).

No dedicated test for this task: `add-produto.tsx`/`edit-produto.tsx` have no existing test coverage in this codebase, and these are plain form-field additions following the exact pattern already used for `nome`/`valor`/`tipoMedida`. Verify manually per Step 5.

- [ ] **Step 1: Add the fields to the type**

In `produto.type.ts`, replace:

```ts
export default interface ProdutoDTO {
  uid?: any | null,
  nome: string,
  valor: number,
  tipoMedida: string,
  categoria: string,
}
```

with:

```ts
export default interface ProdutoDTO {
  uid?: any | null,
  nome: string,
  valor: number,
  tipoMedida: string,
  categoria: string,
  ncm: string,
  cfop: string,
  csosn: string,
  unidadeComercial: string,
}
```

- [ ] **Step 2: Add fields, handlers and inputs to `add-produto.tsx`**

Initialize state defaults (replace the constructor's `this.state = {...}` block):

```tsx
        this.state = {
            categorias: [],
            uid: "",
            nome: "",
            valor: 0,
            tipoMedida: "Unidade",
            categoria: "",
            ncm: "",
            cfop: "5102",
            csosn: "102",
            unidadeComercial: "UN",
            submitted: false,
        };
```

Add change handlers (next to the existing `onChangeCategoria`):

```tsx
    onChangeNcm(e: ChangeEvent<HTMLInputElement>) {
        this.setState({
            ncm: e.target.value
        });
    }

    onChangeCfop(e: ChangeEvent<HTMLInputElement>) {
        this.setState({
            cfop: e.target.value
        });
    }

    onChangeCsosn(e: ChangeEvent<HTMLInputElement>) {
        this.setState({
            csosn: e.target.value
        });
    }

    onChangeUnidadeComercial(e: ChangeEvent<HTMLInputElement>) {
        this.setState({
            unidadeComercial: e.target.value
        });
    }
```

Bind them in the constructor, next to the existing `this.onChangeCategoria = ...` line:

```tsx
        this.onChangeNcm = this.onChangeNcm.bind(this);
        this.onChangeCfop = this.onChangeCfop.bind(this);
        this.onChangeCsosn = this.onChangeCsosn.bind(this);
        this.onChangeUnidadeComercial = this.onChangeUnidadeComercial.bind(this);
```

Include the fields in `saveProduto`'s `data` object:

```tsx
        const data: ProdutoDTO = {
            uid: this.state.uid,
            nome: this.state.nome,
            valor: this.state.valor,
            tipoMedida: this.state.tipoMedida,
            categoria: this.state.categoria,
            ncm: this.state.ncm,
            cfop: this.state.cfop,
            csosn: this.state.csosn,
            unidadeComercial: this.state.unidadeComercial,
        };
```

Reset them in `newProduto`:

```tsx
    newProduto() {
        this.setState({
            uid: "",
            nome: "",
            valor: 0,
            tipoMedida: "Unidade",
            categoria: "",
            ncm: "",
            cfop: "5102",
            csosn: "102",
            unidadeComercial: "UN",
            submitted: false
        });
    }
```

Destructure the new fields in `render` (next to the existing destructuring) and add the inputs after the "Categoria" `Grid item`, before the buttons `Grid item`:

```tsx
    const { submitted, uid, nome, valor, tipoMedida, categoria, categorias,
        ncm, cfop, csosn, unidadeComercial } = this.state;
```

```tsx
                            <Grid item xs={12}>
                                <TextField
                                    fullWidth
                                    label="NCM"
                                    required
                                    value={ncm}
                                    onChange={this.onChangeNcm}
                                    name="ncm"
                                />
                            </Grid>
                            <Grid item xs={12}>
                                <TextField
                                    fullWidth
                                    label="CFOP"
                                    required
                                    value={cfop}
                                    onChange={this.onChangeCfop}
                                    name="cfop"
                                />
                            </Grid>
                            <Grid item xs={12}>
                                <TextField
                                    fullWidth
                                    label="CSOSN"
                                    required
                                    value={csosn}
                                    onChange={this.onChangeCsosn}
                                    name="csosn"
                                />
                            </Grid>
                            <Grid item xs={12}>
                                <TextField
                                    fullWidth
                                    label="Unidade Comercial"
                                    required
                                    value={unidadeComercial}
                                    onChange={this.onChangeUnidadeComercial}
                                    name="unidadeComercial"
                                />
                            </Grid>
```

- [ ] **Step 3: Add the same fields to `edit-produto.tsx`**

Initialize state defaults (replace the constructor's `currentProduto` object):

```tsx
      currentProduto: {
        uid: null,
        nome: "",
        valor: 0,
        categoria: "",
        tipoMedida: "",
        ncm: "",
        cfop: "",
        csosn: "",
        unidadeComercial: "",
      },
```

Add change handlers (next to `onChangeCategoria`), following the existing `prevState` spread pattern:

```tsx
  onChangeNcm(e: ChangeEvent<HTMLInputElement>) {
    const ncm = e.target.value;
    this.setState(function (prevState) {
      return {
        currentProduto: {
          ...prevState.currentProduto,
          ncm: ncm,
        },
      };
    });
  }

  onChangeCfop(e: ChangeEvent<HTMLInputElement>) {
    const cfop = e.target.value;
    this.setState(function (prevState) {
      return {
        currentProduto: {
          ...prevState.currentProduto,
          cfop: cfop,
        },
      };
    });
  }

  onChangeCsosn(e: ChangeEvent<HTMLInputElement>) {
    const csosn = e.target.value;
    this.setState(function (prevState) {
      return {
        currentProduto: {
          ...prevState.currentProduto,
          csosn: csosn,
        },
      };
    });
  }

  onChangeUnidadeComercial(e: ChangeEvent<HTMLInputElement>) {
    const unidadeComercial = e.target.value;
    this.setState(function (prevState) {
      return {
        currentProduto: {
          ...prevState.currentProduto,
          unidadeComercial: unidadeComercial,
        },
      };
    });
  }
```

Bind them in the constructor, next to `this.onChangeCategoria = ...`:

```tsx
    this.onChangeNcm = this.onChangeNcm.bind(this);
    this.onChangeCfop = this.onChangeCfop.bind(this);
    this.onChangeCsosn = this.onChangeCsosn.bind(this);
    this.onChangeUnidadeComercial = this.onChangeUnidadeComercial.bind(this);
```

Add the inputs after the "Categoria" `Grid item` in `render`:

```tsx
                <Grid item xs={12}>
                  <TextField
                    fullWidth
                    label="NCM"
                    value={currentProduto.ncm}
                    onChange={this.onChangeNcm}
                  />
                </Grid>
                <Grid item xs={12}>
                  <TextField
                    fullWidth
                    label="CFOP"
                    value={currentProduto.cfop}
                    onChange={this.onChangeCfop}
                  />
                </Grid>
                <Grid item xs={12}>
                  <TextField
                    fullWidth
                    label="CSOSN"
                    value={currentProduto.csosn}
                    onChange={this.onChangeCsosn}
                  />
                </Grid>
                <Grid item xs={12}>
                  <TextField
                    fullWidth
                    label="Unidade Comercial"
                    value={currentProduto.unidadeComercial}
                    onChange={this.onChangeUnidadeComercial}
                  />
                </Grid>
```

- [ ] **Step 4: Type-check the frontend**

Run: `cd vendas-front/vendas-front && npx tsc --noEmit`
Expected: no errors.

- [ ] **Step 5: Verify manually**

Start the backend (`cd vendas && mvnw.cmd spring-boot:run`, requires MongoDB running) and the frontend (`cd vendas-front/vendas-front && npm start`). Go to "Cadastrar produto", confirm the CFOP/CSOSN/Unidade Comercial fields are pre-filled with `5102`/`102`/`UN`, save a product, then open it in "Editar produto" and confirm the four new fields show the saved values.

- [ ] **Step 6: Commit**

```bash
git add src/types/produto.type.ts src/components/produto/add-produto.tsx src/components/produto/edit-produto.tsx
git commit -m "Add NCM/CFOP/CSOSN/unidade comercial fields to produto forms"
```

(Run from `vendas-front/vendas-front/`.)

---

## Task 8: Wire `NotaFiscalPanel` into checkout (`add-venda.tsx`)

**Files:**
- Modify: `vendas-front/vendas-front/src/components/venda/add-venda.tsx`

**Interfaces:**
- Consumes: `<NotaFiscalPanel vendaUid={string | null} />` (Task 6), `VendaService.create` response now includes `uid` (backend Task 2 already makes this true — no frontend service change needed, `VendaDTO.uid` was already typed as optional).

No dedicated automated test for this task — `add-venda.tsx` is a large existing class component with no current test coverage, and wiring in an already-tested child component (`NotaFiscalPanel`) doesn't add new logic worth a bespoke harness for. Verify manually per Step 5.

- [ ] **Step 1: Import the component**

Add near the other component imports at the top of `add-venda.tsx`:

```tsx
import NotaFiscalPanel from "./nota-fiscal-panel";
```

- [ ] **Step 2: Track the last finalized venda's uid**

Add `lastVendaUid: string | null` to the `State` type:

```tsx
type State = VendaDTO & {
    produtos: Array<ProdutoDTO>,
    vendasEmAberto: Array<VendaDTO>,
    currentItem: VendaItemDTO | null,
    produtoID: string,
    produtoNome: string | null,
    categorias: Array<CategoriaDTO>,
    open: boolean,
    msg: string,
    openModel: boolean,
    lastVendaUid: string | null,
};
```

Initialize it in the constructor's `this.state = {...}` block (add the field to the existing object):

```tsx
            lastVendaUid: null,
```

- [ ] **Step 3: Capture the uid when the sale is finalized**

Replace the body of `finalizarVenda`'s `.then` callback:

```tsx
        VendaService.create(data)
            .then((response: any) => {
                this.setState({
                    open: true,
                    msg: "Venda registrada com sucesso!",
                });

            })
            .catch((e: Error) => {
                console.log(e);
            });
        this.newVenda();
```

with:

```tsx
        VendaService.create(data)
            .then((response: any) => {
                this.setState({
                    open: true,
                    msg: "Venda registrada com sucesso!",
                    lastVendaUid: response.data.uid ?? null,
                });

            })
            .catch((e: Error) => {
                console.log(e);
            });
        this.newVenda();
```

- [ ] **Step 4: Reset the uid when a new sale starts**

In `newVenda`, add `lastVendaUid: null` to the reset object:

```tsx
    newVenda() {
        this.setState({
            uid: null,
            itens: [],
            valorDesconto: 0,
            valorTotal: 0,
            formaPagamento: "Dinheiro",
            valorPago: 0,
            valorTroco: 0,
            currentItem: null,
            produtoID: "",
            produtoNome: null,
            lastVendaUid: null,
        });
    }
```

In `adicionarItem`, reset it the same way `cliente` is reset when starting a fresh cart. Replace:

```tsx
        const list = this.state.itens;
        const cliente = list.length > 0 ? this.state.cliente : "";
```

with:

```tsx
        const list = this.state.itens;
        const startingNewCart = list.length === 0;
        const cliente = list.length > 0 ? this.state.cliente : "";
```

and in the `this.setState({...})` call further down in `adicionarItem`, add:

```tsx
            lastVendaUid: startingNewCart ? null : this.state.lastVendaUid,
```

- [ ] **Step 5: Render the panel next to "Finalizar Compra"**

In the button row inside the cart `Paper` (the `<Box sx={{ display: "flex", flexWrap: "wrap", gap: 1.5 }}>` that holds "Pagamento pendente"/"Finalizar Compra"/"Imprimir"), add the panel as one more item:

```tsx
                                            <Box sx={{ display: "flex", flexWrap: "wrap", gap: 1.5 }}>
                                                <Button onClick={this.pagamentoPendente} variant="outlined" color="secondary" size="medium">
                                                    Pagamento pendente
                                                </Button>
                                                <Button onClick={this.finalizarVenda} variant="contained" color="primary" size="medium">
                                                    Finalizar Compra
                                                </Button>
                                                <Button onClick={this.imprimir} variant="contained" color="primary" size="medium">
                                                    Imprimir
                                                </Button>
                                                <NotaFiscalPanel vendaUid={this.state.lastVendaUid} />
                                            </Box>
```

Destructure `lastVendaUid` isn't needed since it's read via `this.state.lastVendaUid` directly in JSX (matches how `this.state` is otherwise accessed in this file's render for less-common fields — if you prefer, add `lastVendaUid` to the existing destructuring block at the top of `render` instead, and use `lastVendaUid` bare in the JSX above).

- [ ] **Step 6: Type-check the frontend**

Run: `cd vendas-front/vendas-front && npx tsc --noEmit`
Expected: no errors.

- [ ] **Step 7: Verify manually**

With backend and frontend running (and a valid `FOCUSNFE_TOKEN` env var set for the backend, from a Focus NFe homologação account — see Task 9), add an item to the cart, click "Finalizar Compra", and confirm the "Emitir Nota Fiscal" button becomes enabled right after. Click it and confirm it shows either the "Ver DANFE" link or an error alert (both are valid outcomes depending on the sandbox account's state — the point is that the request round-trips).

- [ ] **Step 8: Commit**

```bash
git add src/components/venda/add-venda.tsx
git commit -m "Add Emitir Nota Fiscal button next to Finalizar Compra"
```

(Run from `vendas-front/vendas-front/`.)

---

## Task 9: Wire `NotaFiscalPanel` into sales history (`list-venda.tsx`)

**Files:**
- Modify: `vendas-front/vendas-front/src/components/venda/list-venda.tsx`

**Interfaces:**
- Consumes: `<NotaFiscalPanel vendaUid={string | null} />` (Task 6).

No dedicated automated test — same reasoning as Task 8. Verify manually per Step 3.

- [ ] **Step 1: Import the component**

```tsx
import NotaFiscalPanel from "./nota-fiscal-panel";
```

- [ ] **Step 2: Render it in the selected-venda side panel**

In the `currentVenda ?` block, after the existing `Typography` lines showing venda details and before the items `TableContainer` (or after it — either position works; placing it right after the venda summary keeps it visible without scrolling past the item table):

```tsx
              <Typography>ID: {currentVenda.uid}</Typography>
              <Typography>Cliente: {currentVenda.cliente}</Typography>
              <Typography>Data: {new Date(currentVenda.create).toLocaleString()}</Typography>
              <Typography sx={{ mb: 1 }}>Itens: {currentVenda.itens.length}</Typography>
              <Box sx={{ mb: 2 }}>
                <NotaFiscalPanel vendaUid={currentVenda.uid ?? null} />
              </Box>
```

(`Box` is already imported in this file via the existing `@mui/material` import line — confirm it's in that import list; if not, add it there.)

- [ ] **Step 3: Verify manually**

Open "Lista de Vendas", click a past sale in the table, and confirm the "Nota Fiscal" controls appear in the side panel and reflect that sale's current status (switching between sales updates the panel instead of showing stale data from the previous selection).

- [ ] **Step 4: Commit**

```bash
git add src/components/venda/list-venda.tsx
git commit -m "Show nota fiscal status/actions in sales history panel"
```

(Run from `vendas-front/vendas-front/`.)

---

## Task 10: Manual validation against the Focus NFe homologação sandbox

This task has no code changes — it closes the loop on the one thing that can't be verified without a real Focus NFe account: the exact response field names.

- [ ] **Step 1:** Create a free Focus NFe trial account at [focusnfe.com.br](https://focusnfe.com.br/), register the company (CNPJ `35.242.747/0001-30`) and upload the `.pfx` certificate in their dashboard, and generate a **homologação** API token.
- [ ] **Step 2:** Export the token locally without committing it: `export FOCUSNFE_TOKEN=<token do painel>` (or set it as a Windows environment variable before starting the backend).
- [ ] **Step 3:** Start the backend (`mvnw.cmd spring-boot:run`) and frontend (`npm start`), finalize a test sale, and click "Emitir Nota Fiscal".
- [ ] **Step 4:** Compare the raw JSON response (check the backend logs or a REST client hitting `POST /v1/notas-fiscais/{vendaId}/emitir` directly) against the field names assumed in `FocusNFeEmissorService.interpretarResposta` (`protocolo_autorizacao`, `mensagem_sefaz` were not directly confirmed from documentation — `status`, `chave_nfe`, `caminho_danfe` were). Adjust `textoOuNulo(json, "...")` calls if the real field names differ.
- [ ] **Step 5:** If a field name changed, add a small regression test to `FocusNFeEmissorServiceTest` asserting the corrected field, then commit:

```bash
git add src/main/java/com/sorveteria/bomcream/vendas/fiscal/FocusNFeEmissorService.java src/test/java/com/sorveteria/bomcream/vendas/fiscal/FocusNFeEmissorServiceTest.java
git commit -m "Fix Focus NFe response field name mismatch found in sandbox testing"
```

- [ ] **Step 6:** Do **not** flip `focusnfe.ambiente` to `producao` or use a production token until the SEFAZ-RS credenciamento and CSC are confirmed with the accountant (see the spec's Contexto section) — homologação is the correct environment for all testing.
