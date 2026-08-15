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
        when(service.emitir("venda-1", null)).thenReturn(nota);

        mockMvc.perform(post("/v1/notas-fiscais/venda-1/emitir"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTORIZADA"))
                .andExpect(jsonPath("$.chaveAcesso").value("CHAVE123"));
    }

    @Test
    void buscarRetornaNotaPelaVendaSemSufixoStatus() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new NotaFiscalController(service)).build();

        NotaFiscalEntity nota = NotaFiscalEntity.builder()
                .uid("nota-1").vendaUid("venda-1").status(NotaFiscalStatus.NAO_EMITIDA).build();
        when(service.buscar("venda-1")).thenReturn(nota);

        mockMvc.perform(get("/v1/notas-fiscais/venda-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vendaUid").value("venda-1"))
                .andExpect(jsonPath("$.status").value("NAO_EMITIDA"));
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

    @Test
    void emitirEnviaOCpfDoCorpoAoServico() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new NotaFiscalController(service)).build();

        NotaFiscalEntity nota = NotaFiscalEntity.builder()
                .uid("nota-1").vendaUid("venda-1").status(NotaFiscalStatus.AUTORIZADA)
                .cpfDestinatario("12345678900").build();
        when(service.emitir("venda-1", "12345678900")).thenReturn(nota);

        String body = new ObjectMapper().writeValueAsString(
                EmitirNotaFiscalDTO.builder().cpfDestinatario("12345678900").build());

        mockMvc.perform(post("/v1/notas-fiscais/venda-1/emitir")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cpfDestinatario").value("12345678900"));
    }
}
