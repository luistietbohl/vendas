package com.sorveteria.bomcream.vendas.repository.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "produto")
public class ProdutoEntity {
    @Id
    private String uid;
    private String nome;
    private BigDecimal valor;
    private String tipoMedida;
    private String categoria;
    private String ncm;
    private String cfop;
    private String csosn;
    private String unidadeComercial;
    private LocalDateTime create = LocalDateTime.now();
}
