package com.sorveteria.bomcream.vendas.service;

import com.sorveteria.bomcream.vendas.controller.dto.CategoriaDTO;
import com.sorveteria.bomcream.vendas.repository.CategoriaRepository;
import com.sorveteria.bomcream.vendas.repository.entity.CategoriaEntity;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoriaService {
    private final CategoriaRepository repository;
    private final ModelMapper mapper;

    public void create(CategoriaDTO dto) {
        repository.save(converterDTOToEntity(dto));
    }

    public void createAll(List<CategoriaDTO> list) {
        repository.saveAll(list.stream().map(this::converterDTOToEntity).collect(Collectors.toList()));
    }

    public void edit(CategoriaDTO dto, String id) {
        CategoriaEntity entity = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado"));

        entity.setNome(dto.getNome());
        entity.setOrdem(dto.getOrdem());
        entity.setTipo(dto.getTipo());
        entity.setNcmPadrao(dto.getNcmPadrao());

        repository.save(entity);
    }

    public List<CategoriaDTO> findAll() {
        return ((List<CategoriaEntity>) repository.findAll(Sort.by(Sort.Direction.ASC, "ordem"))).stream()
                .map(this::converterEntityToDTO).collect(Collectors.toList());
    }

    public void delete(String id) {
        repository.deleteById(id);
    }

    public void deleteAll() {
        repository.deleteAll();
    }

    public CategoriaDTO get(String id) {
        return converterEntityToDTO(repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Categoria não encontrado")));
    }

    private CategoriaDTO converterEntityToDTO(CategoriaEntity entity) {
        return mapper.map(entity, CategoriaDTO.class);
    }

    private CategoriaEntity converterDTOToEntity(CategoriaDTO dto) {
        return mapper.map(dto, CategoriaEntity.class);
    }

}
