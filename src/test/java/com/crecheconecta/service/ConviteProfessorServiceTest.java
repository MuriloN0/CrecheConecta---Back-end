package com.crecheconecta.service;

import com.crecheconecta.dto.ConvidarProfessorRequest;
import com.crecheconecta.dto.ConviteCadastroResponse;
import com.crecheconecta.entity.ConviteCadastro;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.exception.AcessoNegadoException;
import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.repository.ConviteCadastroRepository;
import com.crecheconecta.repository.UsuarioRepository;
import com.crecheconecta.security.UsuarioAtual;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static com.crecheconecta.service.DadosAutenticacaoTest.*;
import static com.crecheconecta.service.GerenciadorTransacoesTest.transacoes;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConviteProfessorServiceTest {

    private final ConviteCadastroRepository convites =
            mock(ConviteCadastroRepository.class);

    private final UsuarioRepository usuarios =
            mock(UsuarioRepository.class);

    private final TokenConviteService tokens =
            mock(TokenConviteService.class);

    private final EmailService emails = mock(EmailService.class);

    private final AuditoriaService auditoria =
            mock(AuditoriaService.class);

    private final ConviteProfessorService service =
            criarService("https://example.test/cadastro");

    private final Usuario diretor = usuario(Perfil.DIRECAO);

    private final UsuarioAtual solicitante =
            new UsuarioAtual(diretor.getId(), Perfil.DIRECAO);

    @Test
    void deveCriarConviteComHashValidadeEEmailNormalizado() {
        autorizar();
        prepararToken();

        var resposta = convidar();

        var captor = ArgumentCaptor.forClass(ConviteCadastro.class);
        verify(convites).saveAndFlush(captor.capture());

        var salvo = captor.getValue();

        assertEquals("Professor de teste", salvo.getNome());
        assertEquals(EMAIL, salvo.getEmail());
        assertEquals(Perfil.PROFESSOR, salvo.getPerfil());
        assertEquals(HASH, salvo.getTokenHash());

        assertEquals(
                Duration.ofHours(24),
                Duration.between(
                        salvo.getCriadoEm(),
                        salvo.getExpiraEm()
                )
        );

        assertEquals(salvo.getId(), resposta.id());

        verify(emails).enviarConviteProfessor(
                salvo.getId(),
                EMAIL,
                "https://example.test/cadastro#token=" + TOKEN
        );
    }

    @Test
    void deveRejeitarEmailJaCadastrado() {
        autorizar();

        when(usuarios.existsByEmail(EMAIL)).thenReturn(true);

        assertThrows(RegraNegocioException.class, this::convidar);

        verifyNoInteractions(emails);
        verify(convites, never()).saveAndFlush(any());
    }

    @Test
    void deveImpedirReenvioDentroDoIntervalo() {
        autorizar();

        var anterior = convite(Perfil.PROFESSOR);
        anterior.setCriadoEm(Instant.now());

        when(convites.buscarPendentePorEmailComBloqueio(EMAIL))
                .thenReturn(Optional.of(anterior));

        assertThrows(RegraNegocioException.class, this::convidar);

        assertNull(anterior.getCanceladoEm());
        verifyNoInteractions(emails);
    }

    @Test
    void deveCancelarConviteAnteriorAoReenviar() {
        autorizar();
        prepararToken();

        var anterior = convite(Perfil.PROFESSOR);

        when(convites.buscarPendentePorEmailComBloqueio(EMAIL))
                .thenReturn(Optional.of(anterior));

        convidar();

        assertNotNull(anterior.getCanceladoEm());
        verify(convites).flush();
    }

    @Test
    void deveCancelarNovoConviteSeEmailFalhar() {
        autorizar();
        prepararToken();

        var cancelavel = convite(Perfil.PROFESSOR);

        when(convites.buscarPorIdComBloqueio(any(UUID.class)))
                .thenReturn(Optional.of(cancelavel));

        when(emails.enviarConviteProfessor(
                any(UUID.class),
                anyString(),
                anyString()
        )).thenThrow(new IllegalStateException("Falha simulada"));

        assertThrows(IllegalStateException.class, this::convidar);

        assertNotNull(cancelavel.getCanceladoEm());
    }

    @Test
    void deveNegarDiretorInativo() {
        autorizar();
        diretor.setAtivo(false);

        assertThrows(AcessoNegadoException.class, this::convidar);

        verifyNoInteractions(convites, emails);
    }

    @ParameterizedTest
    @EnumSource(
            value = Perfil.class,
            names = {"PROFESSOR", "RESPONSAVEL"}
    )
    void deveNegarOutrosPerfis(Perfil perfil) {
        assertThrows(
                AcessoNegadoException.class,
                () -> service.convidar(
                        new ConvidarProfessorRequest("Nome", EMAIL),
                        new UsuarioAtual(UUID.randomUUID(), perfil)
                )
        );

        verifyNoInteractions(usuarios, convites, emails);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://example.test/cadastro",
            "https://usuario:senha@example.test/cadastro",
            "https://example.test/cadastro?token=abc",
            "https://example.test/cadastro#token=abc"
    })
    void deveRejeitarUrlDeCadastroInadequada(String url) {
        assertThrows(
                IllegalArgumentException.class,
                () -> criarService(url)
        );
    }

    private ConviteProfessorService criarService(String url) {
        return new ConviteProfessorService(
                convites,
                usuarios,
                tokens,
                emails,
                auditoria,
                transacoes(),
                url
        );
    }

    private void autorizar() {
        when(usuarios.buscarPorIdComBloqueio(diretor.getId()))
                .thenReturn(Optional.of(diretor));
    }

    private void prepararToken() {
        when(tokens.gerarToken()).thenReturn(TOKEN);
        when(tokens.calcularHash(TOKEN)).thenReturn(HASH);
    }

    private ConviteCadastroResponse convidar() {
        return service.convidar(
                new ConvidarProfessorRequest(
                        " Professor de teste ",
                        EMAIL.toUpperCase(Locale.ROOT)
                ),
                solicitante
        );
    }
}