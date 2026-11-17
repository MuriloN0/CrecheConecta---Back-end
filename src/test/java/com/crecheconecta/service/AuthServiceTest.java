package com.crecheconecta.service;

import com.crecheconecta.controller.AuthController;
import com.crecheconecta.dto.LoginRequest;
import com.crecheconecta.entity.AcaoVerificacao;
import com.crecheconecta.entity.FinalidadeAcao;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.exception.ApiExceptionHandler;
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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.web.ErrorResponseException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
        when(encoder.encode(anyString())).thenReturn("hash-ficticio");

        authService = new AuthService(usuarios, acoes, encoder, segredos, emailService, transactionManager);

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
        var request = new LoginRequest("direcao@example.test", "senha-correta");

        when(usuarios.buscarPorEmailComBloqueio(usuario.getEmail())).thenReturn(Optional.of(usuario));
        when(encoder.matches("senha-correta", "hash-da-senha")).thenReturn(true);
        when(segredos.gerarCodigo()).thenReturn("012345");
        when(segredos.proteger(eq(usuario.getId()), any(UUID.class), eq(FinalidadeAcao.LOGIN), eq("012345")))
                .thenReturn("hash-do-codigo");

        Instant antes = Instant.now();
        var resposta = authService.iniciarLogin(request);
        Instant depois = Instant.now();

        var captor = ArgumentCaptor.forClass(AcaoVerificacao.class);
        verify(acoes).save(captor.capture());
        AcaoVerificacao acaoCriada = captor.getValue();

        assertEquals(usuario, acaoCriada.getUsuario());
        assertEquals(FinalidadeAcao.LOGIN, acaoCriada.getFinalidade());
        assertEquals("hash-do-codigo", acaoCriada.getSegredoHash());
        assertEquals(0, acaoCriada.getTentativas());
        assertEquals(acaoCriada.getId(), resposta.acaoId());
        assertEquals(acaoCriada.getExpiraEm(), resposta.expiraEm());
        assertEquals("Código enviado. Confira seu e-mail.", resposta.mensagem());
        assertFalse(resposta.expiraEm().isBefore(antes.plusSeconds(300)));
        assertFalse(resposta.expiraEm().isAfter(depois.plusSeconds(300)));

        verify(emailService).enviarCodigo(
                acaoCriada.getId(), usuario.getEmail(), "012345", FinalidadeAcao.LOGIN
        );
    }

    @Test
    public void deveLancarExcecaoENaoEnviarCodigoQuandoSenhaIncorreta() {
        var request = new LoginRequest(usuario.getEmail(), "senha-incorreta");

        when(usuarios.buscarPorEmailComBloqueio(usuario.getEmail())).thenReturn(Optional.of(usuario));
        when(encoder.matches("senha-incorreta", "hash-da-senha")).thenReturn(false);

        var exception = assertThrows(AutenticacaoException.class, () -> authService.iniciarLogin(request));

        assertEquals(ErroAutenticacao.CREDENCIAIS_INVALIDAS, exception.getErro());
        assertEquals("E-mail ou senha inválidos.", exception.getMessage());
        assertEquals(1, usuario.getTentativas());
        assertNull(usuario.getBloqueadoAte());

        verify(acoes, never()).save(any());
        verify(emailService, never()).enviarCodigo(any(), anyString(), anyString(), any());
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 5})
    public void deveBloquearContaSomenteAoAtingirQuintaFalha(int totalDeFalhas) {
        usuario.setTentativas(totalDeFalhas - 1);
        var request = new LoginRequest(usuario.getEmail(), "senha-incorreta");

        when(usuarios.buscarPorEmailComBloqueio(usuario.getEmail())).thenReturn(Optional.of(usuario));
        when(encoder.matches("senha-incorreta", "hash-da-senha")).thenReturn(false);

        Instant antes = Instant.now();

        if (totalDeFalhas == 4) {
            var exception = assertThrows(
                    AutenticacaoException.class,
                    () -> authService.iniciarLogin(request)
            );
            assertEquals(ErroAutenticacao.CREDENCIAIS_INVALIDAS, exception.getErro());
            assertNull(usuario.getBloqueadoAte());
        } else {
            var exception = assertThrows(
                    ErrorResponseException.class,
                    () -> authService.iniciarLogin(request)
            );
            assertBloqueio(exception, 300);

            Instant depois = Instant.now();
            assertNotNull(usuario.getBloqueadoAte());
            assertFalse(usuario.getBloqueadoAte().isBefore(antes.plusSeconds(300)));
            assertFalse(usuario.getBloqueadoAte().isAfter(depois.plusSeconds(300)));
        }

        assertEquals(totalDeFalhas, usuario.getTentativas());
        verify(acoes, never()).save(any());
        verifyNoInteractions(emailService);
    }

    @Test
    void deveRejeitarUsuarioInexistenteSemEnviarCodigo() {
        var request = new LoginRequest("inexistente@example.test", "senha-informada");
        when(usuarios.buscarPorEmailComBloqueio(request.email())).thenReturn(Optional.empty());

        var exception = assertThrows(AutenticacaoException.class, () -> authService.iniciarLogin(request));

        assertEquals(ErroAutenticacao.CREDENCIAIS_INVALIDAS, exception.getErro());
        assertEquals("E-mail ou senha inválidos.", exception.getMessage());
        verify(encoder).matches("senha-informada", "hash-ficticio");
        verify(acoes, never()).save(any());
        verifyNoInteractions(emailService);
    }

    @Test
    void deveRejeitarContaInativaSemConferirSenha() {
        usuario.setAtivo(false);
        var request = new LoginRequest(usuario.getEmail(), "senha-correta");
        when(usuarios.buscarPorEmailComBloqueio(usuario.getEmail())).thenReturn(Optional.of(usuario));

        var exception = assertThrows(AutenticacaoException.class, () -> authService.iniciarLogin(request));

        assertEquals(ErroAutenticacao.CREDENCIAIS_INVALIDAS, exception.getErro());
        assertEquals("E-mail ou senha inválidos.", exception.getMessage());
        assertEquals(0, usuario.getTentativas());
        verify(encoder, never()).matches(anyString(), anyString());
        verify(acoes, never()).save(any());
        verifyNoInteractions(emailService);
    }

    @Test
    void deveRejeitarContaBloqueadaSemConferirSenha() {
        Instant bloqueadoAte = Instant.now().plusSeconds(90);
        usuario.setTentativas(5);
        usuario.setBloqueadoAte(bloqueadoAte);
        var request = new LoginRequest(usuario.getEmail(), "senha-correta");

        when(usuarios.buscarPorEmailComBloqueio(usuario.getEmail())).thenReturn(Optional.of(usuario));

        var exception = assertThrows(ErrorResponseException.class, () -> authService.iniciarLogin(request));

        assertBloqueio(exception, 90);
        assertEquals(5, usuario.getTentativas());
        assertEquals(bloqueadoAte, usuario.getBloqueadoAte());
        verify(encoder, never()).matches(anyString(), anyString());
        verify(acoes, never()).save(any());
        verifyNoInteractions(emailService);
    }

    @Test
    void devePermitirLoginQuandoBloqueioJaExpirou() {
        usuario.setTentativas(5);
        usuario.setBloqueadoAte(Instant.now().minusSeconds(60));
        var request = new LoginRequest(usuario.getEmail(), "senha-correta");

        when(usuarios.buscarPorEmailComBloqueio(usuario.getEmail())).thenReturn(Optional.of(usuario));
        when(encoder.matches("senha-correta", "hash-da-senha")).thenReturn(true);
        when(segredos.gerarCodigo()).thenReturn("012345");
        when(segredos.proteger(eq(usuario.getId()), any(UUID.class), eq(FinalidadeAcao.LOGIN), eq("012345")))
                .thenReturn("hash-do-codigo");

        var resposta = authService.iniciarLogin(request);

        assertNotNull(resposta.acaoId());
        assertEquals(0, usuario.getTentativas());
        assertNull(usuario.getBloqueadoAte());
        verify(acoes).save(any(AcaoVerificacao.class));
        verify(emailService).enviarCodigo(
                resposta.acaoId(), usuario.getEmail(), "012345", FinalidadeAcao.LOGIN
        );
    }

    @Test
    void deveImpedirReenvioAntesDeSessentaSegundos() {
        var request = new LoginRequest(usuario.getEmail(), "senha-correta");

        var acaoAnterior = new AcaoVerificacao();
        acaoAnterior.setUsuario(usuario);
        acaoAnterior.setFinalidade(FinalidadeAcao.LOGIN);
        acaoAnterior.setCriadoEm(Instant.now());

        when(usuarios.buscarPorEmailComBloqueio(usuario.getEmail())).thenReturn(Optional.of(usuario));
        when(encoder.matches("senha-correta", "hash-da-senha")).thenReturn(true);
        when(acoes.findFirstByUsuario_IdAndFinalidadeOrderByCriadoEmDesc(usuario.getId(), FinalidadeAcao.LOGIN))
                .thenReturn(Optional.of(acaoAnterior));

        var exception = assertThrows(AutenticacaoException.class, () -> authService.iniciarLogin(request));

        assertEquals(ErroAutenticacao.LIMITE_TENTATIVAS, exception.getErro());
        assertEquals("Limite de tentativas atingido. Tente novamente mais tarde.", exception.getMessage());
        verify(acoes, never()).save(any());
        verifyNoInteractions(segredos, emailService);
    }

    @ParameterizedTest
    @ValueSource(ints = {72, 73})
    void deveRespeitarLimiteDeSetentaEDoisBytesDaSenha(int quantidadeDeBytes) {
        String senha = "a".repeat(quantidadeDeBytes);
        var request = new LoginRequest(usuario.getEmail(), senha);

        when(usuarios.buscarPorEmailComBloqueio(usuario.getEmail())).thenReturn(Optional.of(usuario));

        if (quantidadeDeBytes == 72) {
            when(encoder.matches(senha, "hash-da-senha")).thenReturn(true);
            when(segredos.gerarCodigo()).thenReturn("012345");
            when(segredos.proteger(eq(usuario.getId()), any(UUID.class), eq(FinalidadeAcao.LOGIN), eq("012345")))
                    .thenReturn("hash-do-codigo");

            var resposta = authService.iniciarLogin(request);

            assertNotNull(resposta.acaoId());
            verify(encoder).matches(senha, "hash-da-senha");
            verify(acoes).save(any(AcaoVerificacao.class));
            verify(emailService).enviarCodigo(
                    resposta.acaoId(), usuario.getEmail(), "012345", FinalidadeAcao.LOGIN
            );
        } else {
            var exception = assertThrows(
                    AutenticacaoException.class,
                    () -> authService.iniciarLogin(request)
            );

            assertEquals(ErroAutenticacao.CREDENCIAIS_INVALIDAS, exception.getErro());
            assertEquals("E-mail ou senha inválidos.", exception.getMessage());
            assertEquals(1, usuario.getTentativas());
            verify(encoder, never()).matches(anyString(), anyString());
            verify(acoes, never()).save(any());
            verifyNoInteractions(emailService);
        }
    }

    @Test
    void deveRejeitarSenhaMultibyteQueUltrapassaLimiteUtf8() {
        String senha = "á".repeat(37);
        var request = new LoginRequest(usuario.getEmail(), senha);

        when(usuarios.buscarPorEmailComBloqueio(usuario.getEmail())).thenReturn(Optional.of(usuario));

        var exception = assertThrows(AutenticacaoException.class, () -> authService.iniciarLogin(request));

        assertEquals(ErroAutenticacao.CREDENCIAIS_INVALIDAS, exception.getErro());
        assertEquals("E-mail ou senha inválidos.", exception.getMessage());
        assertEquals(1, usuario.getTentativas());
        verify(encoder, never()).matches(anyString(), anyString());
        verify(acoes, never()).save(any());
        verifyNoInteractions(emailService);
    }

    @Test
    void deveResponderCom429ETempoRestanteNaQuintaFalha() throws Exception {
        when(usuarios.buscarPorEmailComBloqueio(usuario.getEmail())).thenReturn(Optional.of(usuario));

        var controller = new AuthController(
                authService,
                mock(ConfirmacaoLoginService.class),
                mock(SessaoService.class),
                mock(TokenSessaoService.class),
                false
        );
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler(mock(AuditoriaService.class)))
                .build();
        String corpo = """
                {"email":"direcao@example.test","senha":"senha-incorreta"}
                """;

        for (int tentativa = 1; tentativa < 5; tentativa++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(corpo))
                    .andExpect(status().isUnauthorized());
        }

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.codigo").value("LOGIN_BLOQUEADO"))
                .andExpect(jsonPath("$.segundosRestantes").isNumber());
    }

    private static void assertBloqueio(ErrorResponseException exception, long maximoSegundos) {
        assertEquals(429, exception.getStatusCode().value());
        var propriedades = exception.getBody().getProperties();
        assertNotNull(propriedades);
        assertEquals("LOGIN_BLOQUEADO", propriedades.get("codigo"));
        long segundos = assertInstanceOf(Long.class, propriedades.get("segundosRestantes"));
        assertTrue(segundos > 0 && segundos <= maximoSegundos);
    }
}