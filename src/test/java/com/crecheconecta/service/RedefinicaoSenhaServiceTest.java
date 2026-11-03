package com.crecheconecta.service;

import com.crecheconecta.dto.RedefinirSenhaRequest;
import com.crecheconecta.entity.AcaoVerificacao;
import com.crecheconecta.entity.FinalidadeAcao;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.exception.AutenticacaoException;
import com.crecheconecta.exception.ErroAutenticacao;
import com.crecheconecta.repository.AcaoVerificacaoRepository;
import com.crecheconecta.repository.SessaoRepository;
import com.crecheconecta.repository.UsuarioRepository;
import com.crecheconecta.security.SegredoVerificacaoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.crecheconecta.service.DadosAutenticacaoTest.*;
import static com.crecheconecta.service.GerenciadorTransacoesTest.transacoes;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RedefinicaoSenhaServiceTest {

    private final UsuarioRepository usuarios =
            mock(UsuarioRepository.class);

    private final AcaoVerificacaoRepository acoes =
            mock(AcaoVerificacaoRepository.class);

    private final SessaoRepository sessoes =
            mock(SessaoRepository.class);

    private final SegredoVerificacaoService segredos =
            mock(SegredoVerificacaoService.class);

    private final PasswordEncoder encoder =
            mock(PasswordEncoder.class);

    private final EmailService emails = mock(EmailService.class);

    private final RedefinicaoSenhaService service =
            new RedefinicaoSenhaService(
                    usuarios,
                    acoes,
                    sessoes,
                    segredos,
                    encoder,
                    emails,
                    transacoes()
            );

    private final Usuario pessoa = usuario(Perfil.RESPONSAVEL);

    private final AcaoVerificacao autorizacao =
            acao(pessoa, FinalidadeAcao.REDEFINICAO_SENHA);

    @Test
    void deveTrocarSenhaConsumirAutorizacaoERevogarSessoes() {
        prepararBusca();
        tokenCorreto();

        pessoa.setTentativas(3);
        pessoa.setBloqueadoAte(Instant.now().plusSeconds(600));

        when(encoder.encode(SENHA)).thenReturn("novo-hash");

        var pendente = acao(pessoa, FinalidadeAcao.LOGIN);

        when(acoes.buscarPendentes(
                pessoa.getId(),
                FinalidadeAcao.LOGIN
        )).thenReturn(List.of(pendente));

        redefinir(SENHA, SENHA);

        assertEquals("novo-hash", pessoa.getSenhaHash());
        assertEquals(0, pessoa.getTentativas());
        assertNull(pessoa.getBloqueadoAte());
        assertNotNull(autorizacao.getConsumidoEm());
        assertNotNull(pendente.getInvalidadoEm());

        verify(sessoes).revogarTodasDoUsuario(
                eq(pessoa.getId()),
                any(Instant.class)
        );

        verify(emails).enviarAvisoSenhaAlterada(
                autorizacao.getId(),
                EMAIL
        );
    }

    @Test
    void deveRejeitarTokenIncorretoSemTrocarSenha() {
        prepararBusca();

        assertThrows(
                AutenticacaoException.class,
                () -> redefinir(SENHA, SENHA)
        );

        assertEquals("hash-antigo", pessoa.getSenhaHash());
        assertEquals(1, autorizacao.getTentativas());

        verifyNoInteractions(encoder, sessoes, emails);
    }

    @Test
    void deveInvalidarAutorizacaoNaQuintaTentativaIncorreta() {
        prepararBusca();
        autorizacao.setTentativas(4);

        var erro = assertThrows(
                AutenticacaoException.class,
                () -> redefinir(SENHA, SENHA)
        );

        assertEquals(
                ErroAutenticacao.LIMITE_TENTATIVAS,
                erro.getErro()
        );

        assertNotNull(autorizacao.getInvalidadoEm());
    }

    @ParameterizedTest
    @ValueSource(strings = {"curta", "            "})
    void deveRejeitarSenhaInvalida(String senha) {
        prepararBusca();
        tokenCorreto();

        var erro = assertThrows(
                AutenticacaoException.class,
                () -> redefinir(senha, senha)
        );

        assertEquals(
                ErroAutenticacao.SENHA_INVALIDA,
                erro.getErro()
        );

        verifyNoInteractions(encoder, sessoes, emails);
    }

    @Test
    void deveRejeitarSenhaAcimaDoLimiteEmBytes() {
        prepararBusca();
        tokenCorreto();

        String senha = "á".repeat(37);

        assertThrows(
                AutenticacaoException.class,
                () -> redefinir(senha, senha)
        );

        verifyNoInteractions(encoder, sessoes, emails);
    }

    @Test
    void deveExigirConfirmacaoIgual() {
        prepararBusca();
        tokenCorreto();

        var erro = assertThrows(
                AutenticacaoException.class,
                () -> redefinir(SENHA, "OutraSenha123!")
        );

        assertEquals(
                ErroAutenticacao.SENHAS_DIFERENTES,
                erro.getErro()
        );

        verifyNoInteractions(encoder, sessoes, emails);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "expirada",
            "consumida",
            "invalidada",
            "outra-finalidade"
    })
    void deveRejeitarAutorizacaoIndisponivel(String motivo) {
        prepararBusca();

        switch (motivo) {
            case "expirada" ->
                    autorizacao.setExpiraEm(
                            Instant.now().minusSeconds(3600)
                    );
            case "consumida" ->
                    autorizacao.setConsumidoEm(Instant.now());
            case "invalidada" ->
                    autorizacao.setInvalidadoEm(Instant.now());
            case "outra-finalidade" ->
                    autorizacao.setFinalidade(FinalidadeAcao.LOGIN);
            default ->
                    throw new IllegalArgumentException(motivo);
        }

        assertThrows(
                AutenticacaoException.class,
                () -> redefinir(SENHA, SENHA)
        );

        verifyNoInteractions(encoder, sessoes, emails);
    }

    @Test
    void deveNegarUsuarioInativo() {
        prepararBusca();
        pessoa.setAtivo(false);

        assertThrows(
                AutenticacaoException.class,
                () -> redefinir(SENHA, SENHA)
        );

        assertNotNull(autorizacao.getInvalidadoEm());

        verifyNoInteractions(encoder, sessoes, emails);
    }

    @Test
    void falhaDoAvisoPorEmailNaoDeveDesfazerTrocaConcluida() {
        prepararBusca();
        tokenCorreto();

        when(encoder.encode(SENHA)).thenReturn("novo-hash");

        when(emails.enviarAvisoSenhaAlterada(
                autorizacao.getId(),
                EMAIL
        )).thenThrow(new IllegalStateException("Falha simulada"));

        assertDoesNotThrow(() -> redefinir(SENHA, SENHA));

        assertEquals("novo-hash", pessoa.getSenhaHash());
        assertNotNull(autorizacao.getConsumidoEm());

        verify(sessoes).revogarTodasDoUsuario(
                eq(pessoa.getId()),
                any(Instant.class)
        );
    }

    private void prepararBusca() {
        when(acoes.buscarUsuarioId(autorizacao.getId()))
                .thenReturn(Optional.of(pessoa.getId()));

        when(usuarios.buscarPorIdComBloqueio(pessoa.getId()))
                .thenReturn(Optional.of(pessoa));

        when(acoes.buscarPorIdComBloqueio(autorizacao.getId()))
                .thenReturn(Optional.of(autorizacao));
    }

    private void tokenCorreto() {
        when(segredos.conferir(
                pessoa.getId(),
                autorizacao.getId(),
                FinalidadeAcao.REDEFINICAO_SENHA,
                TOKEN,
                HASH
        )).thenReturn(true);
    }

    private void redefinir(String senha, String confirmacao) {
        service.redefinir(
                new RedefinirSenhaRequest(
                        autorizacao.getId(),
                        TOKEN,
                        senha,
                        confirmacao
                )
        );
    }
}