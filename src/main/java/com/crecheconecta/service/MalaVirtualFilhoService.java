package com.crecheconecta.service;

import com.crecheconecta.dto.MalaVirtualResponse;
import com.crecheconecta.entity.ItemMala;
import com.crecheconecta.entity.MalaVirtual;
import com.crecheconecta.repository.ItemMalaRepository;
import com.crecheconecta.repository.MalaVirtualRepository;
import com.crecheconecta.security.UsuarioAtual;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Portal dos pais: Mala Virtual do filho (as 20 mais recentes). Só leitura.
 */
@Service
public class MalaVirtualFilhoService {

    private final VinculoResponsavelService acessoResponsavel;
    private final MalaVirtualRepository malas;
    private final ItemMalaRepository itens;
    private final AuditoriaService auditoria;

    public MalaVirtualFilhoService(VinculoResponsavelService acessoResponsavel, MalaVirtualRepository malas, ItemMalaRepository itens, AuditoriaService auditoria) {
        this.acessoResponsavel = acessoResponsavel;
        this.malas = malas;
        this.itens = itens;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public List<MalaVirtualResponse> listar(UUID alunoId, UsuarioAtual solicitante) {
        acessoResponsavel.exigirResponsavelDoAluno(solicitante, alunoId);

        List<MalaVirtual> recentes = malas.findTop20ByAlunoIdOrderByDataDesc(alunoId);

        // 1 consulta para os itens de todas as malas, depois agrupa por malaId.
        Map<UUID, List<ItemMala>> itensPorMala = recentes.isEmpty() ? Map.of() : itens.findByMalaIdIn(recentes.stream().map(MalaVirtual::getId).toList()).stream().collect(Collectors.groupingBy(ItemMala::getMalaId));

        var resposta = recentes.stream().map(mala -> MalaVirtualResponse.de(mala, itensPorMala.getOrDefault(mala.getId(), List.of()))).toList();

        auditoria.registrar("MALAS_VIRTUAIS_LISTADAS", solicitante.id(), alunoId);
        return resposta;
    }
}
