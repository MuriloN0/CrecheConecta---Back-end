package com.crecheconecta.service;

import com.crecheconecta.dto.AtualizarTermoVersaoRequest;
import com.crecheconecta.dto.TermoVersaoRequest;
import com.crecheconecta.dto.TermoVersaoResponse;
import com.crecheconecta.entity.StatusTermo;
import com.crecheconecta.entity.TermoVersao;
import com.crecheconecta.exception.ConflitoVersaoException;
import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.exception.TermoNaoEncontradoException;
import com.crecheconecta.repository.AceiteTermoRepository;
import com.crecheconecta.repository.TermoVersaoRepository;
import com.crecheconecta.security.UsuarioAtual;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@PreAuthorize("hasRole('DIRECAO')")
public class TermoVersaoService {

    private final TermoVersaoRepository termos;
    private final AceiteTermoRepository aceites;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public TermoVersaoService(
            TermoVersaoRepository termos,
            AceiteTermoRepository aceites,
            AuditoriaService auditoria,
            Clock clock
    ) {
        this.termos = termos;
        this.aceites = aceites;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<TermoVersaoResponse> listar() {
        Map<String, Long> aceitesPorVersao = contarAceites();
        return termos.findAllByOrderByCriadoEmDescIdDesc().stream()
                .map(termo -> comTotal(termo, aceitesPorVersao))
                .toList();
    }

    @Transactional(readOnly = true)
    public PaginaTermos listarPaginado(int pagina, StatusTermo status) {
        if (pagina < 0) {
            throw new RegraNegocioException("Informe uma página a partir de zero.");
        }

        var ordenacao = Sort.by(Sort.Direction.DESC, "criadoEm", "id");
        var paginacao = PageRequest.of(pagina, 10, ordenacao);
        var resultado = status == null
                ? termos.findAll(paginacao)
                : termos.findAllByStatus(status, paginacao);

        Map<String, Long> totais = contarAceites();
        var versoes = resultado.map(termo -> comTotal(termo, totais));
        var vigente = termos.findByStatus(StatusTermo.VIGENTE)
                .map(termo -> comTotal(termo, totais))
                .orElse(null);

        return new PaginaTermos(versoes.getContent(), versoes.getTotalPages(), vigente);
    }

    public record PaginaTermos(
            List<TermoVersaoResponse> content,
            int totalPages,
            TermoVersaoResponse vigente
    ) {}

    @Transactional(readOnly = true)
    public TermoVersaoResponse visualizar(UUID id) {
        TermoVersao termo = buscar(id);
        return TermoVersaoResponse.de(termo, contarAceites().getOrDefault(termo.getIdentificador(), 0L));
    }

    @Transactional
    public TermoVersaoResponse criar(TermoVersaoRequest request, UsuarioAtual solicitante) {
        String identificador = request.identificador().strip();
        if (termos.existsByIdentificadorIgnoreCase(identificador)) {
            throw new RegraNegocioException("Já existe uma versão com este identificador.");
        }

        Instant agora = clock.instant();
        TermoVersao termo = new TermoVersao();
        termo.setIdentificador(identificador);
        termo.setTermoUso(request.termoUso().strip());
        termo.setPoliticaPrivacidade(request.politicaPrivacidade().strip());
        termo.setStatus(StatusTermo.RASCUNHO);
        termo.setCriadoPor(solicitante.id());
        termo.setCriadoEm(agora);
        termo.setAtualizadoEm(agora);

        termos.saveAndFlush(termo);
        auditoria.aposCommit("TERMO_RASCUNHO_CRIADO", solicitante.id(), termo.getId());
        return TermoVersaoResponse.de(termo, 0);
    }

    @Transactional
    public TermoVersaoResponse atualizar(UUID id, AtualizarTermoVersaoRequest request, UsuarioAtual solicitante) {
        TermoVersao termo = buscar(id);
        exigirRascunho(termo, "Somente rascunhos podem ser editados.");
        exigirVersao(termo, request.versao());

        String identificador = request.identificador().strip();
        if (termos.existsByIdentificadorIgnoreCaseAndIdNot(identificador, id)) {
            throw new RegraNegocioException("Já existe uma versão com este identificador.");
        }

        termo.setIdentificador(identificador);
        termo.setTermoUso(request.termoUso().strip());
        termo.setPoliticaPrivacidade(request.politicaPrivacidade().strip());
        termo.setAtualizadoEm(clock.instant());

        termos.saveAndFlush(termo);
        auditoria.aposCommit("TERMO_RASCUNHO_EDITADO", solicitante.id(), termo.getId());
        return TermoVersaoResponse.de(termo, 0);
    }

    @Transactional
    public void excluir(UUID id, UsuarioAtual solicitante) {
        TermoVersao termo = buscar(id);
        exigirRascunho(termo, "Somente rascunhos podem ser excluídos.");

        termos.delete(termo);
        termos.flush();
        auditoria.aposCommit("TERMO_RASCUNHO_EXCLUIDO", solicitante.id(), id);
    }

    @Transactional
    public TermoVersaoResponse publicar(UUID id, Long versao, UsuarioAtual solicitante) {
        TermoVersao termo = buscar(id);
        exigirRascunho(termo, "Somente rascunhos podem ser publicados.");
        exigirVersao(termo, versao);
        Instant agora = clock.instant();

        termos.buscarPorStatusComBloqueio(StatusTermo.VIGENTE).ifPresent(anterior -> {
            anterior.setStatus(StatusTermo.ARQUIVADO);
            anterior.setAtualizadoEm(agora);
            termos.saveAndFlush(anterior);
        });

        termo.setStatus(StatusTermo.VIGENTE);
        termo.setPublicadoEm(agora);
        termo.setAtualizadoEm(agora);
        termos.saveAndFlush(termo);

        auditoria.aposCommit("TERMO_PUBLICADO", solicitante.id(), termo.getId());
        return TermoVersaoResponse.de(termo, contarAceites().getOrDefault(termo.getIdentificador(), 0L));
    }

    private TermoVersao buscar(UUID id) {
        return termos.findById(id).orElseThrow(TermoNaoEncontradoException::new);
    }

    private void exigirRascunho(TermoVersao termo, String mensagem) {
        if (termo.getStatus() != StatusTermo.RASCUNHO) {
            throw new RegraNegocioException(mensagem);
        }
    }

    private void exigirVersao(TermoVersao termo, Long versao) {
        if (!Objects.equals(termo.getVersao(), versao)) {
            throw new ConflitoVersaoException();
        }
    }

    private TermoVersaoResponse comTotal(TermoVersao termo, Map<String, Long> totais) {
        return TermoVersaoResponse.de(termo, totais.getOrDefault(termo.getIdentificador(), 0L));
    }

    private Map<String, Long> contarAceites() {
        return aceites.contarPorVersao().stream()
                .collect(Collectors.toMap(
                        AceiteTermoRepository.AceitesPorVersao::getIdentificador,
                        AceiteTermoRepository.AceitesPorVersao::getTotal
                ));
    }
}