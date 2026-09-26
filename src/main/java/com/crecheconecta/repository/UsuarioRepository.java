package com.crecheconecta.repository;

import com.crecheconecta.entity.Usuario;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT u
        FROM Usuario u
        WHERE u.email = :email
        """)
    Optional<Usuario> buscarPorEmailComBloqueio(
            @Param("email") String email
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT u
        FROM Usuario u
        WHERE u.id = :id
        """)
    Optional<Usuario> buscarPorIdComBloqueio(
            @Param("id") UUID id
    );
}
