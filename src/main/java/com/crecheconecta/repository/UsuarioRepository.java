package com.crecheconecta.repository;

import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.SituacaoCadastro;
import com.crecheconecta.entity.Usuario;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);

    Page<Usuario> findByPerfilAndSituacaoCadastro(
            Perfil perfil, SituacaoCadastro situacaoCadastro, Pageable pageable
    );

    Page<Usuario> findByPerfil(Perfil perfil, Pageable pageable);

    @Query("""
            SELECT u FROM Usuario u
            WHERE u.perfil = :perfil
              AND (:todasSituacoes = true OR u.situacaoCadastro = :situacao)
              AND (
                  LOCATE(LOWER(:busca), LOWER(u.nome)) > 0
                  OR LOCATE(LOWER(:busca), LOWER(u.email)) > 0
              )
            """)
    Page<Usuario> buscarPorNomeOuEmail(
            @Param("perfil") Perfil perfil,
            @Param("todasSituacoes") boolean todasSituacoes,
            @Param("situacao") SituacaoCadastro situacao,
            @Param("busca") String busca,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT u
            FROM Usuario u
            WHERE u.email = :email
            """)
    Optional<Usuario> buscarPorEmailComBloqueio(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT u
            FROM Usuario u
            WHERE u.id = :id
            """)
    Optional<Usuario> buscarPorIdComBloqueio(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT u
            FROM Usuario u
            WHERE u.perfil = :perfil
            ORDER BY u.id
            """)
    List<Usuario> buscarPorPerfilComBloqueio(@Param("perfil") Perfil perfil);
}