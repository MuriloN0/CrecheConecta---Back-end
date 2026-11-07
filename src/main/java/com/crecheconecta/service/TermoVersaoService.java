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

/**
 * CRUD dos termos de uso e da política de privacidade, só para a DIRECAO.
 *
 * Regras: nasce como RASCUNHO; só rascunho pode ser editado ou excluído; publicar torna a versão
 * VIGENTE e arquiva a anterior (só existe uma vigente); a edição usa "versao" para barrar sobrescrita.
 */
@Service
@PreAuthorize("hasRole('DIRECAO')")
public class TermoVersaoService {

    private final TermoVersaoRepository termos;
    private final AceiteTermoRepository aceites;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public TermoVersaoService(TermoVersaoRepository termos, AceiteTermoRepository aceites, AuditoriaService auditoria, Clock clock) {
        this.termos = termos;
        this.aceites = aceites;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<TermoVersaoResponse> listar() {
        Map<String, Long> aceitesPorVersao = contarAceites();
        return termos.findAllByOrderByCriadoEmDescIdDesc().stream()
                .map(termo -> TermoVersaoResponse.de(termo, aceitesPorVersao.getOrDefault(termo.getIdentificador(), 0L)))
                .toList();
    }

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

        // saveAndFlush: o @Version só incrementa no flush; sem ele a resposta devolveria a versão antiga.
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

    /** Torna o rascunho a versão vigente e arquiva a que estava vigente. Todos os usuários precisarão aceitar de novo. */
    @Transactional
    public TermoVersaoResponse publicar(UUID id, Long versao, UsuarioAtual solicitante) {
        TermoVersao termo = buscar(id);
        exigirRascunho(termo, "Somente rascunhos podem ser publicados.");
        exigirVersao(termo, versao);

        Instant agora = clock.instant();

        // Arquiva a vigente primeiro: o índice único não aceita duas vigentes, nem por um instante.
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

    private Map<String, Long> contarAceites() {
        return aceites.contarPorVersao().stream()
                .collect(Collectors.toMap(AceiteTermoRepository.AceitesPorVersao::getIdentificador, AceiteTermoRepository.AceitesPorVersao::getTotal));
    }
}
