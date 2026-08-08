package com.sorveteria.bomcream.vendas.controller;

import com.sorveteria.bomcream.vendas.controller.dto.FilterVendaDTO;
import com.sorveteria.bomcream.vendas.controller.dto.VendaDTO;
import com.sorveteria.bomcream.vendas.service.VendaService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/vendas")
@RequiredArgsConstructor
public class VendaController {
    private final VendaService service;

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
                                       @RequestBody FilterVendaDTO dto) {
        return ResponseEntity.ok(service.findByFilter(dto.getStart(), dto.getEnd(),
                PageRequest.of(page, size)));
    }

    @PostMapping("/filter_all")
    public ResponseEntity listByFilter(@RequestBody FilterVendaDTO dto) {
        return ResponseEntity.ok(service.findByFilter(dto.getStart(), dto.getEnd()));
    }

    @PostMapping
    public ResponseEntity create(@RequestBody VendaDTO dto) {
        return ResponseEntity.ok(service.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity edit(@PathVariable String id, @RequestBody VendaDTO dto) {
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
