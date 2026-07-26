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
