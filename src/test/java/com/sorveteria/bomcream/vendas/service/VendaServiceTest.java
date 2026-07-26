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
