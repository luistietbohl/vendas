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
