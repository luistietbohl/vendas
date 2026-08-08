package com.sorveteria.bomcream.vendas.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProdutoDTO {
    private String uid;
    private String nome;
    private BigDecimal valor;
    private String tipoMedida;
    private String categoria;
    private String ncm;
    private String cfop;
    private String csosn;
    private String unidadeComercial;
}
