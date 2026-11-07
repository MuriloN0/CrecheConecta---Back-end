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
import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.exception.SolicitacaoMaterialNaoEncontradaException;
import com.crecheconecta.repository.ItemSolicitacaoMaterialRepository;
import com.crecheconecta.repository.MensagemSolicitacaoMaterialRepository;
import com.crecheconecta.repository.SolicitacaoMaterialRepository;
import com.crecheconecta.repository.UsuarioRepository;
import com.crecheconecta.security.UsuarioAtual;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@PreAuthorize("hasAnyRole('DIRECAO', 'PROFESSOR')")
public class SolicitacaoMaterialService {

    private static final Sort MAIS_RECENTES = Sort.by("criadoEm").descending().and(Sort.by("id").descending());

    private final SolicitacaoMaterialRepository solicitacoes;
    private final ItemSolicitacaoMaterialRepository itens;
    private final MensagemSolicitacaoMaterialRepository mensagens;
    private final UsuarioRepository usuarios;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public SolicitacaoMaterialService(
            SolicitacaoMaterialRepository solicitacoes,
            ItemSolicitacaoMaterialRepository itens,
            MensagemSolicitacaoMaterialRepository mensagens,
            UsuarioRepository usuarios,
            AuditoriaService auditoria,
            Clock clock
    ) {

        this.solicitacoes = solicitacoes;
        this.itens = itens;
        this.mensagens = mensagens;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    @Transactional
    @PreAuthorize("hasRole('PROFESSOR')")
    public SolicitacaoMaterialResponse criar(CriarSolicitacaoMaterialRequest request, UsuarioAtual solicitante) {

        Usuario professor = validarUsuario(solicitante, Perfil.PROFESSOR);

        validarItensRepetidos(request.itens());

        SolicitacaoMaterial solicitacao = new SolicitacaoMaterial(
                professor.getId(),
                request.sala(),
                request.titulo(),
                request.descricao(),
                request.prioridade(),
                clock.instant()
        );

        solicitacoes.saveAndFlush(solicitacao);

        List<ItemSolicitacaoMaterial> itensDaSolicitacao = salvarItens(solicitacao.getId(), request.itens());

        auditoria.aposCommit("SOLICITACAO_MATERIAL_CRIADA", professor.getId(), solicitacao.getId());

        return resposta(solicitacao, itensDaSolicitacao);
    }

    @Transactional(readOnly = true)
    public Page<SolicitacaoMaterialResponse> listar(
            int pagina,
            int tamanho,
            StatusSolicitacaoMaterial status,
            PrioridadeSolicitacaoMaterial prioridade,
            UsuarioAtual solicitante
    ) {

        Usuario usuario = validarUsuario(solicitante, null);

        if (pagina < 0 || tamanho < 1 || tamanho > 100) {

            throw new RegraNegocioException("Informe página a partir de zero e tamanho entre 1 e 100.");
        }

        Pageable paginacao = PageRequest.of(pagina, tamanho, MAIS_RECENTES);

        Page<SolicitacaoMaterial> resultado = usuario.getPerfil() == Perfil.DIRECAO
                ? solicitacoes.listarTodas(status, prioridade, paginacao)
                : solicitacoes.listarDoProfessor(usuario.getId(), status, prioridade, paginacao);

        Map<UUID, List<ItemSolicitacaoMaterial>> itensPorSolicitacao = itensPorSolicitacao(resultado.getContent());

        Map<UUID, String> nomesProfessores = nomesDosUsuarios(resultado.stream()
                .map(SolicitacaoMaterial::getProfessorId)
                .toList());

        return resultado.map(solicitacao -> SolicitacaoMaterialResponse.de(
                solicitacao,
                itensPorSolicitacao.getOrDefault(solicitacao.getId(), List.of()),
                nomesProfessores.get(solicitacao.getProfessorId())
        ));
    }

    @Transactional(readOnly = true)
    public SolicitacaoMaterialDetalheResponse visualizar(UUID solicitacaoId, UsuarioAtual solicitante) {

        Usuario usuario = validarUsuario(solicitante, null);

        SolicitacaoMaterial solicitacao = solicitacoes.findById(solicitacaoId)
                .orElseThrow(SolicitacaoMaterialNaoEncontradaException::new);

        exigirAcesso(solicitacao, usuario);

        return detalhe(solicitacao);
    }

    @Transactional
    @PreAuthorize("hasRole('PROFESSOR')")
    public SolicitacaoMaterialResponse atualizar(
            UUID solicitacaoId,
            AtualizarSolicitacaoMaterialRequest request,
            UsuarioAtual solicitante
    ) {

        Usuario professor = validarUsuario(solicitante, Perfil.PROFESSOR);

        SolicitacaoMaterial solicitacao = buscarComBloqueio(solicitacaoId);

        exigirAutor(solicitacao, professor);
        solicitacao.exigirVersao(request.versao());

        validarItensRepetidos(request.itens());

        solicitacao.atualizar(
                request.sala(),
                request.titulo(),
                request.descricao(),
                request.prioridade(),
                clock.instant()
        );

        itens.deleteAll(itens.findBySolicitacaoId(solicitacaoId));

        List<ItemSolicitacaoMaterial> itensDaSolicitacao = salvarItens(solicitacaoId, request.itens());

        solicitacoes.flush();

        auditoria.aposCommit("SOLICITACAO_MATERIAL_ATUALIZADA", professor.getId(), solicitacao.getId());

        return resposta(solicitacao, itensDaSolicitacao);
    }

    @Transactional
    public SolicitacaoMaterialDetalheResponse enviarMensagem(
            UUID solicitacaoId,
            EnviarMensagemSolicitacaoMaterialRequest request,
            UsuarioAtual solicitante
    ) {

        Usuario usuario = validarUsuario(solicitante, null);

        SolicitacaoMaterial solicitacao = buscarComBloqueio(solicitacaoId);

        exigirAcesso(solicitacao, usuario);

        mensagens.save(solicitacao.registrarMensagem(usuario.getId(), request.conteudo(), clock.instant()));

        solicitacoes.flush();

        auditoria.aposCommit("SOLICITACAO_MATERIAL_MENSAGEM_ENVIADA", usuario.getId(), solicitacao.getId());

        return detalhe(solicitacao);
    }

    @Transactional
    @PreAuthorize("hasRole('DIRECAO')")
    public SolicitacaoMaterialResponse atender(
            UUID solicitacaoId,
            AtenderSolicitacaoMaterialRequest request,
            UsuarioAtual solicitante
    ) {

        Usuario diretor = validarUsuario(solicitante, Perfil.DIRECAO);

        SolicitacaoMaterial solicitacao = buscarComBloqueio(solicitacaoId);

        solicitacao.exigirVersao(request.versao());
        solicitacao.atender(diretor.getId(), request.mensagem(), clock.instant()).ifPresent(mensagens::save);

        solicitacoes.flush();

        auditoria.aposCommit("SOLICITACAO_MATERIAL_ATENDIDA", diretor.getId(), solicitacao.getId());

        return resposta(solicitacao, itens.findBySolicitacaoId(solicitacaoId));
    }

    @Transactional
    @PreAuthorize("hasRole('DIRECAO')")
    public SolicitacaoMaterialResponse recusar(
            UUID solicitacaoId,
            RecusarSolicitacaoMaterialRequest request,
            UsuarioAtual solicitante
    ) {

        Usuario diretor = validarUsuario(solicitante, Perfil.DIRECAO);

        SolicitacaoMaterial solicitacao = buscarComBloqueio(solicitacaoId);

        solicitacao.exigirVersao(request.versao());

        mensagens.save(solicitacao.recusar(diretor.getId(), request.motivo(), clock.instant()));

        solicitacoes.flush();

        auditoria.aposCommit("SOLICITACAO_MATERIAL_RECUSADA", diretor.getId(), solicitacao.getId());

        return resposta(solicitacao, itens.findBySolicitacaoId(solicitacaoId));
    }

    @Transactional
    @PreAuthorize("hasRole('PROFESSOR')")
    public SolicitacaoMaterialResponse cancelar(
            UUID solicitacaoId,
            CancelarSolicitacaoMaterialRequest request,
            UsuarioAtual solicitante
    ) {

        Usuario professor = validarUsuario(solicitante, Perfil.PROFESSOR);

        SolicitacaoMaterial solicitacao = buscarComBloqueio(solicitacaoId);

        exigirAutor(solicitacao, professor);
        solicitacao.exigirVersao(request.versao());
        solicitacao.cancelar(clock.instant());

        solicitacoes.flush();

        auditoria.aposCommit("SOLICITACAO_MATERIAL_CANCELADA", professor.getId(), solicitacao.getId());

        return resposta(solicitacao, itens.findBySolicitacaoId(solicitacaoId));
    }

    private SolicitacaoMaterial buscarComBloqueio(UUID solicitacaoId) {

        return solicitacoes.buscarComBloqueio(solicitacaoId)
                .orElseThrow(SolicitacaoMaterialNaoEncontradaException::new);
    }

    private List<ItemSolicitacaoMaterial> salvarItens(UUID solicitacaoId, List<ItemSolicitacaoMaterialRequest> pedidos) {

        List<ItemSolicitacaoMaterial> novosItens = pedidos.stream()
                .map(pedido -> new ItemSolicitacaoMaterial(solicitacaoId, pedido.nome(), pedido.quantidade()))
                .toList();

        itens.saveAll(novosItens);

        return novosItens;
    }

    private SolicitacaoMaterialResponse resposta(SolicitacaoMaterial solicitacao, List<ItemSolicitacaoMaterial> itensDaSolicitacao) {

        String professorNome = usuarios.findById(solicitacao.getProfessorId())
                .map(Usuario::getNome)
                .orElse(null);

        return SolicitacaoMaterialResponse.de(solicitacao, itensDaSolicitacao, professorNome);
    }

    private SolicitacaoMaterialDetalheResponse detalhe(SolicitacaoMaterial solicitacao) {

        List<MensagemSolicitacaoMaterial> conversa = mensagens.findBySolicitacaoIdOrderByCriadoEmAscIdAsc(solicitacao.getId());

        Set<UUID> idsDosAutores = conversa.stream()
                .map(MensagemSolicitacaoMaterial::getAutorId)
                .collect(Collectors.toSet());

        Map<UUID, Usuario> autores = usuarios.findAllById(idsDosAutores)
                .stream()
                .collect(Collectors.toMap(Usuario::getId, Function.identity()));

        List<SolicitacaoMaterialDetalheResponse.Mensagem> mensagensDaConversa = conversa.stream()
                .map(mensagem -> SolicitacaoMaterialDetalheResponse.Mensagem.de(mensagem, autores.get(mensagem.getAutorId())))
                .toList();

        List<ItemSolicitacaoMaterial> itensDaSolicitacao = itens.findBySolicitacaoId(solicitacao.getId());

        return new SolicitacaoMaterialDetalheResponse(resposta(solicitacao, itensDaSolicitacao), mensagensDaConversa);
    }

    private Map<UUID, List<ItemSolicitacaoMaterial>> itensPorSolicitacao(List<SolicitacaoMaterial> lista) {

        if (lista.isEmpty()) {

            return Map.of();
        }

        List<UUID> ids = lista.stream()
                .map(SolicitacaoMaterial::getId)
                .toList();

        return itens.findBySolicitacaoIdIn(ids)
                .stream()
                .collect(Collectors.groupingBy(ItemSolicitacaoMaterial::getSolicitacaoId));
    }

    private Map<UUID, String> nomesDosUsuarios(Collection<UUID> ids) {

        return usuarios.findAllById(new HashSet<>(ids))
                .stream()
                .collect(Collectors.toMap(Usuario::getId, Usuario::getNome));
    }

    private Usuario validarUsuario(UsuarioAtual solicitante, Perfil perfilExigido) {

        if (solicitante == null || solicitante.id() == null || solicitante.perfil() == null) {

            throw acessoNegado();
        }

        Usuario usuario = usuarios.findById(solicitante.id()).orElseThrow(this::acessoNegado);

        if (!usuario.isAtivo() || usuario.getSituacaoCadastro() != SituacaoCadastro.APROVADO || usuario.getPerfil() != solicitante.perfil()) {

            throw acessoNegado();
        }

        boolean perfilPermitido = usuario.getPerfil() == Perfil.DIRECAO || usuario.getPerfil() == Perfil.PROFESSOR;

        if (!perfilPermitido || (perfilExigido != null && usuario.getPerfil() != perfilExigido)) {

            throw acessoNegado();
        }

        return usuario;
    }

    private void exigirAcesso(SolicitacaoMaterial solicitacao, Usuario usuario) {

        if (usuario.getPerfil() == Perfil.PROFESSOR && !solicitacao.pertenceAo(usuario.getId())) {

            throw new AccessDeniedException("Você só pode acessar as suas solicitações.");
        }
    }

    private void exigirAutor(SolicitacaoMaterial solicitacao, Usuario professor) {

        if (!solicitacao.pertenceAo(professor.getId())) {

            throw new AccessDeniedException("Somente o professor que abriu a solicitação pode alterá-la.");
        }
    }

    private void validarItensRepetidos(List<ItemSolicitacaoMaterialRequest> pedidos) {

        Set<String> nomes = new HashSet<>();

        for (ItemSolicitacaoMaterialRequest pedido : pedidos) {

            if (!nomes.add(pedido.nome().strip().toLowerCase(Locale.ROOT))) {

                throw new RegraNegocioException("A solicitação possui itens repetidos.");
            }
        }
    }

    private AccessDeniedException acessoNegado() {

        return new AccessDeniedException("Você não possui permissão para esta operação.");
    }
}
