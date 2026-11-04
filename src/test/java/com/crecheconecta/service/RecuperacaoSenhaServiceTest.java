package com.crecheconecta.service;

import com.crecheconecta.dto.SolicitarRecuperacaoRequest;
import com.crecheconecta.dto.SolicitarRecuperacaoResponse;
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
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static com.crecheconecta.service.DadosAutenticacaoTest.*;
import static com.crecheconecta.service.GerenciadorTransacoesTest.transacoes;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RecuperacaoSenhaServiceTest {

    private final UsuarioRepository usuarios =
            mock(UsuarioRepository.class);

    private final AcaoVerificacaoRepository acoes =
            mock(AcaoVerificacaoRepository.class);

    private final SegredoVerificacaoService segredos =
            mock(SegredoVerificacaoService.class);

    private final EmailService emails = mock(EmailService.class);

    private final Usuario pessoa = usuario(Perfil.RESPONSAVEL);

    @Test
    void deveResponderGenericamenteParaEmailInexistente() {
        var resposta = solicitar();

        assertNotNull(resposta);

        verify(usuarios).buscarPorEmailComBloqueio(EMAIL);
        verifyNoInteractions(emails, segredos);
        verify(acoes, never()).save(any());
    }

    @Test
    void naoDeveEnviarParaUsuarioInativo() {
        prepararUsuario();
        pessoa.setAtivo(false);

        assertNotNull(solicitar());

        verifyNoInteractions(emails, segredos);
    }

    @Test
    void deveCriarAcaoProtegidaEEnviarCodigo() {
        prepararUsuario();

        when(segredos.gerarCodigo()).thenReturn("123456");

        when(segredos.proteger(
                eq(pessoa.getId()),
                any(UUID.class),
                eq(FinalidadeAcao.RECUPERACAO_SENHA),
                eq("123456")
        )).thenReturn(HASH);

        solicitar();

        var captor = ArgumentCaptor.forClass(AcaoVerificacao.class);
        verify(acoes).save(captor.capture());

        var salva = captor.getValue();

        assertEquals(HASH, salva.getSegredoHash());

        assertEquals(
                FinalidadeAcao.RECUPERACAO_SENHA,
                salva.getFinalidade()
        );

        verify(emails).enviarCodigo(
                salva.getId(),
                EMAIL,
                "123456",
                FinalidadeAcao.RECUPERACAO_SENHA
        );
    }

    @Test
    void deveRespeitarIntervaloEntreSolicitacoes() {
        prepararUsuario();

        var recente = acao(
                pessoa,
                FinalidadeAcao.RECUPERACAO_SENHA
        );

        recente.setCriadoEm(Instant.now());

        when(acoes.findFirstByUsuario_IdAndFinalidadeOrderByCriadoEmDesc(
                pessoa.getId(),
                FinalidadeAcao.RECUPERACAO_SENHA
        )).thenReturn(Optional.of(recente));

        solicitar();

        verifyNoInteractions(emails, segredos);
        verify(acoes, never()).save(any());
    }

    @Test
    void deveRespeitarLimiteDeCincoSolicitacoesPorHora() {
        prepararUsuario();

        when(acoes.countByUsuario_IdAndFinalidadeAndCriadoEmGreaterThanEqual(
                eq(pessoa.getId()),
                eq(FinalidadeAcao.RECUPERACAO_SENHA),
                any(Instant.class)
        )).thenReturn(5L);

        solicitar();

        verifyNoInteractions(emails, segredos);
        verify(acoes, never()).save(any());
    }

    @Test
    void deveInvalidarAcaoQuandoEnvioFalhar() {
        prepararUsuario();

        when(segredos.gerarCodigo()).thenReturn("123456");

        var salva = new AcaoVerificacao[1];

        doAnswer(invocation -> {
            salva[0] = invocation.getArgument(0);
            return salva[0];
        }).when(acoes).save(any(AcaoVerificacao.class));

        when(emails.enviarCodigo(
                any(UUID.class),
                eq(EMAIL),
                eq("123456"),
                eq(FinalidadeAcao.RECUPERACAO_SENHA)
        )).thenThrow(new IllegalStateException("Falha simulada"));

        when(usuarios.buscarPorIdComBloqueio(pessoa.getId()))
                .thenReturn(Optional.of(pessoa));

        when(acoes.buscarPorIdComBloqueio(any(UUID.class)))
                .thenAnswer(invocation -> Optional.of(salva[0]));

        assertNotNull(solicitar());
        assertNotNull(salva[0].getInvalidadoEm());
    }

    @Test
    void deveInformarIndisponibilidadeSeFilaRejeitarTarefa() {
        var service = criarService(tarefa -> {
            throw new TaskRejectedException("Fila cheia");
        });

        var erro = assertThrows(
                AutenticacaoException.class,
                () -> service.solicitar(
                        new SolicitarRecuperacaoRequest(EMAIL)
                )
        );

        assertEquals(
                ErroAutenticacao.ENVIO_EMAIL_INDISPONIVEL,
                erro.getErro()
        );

        verifyNoInteractions(usuarios, emails);
    }

    private RecuperacaoSenhaService criarService(
            TaskExecutor executor
    ) {
        return new RecuperacaoSenhaService(
                usuarios,
                acoes,
                segredos,
                emails,
                executor,
                transacoes()
        );
    }

    private void prepararUsuario() {
        when(usuarios.buscarPorEmailComBloqueio(EMAIL))
                .thenReturn(Optional.of(pessoa));
    }

    private SolicitarRecuperacaoResponse solicitar() {
        return criarService(Runnable::run).solicitar(
                new SolicitarRecuperacaoRequest(
                        EMAIL.toUpperCase(Locale.ROOT)
                )
        );
    }
}