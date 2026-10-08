package com.crecheconecta.service;

import com.crecheconecta.dto.LoginRequest;
import com.crecheconecta.entity.AcaoVerificacao;
import com.crecheconecta.entity.FinalidadeAcao;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.exception.AutenticacaoException;
import com.crecheconecta.exception.ErroAutenticacao;
import com.crecheconecta.repository.AcaoVerificacaoRepository;
import com.crecheconecta.repository.UsuarioRepository;
import com.crecheconecta.security.SegredoVerificacaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UsuarioRepository usuarios;

    @Mock
    private AcaoVerificacaoRepository acoes;

    @Mock
    private PasswordEncoder encoder;

    @Mock
    private SegredoVerificacaoService segredos;

    @Mock
    private EmailService emailService;

    @Mock
    private PlatformTransactionManager transactionManager;

    private AuthService authService;

    private Usuario usuario;

    @BeforeEach
    public void preparar() {
        when(transactionManager.getTransaction(any()))
                .thenReturn(mock(TransactionStatus.class));

        when(encoder.encode(anyString()))
                .thenReturn("hash-ficticio");

        authService = new AuthService(
                usuarios,
                acoes,
                encoder,
                segredos,
                emailService,
                transactionManager
        );

        usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setNome("Direção");
        usuario.setEmail("direcao@example.test");
        usuario.setPerfil(Perfil.DIRECAO);
        usuario.setSenhaHash("hash-da-senha");
        usuario.setAtivo(true);
        usuario.setTentativas(0);
    }

    @Test
    public void deveCriarAcaoEEnviarCodigoQuandoCredenciaisCorretas() {
        // Arrange
        var request = new LoginRequest(
                "direcao@example.test",
                "senha-correta"
        );

        when(usuarios.buscarPorEmailComBloqueio(usuario.getEmail()))
                .thenReturn(Optional.of(usuario));

        when(encoder.matches("senha-correta", "hash-da-senha"))
                .thenReturn(true);

        when(segredos.gerarCodigo())
                .thenReturn("012345");

        when(segredos.proteger(
                eq(usuario.getId()),
                any(UUID.class),
                eq(FinalidadeAcao.LOGIN),
                eq("012345")
        )).thenReturn("hash-do-codigo");

        Instant antes = Instant.now();

        // Act
        var resposta = authService.iniciarLogin(request);

        Instant depois = Instant.now();

        // Assert
        var captor = ArgumentCaptor.forClass(AcaoVerificacao.class);

        verify(acoes).save(captor.capture());

        AcaoVerificacao acaoCriada = captor.getValue();

        assertEquals(usuario, acaoCriada.getUsuario());
        assertEquals(FinalidadeAcao.LOGIN, acaoCriada.getFinalidade());
        assertEquals("hash-do-codigo", acaoCriada.getSegredoHash());
        assertEquals(0, acaoCriada.getTentativas());

        assertEquals(acaoCriada.getId(), resposta.acaoId());
        assertEquals(acaoCriada.getExpiraEm(), resposta.expiraEm());
        assertEquals(
                "Código enviado. Confira seu e-mail.",
                resposta.mensagem()
        );

        // O código deve expirar cinco minutos após sua criação.
        assertFalse(
                resposta.expiraEm().isBefore(antes.plusSeconds(300))
        );

        assertFalse(
                resposta.expiraEm().isAfter(depois.plusSeconds(300))
        );

        verify(emailService).enviarCodigo(
                acaoCriada.getId(),
                usuario.getEmail(),
                "012345",
                FinalidadeAcao.LOGIN
        );
    }

    @Test
    public void deveLancarExcecaoENaoEnviarCodigoQuandoSenhaIncorreta() {
        // Arrange
        var request = new LoginRequest(
                usuario.getEmail(),
                "senha-incorreta"
        );

        when(usuarios.buscarPorEmailComBloqueio(usuario.getEmail()))
                .thenReturn(Optional.of(usuario));

        when(encoder.matches("senha-incorreta", "hash-da-senha"))
                .thenReturn(false);

        // Act
        var exception = assertThrows(
                AutenticacaoException.class,
                () -> authService.iniciarLogin(request)
        );

        // Assert
        assertEquals(
                ErroAutenticacao.CREDENCIAIS_INVALIDAS,
                exception.getErro()
        );

        assertEquals(
                "E-mail ou senha inválidos.",
                exception.getMessage()
        );

        assertEquals(1, usuario.getTentativas());
        assertNull(usuario.getBloqueadoAte());

        verify(acoes, never()).save(any());

        verify(emailService, never()).enviarCodigo(
                any(),
                anyString(),
                anyString(),
                any()
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 5})
    public void deveBloquearContaSomenteAoAtingirQuintaFalha(
            int totalDeFalhas
    ) {
        // Arrange
        usuario.setTentativas(totalDeFalhas - 1);

        var request = new LoginRequest(
                usuario.getEmail(),
                "senha-incorreta"
        );

        when(usuarios.buscarPorEmailComBloqueio(usuario.getEmail()))
                .thenReturn(Optional.of(usuario));

        when(encoder.matches("senha-incorreta", "hash-da-senha"))
                .thenReturn(false);

        Instant antes = Instant.now();

        // Act
        var exception = assertThrows(
                AutenticacaoException.class,
                () -> authService.iniciarLogin(request)
        );

        Instant depois = Instant.now();

        // Assert
        assertEquals(
                ErroAutenticacao.CREDENCIAIS_INVALIDAS,
                exception.getErro()
        );

        assertEquals(
                "E-mail ou senha inválidos.",
                exception.getMessage()
        );

        assertEquals(totalDeFalhas, usuario.getTentativas());

        if (totalDeFalhas == 4) {
            assertNull(usuario.getBloqueadoAte());
        } else {
            assertNotNull(usuario.getBloqueadoAte());

            // Na quinta falha, bloqueia por 15 minutos ou 900 segundos.
            assertFalse(
                    usuario.getBloqueadoAte()
                            .isBefore(antes.plusSeconds(900))
            );

            assertFalse(
                    usuario.getBloqueadoAte()
                            .isAfter(depois.plusSeconds(900))
            );
        }

        verify(acoes, never()).save(any());
        verifyNoInteractions(emailService);
    }

    @Test
    void deveRejeitarUsuarioInexistenteSemEnviarCodigo() {
        // Arrange
        var request = new LoginRequest(
                "inexistente@example.test",
                "senha-informada"
        );

        when(usuarios.buscarPorEmailComBloqueio(request.email()))
                .thenReturn(Optional.empty());

        // Act
        var exception = assertThrows(
                AutenticacaoException.class,
                () -> authService.iniciarLogin(request)
        );

        // Assert
        assertEquals(
                ErroAutenticacao.CREDENCIAIS_INVALIDAS,
                exception.getErro()
        );

        assertEquals(
                "E-mail ou senha inválidos.",
                exception.getMessage()
        );

        // Mesmo sem usuário, confere contra o hash fictício.
        verify(encoder).matches(
                "senha-informada",
                "hash-ficticio"
        );

        verify(acoes, never()).save(any());
        verifyNoInteractions(emailService);
    }

    @Test
    void deveRejeitarContaInativaSemConferirSenha() {
        // Arrange
        usuario.setAtivo(false);

        var request = new LoginRequest(
                usuario.getEmail(),
                "senha-correta"
        );

        when(usuarios.buscarPorEmailComBloqueio(usuario.getEmail()))
                .thenReturn(Optional.of(usuario));

        // Act
        var exception = assertThrows(
                AutenticacaoException.class,
                () -> authService.iniciarLogin(request)
        );

        // Assert
        assertEquals(
                ErroAutenticacao.CREDENCIAIS_INVALIDAS,
                exception.getErro()
        );

        assertEquals(
                "E-mail ou senha inválidos.",
                exception.getMessage()
        );

        assertEquals(0, usuario.getTentativas());

        verify(encoder, never()).matches(
                anyString(),
                anyString()
        );

        verify(acoes, never()).save(any());
        verifyNoInteractions(emailService);
    }

    @Test
    void deveRejeitarContaBloqueadaSemConferirSenha() {
        // Arrange
        Instant bloqueadoAte = Instant.now().plusSeconds(900);

        usuario.setTentativas(5);
        usuario.setBloqueadoAte(bloqueadoAte);

        var request = new LoginRequest(
                usuario.getEmail(),
                "senha-correta"
        );

        when(usuarios.buscarPorEmailComBloqueio(usuario.getEmail()))
                .thenReturn(Optional.of(usuario));

        // Act
        var exception = assertThrows(
                AutenticacaoException.class,
                () -> authService.iniciarLogin(request)
        );

        // Assert
        assertEquals(
                ErroAutenticacao.CREDENCIAIS_INVALIDAS,
                exception.getErro()
        );

        assertEquals(
                "E-mail ou senha inválidos.",
                exception.getMessage()
        );

        assertEquals(5, usuario.getTentativas());
        assertEquals(bloqueadoAte, usuario.getBloqueadoAte());

        verify(encoder, never()).matches(
                anyString(),
                anyString()
        );

        verify(acoes, never()).save(any());
        verifyNoInteractions(emailService);
    }

    @Test
    void devePermitirLoginQuandoBloqueioJaExpirou() {
        // Arrange
        usuario.setTentativas(5);
        usuario.setBloqueadoAte(
                Instant.now().minusSeconds(60)
        );

        var request = new LoginRequest(
                usuario.getEmail(),
                "senha-correta"
        );

        when(usuarios.buscarPorEmailComBloqueio(usuario.getEmail()))
                .thenReturn(Optional.of(usuario));

        when(encoder.matches("senha-correta", "hash-da-senha"))
                .thenReturn(true);

        when(segredos.gerarCodigo())
                .thenReturn("012345");

        when(segredos.proteger(
                eq(usuario.getId()),
                any(UUID.class),
                eq(FinalidadeAcao.LOGIN),
                eq("012345")
        )).thenReturn("hash-do-codigo");

        // Act
        var resposta = authService.iniciarLogin(request);

        // Assert
        assertNotNull(resposta.acaoId());
        assertEquals(0, usuario.getTentativas());
        assertNull(usuario.getBloqueadoAte());

        verify(acoes).save(any(AcaoVerificacao.class));

        verify(emailService).enviarCodigo(
                resposta.acaoId(),
                usuario.getEmail(),
                "012345",
                FinalidadeAcao.LOGIN
        );
    }

    @Test
    void deveImpedirReenvioAntesDeSessentaSegundos() {
        // Arrange
        var request = new LoginRequest(
                usuario.getEmail(),
                "senha-correta"
        );

        var acaoAnterior = new AcaoVerificacao();
        acaoAnterior.setUsuario(usuario);
        acaoAnterior.setFinalidade(FinalidadeAcao.LOGIN);
        acaoAnterior.setCriadoEm(Instant.now());

        when(usuarios.buscarPorEmailComBloqueio(usuario.getEmail()))
                .thenReturn(Optional.of(usuario));

        when(encoder.matches("senha-correta", "hash-da-senha"))
                .thenReturn(true);

        when(
                acoes.findFirstByUsuario_IdAndFinalidadeOrderByCriadoEmDesc(
                        usuario.getId(),
                        FinalidadeAcao.LOGIN
                )
        ).thenReturn(Optional.of(acaoAnterior));

        // Act
        var exception = assertThrows(
                AutenticacaoException.class,
                () -> authService.iniciarLogin(request)
        );

        // Assert
        assertEquals(
                ErroAutenticacao.LIMITE_TENTATIVAS,
                exception.getErro()
        );

        assertEquals(
                "Limite de tentativas atingido. Tente novamente mais tarde.",
                exception.getMessage()
        );

        verify(acoes, never()).save(any());
        verifyNoInteractions(segredos, emailService);
    }

    @ParameterizedTest
    @ValueSource(ints = {72, 73})
    void deveRespeitarLimiteDeSetentaEDoisBytesDaSenha(
            int quantidadeDeBytes
    ) {
        // Arrange: cada "a" ocupa um byte em UTF-8.
        String senha = "a".repeat(quantidadeDeBytes);

        var request = new LoginRequest(
                usuario.getEmail(),
                senha
        );

        when(usuarios.buscarPorEmailComBloqueio(usuario.getEmail()))
                .thenReturn(Optional.of(usuario));

        if (quantidadeDeBytes == 72) {
            when(encoder.matches(senha, "hash-da-senha"))
                    .thenReturn(true);

            when(segredos.gerarCodigo())
                    .thenReturn("012345");

            when(segredos.proteger(
                    eq(usuario.getId()),
                    any(UUID.class),
                    eq(FinalidadeAcao.LOGIN),
                    eq("012345")
            )).thenReturn("hash-do-codigo");
        }

        // Act + Assert
        if (quantidadeDeBytes == 72) {
            var resposta = authService.iniciarLogin(request);

            assertNotNull(resposta.acaoId());

            verify(encoder).matches(
                    senha,
                    "hash-da-senha"
            );

            verify(acoes).save(any(AcaoVerificacao.class));

            verify(emailService).enviarCodigo(
                    resposta.acaoId(),
                    usuario.getEmail(),
                    "012345",
                    FinalidadeAcao.LOGIN
            );
        } else {
            var exception = assertThrows(
                    AutenticacaoException.class,
                    () -> authService.iniciarLogin(request)
            );

            assertEquals(
                    ErroAutenticacao.CREDENCIAIS_INVALIDAS,
                    exception.getErro()
            );

            assertEquals(
                    "E-mail ou senha inválidos.",
                    exception.getMessage()
            );

            assertEquals(1, usuario.getTentativas());

            verify(encoder, never()).matches(
                    anyString(),
                    anyString()
            );

            verify(acoes, never()).save(any());
            verifyNoInteractions(emailService);
        }
    }

    @Test
    void deveRejeitarSenhaMultibyteQueUltrapassaLimiteUtf8() {
        // Arrange
        String senha = "á".repeat(37);

        var request = new LoginRequest(
                usuario.getEmail(),
                senha
        );

        when(usuarios.buscarPorEmailComBloqueio(usuario.getEmail()))
                .thenReturn(Optional.of(usuario));

        // Act
        var exception = assertThrows(
                AutenticacaoException.class,
                () -> authService.iniciarLogin(request)
        );

        // Assert
        assertEquals(
                ErroAutenticacao.CREDENCIAIS_INVALIDAS,
                exception.getErro()
        );

        assertEquals(
                "E-mail ou senha inválidos.",
                exception.getMessage()
        );

        assertEquals(1, usuario.getTentativas());

        verify(encoder, never()).matches(
                anyString(),
                anyString()
        );

        verify(acoes, never()).save(any());
        verifyNoInteractions(emailService);
    }
}