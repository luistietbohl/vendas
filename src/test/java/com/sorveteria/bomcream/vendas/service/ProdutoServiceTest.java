package com.sorveteria.bomcream.vendas.service;

import com.sorveteria.bomcream.vendas.controller.dto.ProdutoDTO;
import com.sorveteria.bomcream.vendas.repository.ProdutoRepository;
import com.sorveteria.bomcream.vendas.repository.entity.ProdutoEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProdutoServiceTest {

    @Mock
    private ProdutoRepository repository;

    @Mock
    private MongoTemplate mongoTemplate;

    @Test
    void editPersistsFiscalFields() {
        ProdutoService service = new ProdutoService(repository, mongoTemplate, new ModelMapper());

        ProdutoEntity existente = ProdutoEntity.builder()
                .uid("p1")
                .nome("Sorvete 500ml")
                .valor(new BigDecimal("15.00"))
                .tipoMedida("Unidade")
                .categoria("expresso")
                .build();

        when(repository.findById("p1")).thenReturn(Optional.of(existente));
        when(repository.save(any(ProdutoEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProdutoDTO dto = ProdutoDTO.builder()
                .nome("Sorvete 500ml")
                .valor(new BigDecimal("15.00"))
                .tipoMedida("Unidade")
                .categoria("expresso")
                .ncm("21050010")
                .cfop("5102")
                .csosn("102")
                .unidadeComercial("UN")
                .build();

        service.edit(dto, "p1");

        assertEquals("21050010", existente.getNcm());
        assertEquals("5102", existente.getCfop());
        assertEquals("102", existente.getCsosn());
        assertEquals("UN", existente.getUnidadeComercial());
    }
}
