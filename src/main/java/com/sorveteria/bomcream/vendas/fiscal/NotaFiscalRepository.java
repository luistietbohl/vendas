package com.sorveteria.bomcream.vendas.fiscal;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface NotaFiscalRepository extends MongoRepository<NotaFiscalEntity, String> {
    Optional<NotaFiscalEntity> findByVendaUid(String vendaUid);
}
