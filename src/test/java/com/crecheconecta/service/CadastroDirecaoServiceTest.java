package com.crecheconecta.service;

import com.crecheconecta.dto.ConcluirCadastroDirecaoRequest;
import com.crecheconecta.dto.ConvidarDirecaoRequest;
import com.crecheconecta.entity.ConviteCadastro;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.SituacaoCadastro;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.repository.ConviteCadastroRepository;
import com.crecheconecta.repository.UsuarioRepository;
import com.crecheconecta.security.UsuarioAtual;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CadastroDirecaoServiceTest {

    @Mock
    private UsuarioRepository usuarios;

    @Mock
    private ConviteCadastroRepository convites;

    @Mock
    private TokenConviteService tokens;

    @Mock
    private PasswordEncoder encoder;

    @Mock
    private EmailService emails;

    @Mock
    private AuditoriaService auditoria;

    @Mock
    private PlatformTransactionManager transactionManager;

    private CadastroDirecaoService service;
    private Usuario diretor;
    private UsuarioAtual solicitante;
    private ConviteCadastro convite;
    private Instant agora;

    private static final String TOKEN = "A".repeat(43);
    private static final String HASH = "b".repeat(64);
    private static final String SENHA = "SenhaSegura123!";
    private static final String URL =
            "http://localhost:3000/cadastro/direcao";

    @BeforeEach
    void preparar() {
        agora = Instant.parse("2026-11-02T12:00:00Z");

        lenient().when(
                transactionManager.getTransaction(
                        any(TransactionDefinition.class)
                )
        ).thenAnswer(invocation -> new SimpleTransactionStatus());

        service = criarService(URL);

        diretor = new Usuario();
        diretor.setId(UUID.randomUUID());
        diretor.setPerfil(Perfil.DIRECAO);
        diretor.setAtivo(true);
        diretor.setSituacaoCadastro(SituacaoCadastro.APROVADO);

        solicitante = new UsuarioAtual(
                diretor.getId(),
                Perfil.DIRECAO
        );

        convite = new ConviteCadastro();
        convite.setNome("Nova direção");
        convite.setEmail("direcao@example.com");
        convite.setPerfil(Perfil.DIRECAO);
        convite.setCriadoPor(diretor);
        convite.setTokenHash(HASH);
        convite.setCriadoEm(agora.minusSeconds(120));
        convite.setExpiraEm(agora.plusSeconds(3600));
    }

    @Test
    void deveCriarConviteNormalizadoEEnviarEmail() {
        prepararDiretorAutorizado();

        when(tokens.gerarToken()).thenReturn(TOKEN);
        when(tokens.calcularHash(TOKEN)).thenReturn(HASH);

        var request = new ConvidarDirecaoRequest(
                " Nova direção ",
                "DIRECAO@EXAMPLE.COM"
        );

        var resposta = service.convidar(request, solicitante);

        var captor = ArgumentCaptor.forClass(ConviteCadastro.class);
        verify(convites).saveAndFlush(captor.capture());

        var salvo = captor.getValue();

        assertEquals("Nova direção", salvo.getNome());
        assertEquals("direcao@example.com", salvo.getEmail());
        assertEquals(Perfil.DIRECAO, salvo.getPerfil());
        assertEquals(HASH, salvo.getTokenHash());
        assertEquals(agora.plusSeconds(86400), salvo.getExpiraEm());
        assertEquals(salvo.getId(), resposta.id());

        verify(emails).enviarConviteDirecao(
                salvo.getId(),
                salvo.getEmail(),
                URL + "#token=" + TOKEN
        );
    }

    @Test
    void deveNegarConviteSemAutenticacao() {
        assertThrows(
                AccessDeniedException.class,
                () -> service.convidar(requestConvite(), null)
        );

        verifyNoInteractions(usuarios, convites, emails);
    }

    @ParameterizedTest
    @EnumSource(
            value = Perfil.class,
            names = {"PROFESSOR", "RESPONSAVEL"}
    )
    void deveNegarConviteDeOutroPerfil(Perfil perfil) {
        var usuario = new UsuarioAtual(UUID.randomUUID(), perfil);

        assertThrows(
                AccessDeniedException.class,
                () -> service.convidar(requestConvite(), usuario)
        );

        verifyNoInteractions(usuarios, convites, emails);
    }

    @Test
    void deveNegarDiretorInativo() {
        diretor.setAtivo(false);
        prepararDiretorAutorizado();

        assertThrows(
                AccessDeniedException.class,
                () -> service.convidar(requestConvite(), solicitante)
        );

        verifyNoInteractions(convites, emails);
    }

    @Test
    void deveRejeitarEmailJaCadastrado() {
        prepararDiretorAutorizado();

        when(usuarios.existsByEmail(convite.getEmail()))
                .thenReturn(true);

        assertThrows(
                RegraNegocioException.class,
                () -> service.convidar(requestConvite(), solicitante)
        );

        verify(convites, never()).saveAndFlush(any());
        verifyNoInteractions(emails);
    }

    @Test
    void deveImpedirReenvioAntesDeSessentaSegundos() {
        prepararDiretorAutorizado();

        convite.setCriadoEm(agora.minusSeconds(30));

        when(convites.buscarPendentePorEmailComBloqueio(
                convite.getEmail()
        )).thenReturn(Optional.of(convite));

        assertThrows(
                RegraNegocioException.class,
                () -> service.convidar(requestConvite(), solicitante)
        );

        assertNull(convite.getCanceladoEm());
        verify(convites, never()).saveAndFlush(any());
        verifyNoInteractions(emails);
    }

    @Test
    void deveSubstituirConviteDepoisDoIntervalo() {
        prepararDiretorAutorizado();

        when(convites.buscarPendentePorEmailComBloqueio(
                convite.getEmail()
        )).thenReturn(Optional.of(convite));

        when(tokens.gerarToken()).thenReturn(TOKEN);
        when(tokens.calcularHash(TOKEN)).thenReturn(HASH);

        service.convidar(requestConvite(), solicitante);

        assertEquals(agora, convite.getCanceladoEm());
        verify(convites).saveAndFlush(any(ConviteCadastro.class));
    }

    @Test
    void naoDeveCancelarConviteDeOutroPerfil() {
        prepararDiretorAutorizado();
        convite.setPerfil(Perfil.PROFESSOR);

        when(convites.buscarPendentePorEmailComBloqueio(
                convite.getEmail()
        )).thenReturn(Optional.of(convite));

        assertThrows(
                RegraNegocioException.class,
                () -> service.convidar(requestConvite(), solicitante)
        );

        assertNull(convite.getCanceladoEm());
        verifyNoInteractions(emails);
    }

    @Test
    void deveCancelarConviteSeEnvioFalhar() {
        prepararDiretorAutorizado();

        when(tokens.gerarToken()).thenReturn(TOKEN);
        when(tokens.calcularHash(TOKEN)).thenReturn(HASH);

        when(convites.buscarPorIdComBloqueio(any(UUID.class)))
                .thenReturn(Optional.of(convite));

        var falha = new IllegalStateException("Falha simulada");

        when(emails.enviarConviteDirecao(
                any(UUID.class),
                anyString(),
                anyString()
        )).thenThrow(falha);

        var recebida = assertThrows(
                IllegalStateException.class,
                () -> service.convidar(requestConvite(), solicitante)
        );

        assertSame(falha, recebida);
        assertEquals(agora, convite.getCanceladoEm());
    }

    @Test
    void deveConsultarConviteValido() {
        prepararBuscaConvite();

        var resposta = service.consultar(TOKEN);

        assertEquals(convite.getNome(), resposta.nome());
        assertEquals(convite.getEmail(), resposta.email());
        assertEquals(convite.getExpiraEm(), resposta.expiraEm());

        verifyNoInteractions(encoder);
        verify(usuarios, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"abc", "token-invalido!"})
    void deveRejeitarTokenMalformado(String token) {
        assertThrows(
                RegraNegocioException.class,
                () -> service.consultar(token)
        );

        verifyNoInteractions(tokens, convites);
    }

    @Test
    void deveRejeitarTokenNaoEncontrado() {
        when(tokens.calcularHash(TOKEN)).thenReturn(HASH);
        when(convites.buscarPorTokenHashComBloqueio(HASH))
                .thenReturn(Optional.empty());

        assertThrows(
                RegraNegocioException.class,
                () -> service.consultar(TOKEN)
        );
    }

    @Test
    void deveRejeitarConviteExpirado() {
        convite.setExpiraEm(agora);
        prepararBuscaConvite();

        assertThrows(
                RegraNegocioException.class,
                () -> service.concluir(requestCadastro(SENHA))
        );

        verify(usuarios, never()).saveAndFlush(any());
    }

    @Test
    void deveRejeitarConviteCancelado() {
        convite.setCanceladoEm(agora.minusSeconds(1));
        prepararBuscaConvite();

        assertThrows(
                RegraNegocioException.class,
                () -> service.concluir(requestCadastro(SENHA))
        );

        verifyNoInteractions(encoder);
    }

    @Test
    void deveRejeitarConviteConsumido() {
        convite.setConsumidoEm(agora.minusSeconds(1));
        prepararBuscaConvite();

        assertThrows(
                RegraNegocioException.class,
                () -> service.concluir(requestCadastro(SENHA))
        );

        verifyNoInteractions(encoder);
    }

    @Test
    void deveRejeitarConviteDeProfessor() {
        convite.setPerfil(Perfil.PROFESSOR);
        prepararBuscaConvite();

        assertThrows(
                RegraNegocioException.class,
                () -> service.concluir(requestCadastro(SENHA))
        );

        verifyNoInteractions(encoder);
    }

    @Test
    void deveCadastrarDirecaoComSenhaCodificada() {
        prepararBuscaConvite();

        when(encoder.encode(SENHA)).thenReturn("hash-da-senha");

        doAnswer(invocation -> {
            Usuario usuario = invocation.getArgument(0);
            usuario.setId(UUID.randomUUID());
            return usuario;
        }).when(usuarios).saveAndFlush(any(Usuario.class));

        var resposta = service.concluir(requestCadastro(SENHA));

        var captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).saveAndFlush(captor.capture());

        var salvo = captor.getValue();

        assertEquals(convite.getNome(), salvo.getNome());
        assertEquals(convite.getEmail(), salvo.getEmail());
        assertEquals("11999999999", salvo.getTelefone());
        assertEquals("hash-da-senha", salvo.getSenhaHash());
        assertEquals(Perfil.DIRECAO, salvo.getPerfil());
        assertEquals(
                SituacaoCadastro.APROVADO,
                salvo.getSituacaoCadastro()
        );
        assertTrue(salvo.isAtivo());
        assertEquals(agora, convite.getConsumidoEm());
        assertEquals(salvo.getId(), resposta.usuarioId());

        verify(auditoria).aposCommit(
                "DIRECAO_CADASTRO_CONCLUIDO",
                salvo.getId(),
                salvo.getId()
        );
    }

    @Test
    void deveRejeitarCadastroSeEmailJaExiste() {
        prepararBuscaConvite();

        when(usuarios.existsByEmail(convite.getEmail())).thenReturn(true);

        assertThrows(RegraNegocioException.class, () -> service.concluir(requestCadastro(SENHA)));

        assertNull(convite.getConsumidoEm());
        verify(usuarios, never()).saveAndFlush(any());
    }

    @Test
    void deveRejeitarSenhaCurta() {
        prepararBuscaConvite();

        assertThrows(RegraNegocioException.class, () -> service.concluir(requestCadastro("curta")));

        verifyNoInteractions(encoder);
        verify(usuarios, never()).saveAndFlush(any());
    }

    @Test
    void deveRejeitarSenhaAcimaDeSetentaEDoisBytes() {
        prepararBuscaConvite();

        String senha = "á".repeat(37);

        assertThrows(
                RegraNegocioException.class,
                () -> service.concluir(requestCadastro(senha))
        );

        verifyNoInteractions(encoder);
    }

    @Test
    void deveRejeitarTelefoneInvalido() {
        prepararBuscaConvite();

        var request = new ConcluirCadastroDirecaoRequest(TOKEN, "123", SENHA);

        assertThrows(RegraNegocioException.class, () -> service.concluir(request));

        verifyNoInteractions(encoder);
    }

    @Test
    void naoDeveConsumirConviteSePersistenciaFalhar() {
        prepararBuscaConvite();

        when(encoder.encode(SENHA)).thenReturn("hash-da-senha");

        when(usuarios.saveAndFlush(any(Usuario.class))).thenThrow(new IllegalStateException("Falha simulada"));

        assertThrows(IllegalStateException.class,
                () -> service.concluir(requestCadastro(SENHA)));

        assertNull(convite.getConsumidoEm());
        verifyNoInteractions(auditoria);
    }

    @Test
    void deveRejeitarUrlHttpExterna() {
        assertThrows(IllegalArgumentException.class,
                () -> criarService("http://example.com/cadastro")
        );
    }

    private CadastroDirecaoService criarService(String url) {

        return new CadastroDirecaoService(usuarios, convites, tokens, encoder, emails, auditoria, Clock.fixed(agora, ZoneOffset.UTC), transactionManager, url);
    }

    private void prepararDiretorAutorizado() {

        when(usuarios.buscarPorIdComBloqueio(diretor.getId())).thenReturn(Optional.of(diretor));
    }

    private void prepararBuscaConvite() {

        when(tokens.calcularHash(TOKEN)).thenReturn(HASH);

        when(convites.buscarPorTokenHashComBloqueio(HASH)).thenReturn(Optional.of(convite));
    }

    private ConvidarDirecaoRequest requestConvite() {

        return new ConvidarDirecaoRequest("Nova direção", "direcao@example.com");
    }

    private ConcluirCadastroDirecaoRequest requestCadastro(String senha) {

        return new ConcluirCadastroDirecaoRequest(TOKEN, "11999999999", senha);
    }
}