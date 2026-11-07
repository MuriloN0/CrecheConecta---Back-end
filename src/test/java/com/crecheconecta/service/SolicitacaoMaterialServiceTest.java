package com.crecheconecta.service;

import com.crecheconecta.dto.AtenderSolicitacaoMaterialRequest;
import com.crecheconecta.dto.AtualizarSolicitacaoMaterialRequest;
import com.crecheconecta.dto.CancelarSolicitacaoMaterialRequest;
import com.crecheconecta.dto.CriarSolicitacaoMaterialRequest;
import com.crecheconecta.dto.EnviarMensagemSolicitacaoMaterialRequest;
import com.crecheconecta.dto.ItemSolicitacaoMaterialRequest;
import com.crecheconecta.dto.RecusarSolicitacaoMaterialRequest;
import com.crecheconecta.dto.SolicitacaoMaterialDetalheResponse;
import com.crecheconecta.dto.SolicitacaoMaterialResponse;
import com.crecheconecta.entity.ItemSolicitacaoMaterial;
import com.crecheconecta.entity.MensagemSolicitacaoMaterial;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.PrioridadeSolicitacaoMaterial;
import com.crecheconecta.entity.SituacaoCadastro;
import com.crecheconecta.entity.SolicitacaoMaterial;
import com.crecheconecta.entity.StatusSolicitacaoMaterial;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.exception.ConflitoVersaoException;
import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.repository.ItemSolicitacaoMaterialRepository;
import com.crecheconecta.repository.MensagemSolicitacaoMaterialRepository;
import com.crecheconecta.repository.SolicitacaoMaterialRepository;
import com.crecheconecta.repository.UsuarioRepository;
import com.crecheconecta.security.UsuarioAtual;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SolicitacaoMaterialServiceTest {

    @Mock private SolicitacaoMaterialRepository solicitacoes;
    @Mock private ItemSolicitacaoMaterialRepository itens;
    @Mock private MensagemSolicitacaoMaterialRepository mensagens;
    @Mock private UsuarioRepository usuarios;
    @Mock private AuditoriaService auditoria;

    private SolicitacaoMaterialService service;
    private Instant agora;
    private Usuario professor;
    private Usuario diretor;
    private UsuarioAtual comoProfessor;
    private UsuarioAtual comoDirecao;

    @BeforeEach
    void preparar() {
        agora = Instant.parse("2026-11-07T15:00:00Z");

        service = new SolicitacaoMaterialService(
                solicitacoes,
                itens,
                mensagens,
                usuarios,
                auditoria,
                Clock.fixed(agora, ZoneOffset.UTC)
        );

        professor = usuario("Ana Professora", Perfil.PROFESSOR);
        diretor = usuario("Carlos Diretor", Perfil.DIRECAO);

        comoProfessor = new UsuarioAtual(professor.getId(), Perfil.PROFESSOR);
        comoDirecao = new UsuarioAtual(diretor.getId(), Perfil.DIRECAO);
    }

    @Test
    void professorAbreSolicitacaoComItens() {
        autorizar(professor);
        when(solicitacoes.saveAndFlush(any())).thenAnswer(invocacao -> {
            SolicitacaoMaterial solicitacao = invocacao.getArgument(0);
            ReflectionTestUtils.setField(solicitacao, "id", UUID.randomUUID());
            return solicitacao;
        });

        CriarSolicitacaoMaterialRequest request = criarRequest(item("  Canetão azul ", 5), item("Apagador", 2));

        SolicitacaoMaterialResponse resposta = service.criar(request, comoProfessor);

        assertEquals(StatusSolicitacaoMaterial.ABERTA, resposta.status());
        assertEquals("Materiais da sala 3", resposta.titulo());
        assertEquals("Sala 3", resposta.sala());
        assertEquals("Ana Professora", resposta.professorNome());
        assertEquals(List.of("Apagador", "Canetão azul"), nomesDosItens(resposta));
        verify(auditoria).aposCommit(eq("SOLICITACAO_MATERIAL_CRIADA"), eq(professor.getId()), any());
    }

    @Test
    void naoAceitaOMesmoItemDuasVezes() {
        autorizar(professor);

        CriarSolicitacaoMaterialRequest request = criarRequest(item("Apagador", 1), item(" apagador ", 2));

        assertThrows(RegraNegocioException.class, () -> service.criar(request, comoProfessor));

        verify(solicitacoes, never()).saveAndFlush(any());
    }

    @Test
    void responsavelNaoTemAcesso() {
        Usuario responsavel = usuario("Maria Responsável", Perfil.RESPONSAVEL);
        autorizar(responsavel);

        UsuarioAtual comoResponsavel = new UsuarioAtual(responsavel.getId(), Perfil.RESPONSAVEL);

        assertThrows(AccessDeniedException.class, () -> service.listar(0, 20, null, null, comoResponsavel));

        verifyNoInteractions(solicitacoes);
    }

    @Test
    void professorNaoAprovadoNaoTemAcesso() {
        professor.setSituacaoCadastro(SituacaoCadastro.PENDENTE_APROVACAO);
        autorizar(professor);

        assertThrows(AccessDeniedException.class, () -> service.listar(0, 20, null, null, comoProfessor));

        verifyNoInteractions(solicitacoes);
    }

    @Test
    void professorSoVeOsPropriosPedidos() {
        autorizar(professor);
        SolicitacaoMaterial solicitacao = solicitacao(professor.getId());
        Page<SolicitacaoMaterial> pagina = new PageImpl<>(List.of(solicitacao), PageRequest.of(0, 20), 1);
        ItemSolicitacaoMaterial apagador = new ItemSolicitacaoMaterial(solicitacao.getId(), "Apagador", 2);

        when(solicitacoes.listarDoProfessor(eq(professor.getId()), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(pagina);
        when(itens.findBySolicitacaoIdIn(any())).thenReturn(List.of(apagador));
        when(usuarios.findAllById(any())).thenReturn(List.of(professor));

        Page<SolicitacaoMaterialResponse> resposta = service.listar(0, 20, null, null, comoProfessor);

        assertEquals(1, resposta.getTotalElements());
        assertEquals("Ana Professora", resposta.getContent().getFirst().professorNome());
        assertEquals(List.of("Apagador"), nomesDosItens(resposta.getContent().getFirst()));
        verify(solicitacoes, never()).listarTodas(any(), any(), any());
    }

    @Test
    void direcaoVeTodosOsPedidos() {
        autorizar(diretor);
        when(solicitacoes.listarTodas(eq(StatusSolicitacaoMaterial.ABERTA), isNull(), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.listar(0, 20, StatusSolicitacaoMaterial.ABERTA, null, comoDirecao);

        verify(solicitacoes, never()).listarDoProfessor(any(), any(), any(), any());
    }

    @Test
    void naoAceitaMaisDeCemPorPagina() {
        autorizar(diretor);

        assertThrows(RegraNegocioException.class, () -> service.listar(0, 101, null, null, comoDirecao));
    }

    @Test
    void professorNaoAbrePedidoDeOutroProfessor() {
        autorizar(professor);
        SolicitacaoMaterial solicitacaoDeOutro = solicitacao(UUID.randomUUID());
        when(solicitacoes.findById(solicitacaoDeOutro.getId())).thenReturn(Optional.of(solicitacaoDeOutro));

        assertThrows(AccessDeniedException.class, () -> service.visualizar(solicitacaoDeOutro.getId(), comoProfessor));
    }

    @Test
    void conversaMostraQuemEscreveuCadaMensagem() {
        autorizar(diretor);
        autorizar(professor);
        SolicitacaoMaterial solicitacao = solicitacao(professor.getId());
        MensagemSolicitacaoMaterial pergunta = solicitacao.registrarMensagem(professor.getId(), "Chega até sexta?", agora);
        MensagemSolicitacaoMaterial resposta = solicitacao.registrarMensagem(diretor.getId(), "Chega sim.", agora);

        when(solicitacoes.findById(solicitacao.getId())).thenReturn(Optional.of(solicitacao));
        when(mensagens.findBySolicitacaoIdOrderByCriadoEmAscIdAsc(solicitacao.getId()))
                .thenReturn(List.of(pergunta, resposta));
        when(usuarios.findAllById(any())).thenReturn(List.of(professor, diretor));

        SolicitacaoMaterialDetalheResponse detalhe = service.visualizar(solicitacao.getId(), comoDirecao);

        assertEquals(2, detalhe.mensagens().size());
        assertEquals(Perfil.PROFESSOR, detalhe.mensagens().get(0).autorPerfil());
        assertEquals("Carlos Diretor", detalhe.mensagens().get(1).autorNome());
    }

    @Test
    void editarTrocaOsItensDoPedido() {
        autorizar(professor);
        SolicitacaoMaterial solicitacao = solicitacao(professor.getId());
        List<ItemSolicitacaoMaterial> itensAntigos = List.of(new ItemSolicitacaoMaterial(solicitacao.getId(), "Giz", 10));

        when(solicitacoes.buscarComBloqueio(solicitacao.getId())).thenReturn(Optional.of(solicitacao));
        when(itens.findBySolicitacaoId(solicitacao.getId())).thenReturn(itensAntigos);

        AtualizarSolicitacaoMaterialRequest request = new AtualizarSolicitacaoMaterialRequest(
                "Materiais urgentes",
                null,
                PrioridadeSolicitacaoMaterial.ALTA,
                "Brinquedoteca",
                List.of(item("Canetão preto", 3)),
                0L
        );

        SolicitacaoMaterialResponse resposta = service.atualizar(solicitacao.getId(), request, comoProfessor);

        assertEquals("Materiais urgentes", resposta.titulo());
        assertEquals("Brinquedoteca", resposta.sala());
        assertEquals(PrioridadeSolicitacaoMaterial.ALTA, resposta.prioridade());
        assertEquals(List.of("Canetão preto"), nomesDosItens(resposta));
        verify(itens).deleteAll(itensAntigos);
        verify(auditoria).aposCommit("SOLICITACAO_MATERIAL_ATUALIZADA", professor.getId(), solicitacao.getId());
    }

    @Test
    void naoDaParaEditarPedidoFinalizado() {
        autorizar(professor);
        SolicitacaoMaterial solicitacao = solicitacao(professor.getId());
        solicitacao.cancelar(agora);
        when(solicitacoes.buscarComBloqueio(solicitacao.getId())).thenReturn(Optional.of(solicitacao));

        AtualizarSolicitacaoMaterialRequest request = new AtualizarSolicitacaoMaterialRequest(
                "Outro título",
                null,
                PrioridadeSolicitacaoMaterial.BAIXA,
                "Sala 3",
                List.of(item("Apagador", 1)),
                0L
        );

        assertThrows(RegraNegocioException.class, () -> service.atualizar(solicitacao.getId(), request, comoProfessor));

        verify(itens, never()).deleteAll(any());
    }

    @Test
    void mensagemESalvaSemEspacosSobrando() {
        autorizar(professor);
        SolicitacaoMaterial solicitacao = solicitacao(professor.getId());
        when(solicitacoes.buscarComBloqueio(solicitacao.getId())).thenReturn(Optional.of(solicitacao));

        EnviarMensagemSolicitacaoMaterialRequest request = new EnviarMensagemSolicitacaoMaterialRequest("  Alguma novidade?  ");

        service.enviarMensagem(solicitacao.getId(), request, comoProfessor);

        ArgumentCaptor<MensagemSolicitacaoMaterial> mensagemSalva = ArgumentCaptor.forClass(MensagemSolicitacaoMaterial.class);
        verify(mensagens).save(mensagemSalva.capture());
        assertEquals("Alguma novidade?", mensagemSalva.getValue().getConteudo());
        assertEquals(professor.getId(), mensagemSalva.getValue().getAutorId());
        verify(auditoria).aposCommit("SOLICITACAO_MATERIAL_MENSAGEM_ENVIADA", professor.getId(), solicitacao.getId());
    }

    @Test
    void naoDaParaMandarMensagemEmPedidoFinalizado() {
        autorizar(diretor);
        SolicitacaoMaterial solicitacao = solicitacao(professor.getId());
        solicitacao.atender(diretor.getId(), null, agora);
        when(solicitacoes.buscarComBloqueio(solicitacao.getId())).thenReturn(Optional.of(solicitacao));

        EnviarMensagemSolicitacaoMaterialRequest request = new EnviarMensagemSolicitacaoMaterialRequest("Mais uma coisa");

        assertThrows(RegraNegocioException.class, () -> service.enviarMensagem(solicitacao.getId(), request, comoDirecao));

        verify(mensagens, never()).save(any());
    }

    @Test
    void direcaoAtendeEDeixaUmRecado() {
        autorizar(diretor);
        autorizar(professor);
        SolicitacaoMaterial solicitacao = solicitacao(professor.getId());
        when(solicitacoes.buscarComBloqueio(solicitacao.getId())).thenReturn(Optional.of(solicitacao));

        AtenderSolicitacaoMaterialRequest request = new AtenderSolicitacaoMaterialRequest("Pode retirar na secretaria", 0L);

        SolicitacaoMaterialResponse resposta = service.atender(solicitacao.getId(), request, comoDirecao);

        assertEquals(StatusSolicitacaoMaterial.ATENDIDA, resposta.status());
        assertEquals(agora, resposta.finalizadoEm());
        verify(mensagens).save(any(MensagemSolicitacaoMaterial.class));
        verify(auditoria).aposCommit("SOLICITACAO_MATERIAL_ATENDIDA", diretor.getId(), solicitacao.getId());
    }

    @Test
    void direcaoAtendeSemDeixarRecado() {
        autorizar(diretor);
        autorizar(professor);
        SolicitacaoMaterial solicitacao = solicitacao(professor.getId());
        when(solicitacoes.buscarComBloqueio(solicitacao.getId())).thenReturn(Optional.of(solicitacao));

        AtenderSolicitacaoMaterialRequest request = new AtenderSolicitacaoMaterialRequest("   ", 0L);

        service.atender(solicitacao.getId(), request, comoDirecao);

        assertEquals(StatusSolicitacaoMaterial.ATENDIDA, solicitacao.getStatus());
        verify(mensagens, never()).save(any());
    }

    @Test
    void recusaGuardaOMotivoNaConversa() {
        autorizar(diretor);
        autorizar(professor);
        SolicitacaoMaterial solicitacao = solicitacao(professor.getId());
        when(solicitacoes.buscarComBloqueio(solicitacao.getId())).thenReturn(Optional.of(solicitacao));

        RecusarSolicitacaoMaterialRequest request = new RecusarSolicitacaoMaterialRequest("Sem verba este mês", 0L);

        SolicitacaoMaterialResponse resposta = service.recusar(solicitacao.getId(), request, comoDirecao);

        ArgumentCaptor<MensagemSolicitacaoMaterial> motivo = ArgumentCaptor.forClass(MensagemSolicitacaoMaterial.class);
        verify(mensagens).save(motivo.capture());
        assertEquals("Sem verba este mês", motivo.getValue().getConteudo());
        assertEquals(StatusSolicitacaoMaterial.RECUSADA, resposta.status());
    }

    @Test
    void naoAceitaVersaoDesatualizada() {
        autorizar(diretor);
        SolicitacaoMaterial solicitacao = solicitacao(professor.getId());
        when(solicitacoes.buscarComBloqueio(solicitacao.getId())).thenReturn(Optional.of(solicitacao));

        AtenderSolicitacaoMaterialRequest request = new AtenderSolicitacaoMaterialRequest(null, 5L);

        assertThrows(ConflitoVersaoException.class, () -> service.atender(solicitacao.getId(), request, comoDirecao));

        assertEquals(StatusSolicitacaoMaterial.ABERTA, solicitacao.getStatus());
    }

    @Test
    void naoDaParaAtenderPedidoCancelado() {
        autorizar(diretor);
        SolicitacaoMaterial solicitacao = solicitacao(professor.getId());
        solicitacao.cancelar(agora);
        when(solicitacoes.buscarComBloqueio(solicitacao.getId())).thenReturn(Optional.of(solicitacao));

        AtenderSolicitacaoMaterialRequest request = new AtenderSolicitacaoMaterialRequest(null, 0L);

        assertThrows(RegraNegocioException.class, () -> service.atender(solicitacao.getId(), request, comoDirecao));

        assertEquals(StatusSolicitacaoMaterial.CANCELADA, solicitacao.getStatus());
    }

    @Test
    void professorCancelaOProprioPedido() {
        autorizar(professor);
        SolicitacaoMaterial solicitacao = solicitacao(professor.getId());
        when(solicitacoes.buscarComBloqueio(solicitacao.getId())).thenReturn(Optional.of(solicitacao));

        CancelarSolicitacaoMaterialRequest request = new CancelarSolicitacaoMaterialRequest(0L);

        SolicitacaoMaterialResponse resposta = service.cancelar(solicitacao.getId(), request, comoProfessor);

        assertEquals(StatusSolicitacaoMaterial.CANCELADA, resposta.status());
        verify(auditoria).aposCommit("SOLICITACAO_MATERIAL_CANCELADA", professor.getId(), solicitacao.getId());
    }

    @Test
    void professorNaoCancelaPedidoDeOutro() {
        autorizar(professor);
        SolicitacaoMaterial solicitacaoDeOutro = solicitacao(UUID.randomUUID());
        when(solicitacoes.buscarComBloqueio(solicitacaoDeOutro.getId())).thenReturn(Optional.of(solicitacaoDeOutro));

        CancelarSolicitacaoMaterialRequest request = new CancelarSolicitacaoMaterialRequest(0L);

        assertThrows(AccessDeniedException.class, () -> service.cancelar(solicitacaoDeOutro.getId(), request, comoProfessor));

        assertEquals(StatusSolicitacaoMaterial.ABERTA, solicitacaoDeOutro.getStatus());
    }

    private void autorizar(Usuario usuario) {
        when(usuarios.findById(usuario.getId())).thenReturn(Optional.of(usuario));
    }

    private Usuario usuario(String nome, Perfil perfil) {
        Usuario usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setNome(nome);
        usuario.setPerfil(perfil);
        usuario.setAtivo(true);
        usuario.setSituacaoCadastro(SituacaoCadastro.APROVADO);
        return usuario;
    }

    private SolicitacaoMaterial solicitacao(UUID professorId) {
        SolicitacaoMaterial solicitacao = new SolicitacaoMaterial(
                professorId,
                "Sala 3",
                "Materiais da sala 3",
                null,
                PrioridadeSolicitacaoMaterial.MEDIA,
                agora
        );
        ReflectionTestUtils.setField(solicitacao, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(solicitacao, "versao", 0L);
        return solicitacao;
    }

    private CriarSolicitacaoMaterialRequest criarRequest(ItemSolicitacaoMaterialRequest... itensPedidos) {
        return new CriarSolicitacaoMaterialRequest(
                "  Materiais da sala 3 ",
                "Os canetões acabaram.",
                PrioridadeSolicitacaoMaterial.MEDIA,
                "  Sala 3 ",
                List.of(itensPedidos)
        );
    }

    private ItemSolicitacaoMaterialRequest item(String nome, int quantidade) {
        return new ItemSolicitacaoMaterialRequest(nome, quantidade);
    }

    private List<String> nomesDosItens(SolicitacaoMaterialResponse resposta) {
        return resposta.itens()
                .stream()
                .map(SolicitacaoMaterialResponse.Item::nome)
                .toList();
    }
}
