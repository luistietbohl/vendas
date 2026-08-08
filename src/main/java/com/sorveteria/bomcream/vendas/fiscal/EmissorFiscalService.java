package com.sorveteria.bomcream.vendas.fiscal;

import com.sorveteria.bomcream.vendas.repository.entity.VendaEntity;

public interface EmissorFiscalService {
    ResultadoEmissaoFiscal emitir(VendaEntity venda);

    ResultadoEmissaoFiscal consultarStatus(String referencia);

    ResultadoEmissaoFiscal cancelar(String referencia, String justificativa);
}
