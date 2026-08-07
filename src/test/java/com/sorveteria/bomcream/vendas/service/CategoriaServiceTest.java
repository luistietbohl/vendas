package com.sorveteria.bomcream.vendas.service;

import com.sorveteria.bomcream.vendas.controller.dto.CategoriaDTO;
import com.sorveteria.bomcream.vendas.repository.CategoriaRepository;
import com.sorveteria.bomcream.vendas.repository.entity.CategoriaEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoriaServiceTest {

    @Mock
    private CategoriaRepository repository;

    @Test
    void editPersistsNcmPadrao() {
        CategoriaService service = new CategoriaService(repository, new ModelMapper());

        CategoriaEntity existente = CategoriaEntity.builder()
                .uid("c1").nome("Expresso").tipo("visivel").ordem(1)
                .build();

        when(repository.findById("c1")).thenReturn(Optional.of(existente));
        when(repository.save(any(CategoriaEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CategoriaDTO dto = CategoriaDTO.builder()
                .nome("Expresso").tipo("visivel").ordem(1).ncmPadrao("21050010")
                .build();

        service.edit(dto, "c1");

        assertEquals("21050010", existente.getNcmPadrao());
    }
}
