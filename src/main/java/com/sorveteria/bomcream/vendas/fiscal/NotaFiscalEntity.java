package com.sorveteria.bomcream.vendas.fiscal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "nota_fiscal")
public class NotaFiscalEntity {
    @Id
    private String uid;
    private String vendaUid;
    private NotaFiscalStatus status;
    private String numero;
    private String serie;
    private String chaveAcesso;
    private String protocoloAutorizacao;
    private String mensagemSefaz;
    private String urlDanfe;
    private LocalDateTime dataEmissao;
    private LocalDateTime dataCancelamento;
    private String justificativaCancelamento;
}
