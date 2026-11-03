package com.crecheconecta.service;

import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.Sessao;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.repository.SessaoRepository;
import com.crecheconecta.security.UsuarioAutenticado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static com.crecheconecta.service.DadosAutenticacaoTest.HASH;
import static com.crecheconecta.service.DadosAutenticacaoTest.TOKEN;
import static com.crecheconecta.service.DadosAutenticacaoTest.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SessaoServiceTest {

    private final SessaoRepository repository =
            mock(SessaoRepository.class);

    private final TokenSessaoService tokens =
            mock(TokenSessaoService.class);

    private final SessaoService service =
            new SessaoService(repository, tokens);

    private Usuario pessoa;
    private Sessao sessao;

    @BeforeEach
    void preparar() {
        pessoa = usuario(Perfil.PROFESSOR);

        sessao = new Sessao();
        sessao.setId(UUID.randomUUID());
        sessao.setUsuario(pessoa);
        sessao.setExpiraEm(Instant.now().plusSeconds(3600));
    }

    @Test
    void deveAutenticarSessaoValida() {
        prepararBusca();

        var autenticado = service.autenticar(TOKEN).orElseThrow();

        assertEquals(pessoa.getId(), autenticado.usuarioId());
        assertEquals(sessao.getId(), autenticado.sessaoId());
        assertEquals(Perfil.PROFESSOR, autenticado.perfil());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"abc", "token-invalido!"})
    void deveRejeitarTokenMalformado(String token) {
        assertTrue(service.autenticar(token).isEmpty());

        verifyNoInteractions(repository, tokens);
    }

    @Test
    void deveRejeitarSessaoNaoEncontrada() {
        when(tokens.calcularHash(TOKEN)).thenReturn(HASH);

        assertTrue(service.autenticar(TOKEN).isEmpty());
    }

    @Test
    void deveRejeitarSessaoRevogada() {
        prepararBusca();
        sessao.setRevogadoEm(Instant.now());

        assertTrue(service.autenticar(TOKEN).isEmpty());
    }

    @Test
    void deveRejeitarSessaoExpirada() {
        prepararBusca();
        sessao.setExpiraEm(Instant.now().minusSeconds(3600));

        assertTrue(service.autenticar(TOKEN).isEmpty());
    }

    @Test
    void deveRejeitarUsuarioInativo() {
        prepararBusca();
        pessoa.setAtivo(false);

        assertTrue(service.autenticar(TOKEN).isEmpty());
    }

    @Test
    void deveRevogarSomenteASessaoDoUsuarioNoLogout() {
        var autenticado = new UsuarioAutenticado(
                pessoa.getId(),
                sessao.getId(),
                pessoa.getNome(),
                pessoa.getEmail(),
                pessoa.getPerfil()
        );

        service.logout(autenticado);

        verify(repository).revogar(
                eq(sessao.getId()),
                eq(pessoa.getId()),
                any(Instant.class)
        );

        verify(repository, never()).revogarTodasDoUsuario(
                any(UUID.class),
                any(Instant.class)
        );
    }

    private void prepararBusca() {
        when(tokens.calcularHash(TOKEN)).thenReturn(HASH);

        when(repository.findByTokenHash(HASH))
                .thenReturn(Optional.of(sessao));
    }
}