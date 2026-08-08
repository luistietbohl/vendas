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

    public NotaFiscalEntity buscar(String vendaId) {
        return notaFiscalRepository.findByVendaUid(vendaId)
                .orElseGet(() -> NotaFiscalEntity.builder()
                        .vendaUid(vendaId)
                        .status(NotaFiscalStatus.NAO_EMITIDA)
                        .build());
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
        if (resultado.getNumero() != null) {
            nota.setNumero(resultado.getNumero());
        }
        if (resultado.getSerie() != null) {
            nota.setSerie(resultado.getSerie());
        }
        if (resultado.getChaveAcesso() != null) {
            nota.setChaveAcesso(resultado.getChaveAcesso());
        }
        if (resultado.getProtocoloAutorizacao() != null) {
            nota.setProtocoloAutorizacao(resultado.getProtocoloAutorizacao());
        }
        if (resultado.getMensagemSefaz() != null) {
            nota.setMensagemSefaz(resultado.getMensagemSefaz());
        }
        if (resultado.getUrlDanfe() != null) {
            nota.setUrlDanfe(resultado.getUrlDanfe());
        }
    }
}
