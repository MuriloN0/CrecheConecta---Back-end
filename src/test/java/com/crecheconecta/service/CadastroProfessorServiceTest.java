package com.crecheconecta.service;

import com.crecheconecta.dto.ConcluirCadastroProfessorRequest;
import com.crecheconecta.entity.ConviteCadastro;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.SituacaoCadastro;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.repository.ConviteCadastroRepository;
import com.crecheconecta.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static com.crecheconecta.service.DadosAutenticacaoTest.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CadastroProfessorServiceTest {

    private final ConviteCadastroRepository convites =
            mock(ConviteCadastroRepository.class);

    private final UsuarioRepository usuarios =
            mock(UsuarioRepository.class);

    private final TokenConviteService tokens =
            mock(TokenConviteService.class);

    private final PasswordEncoder encoder =
            mock(PasswordEncoder.class);

    private final AuditoriaService auditoria =
            mock(AuditoriaService.class);

    private final CadastroProfessorService service =
            new CadastroProfessorService(
                    convites,
                    usuarios,
                    tokens,
                    encoder,
                    auditoria
            );

    private final ConviteCadastro convite =
            convite(Perfil.PROFESSOR);

    @Test
    void deveConsultarConviteValido() {
        prepararBusca();

        var resposta = service.consultar(TOKEN);

        assertEquals(EMAIL, resposta.email());
        assertEquals(convite.getNome(), resposta.nome());
    }

    @Test
    void deveCadastrarProfessorInativoEPendenteComSenhaCodificada() {
        prepararBusca();

        when(encoder.encode(SENHA)).thenReturn("senha-codificada");

        var idCriado = UUID.randomUUID();

        doAnswer(invocation -> {
            Usuario salvo = invocation.getArgument(0);
            salvo.setId(idCriado);
            return salvo;
        }).when(usuarios).saveAndFlush(any(Usuario.class));

        var resposta = service.concluir(request(SENHA));

        var captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).saveAndFlush(captor.capture());

        var salvo = captor.getValue();

        assertEquals(idCriado, resposta.usuarioId());
        assertEquals("Professor novo", salvo.getNome());
        assertEquals(EMAIL, salvo.getEmail());
        assertEquals(Perfil.PROFESSOR, salvo.getPerfil());
        assertEquals("senha-codificada", salvo.getSenhaHash());

        assertEquals(
                SituacaoCadastro.PENDENTE_APROVACAO,
                salvo.getSituacaoCadastro()
        );

        assertFalse(salvo.isAtivo());
        assertNotNull(convite.getConsumidoEm());

        verify(convites).flush();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "expirado",
            "cancelado",
            "consumido",
            "outro-perfil"
    })
    void deveRejeitarConviteIndisponivel(String motivo) {
        prepararBusca();

        switch (motivo) {
            case "expirado" ->
                    convite.setExpiraEm(
                            Instant.now().minusSeconds(3600)
                    );
            case "cancelado" ->
                    convite.setCanceladoEm(Instant.now());
            case "consumido" ->
                    convite.setConsumidoEm(Instant.now());
            case "outro-perfil" ->
                    convite.setPerfil(Perfil.DIRECAO);
            default ->
                    throw new IllegalArgumentException(motivo);
        }

        assertThrows(
                RegraNegocioException.class,
                () -> service.concluir(request(SENHA))
        );

        verifyNoInteractions(encoder);
        verify(usuarios, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"abc", "token-invalido"})
    void deveRejeitarTokenMalformado(String token) {
        assertThrows(
                RegraNegocioException.class,
                () -> service.consultar(token)
        );

        verifyNoInteractions(tokens, convites);
    }

    @Test
    void deveRejeitarEmailJaCadastradoSemConsumirConvite() {
        prepararBusca();

        when(usuarios.existsByEmail(EMAIL)).thenReturn(true);

        assertThrows(
                RegraNegocioException.class,
                () -> service.concluir(request(SENHA))
        );

        assertNull(convite.getConsumidoEm());
        verify(usuarios, never()).saveAndFlush(any());
    }

    @Test
    void deveRejeitarSenhaAcimaDoLimiteEmBytes() {
        prepararBusca();

        assertThrows(
                RegraNegocioException.class,
                () -> service.concluir(request("á".repeat(37)))
        );

        verifyNoInteractions(encoder);
    }

    @Test
    void naoDeveConsumirConviteSePersistenciaFalhar() {
        prepararBusca();

        when(encoder.encode(SENHA)).thenReturn("senha-codificada");

        when(usuarios.saveAndFlush(any(Usuario.class)))
                .thenThrow(
                        new IllegalStateException("Falha simulada")
                );

        assertThrows(
                IllegalStateException.class,
                () -> service.concluir(request(SENHA))
        );

        assertNull(convite.getConsumidoEm());
        verifyNoInteractions(auditoria);
    }

    private void prepararBusca() {
        when(tokens.calcularHash(TOKEN)).thenReturn(HASH);

        when(convites.buscarPorTokenHashComBloqueio(HASH))
                .thenReturn(Optional.of(convite));
    }

    private ConcluirCadastroProfessorRequest request(String senha) {
        return new ConcluirCadastroProfessorRequest(
                TOKEN,
                " Professor novo ",
                "11999999999",
                senha
        );
    }
}