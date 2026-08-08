package com.sorveteria.bomcream.vendas.service;

import com.sorveteria.bomcream.vendas.controller.dto.ProdutoDTO;
import com.sorveteria.bomcream.vendas.repository.entity.ProdutoEntity;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProdutoFiscalMappingTest {

    @Test
    void mapsFiscalFieldsBetweenEntityAndDto() {
        ModelMapper mapper = new ModelMapper();

        ProdutoEntity entity = ProdutoEntity.builder()
                .uid("p1")
                .nome("Sorvete 500ml")
                .ncm("21050010")
                .cfop("5102")
                .csosn("102")
                .unidadeComercial("UN")
                .build();

        ProdutoDTO dto = mapper.map(entity, ProdutoDTO.class);

        assertEquals("21050010", dto.getNcm());
        assertEquals("5102", dto.getCfop());
        assertEquals("102", dto.getCsosn());
        assertEquals("UN", dto.getUnidadeComercial());
    }
}
