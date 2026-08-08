package com.sorveteria.bomcream.vendas.fiscal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResultadoEmissaoFiscal {
    private NotaFiscalStatus status;
    private String numero;
    private String serie;
    private String chaveAcesso;
    private String protocoloAutorizacao;
    private String mensagemSefaz;
    private String urlDanfe;
}
