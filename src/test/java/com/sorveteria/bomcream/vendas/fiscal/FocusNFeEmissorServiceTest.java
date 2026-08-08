package com.sorveteria.bomcream.vendas.fiscal;

import com.sorveteria.bomcream.vendas.repository.entity.ItemVendaEntity;
import com.sorveteria.bomcream.vendas.repository.entity.ProdutoEntity;
import com.sorveteria.bomcream.vendas.repository.entity.VendaEntity;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
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
                .andExpect(jsonPath("$.items[0].codigo_ncm").value("21050010"))
                .andExpect(jsonPath("$.formas_pagamento[0].forma_pagamento").value("01"))
                .andExpect(jsonPath("$.formas_pagamento[0].valor_pagamento").value(15.00))
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
        assertEquals("03", service.mapearFormaPagamento("Credito"));
        assertEquals("04", service.mapearFormaPagamento("Debito"));
        assertEquals("17", service.mapearFormaPagamento("PIX"));
    }

    @Test
    void emitirRetornaErroQuandoFocusNFeRespondeComStatusNao2xx() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        FocusNFeEmissorService service = new FocusNFeEmissorService(
                restTemplate, "TOKEN123", "homologacao", "35242747000130");

        VendaEntity venda = vendaValida();

        server.expect(requestTo("https://homologacao.focusnfe.com.br/v2/nfce?ref=venda-1"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .body("{\"codigo\":\"cnpj_emitente_invalido\",\"mensagem\":\"CNPJ do emitente invalido\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        ResultadoEmissaoFiscal resultado = service.emitir(venda);

        assertEquals(NotaFiscalStatus.ERRO, resultado.getStatus());
        assertNotNull(resultado.getMensagemSefaz());
        assertTrue(resultado.getMensagemSefaz().contains("422"));
        assertTrue(resultado.getMensagemSefaz().contains("cnpj_emitente_invalido"));
        server.verify();
    }

    @Test
    void emitirNaoChamaOGatewayQuandoProdutoNaoTemDadosFiscaisCompletos() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        FocusNFeEmissorService service = new FocusNFeEmissorService(
                restTemplate, "TOKEN123", "homologacao", "35242747000130");

        ProdutoEntity produtoSemNcm = ProdutoEntity.builder()
                .uid("p1").nome("Sorvete Sem Ficha Fiscal").valor(new BigDecimal("15.00"))
                .ncm(null).cfop("5102").csosn("102").unidadeComercial("UN")
                .build();

        ItemVendaEntity item = ItemVendaEntity.builder()
                .produto(produtoSemNcm).quantidade(BigDecimal.ONE).valorItem(new BigDecimal("15.00"))
                .build();

        VendaEntity venda = VendaEntity.builder()
                .uid("venda-2")
                .itens(List.of(item))
                .valorTotal(new BigDecimal("15.00"))
                .formaPagamento("Dinheiro")
                .create(LocalDateTime.of(2026, 7, 25, 10, 0))
                .build();

        ResultadoEmissaoFiscal resultado = service.emitir(venda);

        assertEquals(NotaFiscalStatus.ERRO, resultado.getStatus());
        assertNotNull(resultado.getMensagemSefaz());
        assertTrue(resultado.getMensagemSefaz().contains("Sorvete Sem Ficha Fiscal"));
        server.verify();
    }

    @Test
    void consultarStatusMapeiaStatusDesconhecidoParaDesconhecidoEmVezDeErro() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        FocusNFeEmissorService service = new FocusNFeEmissorService(
                restTemplate, "TOKEN123", "homologacao", "35242747000130");

        server.expect(requestTo("https://homologacao.focusnfe.com.br/v2/nfce/venda-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"status\":\"algum_status_novo\"}",
                        MediaType.APPLICATION_JSON));

        ResultadoEmissaoFiscal resultado = service.consultarStatus("venda-1");

        assertEquals(NotaFiscalStatus.DESCONHECIDO, resultado.getStatus());
        server.verify();
    }

    @Test
    void resolverUrlDanfeAdicionaODominioQuandoOCaminhoForRelativo() {
        FocusNFeEmissorService service = new FocusNFeEmissorService(
                new RestTemplate(), "TOKEN123", "producao", "35242747000130");

        assertEquals("https://api.focusnfe.com.br/notas_fiscais_consumidor/NFe123.html",
                service.resolverUrlDanfe("/notas_fiscais_consumidor/NFe123.html"));
    }

    @Test
    void resolverUrlDanfeMantemUmaUrlJaAbsoluta() {
        FocusNFeEmissorService service = new FocusNFeEmissorService(
                new RestTemplate(), "TOKEN123", "homologacao", "35242747000130");

        assertEquals("https://focusnfe/danfe/1", service.resolverUrlDanfe("https://focusnfe/danfe/1"));
    }

    @Test
    void resolverUrlDanfeMantemNuloQuandoNaoHaCaminho() {
        FocusNFeEmissorService service = new FocusNFeEmissorService(
                new RestTemplate(), "TOKEN123", "homologacao", "35242747000130");

        assertEquals(null, service.resolverUrlDanfe(null));
    }

    private VendaEntity vendaValida() {
        ProdutoEntity produto = ProdutoEntity.builder()
                .uid("p1").nome("Sorvete 500ml").valor(new BigDecimal("15.00"))
                .ncm("21050010").cfop("5102").csosn("102").unidadeComercial("UN")
                .build();

        ItemVendaEntity item = ItemVendaEntity.builder()
                .produto(produto).quantidade(BigDecimal.ONE).valorItem(new BigDecimal("15.00"))
                .build();

        return VendaEntity.builder()
                .uid("venda-1")
                .itens(List.of(item))
                .valorTotal(new BigDecimal("15.00"))
                .formaPagamento("Dinheiro")
                .create(LocalDateTime.of(2026, 7, 25, 10, 0))
                .build();
    }
}
