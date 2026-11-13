package com.crecheconecta.service;

import com.crecheconecta.dto.AtualizarMalaVirtualRequest;
import com.crecheconecta.dto.CriarMalaVirtualRequest;
import com.crecheconecta.dto.ItemMalaRequest;
import com.crecheconecta.dto.MalaVirtualTurmaResponse;
import com.crecheconecta.dto.ProgressoMalaAlunoResponse;
import com.crecheconecta.entity.ItemMala;
import com.crecheconecta.entity.MalaVirtual;
import com.crecheconecta.exception.ConflitoVersaoException;
import com.crecheconecta.exception.MalaVirtualNaoEncontradaException;
import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.exception.TurmaNaoEncontradaException;
import com.crecheconecta.repository.ItemMalaRepository;
import com.crecheconecta.repository.MalaVirtualRepository;
import com.crecheconecta.repository.MatriculaTurmaRepository;
import com.crecheconecta.repository.TurmaRepository;
import com.crecheconecta.security.UsuarioAtual;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.crecheconecta.service.MalaVirtualSuporte.textoOpcional;

@Service
@PreAuthorize("hasRole('PROFESSOR')")
public class MalaVirtualService {

    private final VinculoProfessorService vinculoProfessor;
    private final TurmaRepository turmas;
    private final MatriculaTurmaRepository matriculas;
    private final MalaVirtualRepository malas;
    private final ItemMalaRepository itens;
    private final MalaVirtualSuporte suporte;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public MalaVirtualService(
            VinculoProfessorService vinculoProfessor,
            TurmaRepository turmas,
            MatriculaTurmaRepository matriculas,
            MalaVirtualRepository malas,
            ItemMalaRepository itens,
            MalaVirtualSuporte suporte,
            AuditoriaService auditoria,
            Clock clock) {

        this.vinculoProfessor = vinculoProfessor;
        this.turmas = turmas;
        this.matriculas = matriculas;
        this.malas = malas;
        this.itens = itens;
        this.suporte = suporte;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    @Transactional
    public MalaVirtualTurmaResponse criar(UUID turmaId, CriarMalaVirtualRequest request, UsuarioAtual solicitante) {

        vinculoProfessor.exigirProfessorDaTurma(turmaId, solicitante);

        var turma = turmas.findById(turmaId).orElseThrow(TurmaNaoEncontradaException::new);

        if (!turma.isAtivo()) {

            throw new RegraNegocioException("A turma está inativa.");
        }

        validarDataDaAula(request.data());
        validarItensRepetidos(request.itens());

        var agora = clock.instant();

        var mala = new MalaVirtual();
        mala.setTurmaId(turmaId);
        mala.setProfessorId(solicitante.id());
        mala.setTitulo(request.titulo().strip());
        mala.setData(request.data());
        mala.setObservacao(textoOpcional(request.observacao()));
        mala.setCriadoEm(agora);
        mala.setAtualizadoEm(agora);

        malas.saveAndFlush(mala);

        var itensDaMala = request.itens().stream().map(pedido -> novoItem(mala.getId(), pedido)).toList();

        itens.saveAll(itensDaMala);

        auditoria.aposCommit("MALA_VIRTUAL_CRIADA", solicitante.id(), mala.getId());

        return MalaVirtualTurmaResponse.de(mala, itensDaMala);
    }

    @Transactional(readOnly = true)
    public List<MalaVirtualTurmaResponse> listar(UUID turmaId, UsuarioAtual solicitante) {

        vinculoProfessor.exigirProfessorDaTurma(turmaId, solicitante);

        var malasDaTurma = malas.findByTurmaIdOrderByDataDescCriadoEmDesc(turmaId);
        var itensPorMala = suporte.itensPorMala(malasDaTurma);

        return malasDaTurma.stream()
                .map(mala -> MalaVirtualTurmaResponse.de(mala, itensPorMala.getOrDefault(mala.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public MalaVirtualTurmaResponse visualizar(UUID turmaId, UUID malaId, UsuarioAtual solicitante) {

        vinculoProfessor.exigirProfessorDaTurma(turmaId, solicitante);

        var mala = malas.findByIdAndTurmaId(malaId, turmaId).orElseThrow(MalaVirtualNaoEncontradaException::new);

        return MalaVirtualTurmaResponse.de(mala, itens.findByMalaId(malaId));
    }

    @Transactional(readOnly = true)
    public List<ProgressoMalaAlunoResponse> progresso(UUID turmaId, UUID malaId, UsuarioAtual solicitante) {

        vinculoProfessor.exigirProfessorDaTurma(turmaId, solicitante);

        if (!malas.existsByIdAndTurmaId(malaId, turmaId)) {

            throw new MalaVirtualNaoEncontradaException();
        }

        var itensDaMala = itens.findByMalaId(malaId);
        var marcacoesPorAluno = suporte.marcacoesPorAluno(itensDaMala);

        return matriculas.buscarAlunosAtuaisDaTurma(turmaId)
                .stream()
                .map(aluno -> ProgressoMalaAlunoResponse.de(aluno, itensDaMala, marcacoesPorAluno.getOrDefault(aluno.getId(), Map.of())))
                .toList();
    }

    @Transactional
    public MalaVirtualTurmaResponse atualizar(UUID turmaId, UUID malaId, AtualizarMalaVirtualRequest request, UsuarioAtual solicitante) {

        vinculoProfessor.exigirProfessorDaTurma(turmaId, solicitante);

        var mala = buscarMalaDoAutor(turmaId, malaId, solicitante);

        if (!Objects.equals(mala.getVersao(), request.versao())) {

            throw new ConflitoVersaoException();
        }

        if (!Objects.equals(request.data(), mala.getData())) {

            validarDataDaAula(request.data());
        }

        validarItensRepetidos(request.itens());

        Map<UUID, ItemMala> itensAtuais = itens.findByMalaId(malaId)
                .stream()
                .collect(Collectors.toMap(ItemMala::getId, Function.identity()));

        var idsMantidos = new HashSet<UUID>();
        var itensAtualizados = new ArrayList<ItemMala>();

        for (var pedido : request.itens()) {

            if (pedido.id() == null) {

                itensAtualizados.add(novoItem(malaId, pedido));
                continue;
            }

            var item = itensAtuais.get(pedido.id());

            if (item == null) {

                throw new RegraNegocioException("Há itens que não pertencem a esta mala.");
            }

            if (!idsMantidos.add(item.getId())) {

                throw new RegraNegocioException("A mala possui itens repetidos.");
            }

            item.setNome(pedido.nome().strip());
            item.setQuantidade(pedido.quantidade());
            itensAtualizados.add(item);
        }

        var itensRemovidos = itensAtuais.values().stream().filter(item -> !idsMantidos.contains(item.getId())).toList();

        itens.deleteAll(itensRemovidos);
        itens.saveAll(itensAtualizados);

        mala.setTitulo(request.titulo().strip());
        mala.setData(request.data());
        mala.setObservacao(textoOpcional(request.observacao()));
        mala.setAtualizadoEm(clock.instant());

        malas.flush();

        auditoria.aposCommit("MALA_VIRTUAL_ATUALIZADA", solicitante.id(), mala.getId());

        return MalaVirtualTurmaResponse.de(mala, itensAtualizados);
    }

    @Transactional
    public void excluir(UUID turmaId, UUID malaId, UsuarioAtual solicitante) {

        vinculoProfessor.exigirProfessorDaTurma(turmaId, solicitante);

        var mala = buscarMalaDoAutor(turmaId, malaId, solicitante);

        malas.delete(mala);
        malas.flush();

        auditoria.aposCommit("MALA_VIRTUAL_EXCLUIDA", solicitante.id(), mala.getId());
    }

    private MalaVirtual buscarMalaDoAutor(UUID turmaId, UUID malaId, UsuarioAtual solicitante) {

        var mala = malas.buscarDaTurmaComBloqueio(malaId, turmaId).orElseThrow(MalaVirtualNaoEncontradaException::new);

        if (!mala.getProfessorId().equals(solicitante.id())) {

            throw new AccessDeniedException("Somente o professor que criou a mala pode alterá-la.");
        }

        return mala;
    }

    private ItemMala novoItem(UUID malaId, ItemMalaRequest pedido) {

        var item = new ItemMala();
        item.setMalaId(malaId);
        item.setNome(pedido.nome().strip());
        item.setQuantidade(pedido.quantidade());

        return item;
    }

    private void validarDataDaAula(LocalDate data) {

        if (data.isBefore(suporte.hoje())) {

            throw new RegraNegocioException("A data da aula não pode estar no passado.");
        }
    }

    private void validarItensRepetidos(List<ItemMalaRequest> pedidos) {

        var nomes = new HashSet<String>();

        for (var pedido : pedidos) {

            if (!nomes.add(pedido.nome().strip().toLowerCase(Locale.ROOT))) {

                throw new RegraNegocioException("A mala possui itens repetidos.");
            }
        }
    }
}
