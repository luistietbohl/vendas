package com.sorveteria.bomcream.vendas.service;

import com.mongodb.client.result.UpdateResult;
import com.sorveteria.bomcream.vendas.controller.dto.ProdutoDTO;
import com.sorveteria.bomcream.vendas.repository.ProdutoRepository;
import com.sorveteria.bomcream.vendas.repository.entity.ProdutoEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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

    @Test
    void aplicarNcmPadraoAtualizaSomenteProdutosSemNcmDaCategoria() {
        ProdutoService service = new ProdutoService(repository, mongoTemplate, new ModelMapper());

        UpdateResult resultado = mock(UpdateResult.class);
        when(resultado.getModifiedCount()).thenReturn(7L);
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(ProdutoEntity.class)))
                .thenReturn(resultado);

        long quantidade = service.aplicarNcmPadrao("expresso", "21050010");

        assertEquals(7L, quantidade);

        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateMulti(any(Query.class), updateCaptor.capture(), eq(ProdutoEntity.class));
        assertEquals("21050010", updateCaptor.getValue().getUpdateObject().get("$set", org.bson.Document.class).get("ncm"));
    }
}
