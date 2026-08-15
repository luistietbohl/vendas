package com.sorveteria.bomcream.vendas.fiscal;

import com.sorveteria.bomcream.vendas.repository.VendaRepository;
import com.sorveteria.bomcream.vendas.repository.entity.VendaEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
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
        when(emissorFiscalService.emitir(venda, null)).thenReturn(
                ResultadoEmissaoFiscal.builder()
                        .status(NotaFiscalStatus.AUTORIZADA)
                        .chaveAcesso("CHAVE123")
                        .urlDanfe("https://focusnfe/danfe/1")
                        .build());
        when(notaFiscalRepository.save(any(NotaFiscalEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NotaFiscalEntity resultado = service.emitir("venda-1", null);

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
                .numero("100").serie("1")
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
        assertNotNull(resultado.getDataCancelamento());
        assertEquals("100", resultado.getNumero());
        assertEquals("1", resultado.getSerie());
    }

    @Test
    void consultarStatusAtualizaNotaExistenteComRetornoDoGateway() {
        NotaFiscalService service = new NotaFiscalService(notaFiscalRepository, emissorFiscalService, vendaRepository);

        NotaFiscalEntity notaExistente = NotaFiscalEntity.builder()
                .uid("nota-1").vendaUid("venda-1").status(NotaFiscalStatus.PROCESSANDO)
                .numero("100").serie("1")
                .build();
        when(notaFiscalRepository.findByVendaUid("venda-1")).thenReturn(Optional.of(notaExistente));
        when(emissorFiscalService.consultarStatus("venda-1")).thenReturn(
                ResultadoEmissaoFiscal.builder()
                        .status(NotaFiscalStatus.AUTORIZADA)
                        .chaveAcesso("CHAVE456")
                        .build());
        when(notaFiscalRepository.save(any(NotaFiscalEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NotaFiscalEntity resultado = service.consultarStatus("venda-1");

        assertEquals(NotaFiscalStatus.AUTORIZADA, resultado.getStatus());
        assertEquals("CHAVE456", resultado.getChaveAcesso());
        assertEquals("100", resultado.getNumero());
        assertEquals("1", resultado.getSerie());
    }

    @Test
    void consultarStatusLancaExcecaoQuandoNotaNaoExiste() {
        NotaFiscalService service = new NotaFiscalService(notaFiscalRepository, emissorFiscalService, vendaRepository);

        when(notaFiscalRepository.findByVendaUid("venda-1")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.consultarStatus("venda-1"));
    }

    @Test
    void buscarRetornaNotaExistenteSemChamarGateway() {
        NotaFiscalService service = new NotaFiscalService(notaFiscalRepository, emissorFiscalService, vendaRepository);

        NotaFiscalEntity notaExistente = NotaFiscalEntity.builder()
                .uid("nota-1").vendaUid("venda-1").status(NotaFiscalStatus.AUTORIZADA)
                .chaveAcesso("CHAVE123").build();
        when(notaFiscalRepository.findByVendaUid("venda-1")).thenReturn(Optional.of(notaExistente));

        NotaFiscalEntity resultado = service.buscar("venda-1");

        assertEquals("nota-1", resultado.getUid());
        assertEquals(NotaFiscalStatus.AUTORIZADA, resultado.getStatus());
        assertEquals("CHAVE123", resultado.getChaveAcesso());
        verifyNoInteractions(emissorFiscalService);
    }

    @Test
    void buscarRetornaPlaceholderNaoEmitidaQuandoNaoExisteRegistro() {
        NotaFiscalService service = new NotaFiscalService(notaFiscalRepository, emissorFiscalService, vendaRepository);

        when(notaFiscalRepository.findByVendaUid("venda-1")).thenReturn(Optional.empty());

        NotaFiscalEntity resultado = service.buscar("venda-1");

        assertEquals("venda-1", resultado.getVendaUid());
        assertEquals(NotaFiscalStatus.NAO_EMITIDA, resultado.getStatus());
        verifyNoInteractions(emissorFiscalService);
    }

    @Test
    void emitirGravaOCpfDestinatarioNaNota() {
        NotaFiscalService service = new NotaFiscalService(notaFiscalRepository, emissorFiscalService, vendaRepository);

        VendaEntity venda = VendaEntity.builder().uid("venda-1").build();
        when(vendaRepository.findById("venda-1")).thenReturn(Optional.of(venda));
        when(notaFiscalRepository.findByVendaUid("venda-1")).thenReturn(Optional.empty());
        when(emissorFiscalService.emitir(venda, "12345678900")).thenReturn(
                ResultadoEmissaoFiscal.builder().status(NotaFiscalStatus.AUTORIZADA).build());
        when(notaFiscalRepository.save(any(NotaFiscalEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NotaFiscalEntity resultado = service.emitir("venda-1", "12345678900");

        assertEquals("12345678900", resultado.getCpfDestinatario());
    }
}
