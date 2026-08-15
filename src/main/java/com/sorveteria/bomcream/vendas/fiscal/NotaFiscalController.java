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
    public ResponseEntity emitir(@PathVariable String vendaId,
                                  @RequestBody(required = false) EmitirNotaFiscalDTO dto) {
        try {
            String cpfDestinatario = dto != null ? dto.getCpfDestinatario() : null;
            return ResponseEntity.ok(service.emitir(vendaId, cpfDestinatario));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    @GetMapping("/{vendaId}")
    public ResponseEntity buscar(@PathVariable String vendaId) {
        return ResponseEntity.ok(service.buscar(vendaId));
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
