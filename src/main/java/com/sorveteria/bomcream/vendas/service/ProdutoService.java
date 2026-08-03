package com.sorveteria.bomcream.vendas.service;

import com.sorveteria.bomcream.vendas.controller.dto.FilterProdutoDTO;
import com.sorveteria.bomcream.vendas.controller.dto.ProdutoDTO;
import com.sorveteria.bomcream.vendas.repository.ProdutoRepository;
import com.sorveteria.bomcream.vendas.repository.entity.ProdutoEntity;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProdutoService {
    private final ProdutoRepository repository;
    private final MongoTemplate mongoTemplate;
    private final ModelMapper mapper;

    public void create(ProdutoDTO dto) {
        repository.save(converterDTOToEntity(dto));
    }

    public void createAll(List<ProdutoDTO> list) {
        repository.saveAll(list.stream().map(this::converterDTOToEntity).collect(Collectors.toList()));
    }

    public void edit(ProdutoDTO dto, String id) {
        ProdutoEntity entity = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado"));

        entity.setNome(dto.getNome());
        entity.setValor(dto.getValor());
        entity.setTipoMedida(dto.getTipoMedida());
        entity.setCategoria(dto.getCategoria());
        entity.setNcm(dto.getNcm());
        entity.setCfop(dto.getCfop());
        entity.setCsosn(dto.getCsosn());
        entity.setUnidadeComercial(dto.getUnidadeComercial());

        repository.save(entity);
    }

    public List<ProdutoDTO> findAll() {
        return ((List<ProdutoEntity>) repository.findAll()).stream()
                .map(this::converterEntityToDTO).collect(Collectors.toList());
    }

    public Page<ProdutoDTO> findPage(Pageable pageable) {
        return repository.findAll(pageable).map(this::converterEntityToDTO);
    }

    public Page<ProdutoDTO> findByFilter(LocalDateTime start, LocalDateTime end, Pageable pageable) {
        return repository.findByCreateBetween(start, end, pageable).map(this::converterEntityToDTO);
    }

    public Page<ProdutoDTO> findByFilter(FilterProdutoDTO dto, Pageable pageable) {
        Query query = new Query();

        if (dto.getNome() != null) {
            query.addCriteria(Criteria.where("nome").regex("^" + dto.getNome()));
        }
        if (dto.getStart() != null && dto.getEnd() != null) {
            query.addCriteria(Criteria.where("create").gte(dto.getStart()).lte(dto.getEnd()));
        } else if (dto.getStart() != null) {
            query.addCriteria(Criteria.where("create").gte(dto.getStart()));
        } else if (dto.getEnd() != null) {
            query.addCriteria(Criteria.where("create").lte(dto.getEnd()));
        }
        long count = mongoTemplate.count(query, ProdutoEntity.class);
        query.with(pageable);
        List<ProdutoEntity> list = mongoTemplate.find(query, ProdutoEntity.class);

        return new PageImpl<ProdutoDTO>(list.stream()
                .map(this::converterEntityToDTO)
                .collect(Collectors.toList()), pageable, count);
    }

    public void delete(String id) {
        repository.deleteById(id);
    }

    public void deleteAll() {
        repository.deleteAll();
    }

    public ProdutoDTO get(String id) {
        return converterEntityToDTO(repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado")));
    }

    private ProdutoDTO converterEntityToDTO(ProdutoEntity entity) {
        return mapper.map(entity, ProdutoDTO.class);
    }

    private ProdutoEntity converterDTOToEntity(ProdutoDTO dto) {
        return mapper.map(dto, ProdutoEntity.class);
    }

}
