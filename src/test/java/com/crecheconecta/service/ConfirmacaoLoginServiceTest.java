package com.crecheconecta.service;

import com.crecheconecta.dto.ConfirmarLoginRequest;
import com.crecheconecta.entity.AcaoVerificacao;
import com.crecheconecta.entity.FinalidadeAcao;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.Sessao;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.exception.AutenticacaoException;
import com.crecheconecta.exception.ErroAutenticacao;
import com.crecheconecta.repository.AcaoVerificacaoRepository;
import com.crecheconecta.repository.SessaoRepository;
import com.crecheconecta.repository.UsuarioRepository;
import com.crecheconecta.security.SegredoVerificacaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConfirmacaoLoginServiceTest {

    @Mock
    private UsuarioRepository usuarios;

    @Mock
    private AcaoVerificacaoRepository acoes;

    @Mock
    private SessaoRepository sessoes;

    @Mock
    private SegredoVerificacaoService segredos;

    @Mock
    private TokenSessaoService tokens;

    @Mock
    private PlatformTransactionManager transactionManager;

    private ConfirmacaoLoginService service;

    private Usuario usuario;

    private AcaoVerificacao acao;

    @BeforeEach
    void preparar() {
        when(transactionManager.getTransaction(any()))
                .thenReturn(mock(TransactionStatus.class));

        service = new ConfirmacaoLoginService(
                usuarios,
                acoes,
                sessoes,
                segredos,
                tokens,
                transactionManager
        );

        usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setNome("Direção");
        usuario.setEmail("direcao@example.test");
        usuario.setPerfil(Perfil.DIRECAO);
        usuario.setAtivo(true);

        acao = new AcaoVerificacao();
        acao.setUsuario(usuario);
        acao.setFinalidade(FinalidadeAcao.LOGIN);
        acao.setSegredoHash("hash-do-codigo");
        acao.setCriadoEm(Instant.now());
        acao.setExpiraEm(Instant.now().plusSeconds(300));
        acao.setTentativas(0);

        when(acoes.buscarUsuarioId(acao.getId()))
                .thenReturn(Optional.of(usuario.getId()));

        when(usuarios.buscarPorIdComBloqueio(usuario.getId()))
                .thenReturn(Optional.of(usuario));

        when(acoes.buscarPorIdComBloqueio(acao.getId()))
                .thenReturn(Optional.of(acao));
    }

    private void configurarCodigoCorreto() {
        when(segredos.conferir(
                usuario.getId(),
                acao.getId(),
                FinalidadeAcao.LOGIN,
                "012345",
                "hash-do-codigo"
        )).thenReturn(true);

        when(tokens.gerar())
                .thenReturn("token-da-sessao");

        when(tokens.calcularHash("token-da-sessao"))
                .thenReturn("hash-do-token");
    }

    @Test
    void deveCriarSessaoEConsumirAcaoQuandoCodigoCorreto() {
        // Arrange
        configurarCodigoCorreto();

        var request = new ConfirmarLoginRequest(
                acao.getId(),
                "012345"
        );

        Instant antes = Instant.now();

        // Act
        var resposta = service.confirmar(request);

        Instant depois = Instant.now();

        // Assert
        assertEquals(
                "token-da-sessao",
                resposta.accessToken()
        );

        assertEquals("Bearer", resposta.tokenType());
        assertEquals(usuario.getId(), resposta.usuarioId());
        assertEquals(usuario.getNome(), resposta.nome());
        assertEquals(Perfil.DIRECAO, resposta.perfil());

        assertNotNull(acao.getConsumidoEm());
        assertNull(acao.getInvalidadoEm());

        var captor = ArgumentCaptor.forClass(Sessao.class);

        verify(sessoes).save(captor.capture());

        Sessao sessaoCriada = captor.getValue();

        assertEquals(usuario, sessaoCriada.getUsuario());

        assertEquals(
                "hash-do-token",
                sessaoCriada.getTokenHash()
        );

        assertEquals(
                resposta.expiraEm(),
                sessaoCriada.getExpiraEm()
        );

        assertEquals(
                acao.getConsumidoEm(),
                sessaoCriada.getCriadoEm()
        );

        // A sessão deve durar 15 minutos.
        assertFalse(
                resposta.expiraEm()
                        .isBefore(antes.plusSeconds(900))
        );

        assertFalse(
                resposta.expiraEm()
                        .isAfter(depois.plusSeconds(900))
        );
    }

    @Test
    void deveIncrementarTentativasQuandoCodigoIncorreto() {
        // Arrange
        var request = new ConfirmarLoginRequest(
                acao.getId(),
                "999999"
        );

        when(segredos.conferir(
                usuario.getId(),
                acao.getId(),
                FinalidadeAcao.LOGIN,
                "999999",
                "hash-do-codigo"
        )).thenReturn(false);

        // Act
        var exception = assertThrows(
                AutenticacaoException.class,
                () -> service.confirmar(request)
        );

        // Assert
        assertEquals(
                ErroAutenticacao.ACAO_INVALIDA,
                exception.getErro()
        );

        assertEquals(
                "Código ou autorização inválido, expirado ou indisponível.",
                exception.getMessage()
        );

        assertEquals(1, acao.getTentativas());
        assertNull(acao.getConsumidoEm());
        assertNull(acao.getInvalidadoEm());

        verify(sessoes, never()).save(any());
        verifyNoInteractions(tokens);
    }

    @Test
    void deveInvalidarAcaoNaQuintaTentativaIncorreta() {
        // Arrange
        acao.setTentativas(4);

        var request = new ConfirmarLoginRequest(
                acao.getId(),
                "999999"
        );

        when(segredos.conferir(
                usuario.getId(),
                acao.getId(),
                FinalidadeAcao.LOGIN,
                "999999",
                "hash-do-codigo"
        )).thenReturn(false);

        // Act
        var exception = assertThrows(
                AutenticacaoException.class,
                () -> service.confirmar(request)
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

        assertEquals(5, acao.getTentativas());
        assertNotNull(acao.getInvalidadoEm());
        assertNull(acao.getConsumidoEm());

        verify(sessoes, never()).save(any());
        verifyNoInteractions(tokens);
    }

    @Test
    void devePermitirCodigoCorretoAposQuatroTentativasIncorretas() {
        // Arrange
        acao.setTentativas(4);
        configurarCodigoCorreto();

        var request = new ConfirmarLoginRequest(
                acao.getId(),
                "012345"
        );

        // Act
        var resposta = service.confirmar(request);

        // Assert
        assertEquals(
                "token-da-sessao",
                resposta.accessToken()
        );

        assertNotNull(acao.getConsumidoEm());
        assertNull(acao.getInvalidadoEm());

        verify(sessoes).save(any(Sessao.class));
    }

    @Test
    void deveRejeitarAcaoExpiradaSemConferirCodigo() {
        // Arrange
        acao.setExpiraEm(
                Instant.now().minusSeconds(60)
        );

        var request = new ConfirmarLoginRequest(
                acao.getId(),
                "012345"
        );

        // Act
        var exception = assertThrows(
                AutenticacaoException.class,
                () -> service.confirmar(request)
        );

        // Assert
        assertEquals(
                ErroAutenticacao.ACAO_INVALIDA,
                exception.getErro()
        );

        assertEquals(
                "Código ou autorização inválido, expirado ou indisponível.",
                exception.getMessage()
        );

        assertNull(acao.getConsumidoEm());
        assertEquals(0, acao.getTentativas());

        verify(sessoes, never()).save(any());
        verifyNoInteractions(segredos, tokens);
    }

    @Test
    void deveRejeitarAcaoJaConsumidaSemCriarOutraSessao() {
        // Arrange
        Instant consumidoEm = Instant.now().minusSeconds(10);
        acao.setConsumidoEm(consumidoEm);

        var request = new ConfirmarLoginRequest(
                acao.getId(),
                "012345"
        );

        // Act
        var exception = assertThrows(
                AutenticacaoException.class,
                () -> service.confirmar(request)
        );

        // Assert
        assertEquals(
                ErroAutenticacao.ACAO_INVALIDA,
                exception.getErro()
        );

        assertEquals(
                "Código ou autorização inválido, expirado ou indisponível.",
                exception.getMessage()
        );

        assertEquals(consumidoEm, acao.getConsumidoEm());

        verify(sessoes, never()).save(any());
        verifyNoInteractions(segredos, tokens);
    }

    @Test
    void deveInvalidarAcaoQuandoExistemDezErrosRecentes() {
        // Arrange
        var request = new ConfirmarLoginRequest(
                acao.getId(),
                "012345"
        );

        when(acoes.somarTentativasDesde(
                eq(usuario.getId()),
                eq(FinalidadeAcao.LOGIN),
                any(Instant.class)
        )).thenReturn(10L);

        // Act
        var exception = assertThrows(
                AutenticacaoException.class,
                () -> service.confirmar(request)
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

        assertNotNull(acao.getInvalidadoEm());
        assertNull(acao.getConsumidoEm());

        verify(sessoes, never()).save(any());
        verifyNoInteractions(segredos, tokens);
    }
}