package com.crecheconecta.repository;

import com.crecheconecta.entity.StatusTermo;
import com.crecheconecta.entity.TermoVersao;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TermoVersaoRepository extends JpaRepository<TermoVersao, UUID> {

    // A versão vigente (no máximo uma, garantido por uk_termos_versoes_vigente).
    Optional<TermoVersao> findByStatus(StatusTermo status);

    // Usado ao publicar: trava a vigente para duas publicações simultâneas não se atropelarem.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TermoVersao t WHERE t.status = :status")
    Optional<TermoVersao> buscarPorStatusComBloqueio(@Param("status") StatusTermo status);

    boolean existsByIdentificadorIgnoreCase(String identificador);

    boolean existsByIdentificadorIgnoreCaseAndIdNot(String identificador, UUID id);

    List<TermoVersao> findAllByOrderByCriadoEmDescIdDesc();
}
