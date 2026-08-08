package com.sorveteria.bomcream.vendas.service;

import com.sorveteria.bomcream.vendas.controller.dto.VendaDTO;
import com.sorveteria.bomcream.vendas.repository.VendaRepository;
import com.sorveteria.bomcream.vendas.repository.entity.VendaEntity;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VendaService {
    private final VendaRepository repository;
    private final ModelMapper mapper;

    public VendaDTO create(VendaDTO dto) {
        VendaEntity saved = repository.save(mapper.map(dto, VendaEntity.class));
        return converterEntityToDTO(saved);
    }

    public void edit(VendaDTO dto, String id) {
        VendaEntity entity = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Venda não encontrada"));

        entity.setFormaPagamento(dto.getFormaPagamento());
        entity.setValorPago(dto.getValorPago());
        entity.setValorTroco(dto.getValorTroco());

        repository.save(entity);
    }

    public List<VendaDTO> findAll() {
        return ((List<VendaEntity>) repository.findAll()).stream()
                .map(this::converterEntityToDTO).collect(Collectors.toList());
    }

    public Page<VendaDTO> findPage(Pageable pageable) {
        return repository.findAll(pageable).map(this::converterEntityToDTO);
    }

    public Page<VendaDTO> findByFilter(LocalDateTime start, LocalDateTime end, Pageable pageable) {
        if (start != null && end != null) {
            return repository.findByCreateBetween(start, end, pageable).map(this::converterEntityToDTO);
        } else {
            return repository.findAll(pageable).map(this::converterEntityToDTO);
        }
    }

    public List<VendaDTO> findByFilter(LocalDateTime start, LocalDateTime end) {
        if (start != null && end != null) {
            return repository.findAllByCreateBetween(start, end).stream().map(this::converterEntityToDTO)
                    .collect(Collectors.toList());
        } else if (start != null) {
            return repository.findAllByCreateAfter(start).stream().map(this::converterEntityToDTO)
                    .collect(Collectors.toList());
        } else if (end != null) {
            return repository.findAllByCreateBefore(end).stream().map(this::converterEntityToDTO)
                    .collect(Collectors.toList());
        } else {
            return ((List<VendaEntity>) repository.findAll()).stream().map(this::converterEntityToDTO)
                    .collect(Collectors.toList());
        }
    }

    public void delete(String id) {
        repository.deleteById(id);
    }

    public void deleteAll() {
        repository.deleteAll();
    }

    private VendaDTO converterEntityToDTO(VendaEntity entity) {
        return mapper.map(entity, VendaDTO.class);
    }
}
