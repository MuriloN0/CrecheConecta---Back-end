package com.crecheconecta.service;

import com.crecheconecta.dto.ConfirmarRecuperacaoRequest;
import com.crecheconecta.dto.ConfirmarRecuperacaoResponse;
import com.crecheconecta.entity.AcaoVerificacao;
import com.crecheconecta.entity.FinalidadeAcao;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.exception.AutenticacaoException;
import com.crecheconecta.exception.ErroAutenticacao;
import com.crecheconecta.repository.AcaoVerificacaoRepository;
import com.crecheconecta.repository.UsuarioRepository;
import com.crecheconecta.security.SegredoVerificacaoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.crecheconecta.service.DadosAutenticacaoTest.*;
import static com.crecheconecta.service.GerenciadorTransacoesTest.transacoes;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConfirmacaoRecuperacaoServiceTest {

    private final UsuarioRepository usuarios =
            mock(UsuarioRepository.class);

    private final AcaoVerificacaoRepository acoes =
            mock(AcaoVerificacaoRepository.class);

    private final SegredoVerificacaoService segredos =
            mock(SegredoVerificacaoService.class);

    private final ConfirmacaoRecuperacaoService service =
            new ConfirmacaoRecuperacaoService(
                    usuarios,
                    acoes,
                    segredos,
                    transacoes()
            );

    private final Usuario pessoa = usuario(Perfil.RESPONSAVEL);

    private final AcaoVerificacao recuperacao =
            acao(pessoa, FinalidadeAcao.RECUPERACAO_SENHA);

    @Test
    void deveConsumirCodigoECriarAutorizacaoComHash() {
        prepararBusca();
        codigoCorreto();

        when(segredos.gerarToken()).thenReturn(TOKEN);

        when(segredos.proteger(
                eq(pessoa.getId()),
                any(UUID.class),
                eq(FinalidadeAcao.REDEFINICAO_SENHA),
                eq(TOKEN)
        )).thenReturn("hash-autorizacao");

        var resposta = confirmar();

        var captor = ArgumentCaptor.forClass(AcaoVerificacao.class);
        verify(acoes).save(captor.capture());

        assertNotNull(recuperacao.getConsumidoEm());
        assertEquals(TOKEN, resposta.tokenRedefinicao());

        assertEquals(
                "hash-autorizacao",
                captor.getValue().getSegredoHash()
        );

        assertEquals(
                FinalidadeAcao.REDEFINICAO_SENHA,
                captor.getValue().getFinalidade()
        );
    }

    @Test
    void deveInvalidarAutorizacoesAnteriores() {
        prepararBusca();
        codigoCorreto();

        var anterior = acao(
                pessoa,
                FinalidadeAcao.REDEFINICAO_SENHA
        );

        when(acoes.buscarPendentes(
                pessoa.getId(),
                FinalidadeAcao.REDEFINICAO_SENHA
        )).thenReturn(List.of(anterior));

        when(segredos.gerarToken()).thenReturn(TOKEN);

        confirmar();

        assertNotNull(anterior.getInvalidadoEm());
    }

    @Test
    void deveContabilizarCodigoIncorretoSemConsumirAcao() {
        prepararBusca();

        assertThrows(
                AutenticacaoException.class,
                this::confirmar
        );

        assertEquals(1, recuperacao.getTentativas());
        assertNull(recuperacao.getConsumidoEm());

        verify(acoes, never()).save(any());
    }

    @Test
    void deveInvalidarNaQuintaTentativaIncorreta() {
        prepararBusca();
        recuperacao.setTentativas(4);

        var erro = assertThrows(
                AutenticacaoException.class,
                this::confirmar
        );

        assertEquals(
                ErroAutenticacao.LIMITE_TENTATIVAS,
                erro.getErro()
        );

        assertNotNull(recuperacao.getInvalidadoEm());
    }

    @Test
    void deveAplicarLimiteAcumuladoDeTentativas() {
        prepararBusca();

        when(acoes.somarTentativasDesde(
                eq(pessoa.getId()),
                eq(FinalidadeAcao.RECUPERACAO_SENHA),
                any(Instant.class)
        )).thenReturn(10L);

        var erro = assertThrows(
                AutenticacaoException.class,
                this::confirmar
        );

        assertEquals(
                ErroAutenticacao.LIMITE_TENTATIVAS,
                erro.getErro()
        );

        verifyNoInteractions(segredos);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "expirada",
            "consumida",
            "invalidada",
            "outra-finalidade"
    })
    void deveRejeitarAcaoIndisponivel(String motivo) {
        prepararBusca();

        switch (motivo) {
            case "expirada" ->
                    recuperacao.setExpiraEm(
                            Instant.now().minusSeconds(3600)
                    );
            case "consumida" ->
                    recuperacao.setConsumidoEm(Instant.now());
            case "invalidada" ->
                    recuperacao.setInvalidadoEm(Instant.now());
            case "outra-finalidade" ->
                    recuperacao.setFinalidade(FinalidadeAcao.LOGIN);
            default ->
                    throw new IllegalArgumentException(motivo);
        }

        assertThrows(
                AutenticacaoException.class,
                this::confirmar
        );

        verifyNoInteractions(segredos);
        verify(acoes, never()).save(any());
    }

    @Test
    void deveNegarUsuarioInativoEInvalidarAcao() {
        prepararBusca();
        pessoa.setAtivo(false);

        assertThrows(
                AutenticacaoException.class,
                this::confirmar
        );

        assertNotNull(recuperacao.getInvalidadoEm());
    }

    @Test
    void deveRejeitarAcaoNaoEncontrada() {
        var erro = assertThrows(
                AutenticacaoException.class,
                this::confirmar
        );

        assertEquals(
                ErroAutenticacao.ACAO_INVALIDA,
                erro.getErro()
        );

        verifyNoInteractions(usuarios, segredos);
    }

    private void prepararBusca() {
        when(acoes.buscarUsuarioId(recuperacao.getId()))
                .thenReturn(Optional.of(pessoa.getId()));

        when(usuarios.buscarPorIdComBloqueio(pessoa.getId()))
                .thenReturn(Optional.of(pessoa));

        when(acoes.buscarPorIdComBloqueio(recuperacao.getId()))
                .thenReturn(Optional.of(recuperacao));
    }

    private void codigoCorreto() {
        when(segredos.conferir(
                pessoa.getId(),
                recuperacao.getId(),
                FinalidadeAcao.RECUPERACAO_SENHA,
                "123456",
                HASH
        )).thenReturn(true);
    }

    private ConfirmarRecuperacaoResponse confirmar() {
        return service.confirmar(
                new ConfirmarRecuperacaoRequest(
                        recuperacao.getId(),
                        "123456"
                )
        );
    }
}