package com.sorveteria.bomcream.vendas.controller;

import com.sorveteria.bomcream.vendas.controller.dto.AplicarNcmPadraoDTO;
import com.sorveteria.bomcream.vendas.controller.dto.FilterProdutoDTO;
import com.sorveteria.bomcream.vendas.controller.dto.ProdutoDTO;
import com.sorveteria.bomcream.vendas.service.ProdutoService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/produto")
@RequiredArgsConstructor
public class ProdutoController {
    private final ProdutoService service;

    @GetMapping("/all")
    public ResponseEntity listAll() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/page")
    public ResponseEntity page(@RequestParam Integer page, @RequestParam Integer size) {
        return ResponseEntity.ok(service.findPage(PageRequest.of(page, size)));
    }

    @PostMapping("/filter")
    public ResponseEntity pageByFilter(@RequestParam Integer page, @RequestParam Integer size,
                                       @RequestBody FilterProdutoDTO dto) {
        return ResponseEntity.ok(service.findByFilter(dto.getStart(), dto.getEnd(),
                PageRequest.of(page, size)));
    }

    @PostMapping("/filter2")
    public ResponseEntity pageByFilter2(@RequestParam Integer page, @RequestParam Integer size,
                                        @RequestBody FilterProdutoDTO dto) {
        return ResponseEntity.ok(service.findByFilter(dto,
                PageRequest.of(page, size)));
    }

    @PostMapping
    public ResponseEntity create(@RequestBody ProdutoDTO dto) {
        service.create(dto);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/aplicar-ncm-padrao")
    public ResponseEntity aplicarNcmPadrao(@RequestBody AplicarNcmPadraoDTO dto) {
        try {
            return ResponseEntity.ok(service.aplicarNcmPadrao(dto.getCategoria(), dto.getNcm()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    @PostMapping("/all")
    public ResponseEntity createAll(@RequestBody List<ProdutoDTO> list) {
        service.createAll(list);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity get(@PathVariable String id) {
        try {
            return ResponseEntity.ok(service.get(id));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }

    }

    @PutMapping("/{id}")
    public ResponseEntity edit(@PathVariable String id, @RequestBody ProdutoDTO dto) {
        try {
            service.edit(dto, id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/all")
    public ResponseEntity deleteAll() {
        service.deleteAll();
        return ResponseEntity.ok().build();
    }
}
