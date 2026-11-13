package com.crecheconecta.service;

import com.crecheconecta.dto.MalaVirtualResponse;
import com.crecheconecta.dto.MarcarItemMalaRequest;
import com.crecheconecta.entity.MalaVirtual;
import com.crecheconecta.entity.MarcacaoItemMala;
import com.crecheconecta.exception.ItemMalaNaoEncontradoException;
import com.crecheconecta.exception.MalaVirtualNaoEncontradaException;
import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.repository.ItemMalaRepository;
import com.crecheconecta.repository.MalaVirtualRepository;
import com.crecheconecta.repository.MarcacaoItemMalaRepository;
import com.crecheconecta.security.UsuarioAtual;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static com.crecheconecta.service.MalaVirtualSuporte.textoOpcional;

@Service
public class MalaVirtualFilhoService {

    private final VinculoResponsavelService acessoResponsavel;
    private final MalaVirtualRepository malas;
    private final ItemMalaRepository itens;
    private final MarcacaoItemMalaRepository marcacoes;
    private final MalaVirtualSuporte suporte;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public MalaVirtualFilhoService(
            VinculoResponsavelService acessoResponsavel,
            MalaVirtualRepository malas,
            ItemMalaRepository itens,
            MarcacaoItemMalaRepository marcacoes,
            MalaVirtualSuporte suporte,
            AuditoriaService auditoria,
            Clock clock) {
        this.acessoResponsavel = acessoResponsavel;
        this.malas = malas;
        this.itens = itens;
        this.marcacoes = marcacoes;
        this.suporte = suporte;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<MalaVirtualResponse> listar(UUID alunoId, UsuarioAtual solicitante) {
        acessoResponsavel.exigirResponsavelDoAluno(solicitante, alunoId);

        var turmaId = acessoResponsavel.turmaAtualDoAluno(alunoId).orElse(null);

        if (turmaId == null) {
            return List.of();
        }

        List<MalaVirtual> recentes = malas.findTop20ByTurmaIdOrderByDataDescCriadoEmDesc(turmaId);
        var itensPorMala = suporte.itensPorMala(recentes);
        var marcacoesDoFilho = suporte.marcacoesDoAluno(alunoId, itensPorMala.values().stream().flatMap(List::stream).toList());
        var hoje = suporte.hoje();

        var resposta = recentes.stream()
                .map(mala -> MalaVirtualResponse.de(mala, itensPorMala.getOrDefault(mala.getId(), List.of()), marcacoesDoFilho, hoje))
                .toList();

        auditoria.registrar("MALAS_VIRTUAIS_LISTADAS", solicitante.id(), alunoId);
        return resposta;
    }

    @Transactional
    public MalaVirtualResponse marcarItem(UUID alunoId, UUID malaId, UUID itemId, MarcarItemMalaRequest request, UsuarioAtual solicitante) {
        acessoResponsavel.exigirResponsavelDoAluno(solicitante, alunoId);

        var turmaId = acessoResponsavel.turmaAtualDoAluno(alunoId).orElseThrow(MalaVirtualNaoEncontradaException::new);

        var mala = malas.findByIdAndTurmaId(malaId, turmaId).orElseThrow(MalaVirtualNaoEncontradaException::new);

        var hoje = suporte.hoje();

        if (mala.getData().isBefore(hoje)) {
            throw new RegraNegocioException("Não é possível marcar itens depois da data da aula.");
        }

        var itensDaMala = itens.findByMalaId(malaId);

        if (itensDaMala.stream().noneMatch(item -> item.getId().equals(itemId))) {
            throw new ItemMalaNaoEncontradoException();
        }

        var marcacao = marcacoes.findByItemIdAndAlunoId(itemId, alunoId).orElseGet(() -> {
            var nova = new MarcacaoItemMala();
            nova.setItemId(itemId);
            nova.setAlunoId(alunoId);
            return nova;
        });

        marcacao.setEnviado(request.enviado());
        marcacao.setObservacao(textoOpcional(request.observacao()));
        marcacao.setMarcadoPor(solicitante.id());
        marcacao.setAtualizadoEm(clock.instant());

        marcacoes.saveAndFlush(marcacao);

        auditoria.aposCommit("ITEM_MALA_MARCADO", solicitante.id(), marcacao.getId());

        return MalaVirtualResponse.de(mala, itensDaMala, suporte.marcacoesDoAluno(alunoId, itensDaMala), hoje);
    }
}
